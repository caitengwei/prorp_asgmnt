package calculator

import scala.math.sqrt

object Polynomial {
  def computeDelta(a: Signal[Double], b: Signal[Double],
      c: Signal[Double]): Signal[Double] = {
    Signal(b() * b() - a() * c() * 4)
  }

  def computeSolutions(a: Signal[Double], b: Signal[Double],
      c: Signal[Double], delta: Signal[Double]): Signal[Set[Double]] = Signal {
    if (delta() < 0) {
      Set()
    } else {
      Set(
        (-b() + sqrt(delta())) / (2 * a()),
        (-b() - sqrt(delta())) / (2 * a())
      )
    }
  }
}
