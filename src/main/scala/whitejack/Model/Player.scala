package whitejack.Model

import scala.collection.mutable.ListBuffer

class Player(val user: User) {
  val hand: ListBuffer[Card] = ListBuffer[Card]()
  var balance = 1000
  var betAmt = 20
  var betConfirmed = false

  def newHand(): Unit = {
    hand.clear()
  }

  def addToHand(card: Card): Unit = {
    hand += card
  }

  def adjustBetAmt(amt: Int): Unit = {
    betAmt += amt
    if (betAmt > balance) {
      betAmt = balance
    }
    if (betAmt < 0) {
      betAmt = 0
    }
  }
}
