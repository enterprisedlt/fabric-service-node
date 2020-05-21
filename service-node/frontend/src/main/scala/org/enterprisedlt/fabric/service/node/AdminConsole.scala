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
                    case s: Initializing => if (s.inProgress) Progress(s) else Init()
                    case _: Ready => Dashboard()
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
        Context.initialize()
        rootComponent().renderIntoDOM(dom.document.getElementById("root"))
    }
}
