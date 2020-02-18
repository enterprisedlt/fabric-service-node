package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import org.enterprisedlt.fabric.service.node.{Context, Initial}
import org.scalajs.dom.html.Div

/**
  * @author Maxim Fedin
  */
object Join {

    case class JoinSettings(
        //TODO
    )

    private val component = ScalaComponent.builder[Unit]("JoinMode")
      .initialState(JoinSettings())
      .renderBackend[Backend]
      .build


    class Backend(val $: BackendScope[Unit, JoinSettings]) {

        def goInit: Callback = Callback {
            Context.State.update(_ => Initial)
        }

        def goBootProgress: Callback = Callback() // TODO

        def render(s: JoinSettings): VdomTagOf[Div] =
            <.div(^.className := "card aut-form-card",
                <.div(^.className := "card-header text-white bg-primary",
                    <.h1("Join to new network")
                ),
                <.hr(),
                <.div(^.className := "card-body aut-form-card",
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Invite:"),
                    <.div(^.className := "input-group col-sm-10",
                        <.div(^.`class` := "custom-file",
                            <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "inviteInput"),
                            <.label(^.`class` := "custom-file-label", "Choose file")
                        )
                    )
                ),



                <.div(^.className := "form-group mt-1",
                    <.button(^.`type` := "button", ^.className := "btn btn-outline-secondary", ^.onClick --> goInit, "Back"),
                    <.button(^.`type` := "button", ^.className := "btn btn-outline-success float-right", ^.onClick --> goBootProgress, "Join")
                )
                //                                                                                                        </form>
            ))
    }

    def apply(): Unmounted[Unit, JoinSettings, Backend] = component()

}
