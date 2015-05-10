package nodescala

import scala.language.postfixOps
import scala.util.{Try, Success, Failure}
import scala.collection._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.async.Async.{async, await}
import org.scalatest._
import NodeScala._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class test extends FunSuite {

  test("A Future should always be completed") {
    val always = Future.always(517)

    assert(Await.result(always, 0 nanos) == 517)
  }
  test("A Future should never be completed") {
    val never = Future.never[Int]

    try {
      Await.result(never, 1 second)
      assert(false)
    } catch {
      case t: TimeoutException => // ok!
    }
  }
  test("All turns lists of future into future of list") {
    val futures = List(Future(1), Future(2), Future(3))
    val no_futures = List(Future(1), Future(2), Future.never[Int], Future(4))

    val rst1 = Future.all(futures)
    val rst2 = Future.all(no_futures)

    assert(Await.result(rst1, 1 second) == Await.result(Future(List(1, 2, 3)), 1 second))
    try {
      Await.result(rst2, 1 second)
      assert(false)
    } catch {
      case t: TimeoutException => // ok!
    }
  }
  test("Any return the first completed one") {
    val futures = List(Future(1), Future(2), Future.never[Int])

    val rst1 = Future.any(futures)
    val expected = List(1, 2)

    assert(expected.contains(Await.result(rst1, 1 second)))
    assert(expected.contains(Await.result(rst1, 1 second)))
    assert(expected.contains(Await.result(rst1, 1 second)))
  }
  test("FutureCompanionOps.delay test") {
    Await.result(Future.delay(0.3 second), 10 second)
    try {
      Await.result(Future.delay(0.3 second), 0.1 second)
    } catch {
      case t: TimeoutException => // ok
      // otherwise do not catch
    }
    Await.result(Future.delay(0.1 second), 0.5 second)
    try {
      Await.result(Future.delay(0.1 second), 0.05 second)
    } catch {
      case t: TimeoutException => // ok
      // otherwise do not catch
    }
  }

  test("FutureOps.now test") {
    val f = Future( 1 )
    val g = Future( throw new RuntimeException("Synthetic") )
    val h = Future( blocking { Thread.sleep(100); 1 } )

    assert(f.now === 1)
    try {
      g.now
    } catch {
      case e: RuntimeException =>
        if (e.getMessage() != "Synthetic")
          assert(false)
      // else ok
      // otherwise do not catch
    }
    try {
      h.now
    } catch {
      case e: NoSuchElementException => // ok
    }
  }
  test("FutureOps.continueWith test") {
    def contCareless(a: Future[Int]): String = (Await.result(a, 1 second) + 1).toString
    val f = Future( 1 )
    assert(Await.result(f.continueWith(contCareless), 1 second) === "2")

    def contThrow(a: Future[Int]): String = throw new RuntimeException("Synthetic")
    try Await.result(f.continueWith(contThrow), 1 second) catch {
      case e: RuntimeException =>
        if (e.getMessage() != "Synthetic")
          assert(false)
      // else ok
      // otherwise do not catch
    }

    val g: Future[Int] = Future( throw new RuntimeException("g failed") )
    try Await.result(g.continueWith(contCareless), 1 second) catch {
      case e: RuntimeException =>
        if (e.getMessage() != "g failed")
          assert(false)
      // else ok
      // otherwise do not catch
    }

    def contThrow2(a: Future[Int]): String = try {
      (Await.result(a, 1 second) + 1).toString
    } catch {
      case _:Exception => throw new RuntimeException("Supressed")
    }
    try Await.result(g.continueWith(contThrow2), 1 second) catch {
      case e: RuntimeException =>
        if (e.getMessage() != "Supressed")
          assert(false)
      // else ok
      // otherwise do not catch
    }
    assert(Await.result(f.continueWith(contThrow2), 1 second) === "2")
  }

  test("FutureOps.continue test") {
    def contCareless(a: Try[Int]): String = (a.get + 1).toString
    val f = Future( 1 )
    assert(Await.result(f.continue(contCareless), 1 second) === "2")

    def contThrow(a: Try[Int]): String = throw new RuntimeException("Synthetic")
    try Await.result(f.continue(contThrow), 1 second) catch {
      case e: RuntimeException =>
        if (e.getMessage() != "Synthetic")
          assert(false)
      // else ok
      // otherwise do not catch
    }

    val g: Future[Int] = Future( throw new RuntimeException("g failed") )
    try Await.result(g.continue(contCareless), 1 second) catch {
      case e: RuntimeException =>
        if (e.getMessage() != "g failed")
          assert(false)
      // else ok
      // otherwise do not catch
    }

    def contThrow2(a: Try[Int]): String = a match {
      case Success(x) => (x + 1).toString
      case Failure(_) => throw new RuntimeException("Supressed")
    }
    try Await.result(g.continue(contThrow2), 1 second) catch {
      case e: RuntimeException =>
        if (e.getMessage() != "Supressed")
          assert(false)
      // else ok
      // otherwise do not catch
    }
    assert(Await.result(f.continue(contThrow2), 1 second) === "2")
  }
  test("FutureCompanionOps.run test") {
    val p = Promise[String]()
    val working = Future.run() { ct =>
      Future {
        while (ct.nonCancelled) {
          Thread.sleep(100)
          println("working")
        }
        println("done")
        p.success("done")
      }
    }
    Future.delay(.5 seconds) onSuccess {
      case _ => working.unsubscribe()
    }
    assert(Await.result(p.future, 1 second) == "done")
  }
  test("CancellationTokenSource should allow stopping the computation") {
    val cts = CancellationTokenSource()
    val ct = cts.cancellationToken
    val p = Promise[String]()

    async {
      while (ct.nonCancelled) {
        // do work
      }

      p.success("done")
    }

    cts.unsubscribe()
    assert(Await.result(p.future, 1 second) == "done")
  }

  class DummyExchange(val request: Request) extends Exchange {
    @volatile var response = ""
    val loaded = Promise[String]()
    def write(s: String) {
      response += s
    }
    def close() {
      loaded.success(response)
    }
  }

  class DummyListener(val port: Int, val relativePath: String) extends NodeScala.Listener {
    self =>

    @volatile private var started = false
    var handler: Exchange => Unit = null

    def createContext(h: Exchange => Unit) = this.synchronized {
      assert(started, "is server started?")
      handler = h
    }

    def removeContext() = this.synchronized {
      assert(started, "is server started?")
      handler = null
    }

    def start() = self.synchronized {
      started = true
      new Subscription {
        def unsubscribe() = self.synchronized {
          started = false
        }
      }
    }

    def emit(req: Request) = {
      val exchange = new DummyExchange(req)
      if (handler != null) handler(exchange)
      exchange
    }
  }

  class DummyServer(val port: Int) extends NodeScala {
    self =>
    val listeners = mutable.Map[String, DummyListener]()

    def createListener(relativePath: String) = {
      val l = new DummyListener(port, relativePath)
      listeners(relativePath) = l
      l
    }

    def emit(relativePath: String, req: Request) = this.synchronized {
      val l = listeners(relativePath)
      l.emit(req)
    }
  }
  test("Server should serve requests") {
    val dummy = new DummyServer(8191)
    val dummySubscription = dummy.start("/testDir") {
      request => for (kv <- request.iterator) yield (kv + "\n").toString
    }

    // wait until server is really installed
    Thread.sleep(500)

    def test(req: Request) {
      val webpage = dummy.emit("/testDir", req)
      val content = Await.result(webpage.loaded.future, 1 second)
      val expected = (for (kv <- req.iterator) yield (kv + "\n").toString).mkString
      assert(content == expected, s"'$content' vs. '$expected'")
    }

    test(immutable.Map("StrangeRequest" -> List("Does it work?")))
    test(immutable.Map("StrangeRequest" -> List("It works!")))
    test(immutable.Map("WorksForThree" -> List("Always works. Trust me.")))

    dummySubscription.unsubscribe()
  }

}




