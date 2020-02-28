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
                    case gs: GlobalState => gs.mode match {
                        case InitMode => Init()
                        case BootstrapMode => Boot()
                        case JoinMode => Join()
                        case BootstrapInProgress => BootProgress()
                        case JoinInProgress => JoinProgress()
                        case ReadyForUse => Dashboard()
                    }
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
