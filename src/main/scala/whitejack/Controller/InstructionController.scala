package whitejack.Controller

import scalafxml.core.macros.sfxml
import whitejack.MainMenu

@sfxml
class InstructionController {
  def handleClose(): Unit = {
    MainMenu.load()
  }
}