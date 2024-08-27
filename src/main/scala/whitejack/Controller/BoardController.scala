package whitejack.Controller

import scalafx.animation.AnimationTimer
import scalafx.event.ActionEvent
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.{Alert, Button, ButtonType, ListView}
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.text.Text
import scalafxml.core.macros.sfxml
import whitejack.Model.User
import whitejack.Util.{CardUtil, ClientUtil, GameEngine}
import whitejack.{GameClient, GameClientApp, Lobby}

import scala.collection.mutable.ListBuffer

@sfxml
class BoardController (
  private var playerLeftName: Text,
  private var playerMiddleName: Text,
  private var playerRightName: Text,
  private var playerLeftBetLabel: Text,
  private var playerLeftBalLabel: Text,
  private var playerLeftMat: ListView[String],
  private var playerRightBetLabel: Text,
  private var playerRightBalLabel: Text,
  private var playerRightMat: ListView[String],
  private var playerLeftCard1: ImageView,
  private var playerLeftCard2: ImageView,
  private var playerLeftCard3: ImageView,
  private var playerLeftCard4: ImageView,
  private var playerLeftCard5: ImageView,
  private var playerMiddleCard1: ImageView,
  private var playerMiddleCard2: ImageView,
  private var playerMiddleCard3: ImageView,
  private var playerMiddleCard4: ImageView,
  private var playerMiddleCard5: ImageView,
  private var playerRightCard1: ImageView,
  private var playerRightCard2: ImageView,
  private var playerRightCard3: ImageView,
  private var playerRightCard4: ImageView,
  private var playerRightCard5: ImageView,
  private var playerTopCard1: ImageView,
  private var playerTopCard2: ImageView,
  private var playerTopCard3: ImageView,
  private var playerTopCard4: ImageView,
  private var playerTopCard5: ImageView,
  private var statusText: Text,
  private var hitBtn: Button,
  private var standBtn: Button,
  private var increaseBetBtn: Button,
  private var decreaseBetBtn: Button,
  private var confirmBetBtn: Button,
  private var playerLeftBet: Text,
  private var playerMiddleBet: Text,
  private var playerRightBet: Text,
  private var playerLeftBal: Text,
  private var playerMiddleBal: Text,
  private var playerRightBal: Text,
  private var nextRoundBtn: Button) {

  val blankImg = new Image(getClass.getResourceAsStream("/whitejack/img/blank.png"))

  clearBoard()

  nextRoundBtn.disable = true
  nextRoundBtn.visible = false
  playerMiddleName.text = ClientUtil.ownName

  private val localPlayer = User(ClientUtil.ownName, GameClientApp.ownRef)
  var game = new GameEngine(ClientUtil.isHost, ClientUtil.roomList.to[ListBuffer], localPlayer)

  if (game.playerLeft.isDefined) {
    playerLeftName.text = game.playerLeft.get.user.name
    playerLeftBetLabel.text = s"${game.playerLeft.get.user.name}'s Bet:"
    playerLeftBalLabel.text = s"${game.playerLeft.get.user.name}'s Balance:"
  } else {
    playerLeftBetLabel.setVisible(false)
    playerLeftBalLabel.setVisible(false)
    playerLeftBet.setVisible(false)
    playerLeftBal.setVisible(false)
    playerLeftMat.setVisible(false)
    playerLeftName.setVisible(false)
  }

  if (game.playerRight.isDefined) {
    playerRightName.text = game.playerRight.get.user.name
    playerRightBetLabel.text = s"${game.playerRight.get.user.name}'s Bet:"
    playerRightBalLabel.text = s"${game.playerRight.get.user.name}'s Balance:"
  } else {
    playerRightBetLabel.setVisible(false)
    playerRightBalLabel.setVisible(false)
    playerRightBet.setVisible(false)
    playerRightBal.setVisible(false)
    playerRightMat.setVisible(false)
    playerRightName.setVisible(false)
  }

  var cardMetaList = new ListBuffer[CardUtil]
  var time = 0L
  val timerCard: AnimationTimer = AnimationTimer(t => {
    if (cardMetaList.isEmpty) {
      timerCard.stop
      if (game.getCurrentUserTurn.isDefined && game.allPlayersConfirmedBet.value) {
        announceTurn(game.getCurrentUserTurn.get)
      }
    }
    if ((t - time) > 0.333e9) {
      val currentCard = cardMetaList.remove(0)
      for (user <- ClientUtil.roomList) {
        ClientUtil.ownRef.foreach(_ ! GameClient.GiveCard(currentCard, user))
      }
      time = t
    }
  })

  if (ClientUtil.isHost) {
    game.roundStart()
    roundStart()
    nextRoundBtn.visible = true
  }

  game.allPlayersConfirmedBet.onChange((_, old, newV) => {
    if (game.allPlayersConfirmedBet.value && ClientUtil.isHost) {
      cardMetaList = game.getStartingCards
      timerCard.start
    }
  })

  val timerTurn: AnimationTimer = AnimationTimer(t => {
    if (time == 0L) {
      time = t
    }
    if ((t - time) > 0.5e9) {
      val nextPlayer = game.getCurrentUserTurn
      if (nextPlayer.isDefined) {
        announceTurn(nextPlayer.get)
      } else {
        roundEnd()
      }
      timerTurn.stop
    }
  })

  def roundStart(): Unit = {
    nextRoundBtn.disable = true
    clearBoard()
    game.roundStart()
    for (user <- ClientUtil.roomList) {
      if (user.ref != ClientUtil.hostRef.get) {
        ClientUtil.ownRef.foreach(_ ! GameClient.RoundStart(user))
      }
    }
  }

  def roundStartReceive(): Unit = {
    clearBoard()
    game.roundStart()
  }

  def updateCard(cardMeta: CardUtil): Unit = {
    var playerInt = game.getPlayerPosition(cardMeta.user)
    val currentPlayer = game.getPlayer(cardMeta.user).get

    if (!cardMeta.isPlayer) {
      playerInt = 0

      if (ClientUtil.isHost && cardMeta.cardPosition == game.dealer.hand.size && game.roundEnd.value) {
        nextRoundBtn.disable = false
      }
    }

    if (cardMeta.isPlayer && !ClientUtil.isHost) {
      currentPlayer.addToHand(cardMeta.card)
    }

    val cardImg = new Image(getClass.getResourceAsStream(s"/whitejack/img/cards/${cardMeta.card.toString}.png"))

    if (playerInt == 0) {
      if (cardMeta.cardPosition == 1) playerTopCard1.image = cardImg
      else if (cardMeta.cardPosition == 2) playerTopCard2.image = cardImg
      else if (cardMeta.cardPosition == 3) playerTopCard3.image = cardImg
      else if (cardMeta.cardPosition == 4) playerTopCard4.image = cardImg
      else if (cardMeta.cardPosition == 5) playerTopCard5.image = cardImg
    }
    else if (playerInt == 1) {
      if (cardMeta.cardPosition == 1) playerLeftCard1.image = cardImg
      else if (cardMeta.cardPosition == 2) playerLeftCard2.image = cardImg
      else if (cardMeta.cardPosition == 3) playerLeftCard3.image = cardImg
      else if (cardMeta.cardPosition == 4) playerLeftCard4.image = cardImg
      else if (cardMeta.cardPosition == 5) playerLeftCard5.image = cardImg
    }
    else if (playerInt == 2) {
      if (cardMeta.cardPosition == 1) playerMiddleCard1.image = cardImg
      else if (cardMeta.cardPosition == 2) playerMiddleCard2.image = cardImg
      else if (cardMeta.cardPosition == 3) playerMiddleCard3.image = cardImg
      else if (cardMeta.cardPosition == 4) playerMiddleCard4.image = cardImg
      else if (cardMeta.cardPosition == 5) playerMiddleCard5.image = cardImg
    }
    else if (playerInt == 3) {
      if (cardMeta.cardPosition == 1) playerRightCard1.image = cardImg
      else if (cardMeta.cardPosition == 2) playerRightCard2.image = cardImg
      else if (cardMeta.cardPosition == 3) playerRightCard3.image = cardImg
      else if (cardMeta.cardPosition == 4) playerRightCard4.image = cardImg
      else if (cardMeta.cardPosition == 5) playerRightCard5.image = cardImg
    }
  }

  def announceTurn(currentUser: User): Unit = {
    for (user <- ClientUtil.roomList) {
      ClientUtil.ownRef.foreach(_ ! GameClient.AnnounceTurn(currentUser, user))
    }
  }

  def announceTurnReceive(currentUser: User): Unit = {
    if (currentUser.ref == ClientUtil.ownRef.get) {
      statusText.text = "Your Move!"
      hitBtn.disable = false
      standBtn.disable = false
    } else {
      statusText.text = s"${currentUser.name}'s turn!"
      hitBtn.disable = true
      standBtn.disable = true
    }
  }

  def playerHit(action: ActionEvent): Unit = {
    ClientUtil.hostRef.foreach(_ ! GameClient.PlayerHit(localPlayer))
    hitBtn.disable = true
    standBtn.disable = true
    increaseBetBtn.disable = true
    decreaseBetBtn.disable = true
  }

  def playerHitReceive(currentUser: User): Unit = {
    val cardMeta = game.playerHit(currentUser)
    for (user <- GameClient.roomList) {
      ClientUtil.ownRef.foreach(_ ! GameClient.GiveCard(cardMeta, user))
    }
    time = 0L
    timerTurn.start
  }

  def playerStand(action: ActionEvent): Unit = {
    ClientUtil.hostRef.foreach(_ ! GameClient.PlayerStand(localPlayer))
    hitBtn.disable = true
    standBtn.disable = true
    increaseBetBtn.disable = true
    decreaseBetBtn.disable = true
  }

  def playerStandReceive(currentUser: User): Unit = {
    game.playerStand(currentUser)
    time = 0L
    timerTurn.start
  }

  def increaseBet(action: ActionEvent): Unit = {
    ClientUtil.ownRef.foreach(_ ! GameClient.IncreaseBetSend(ClientUtil.hostRef.get))
  }

  def decreaseBet(action: ActionEvent): Unit = {
    ClientUtil.ownRef.foreach(_ ! GameClient.DecreaseBetSend(ClientUtil.hostRef.get))
  }

  def increaseBetReceive(user: User): Unit = {
    updateBet(user, game.playerIncreaseBet(user))
  }

  def decreaseBetReceive(user: User): Unit = {
    updateBet(user, game.playerDecreaseBet(user))
  }

  def updateBet(user: User, amt: Int): Unit = {
    for (target <- ClientUtil.roomList) {
      ClientUtil.ownRef.foreach(_ ! GameClient.UpdateBet(target, user, amt))
    }
  }

  def updateBetReceive(user: User, amt: Int): Unit = {
    var playerInt = game.getPlayerPosition(user)

    if (playerInt == 1) {
      playerLeftBet.text = s"${amt}"
    } else if (playerInt == 2) {
      playerMiddleBet.text = s"${amt}"
    } else if (playerInt == 3) {
      playerRightBet.text = s"${amt}"
    }
  }

  def confirmBet(action: ActionEvent): Unit = {
    for (player <- ClientUtil.roomList) {
      val currentUser = game.getPlayer(player).get
      if (currentUser.betAmt == 0) {
        new Alert(AlertType.Warning) {
          title = "ERROR"
          headerText = "Your bet cannot be zero!"
          contentText = "Please set a minimum of 20."
        }.showAndWait()
      } else {
        ClientUtil.ownRef.foreach(_ ! GameClient.ConfirmBetSend(ClientUtil.hostRef.get))
      }
    }
  }

  def confirmBetReceiveAck(): Unit = {
    increaseBetBtn.disable = true
    decreaseBetBtn.disable = true
    confirmBetBtn.disable = true
  }

  def confirmBetReceive(from: User): Unit = {
    game.playerConfirmBet(from)
  }

  def updateBalAndBet(): Unit = {
    for (player <- ClientUtil.roomList) {
      val currentUser = game.getPlayer(player).get
      for (target <- ClientUtil.roomList) {
        ClientUtil.ownRef.foreach(_ ! GameClient.UpdateBalAndBet(target, player, currentUser.balance, currentUser.betAmt))
      }
    }
  }

  def updateBalAndBetReceive(user: User, bal: Int, bet: Int): Unit = {
    val playerInt = game.getPlayerPosition(user)

    if (playerInt == 1) {
      playerLeftBal.text = s"${bal}"
      playerLeftBet.text = s"${bet}"
    } else if (playerInt == 2) {
      playerMiddleBal.text = s"${bal}"
      playerMiddleBet.text = s"${bet}"
    } else if (playerInt == 3) {
      playerRightBal.text = s"${bal}"
      playerRightBet.text = s"${bet}"
    }
  }

  def roundEnd(): Unit = {
    this.cardMetaList = game.getDealerHand
    timerCard.start

    val isHouseWin = game.isHouseWin
    for (user <- GameClient.roomList) {
      if (isHouseWin) {
        ClientUtil.ownRef.foreach(_ ! GameClient.AnnounceHouseWin(user))
      }
      ClientUtil.ownRef.foreach(_ ! GameClient.AnnounceWinResult(user, game.getPlayerResult(user)))
    }
    updateBalAndBet()
  }

  def announceHouseWin(): Unit = {}

  def announceWinResult(playerResult: String): Unit = {
    statusText.text = playerResult
    if (playerResult == "You won!") {
    }
  }

  def hostStartNextRound(action: ActionEvent): Unit = {
    game.prepareNewRound()
    roundStart()
  }

  def clearBoard(): Unit = {
    statusText.text = "Place your bets!"
    playerLeftCard1.image = blankImg
    playerLeftCard2.image = blankImg
    playerLeftCard3.image = blankImg
    playerLeftCard4.image = blankImg
    playerLeftCard5.image = blankImg
    playerMiddleCard1.image = blankImg
    playerMiddleCard2.image = blankImg
    playerMiddleCard3.image = blankImg
    playerMiddleCard4.image = blankImg
    playerMiddleCard5.image = blankImg
    playerRightCard1.image = blankImg
    playerRightCard2.image = blankImg
    playerRightCard3.image = blankImg
    playerRightCard4.image = blankImg
    playerRightCard5.image = blankImg
    playerTopCard1.image = blankImg
    playerTopCard2.image = blankImg
    playerTopCard3.image = blankImg
    playerTopCard4.image = blankImg
    playerTopCard5.image = blankImg
    hitBtn.disable = true
    standBtn.disable = true
    increaseBetBtn.disable = false
    decreaseBetBtn.disable = false
    confirmBetBtn.disable = false
  }

  def updateStatusText(text: String): Unit = {
    statusText.text = text
  }

  def leaveGame(action: ActionEvent): Unit = {
    val alert = new Alert(AlertType.Warning) {
      title = "Leave room"
      headerText = "Leave room"
      contentText = "Are you sure to leave the room?"
    }

    val result = alert.showAndWait()
    result match {
      case Some(ButtonType.OK) =>
        ClientUtil.isPlaying = false
        if (ClientUtil.isHost) {
          for (user <- ClientUtil.roomList) {
            if (user.ref != GameClientApp.ownRef) {
              ClientUtil.ownRef.foreach(_ ! GameClient.HostLeaveRoom(user))
            }
          }
        } else {
          for (user <- ClientUtil.roomList) {
            if (user.ref != GameClientApp.ownRef) {
              ClientUtil.ownRef.foreach(_ ! GameClient.ClientLeaveRoom(user))
            }
          }
        }
        ClientUtil.ownRef.foreach(_ ! GameClient.ResetRoomList)
        Lobby.load()
      case _ =>
    }
  }

  def clientSuddenLeft(user: User): Unit = {
    val playerPosition = game.getPlayerPosition(user)
    game.roomList -= user

    if (playerPosition == 1) {
      playerLeftName.setVisible(false)
      playerLeftMat.setVisible(false)
      playerLeftCard1.setVisible(false)
      playerLeftCard2.setVisible(false)
      playerLeftCard3.setVisible(false)
      playerLeftCard4.setVisible(false)
      playerLeftCard5.setVisible(false)
      playerLeftBalLabel.setVisible(false)
      playerLeftBal.setVisible(false)
      playerLeftBet.setVisible(false)
      playerLeftBetLabel.setVisible(false)
    } else if (playerPosition == 3) {
      playerRightName.setVisible(false)
      playerRightMat.setVisible(false)
      playerRightCard1.setVisible(false)
      playerRightCard2.setVisible(false)
      playerRightCard3.setVisible(false)
      playerRightCard4.setVisible(false)
      playerRightCard5.setVisible(false)
      playerRightBalLabel.setVisible(false)
      playerRightBal.setVisible(false)
      playerRightBet.setVisible(false)
      playerRightBetLabel.setVisible(false)
    }

    if (ClientUtil.isHost) {
      nextRoundBtn.disable = false
      new Alert(AlertType.Warning) {
        title = "ERROR"
        headerText = s"$user has left the room"
        contentText = "Please start a new round."
      }.showAndWait()
    }
  }
}