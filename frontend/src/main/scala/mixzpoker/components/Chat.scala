package mixzpoker.components

import io.circe.syntax._
import io.circe.parser.decode
import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket
import laminar.webcomponents.material.{Fab, Textarea, Textfield}

import scala.concurrent.duration._
import mixzpoker.{AppContext, AppError}
import mixzpoker.domain.chat.{ChatInputMessage, ChatOutputMessage}
import mixzpoker.domain.chat.ChatOutputMessage._
import mixzpoker.domain.chat.ChatInputMessage._
import mixzpoker.domain.user.User
import org.scalajs.dom

object Chat {

  case class ChatState(messages: List[(Option[User], String)] = List.empty) {
    def addMessage(user: User, message: String): ChatState = copy(messages = (Some(user), message) :: messages)
    def addLogMessage(message: String): ChatState = copy(messages = (None, message) :: messages)
  }

  def create(
    chatEndpoint: String,
    chatCls: String
  )(implicit appContext: Var[AppContext]): (Var[ChatState], HtmlElement) = {

    val message = Var("")
    val chatState: Var[ChatState] = Var(ChatState())

    val ws: WebSocket[ChatOutputMessage, ChatInputMessage] = WebSocket
      .url(chatEndpoint)
      .receiveText[ChatOutputMessage](decode[ChatOutputMessage])
      .sendText[ChatInputMessage](_.asJson.noSpaces)
      .build(reconnectRetries = 5, reconnectDelay = 3.seconds)

    def processChatMessage(message: ChatOutputMessage): Unit = message match {
      case ChatMessageFrom(message, user) => chatState.update(_.addMessage(user, message))
      case ErrorMessage(message)          => appContext.now().error.set(AppError.GeneralError(message))
      case KeepAlive =>
    }

    val html = div(
      ws.connect,
      ws.connected --> { _ws =>
        dom.console.log(s"ws connected to $chatEndpoint")
        _ws.send(appContext.now().token)
      },
      ws.received --> { message => processChatMessage(message)},
      cls(chatCls), flexDirection.column,
      Textarea(
        _ => cls("chat-messages"),
        _.`value` <-- chatState.signal.map(
          _.messages.map {
            case (Some(user), msg) => s"${user.name}: $msg"
            case (None, msg)       => s"Log: $msg"
          }.reverse.mkString("\n")
        ),
        _.`disabled` := true,
        _.`rows` := 8, _.`cols` := 130
      ),
      div(
        cls("chat-buttons"), flexDirection.row,
        Textfield(
          _ => cls("chat-send-field"),
          _.`value` <-- message,
          _ => inContext { thisNode => onInput.map(_ => thisNode.ref.`value`) --> message },
          _.`outlined` := true,
          _.`charCounter` := true,
          _.`maxLength` := 1000,
          _ => onKeyPress.filter(e => (e.keyCode == dom.KeyCode.Enter) && message.now().nonEmpty) --> { _ =>
            ws.sendOne(ChatMessage(message.now()))
            message.set("")
          }
        ),
        Fab(
          _.`icon` := "send",
          _ => onClick.filter(_ => message.now().nonEmpty) --> { _ =>
            ws.sendOne(ChatMessage(message.now()))
            message.set("")
          }
        )
      )
    )

    (chatState, html)
  }

}
