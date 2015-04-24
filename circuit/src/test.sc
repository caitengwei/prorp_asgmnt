import circuit._

object test {
  println("Hello world")

  object sim extends Circuits with Parameters
  import sim._
  val in1, in2, sum, carry = new Wire

  in1 setSignal true

  halfAdder(in1, in2, sum, carry)
  probe("sum", sum)
  probe("carry", carry)
  run()

  in2 setSignal true
  run()

  in1 setSignal false
  run()

}