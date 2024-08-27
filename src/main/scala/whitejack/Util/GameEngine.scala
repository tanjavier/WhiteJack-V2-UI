package whitejack.Util

import scalafx.beans.property.{BooleanProperty, StringProperty}
import whitejack.Model.{Card, Dealer, Player, User}

import scala.collection.mutable.ListBuffer

class GameEngine(val isHost: Boolean,
           val roomList: ListBuffer[User],
           val localPlayer: User) {

  var gameStatus: StringProperty = new StringProperty()
  var allPlayersConfirmedBet: BooleanProperty = BooleanProperty(false)
  var roundEnd: BooleanProperty = BooleanProperty(false)

  val maxHandSize: Int = 5
  var usersWithAction = new ListBuffer[User]
  for (user <- roomList) usersWithAction += user
  var currentUser: Option[User] = None
  val dealer = new Dealer()

  var numOfPlayer: Int = roomList.size
  var playerLeft: Option[Player] = None
  var playerMiddle: Option[Player] = None
  var playerRight: Option[Player] = None
  setPlayerPosition()

  def roundStart(): Unit = {
    roundEnd.value = false
    for (user <- roomList) {
      getPlayer(user).get.newHand()
    }
  }

  def setPlayerPosition(): Unit = {
    val tmpList = new ListBuffer[User]()
    for (user <- roomList) tmpList += user

    tmpList -= localPlayer
    playerMiddle = Option(new Player(localPlayer))

    if (tmpList.nonEmpty) {
      playerLeft = Option(new Player(tmpList.remove(0)))
    }
    if (tmpList.nonEmpty) {
      playerRight = Option(new Player(tmpList.remove(0)))
    }
  }

  def getPlayerPosition(user: User): Int = {
    for (p <- playerMiddle) {
      if (p.user == user) {
        return 2
      }
    }
    for (p <- playerLeft) {
      if (p.user == user) {
        return 1
      }
    }
    for (p <- playerRight) {
      if (p.user == user) {
        return 3
      }
    }
    0
  }

  def getPlayer(user: User): Option[Player] = {
    if (playerMiddle.get.user == user) {
      playerMiddle
    } else if (playerLeft.get.user == user) {
      playerLeft
    } else if (playerRight.get.user == user) {
      playerRight
    } else {
      null
    }
  }


  def playerConfirmBet(user: User): Unit = {
    getPlayer(user).get.betConfirmed = true

    var allConfirmedBetCheck = true
    for (player <- roomList) {
      if (!getPlayer(player).get.betConfirmed) {
        allConfirmedBetCheck = false
      }
    }
    allPlayersConfirmedBet.value = allConfirmedBetCheck
  }

  def getStartingCards: ListBuffer[CardUtil] = {
    val cardMetaList = new ListBuffer[CardUtil]()
    var tempCard: Card = Card("","")

    for (user <- roomList) {
      tempCard = dealer.getCard
      getPlayer(user).get.addToHand(tempCard)
      cardMetaList += CardUtil(isPlayer = true, user, tempCard, 1)
    }

    tempCard = dealer.getCard
    dealer.addToHand(tempCard)
    cardMetaList += CardUtil(isPlayer = false, localPlayer, tempCard, 1)

    for (user <- roomList) {
      tempCard = dealer.getCard
      getPlayer(user).get.addToHand(tempCard)
      cardMetaList += CardUtil(isPlayer = true, user, tempCard, 2)
    }

    tempCard = dealer.getCard
    dealer.addToHand(tempCard)
    cardMetaList += CardUtil(isPlayer = false, localPlayer, Card("back","card"), 2)

    cardMetaList
  }

  def getHandValue(hand : ListBuffer[Card]): Int = {
    var aceCount = 0
    var totalWithoutAces = 0
    val dict = Map("2" -> 2, "3" -> 3, "4" -> 4, "5" -> 5, "6" -> 6, "7" -> 7, "8" -> 8, "9" -> 9, "10" -> 10, "jack" -> 10, "queen" -> 10, "king" -> 10)

    for (card <- hand) {
      val rank = card.rank
      if (rank == "ace") {
        aceCount += 1
      } else {
        totalWithoutAces += dict(rank)
      }
    }

    if (aceCount == 0) {
      return totalWithoutAces
    } else if (aceCount == 2 && hand.size == 2) {
      return 21
    }

    val smallAce = 1
    var bigAce = 11
    if (hand.size > 2) {
      bigAce = 10
    }
    val aceCombination = new ListBuffer[Int]
    for (i <- 0 to aceCount) {
      val tmp = totalWithoutAces + ((i*smallAce) + (aceCount-i)*bigAce)
      if (tmp <= 21) {
        aceCombination += tmp
      }
    }

    if (aceCombination.isEmpty) {
      totalWithoutAces + aceCount
    } else {
      aceCombination.max
    }
  }

  def getCurrentUserTurn: Option[User] = {
    if (currentUser.isDefined) {
      return currentUser
    } else if (usersWithAction.nonEmpty) {
      currentUser = Option(usersWithAction.remove(0))
    }
    currentUser
  }

  def playerHit(user: User): CardUtil = {
    val tempCard = dealer.getCard
    val currentPlayer = getPlayer(user).get
    currentPlayer.addToHand(tempCard)

    if (currentPlayer.hand.size == maxHandSize || getHandValue(currentPlayer.hand) >= 21) {
      currentUser = None
    }
    CardUtil(isPlayer = true, user, tempCard, currentPlayer.hand.size)
  }

  def playerStand(user: User): Unit = {
    currentUser = None
  }

  def playerIncreaseBet(user: User): Int = {
    val currentUser = getPlayer(user).get
    currentUser.adjustBetAmt(20)
    currentUser.betAmt
  }

  def playerDecreaseBet(user: User): Int = {
    val currentUser = getPlayer(user).get
    currentUser.adjustBetAmt(-20)
    currentUser.betAmt
  }

  def prepareNewRound(): Unit = {
    usersWithAction.clear()
    for (user <- roomList) {
      getPlayer(user).get.betConfirmed = false
      usersWithAction += user
    }
    if (usersWithAction.nonEmpty) {
      currentUser = Option(usersWithAction.remove(0))
    }
    dealer.newHand()
    allPlayersConfirmedBet.value = false
  }

  def getDealerHand: ListBuffer[CardUtil] = {
    while (getHandValue(dealer.hand) < 15) {
      dealer.addToHand(dealer.getCard)
    }

    val cardMetaList = new ListBuffer[CardUtil]
    for(card <- dealer.hand) {
      cardMetaList += CardUtil(isPlayer = false, localPlayer, card, (cardMetaList.size+1))
    }
    roundEnd.value = true
    cardMetaList
  }

  def isHouseWin: Boolean = {
    val dealerHandValue = getHandValue(dealer.hand)
    var bestPlayerHandValue = 0
    for (user <- roomList) {
      val playerHandValue = getHandValue(getPlayer(user).get.hand)
      if (playerHandValue <= 21 && playerHandValue > bestPlayerHandValue) {
        bestPlayerHandValue = playerHandValue
      }
    }

    if (dealerHandValue <= 21 && dealerHandValue > bestPlayerHandValue) {
      true
    } else {
      false
    }
  }

  def getPlayerResult(user: User): String = {
    val currentUser = getPlayer(user).get
    val currentUserHandValue = getHandValue(currentUser.hand)
    val dealerHandValue = getHandValue(dealer.hand)

    if (currentUserHandValue <= 21 && currentUserHandValue > dealerHandValue) {
      adjustPlayerBalance(currentUser, isPlayerWin = true)
      "You won!"
    } else if (currentUserHandValue > 21 && dealerHandValue > 21) {
      "Both are bust!"
    } else if (currentUserHandValue == dealerHandValue) {
      "A tie!"
    } else if (currentUserHandValue > 21 && dealerHandValue <=21) {
      adjustPlayerBalance(currentUser, isPlayerWin = false)
      "You lost!"
    } else if (dealerHandValue > 21 && currentUserHandValue <= 21) {
      adjustPlayerBalance(currentUser, isPlayerWin = true)
      "You won!"
    } else if (currentUserHandValue < dealerHandValue) {
      adjustPlayerBalance(currentUser, isPlayerWin = false)
      "You lost!"
    } else {
      "An unexpected error occurred."
    }
  }

  def adjustPlayerBalance(player: Player, isPlayerWin: Boolean): Unit = {
    if (isPlayerWin) {
      player.balance += player.betAmt
    } else {
      player.balance -= player.betAmt
      player.adjustBetAmt(0)
    }
  }

}
