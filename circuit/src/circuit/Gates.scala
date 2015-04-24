package circuit

/**
 * DESC:
 *
 * @author tengwei.ctw
 * @version $Id: Gates.java, v0.1 4/24/15 tengwei.ctw Exp $
 */
abstract class Gates extends Simulation {
  def InverterDelay: Int
  def AndGateDelay: Int
  def OrGateDelay: Int

  class Wire {
    private var signal = false
    private var actions: List[Action] = List()

    def getSignal = signal

    def setSignal(s: Boolean) = {
      if (s != signal) {
        signal = s
        actions foreach (_())
      }
    }

    def addAction(a: Action) = {
      actions = a :: actions
      a()
    }
  }

  def inverter(input: Wire, output: Wire) = {
    def invertAction() = {
      val inputSignal = input.getSignal
      afterDelay(InverterDelay) {
        output setSignal !inputSignal
      }
    }

    input addAction invertAction
  }

  def andGate(input1: Wire, input2: Wire, output: Wire) = {
    def andAction() = {
      val signal1 = input1.getSignal
      val signal2 = input2.getSignal
      afterDelay(AndGateDelay) {
        output setSignal signal1 & signal2
      }
    }

    input1 addAction andAction
    input2 addAction andAction
  }

  def orGate(input1: Wire, input2: Wire, output: Wire) = {
    def orAction() = {
      val signal1 = input1.getSignal
      val signal2 = input2.getSignal
      afterDelay(AndGateDelay) {
        output setSignal signal1 | signal2
      }
    }

    input1 addAction orAction
    input2 addAction orAction
  }

  def probe(name: String, wire: Wire) = {
    def probeAction() = {
      println(name + " " + currentTime + " new-value = " + wire.getSignal)
    }
    wire addAction probeAction
  }
}
