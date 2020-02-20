package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import org.enterprisedlt.fabric.service.node.{BootstrapMode, Context, JoinMode}
import org.scalajs.dom.html.Div

/**
 * @author Alexey Polubelov
 */
object Init {

    case class State()

    private val component = ScalaComponent.builder[Unit]("Initial")
      .initialState(State())
      .renderBackend[Backend]
      .build

    class Backend(val $: BackendScope[Unit, State]) {

        def goBootstrap: Callback = Callback {
            Context.switchModeTo(BootstrapMode)
        }

        def goJoin: Callback = Callback {
            Context.switchModeTo(JoinMode)
        }

        def render(s: State): VdomTagOf[Div] =
            <.div(
                <.div(^.className := "card aut-form-card",
                    <.div(^.className := "card-header text-white bg-primary",
                        <.h1("Initialize fabric service")
                    ),
                    <.div(^.className := "card-body",
                        <.h3("Fabric service is not initialized yet, you can:"),
                        <.ul(
                            <.li(
                                <.button(^.tpe := "button", ^.className := "btn btn-link", ^.onClick --> goBootstrap, "Bootstrap new network")
                            ),
                            <.li(
                                <.button(^.tpe := "button", ^.`class` := "btn btn-link", ^.onClick --> goJoin, "Join existing network")
                            )
                        )
                    )
                )
            )
    }

    def apply(): Unmounted[Unit, State, Backend] = component()
}
