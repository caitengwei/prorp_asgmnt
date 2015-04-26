package calculator

import scala.math.sqrt

object Polynomial {
  def computeDelta(a: Signal[Double], b: Signal[Double],
      c: Signal[Double]): Signal[Double] = {
    Signal(b() * b() - a() * c() * 4)
  }

  def computeSolutions(a: Signal[Double], b: Signal[Double],
      c: Signal[Double], delta: Signal[Double]): Signal[Set[Double]] = {
    def mySqrt(x: Signal[Double]): Signal[Double] = {
      if (x() < 0) Signal(0)
      else Signal(sqrt(x()))
    }
    Signal(
      Set(
        (-b() + mySqrt(computeDelta(a, b, c))()) / (2 * a()),
        (-b() - mySqrt(computeDelta(a, b, c))()) / (2 * a())
      )
    )
  }
}
