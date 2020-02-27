package org.enterprisedlt.fabric.service.node

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, ScalaComponent}
import org.enterprisedlt.fabric.service.node.page._
import org.enterprisedlt.fabric.service.node.state.GlobalStateAware
import org.scalajs.dom
import org.scalajs.dom.html.Div

import scala.scalajs.js.annotation.JSExport

/**
 * @author Alexey Polubelov
 */
object AdminConsole {

    private val rootComponent = ScalaComponent.builder[Unit]("Main")
      .renderBackend[MainBackend]
      .componentDidMount($ => Context.State.connect($.backend))
      .build

    class MainBackend(val $: BackendScope[Unit, Unit]) extends GlobalStateAware[AppState, Unit] {
        def renderWithGlobal(s: Unit, global: AppState): VdomTagOf[Div] =
            <.div(
                global match {
                    case Initial => loadingScreen
                    case GlobalState(InitMode, _, _, _,_) => Init()
                    case GlobalState(BootstrapMode, _, _, _,_) => Boot()
                    case GlobalState(JoinMode, _, _, _,_) => Join()
                    case GlobalState(BootstrapInProgress, _, _, _,_) => BootProgress()
                    case GlobalState(JoinInProgress, _, _, _,_) => JoinProgress()
                    case GlobalState(ReadyForUse, _, _, _,_) => Dashboard()
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
