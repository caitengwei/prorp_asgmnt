package quickcheck

import common._

import org.scalacheck._
import Arbitrary._
import Gen._
import Prop._

abstract class QuickCheckHeap extends Properties("Heap") with IntHeap {

  property("min1") = forAll { a: Int =>
    val h = insert(a, empty)
    findMin(h) == a
  }

  property("min2") = forAll { (a: Int, b: Int) =>
    val h = insert(b, insert(a, empty))
    findMin(h) == Math.min(a, b)
  }

  property("gen1") = forAll { (h: H) =>
    val m = if (isEmpty(h)) 0 else findMin(h)
    findMin(insert(m, h))==m
  }

  property("meld1") = forAll { (h: H, i: H) =>
    val a = findMin(h)
    val b = findMin(i)
    val m = meld(h, i)
    findMin(m) == Math.min(a, b)
  }

  property("delete1") = forAll { (h: H, i: H) =>
    val m = meld(h, i)
    def flat(h: H): List[A] = isEmpty(h) match {
      case true => Nil
      case false => findMin(h) :: flat(deleteMin(h))
    }
    val l = flat(m)
    l == l.sorted
    val sl = flat(h) ++ flat(i)
    l == sl.sorted
  }

  lazy val genHeap: Gen[H] = for {
    v <- arbitrary[A]
    h <- oneOf(genHeap, const(empty))
  } yield insert(v, h)

  implicit lazy val arbHeap: Arbitrary[H] = Arbitrary(genHeap)

}
