package org.enterprisedlt.fabric.service.node

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, ScalaComponent}
import org.enterprisedlt.fabric.service.node.page._
import org.enterprisedlt.fabric.service.node.state.{GlobalStateAware, WithGlobalState}
import org.scalajs.dom
import org.scalajs.dom.html.Div

import scala.scalajs.js.annotation.JSExport

/**
 * @author Alexey Polubelov
 */
object AdminConsole {

    case class State(
        global: AppState
    ) extends WithGlobalState[AppState, State] {
        override def withGlobalState(global: AppState): State = this.copy(global = global)
    }

    private val rootComponent = ScalaComponent.builder[Unit]("Main")
      .initialState(State(Initial))
      .renderBackend[MainBackend]
      .componentDidMount($ => Context.State.connect($.backend))
      .build

    class MainBackend(val $: BackendScope[Unit, State]) extends GlobalStateAware[AppState, State] {
        def render(s: State): VdomTagOf[Div] =
            <.div(
                s.global match {
                    case Initial => loadingScreen
                    case GlobalState(InitMode, _) => Init()
                    case GlobalState(BootstrapMode, _) => Boot()
                    case GlobalState(JoinMode, _) => Join()
                    case GlobalState(BootstrapInProgress, _) => BootProgress()
                    case GlobalState(JoinInProgress, _) => JoinProgress()
                    case GlobalState(ReadyForUse, _) => Dashboard()
                }
            )
    }

    private def loadingScreen: VdomTagOf[Div] =
        <.div(^.className := "d-flex justify-content-center",
            <.div(^.className := "spinner-border", ^.role := "status",
                <.span(^.className := "sr-only", "Loading...")
            )
        )

    @JSExport
    def main(args: Array[String]): Unit = {
        Context.initialize
        rootComponent().renderIntoDOM(dom.document.getElementById("root"))
    }
}
