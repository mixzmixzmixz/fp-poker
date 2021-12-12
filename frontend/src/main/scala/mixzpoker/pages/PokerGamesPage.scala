package mixzpoker.pages

import com.raquo.laminar.api.L._
import io.laminext.fetch.circe._
import laminar.webcomponents.material.{Icon, IconButton, List => MWCList}
import mixzpoker.components.Navigation
import mixzpoker.domain.lobby.LobbyDto.LobbyDto
import mixzpoker.{App, AppContext, AppError, Config, Page}

object PokerGamesPage {

  object requests {
    def getPokerGamesRequest()(implicit appContext: Var[AppContext]): EventStream[List[LobbyDto]] = {
      Fetch.get(
        url = s"${Config.rootEndpoint}/poker",
        headers = Map("Authorization" -> appContext.now().token)
      ).decodeOkay[List[LobbyDto]].recoverToTry.map(_.fold(
        err => {
          appContext.now().error.set(AppError.GeneralError(err.toString))
          List()
        },
        resp => resp.data
      ))
    }
  }

  def apply()(implicit appContext: Var[AppContext]): HtmlElement = {

    def GameItem(lobby: LobbyDto): MWCList.ListItem.El = {
      MWCList.ListItem(
        _ => cls("lobby-list-item"),
        _.`tabindex` := -1,
        _.`graphic` := "avatar",
        _.`twoline` := true,
        _.`hasMeta` := true,
        _.slots.graphic(Icon().amend(span("casino"))),
        _.slots.default(span(lobby.name)),
        _.slots.secondary(span(s"${lobby.gameType}   ${lobby.players.length} / ${lobby.gameSettings.maxPlayers}")),
        _.slots.meta(IconButton(_.`icon` := "casino")),
        _ => onClick --> { _ =>
          lobby.gameId.fold {
            appContext.now().error.set(AppError.GeneralError(s"No game for lobby ${lobby.name}"))
            App.router.pushState(Page.Redirect)
          } {
            gameId => App.router.pushState(Page.PokerGame(gameId))
          }
        }
      )
    }

    val $lobbiesWithGame = requests.getPokerGamesRequest().map { games =>
      if (games.isEmpty)
        div("No PokerGames yet!")
      else
        MWCList(
          _ => cls("games-list"),
          _.slots.default(games.map(GameItem): _*),
        )
    }

    div(
      cls := "games-main-area",
      h1("PokerGames"),
      child <-- $lobbiesWithGame
    )
  }

  def controlButtons()(implicit appContext: Var[AppContext]): HtmlElement = Navigation.DefaultTopButtons()
}
