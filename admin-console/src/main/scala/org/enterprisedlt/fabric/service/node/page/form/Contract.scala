package org.enterprisedlt.fabric.service.node.page.form

import cats.Functor
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.all.{VdomTagOf, className, id, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, ScalaComponent}
import monocle.Lens
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.model.CreateContractRequest
import org.enterprisedlt.fabric.service.node.state.GlobalStateAware
import org.scalajs.dom.html.{Div, Select}

import scala.language.higherKinds

/**
 * @author Maxim Fedin
 */
object Contract {


    @Lenses case class ContractState(
        request: CreateContractRequest,
        chosenPackage: String
    )


    object ContractState {
        val Defaults: ContractState = {
            ContractState(
                CreateContractRequest.Defaults, ""
            )
        }
    }


    private val component = ScalaComponent.builder[Unit]("ContractForm")
      .initialState(ContractState.Defaults)
      .renderBackend[Backend]
      .componentDidMount($ => Context.State.connect($.backend))
      .build


    class Backend(val $: BackendScope[Unit, ContractState]) extends FieldBinder[ContractState] with GlobalStateAware[AppState, ContractState] {


        def renderContractPackagesList(s: ContractState, g: GlobalState): VdomTagOf[Select] = {
            <.select(className := "form-control",
                id := "componentType",
                bind(s) := packageCustomLens,
                contractPackagesOptions(s, g)
            )
        }

        private val packageCustomLens =
            new Lens[ContractState, String] {
                override def get(s: ContractState): String = s.chosenPackage

                override def set(b: String): ContractState => ContractState = { state =>
                    state.copy(
                        chosenPackage = b,
                        request = state.request.copy(
                            contractType = b.split("-")(0),
                            version = b.split("-")(1)
                        )
                    )
                }
                override def modifyF[F[_]](f: String => F[String])(s: ContractState)(implicit evidence$1: Functor[F]): F[ContractState] = ???
                override def modify(f: String => String): ContractState => ContractState = ???
            }

        def contractPackagesOptions(s: ContractState, g: GlobalState): TagMod = {
            g.packages.map { name =>
                option((className := "selected").when(s.chosenPackage == name), name)
            }.toTagMod
        }

        def renderWithGlobal(s: ContractState, global: AppState): VdomTagOf[Div] = global match {
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
                    <.div(^.className := "form-group row",
                        <.label(^.className := "col-sm-2 col-form-label", "Name"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control",
                                bind(s) := ContractState.request / CreateContractRequest.name
                            )
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.className := "col-sm-2 col-form-label", "Channel type"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control",
                                bind(s) := ContractState.request / CreateContractRequest.channelName
                            )
                        )
                    ),



                )
            case _ => <.div()

        }
    }


    def apply(): Unmounted[Unit, ContractState, Backend] = component()
}
