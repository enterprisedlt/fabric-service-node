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
object Contract {


    @Lenses case class ContractState(
        request: CreateContractRequest,
        chosenPackage: String,
        global: AppState
    ) extends WithGlobalState[AppState, ContractState] {
        override def withGlobalState(g: AppState): ContractState = this.copy(global = g)
    }


    object ContractState {
        val Defaults: ContractState = {
            ContractState(
                CreateContractRequest.Defaults,
                "",
                Initial)
        }
    }


    private val component = ScalaComponent.builder[Unit]("ContractForm")
      .initialState(ContractState.Defaults)
      .renderBackend[Backend]
      .componentDidMount($ => Context.State.connect($.backend))
      .build


    class Backend(val $: BackendScope[Unit, ContractState]) extends FieldBinder[ContractState] with GlobalStateAware[AppState, ContractState] {


        def renderContractPackagesList(s: ContractState, g: GlobalState): VdomTagOf[Select] = {
            $.modState(
                state => state.copy(
                    state.request.copy(
                        contractType = s.chosenPackage.split("-")(0),
                        version = s.chosenPackage.split("-")(1)
                    )
                )
            )
            <.select(className := "form-control",
                id := "componentType",
                bind(s) := ContractState.chosenPackage,
                contractPackagesOptions(s, g)
            )
        }

        def contractPackagesOptions(s: ContractState, g: GlobalState): TagMod = {
            g.packages.map { name =>
                option((className := "selected"), name)
            }.toTagMod
        }


        def render(s: ContractState): VdomTagOf[Div] =
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
                            <.label(^.className := "col-sm-2 col-form-label", "Contract Type"),
                            <.div(^.className := "col-sm-10",
                                <.input(^.`type` := "text", ^.className := "form-control",
                                    bind(s) := ContractState.request / CreateContractRequest.contractType
                                )
                            )
                        ),
                        <.div(^.className := "form-group row",
                            <.label(^.className := "col-sm-2 col-form-label", "Version"),
                            <.div(^.className := "col-sm-10",
                                <.input(^.`type` := "text", ^.className := "form-control",
                                    bind(s) := ContractState.request / CreateContractRequest.version
                                )
                            )
                        ),


                    )
                case _ => <.div()

            }
    }


    def apply(): Unmounted[Unit, ContractState, Backend] = component()
}
