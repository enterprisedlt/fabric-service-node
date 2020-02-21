package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.all.{VdomTagOf, className, id, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.model.CreateContractRequest
import org.enterprisedlt.fabric.service.node.state.{GlobalStateAware, WithGlobalState}
import org.scalajs.dom.html.{Div, Select}

/**
  * @author Maxim Fedin
  */
object AddContract {


    @Lenses case class AddContractState(
        request: CreateContractRequest,
        chosenPackage: String,
        global: AppState
    ) extends WithGlobalState[AppState, AddContractState] {
        override def withGlobalState(g: AppState): AddContractState = this.copy(global = g)
    }


    object AddContractState {
        val Defaults: AddContractState = {
            AddContractState(
                CreateContractRequest.Defaults,
                "",
                Initial)
        }
    }


    private val component = ScalaComponent.builder[Unit]("AddContractForm")
      .initialState(AddContractState.Defaults)
      .renderBackend[Backend]
      .componentDidMount($ => Context.State.connect($.backend))
      .build


    class Backend(val $: BackendScope[Unit, AddContractState]) extends FieldBinder[AddContractState] with GlobalStateAware[AppState, AddContractState] {


        def renderContractPackagesList(s: AddContractState, g: GlobalState): VdomTagOf[Select] = {
            <.select(className := "form-control",
                id := "componentType",
                bind(s) := AddContractState.chosenPackage,
                contractPackagesOptions(s, g)
            )
        }

        def contractPackagesOptions(s: AddContractState, g: GlobalState): TagMod = {
            g.packages.map { name =>
                option((className := "selected"), name)
            }.toTagMod
        }


        def render(s: AddContractState): VdomTagOf[Div] =
            s.global match {
                case g: GlobalState =>
                    <.div(
                        <.h4("Add contract"),
                        <.span(<.br()),
                        <.div(^.className := "form-group row",
                            <.label(^.`for` := "contractPackages", ^.className := "col-sm-2 col-form-label", "Contract packages"),
                            <.div(^.className := "col-sm-10", renderContractPackagesList(s, g))
                        ),
                        <.div(^.className := "form-group row",
                            <.label(^.className := "col-sm-2 col-form-label", "Name"),
                            <.div(^.className := "col-sm-10",
                                <.input(^.`type` := "text", ^.className := "form-control",
                                    bind(s) := AddContractState.request / CreateContractRequest.name
                                )
                            )
                        ),


                    )
                case _ => <.div()

            }
    }


    def apply(): Unmounted[Unit, AddContractState, Backend] = component()
}
