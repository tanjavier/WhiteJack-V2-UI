package whitejack.Controller

import scalafxml.core.macros.sfxml
import whitejack.{Instruction, Lobby}

@sfxml
class MainMenuController {
  def handleStart(): Unit = {
    Lobby.load()
  }

  def handleInstruction(): Unit = {
    Instruction.load()
  }

  def handleQuit(): Unit = {
    System.exit(0)
  }
}