package kvstore

import akka.actor._

import scala.concurrent.duration._
import scala.language.postfixOps

object Replica {

  sealed trait Operation {
    def key: String

    def id: Long
  }

  case class Insert(key: String, value: String, id: Long) extends Operation

  case class Remove(key: String, id: Long) extends Operation

  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply

  case class OperationAck(id: Long) extends OperationReply

  case class OperationFailed(id: Long) extends OperationReply

  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {

  import context.dispatcher
  import kvstore.Arbiter._
  import kvstore.Persistence._
  import kvstore.Replica._
  import kvstore.Replicator._

  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  case class ReplicateFailure(id: Long)

  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]

  var currSeq = 0L
  var replicateQueue = Map.empty[Long, (ActorRef, Set[ActorRef])]
  var replicateSchedulers = Map.empty[Long, Cancellable]
  var persistQueue = Map.empty[Long, ActorRef]
  var persistSchedulers = Map.empty[Long, Cancellable]

  arbiter ! Join
  val persistence = context.actorOf(persistenceProps)

  def receive = {
    case JoinedPrimary => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  /* TODO Behavior for  the leader role. */
  val leader: Receive = {
    case Insert(key, value, id) => insert(key, value, id)
    case Remove(key, id) => remove(key, id)
    case Get(key, id) => get(key, id)
    case Persisted(key, id) =>
      persistSchedulers(id).cancel()
      persistSchedulers -= id
      val origSender = persistQueue(id)
      persistQueue -= id
      if (!replicateQueue.contains(id)) {
        replicateSchedulers(id).cancel()
        replicateSchedulers -= id
        origSender ! OperationAck(id)
      }
    case Replicated(key, id) =>
      if (replicateQueue.contains(id)) {
        val (origSender, currAckSet) = replicateQueue(id)
        val newAckSet = currAckSet - origSender
        if (newAckSet.isEmpty)
          replicateQueue -= id
        else
          replicateQueue = replicateQueue.updated(id, (origSender, newAckSet))
        if (!replicateQueue.contains(id) && !persistQueue.contains(id)) {
          replicateSchedulers(id).cancel()
          replicateSchedulers -= id
          origSender ! OperationAck(id)
        }
      }
    case ReplicateFailure(id) =>
      if (replicateSchedulers.contains(id)) {
        if (persistSchedulers.contains(id)) {
          persistSchedulers(id).cancel()
          persistSchedulers -= id
        }
        replicateSchedulers -= id

        val origSender =
          if (persistQueue.contains(id)) persistQueue(id)
          else replicateQueue(id)._1
        persistQueue -= id
        replicateQueue -= id
        origSender ! OperationAck(id) // FIXME should return OperationFailure
      }
    case Replicas(replicas) =>
      val secondaryReplicas = replicas.filterNot(_ == self)

      val removedReplicas = secondaries.keySet -- secondaryReplicas
      removedReplicas.foreach(replica => secondaries(replica) ! PoisonPill)
      removedReplicas.foreach {
        replica =>
          replicateQueue.foreach {
            case (id, (origSender, rs)) =>
              if (rs.contains(secondaries(replica))) {
                self.tell(Replicated("", id), secondaries(replica))
              }
          }
      }

      val newReplicas = secondaryReplicas -- secondaries.keySet
      var newSecondaries = Map.empty[ActorRef, ActorRef]
      val newReplicators = newReplicas.map {
        replica => {
          val replicator = context.actorOf(Replicator.props(replica))
          newSecondaries += replica -> replicator
          replicator
        }
      }

      replicators = replicators -- removedReplicas.map(secondaries) ++ newReplicators
      secondaries = secondaries -- removedReplicas ++ newSecondaries
      newReplicators.foreach { replicator =>
        kv.zipWithIndex.foreach {
          case ((k, v), idx) =>
            replicator ! Replicate(k, Some(v), idx)
        }
      }
    case _ => println("Wrong Message")
  }

  /* TODO Behavior for the replica role. */
  val replica: Receive = {
    case Get(key, id) => get(key, id)
    case Snapshot(key, valueOption, seq) =>
      if (seq < currSeq)
        sender ! SnapshotAck(key, seq)
      if (seq == currSeq) {
        valueOption match {
          case None => kv -= key
          case Some(value) => kv += (key -> value)
        }
        currSeq += 1
        persistQueue += seq -> sender
        persistSchedulers += seq -> context.system.scheduler.schedule(
          0 millis, 100 millis, persistence, Persist(key, valueOption, seq))
      }
    case Persisted(key, id) =>
      val origSender = persistQueue(id)
      persistQueue -= id
      persistSchedulers(id).cancel()
      persistSchedulers -= id
      origSender ! SnapshotAck(key, id)
    case _ => println("Wrong Message")
  }

  def insert(key: String, value: String, id: Long) = {
    kv += (key -> value)
    replicateAndPersistent(key, Some(value), id)
  }

  def remove(key: String, id: Long) = {
    kv -= key
    replicateAndPersistent(key, None, id)
  }

  def get(key: String, id: Long) = {
    if (kv contains key) sender ! GetResult(key, Some(kv(key)), id)
    else sender ! GetResult(key, None, id)
  }

  def replicateAndPersistent(key: String, valueOption: Option[String], id: Long) = {
    persistQueue += id -> sender
    persistSchedulers += id -> context.system.scheduler.schedule(
      0 millis, 100 millis, persistence, Persist(key, valueOption, id))

    if (replicators.nonEmpty) {
      replicateQueue += id ->(sender, replicators)
      replicators.foreach { replicator =>
        replicator ! Replicate(key, valueOption, id)
      }
    }
    replicateSchedulers += id -> context.system.scheduler.scheduleOnce(1 second) {
      self ! ReplicateFailure(id)
    }
  }
}

