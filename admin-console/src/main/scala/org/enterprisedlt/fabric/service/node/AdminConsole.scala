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
                Init().when(s.global == Initial),
                Boot().when(s.global == BootstrapMode),
                Join().when(s.global == JoinMode),
                BootProgress().when(s.global == BootstrapInProgress),
                Dashboard().when(s.global == ReadyForUse)
            )
    }

    @JSExport
    def main(args: Array[String]): Unit = {
        rootComponent().renderIntoDOM(dom.document.getElementById("root"))
    }
}
