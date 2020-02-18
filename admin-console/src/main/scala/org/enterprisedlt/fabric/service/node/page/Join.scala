package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import org.enterprisedlt.fabric.service.node.model.JoinOptions
import org.enterprisedlt.fabric.service.node.{Context, Initial}
import org.scalajs.dom.html.Div

/**
  * @author Maxim Fedin
  */
object Join {



    private val component = ScalaComponent.builder[Unit]("JoinMode")
      .initialState(JoinOptions.Defaults)
      .renderBackend[Backend]
      .build


    class Backend(val $: BackendScope[Unit, JoinOptions]) {

        def goInit: Callback = Callback {
            Context.State.update(_ => Initial)
        }

        def goBootProgress: Callback = Callback() // TODO

        def render(s: JoinOptions): VdomTagOf[Div] =
            <.div(^.className := "card aut-form-card",
                <.div(^.className := "card-header text-white bg-primary",
                    <.h1("Join to new network")
                ),
                <.hr(),
                <.div(^.className := "card-body aut-form-card",
                    <.h5("Join settings"),
                    <.div(^.className := "form-group row",
                        <.label(^.className := "col-sm-2 col-form-label", "Invite:"),
                        <.div(^.className := "input-group col-sm-10",
                            <.div(^.`class` := "custom-file",
                                <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "inviteInput"),
                                <.label(^.`class` := "custom-file-label", "Choose file")
                            )
                        )
                    ),
                    <.hr(),
                    <.h5("Network settings"),
                    <.div(^.className := "form-group row",
                        <.table(^.className := "table table-hover table-sm",
                            <.thead(
                                <.tr(
                                    <.th(^.scope := "col", "#"),
                                    <.th(^.scope := "col", "Component type"),
                                    <.th(^.scope := "col", "Component name"),
                                    <.th(^.scope := "col", "Port"),
                                    <.th(^.scope := "col", "Actions"),
                                )
                            ),
                            <.tbody(
                                //TODO
                            )
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "componentType", ^.className := "col-sm-2 col-form-label", "Component type"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "componentType")
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "componentName", ^.className := "col-sm-2 col-form-label", "Component name"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "componentName")
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "port", ^.className := "col-sm-2 col-form-label", "Port"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "port")
                        )
                    ),
                    <.hr(),
                    <.div(^.className := "form-group row",
                        <.button(
                            ^.className := "btn btn-primary",
                            "Add component"
                        )
                    ),
                    <.div(^.className := "form-group mt-1",
                        <.button(^.`type` := "button", ^.className := "btn btn-outline-secondary", ^.onClick --> goInit, "Back"),
                        <.button(^.`type` := "button", ^.className := "btn btn-outline-success float-right", ^.onClick --> goBootProgress, "Join")
                    )
                    //                                                                                                        </form>
                )
            )
    }

    def apply(): Unmounted[Unit, JoinOptions, Backend] = component()

}
