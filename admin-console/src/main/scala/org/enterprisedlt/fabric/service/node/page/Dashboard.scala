package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import org.enterprisedlt.fabric.service.node.util.Tags._
import org.scalajs.dom.html.Div

/**
 * @author Alexey Polubelov
 */
object Dashboard {

    case class State()

    private val component = ScalaComponent.builder[Unit]("Dashboard")
      .initialState(State())
      .renderBackend[Backend]
      .build

    class Backend(val $: BackendScope[Unit, State]) {

        def createInvite: Callback = Callback {
            println("create Invite")
        }

        def render(s: State): VdomTagOf[Div] =
            <.div(
                Tabs(
                    ("general", "General",
                      <.div(
                          <.button(^.tpe := "button", ^.className := "btn btn-outline-secondary", ^.onClick --> createInvite, "Create invite")
                      )
                    ),
                    ("users", "Users",
                      <.div(

                      )
                    ),
                    ("contracts", "Contracts",
                      <.div(

                      )
                    )
                )
            )
    }

    def apply(): Unmounted[Unit, State, Backend] = component()
}
