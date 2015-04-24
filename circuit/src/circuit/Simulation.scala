package circuit

/**
 * DESC:
 *
 * @author tengwei.ctw
 * @version $Id: Simulation.java, v0.1 4/24/15 tengwei.ctw Exp $
 */
trait Simulation {
  type Action = () => Unit
  case class Event(time: Int, action: Action)
  private type Agenda = List[Event]
  private var agenda: Agenda = List()
  private var curtime = 0
  def currentTime: Int = curtime

  def afterDelay(delay: Int)(block: => Unit): Unit = {
    val item = Event(currentTime + delay, () => block)
    agenda = insert(agenda, item)
  }
  def run(): Unit = {
    afterDelay(0) {
      println ("*** Simulation started, time = " + currentTime + " ***")
    }
    loop()
  }

  private def insert(agenda: Agenda, event: Event): Agenda = agenda match {
    case first :: rest if first.time <= event.time =>
      first :: insert(rest, event)
    case _ =>
      event :: agenda
  }

  private def loop(): Unit = agenda match {
    case first :: rest =>
      agenda = rest
      curtime = first.time
      first.action()
      loop()
    case Nil =>
  }
}
