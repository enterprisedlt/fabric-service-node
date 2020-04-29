package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.{VdomTagOf, _}
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model.RegisterBoxManager
import org.enterprisedlt.fabric.service.node.state.GlobalStateAware
import org.enterprisedlt.fabric.service.node.util.DataFunction._
import org.scalajs.dom.html.Div

import scala.language.higherKinds

/**
  * @author Maxim Fedin
  */
object Boxes {


    @Lenses case class BoxesState(
        boxCandidate: RegisterBoxManager
    )

    object BoxesState {
        val Defaults: BoxesState =
            BoxesState(
                RegisterBoxManager.Defaults
            )
    }


    private val component = ScalaComponent.builder[Unit]("Boxes")
      .initialState(BoxesState.Defaults)
      .renderBackend[Backend]
      .componentDidMount($ => Context.State.connect($.backend))
      .build

    class Backend(val $: BackendScope[Unit, BoxesState]) extends FieldBinder[BoxesState] with GlobalStateAware[AppState, BoxesState] {

        def goInit: Callback = Callback {
            Context.switchModeTo(InitMode)
        }


        def addBox(boxCandidate: RegisterBoxManager): Callback = Callback {
            ServiceNodeRemote.registerBox(boxCandidate)
        }

        def renderWithGlobal(s: BoxesState, global: AppState): VdomTagOf[Div] = global match {
            case g: GlobalState =>
                <.div(
                    <.h5("Boxes:"),
                    <.div(^.className := "form-group row",
                        <.table(^.className := "table table-hover table-sm",
                            <.thead(
                                <.tr(
                                    <.th(^.scope := "col", "#"),
                                    <.th(^.scope := "col", "Name"),
                                    <.th(^.scope := "col", "Address"),
                                    //                                            <.th(^.scope := "col", "Actions"),
                                )
                            ),
                            <.tbody(
                                g.boxes.zipWithIndex.map { case (box, index) =>
                                    <.tr(
                                        <.td(^.scope := "row", s"${index + 1}"),
                                        <.td(box.boxName),
                                        <.td(
                                            if (box.boxAddress.trim.nonEmpty) box.boxAddress else "local"
                                        ),
                                    )
                                }.toTagMod,
                            )
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "boxName", ^.className := "col-sm-2 col-form-label", "Box name"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "boxName",
                                bind(s) := BoxesState.boxCandidate / RegisterBoxManager.name)
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "boxAddress", ^.className := "col-sm-2 col-form-label", "Box address"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "boxAddress",
                                bind(s) := BoxesState.boxCandidate / RegisterBoxManager.url)
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.button(
                            ^.className := "btn btn-primary",
                            "Add box",
                            ^.onClick --> addBox(s.boxCandidate)
                        )
                    ),
                    <.span(<.br()),
                    <.hr(),
                    <.div(^.className := "form-group mt-1",
                        <.button(^.`type` := "button", ^.className := "btn btn-outline-secondary", ^.onClick --> goInit, "Back"),

                    )
                )
            case _ => <.div()

        }

    }

    def apply(): Unmounted[Unit, BoxesState, Backend] = component()


}


