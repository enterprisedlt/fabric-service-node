package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.all.{VdomTagOf, className, id, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.FieldBinder
import org.scalajs.dom.html.{Div, Select}
import upickle.default.{macroRW, ReadWriter => RW}

/**
  * @author Maxim Fedin
  */
object AddContract {


    @Lenses case class AddContractState(
        contracts: Array[String]
    )


    object AddContractState {
        val Defaults: AddContractState = {
            AddContractState(Array.empty[String])
        }
        implicit val rw: RW[AddContractState] = macroRW
    }


    private val component = ScalaComponent.builder[Unit]("AddContractForm")
      .initialState(AddContractState.Defaults)
      .renderBackend[Backend]
      .build


    class Backend(val $: BackendScope[Unit, AddContractState]) extends FieldBinder[AddContractState] {


        def renderContractPackagesList(s: AddContractState): VdomTagOf[Select] = {
            <.select(className := "form-control",
                id := "componentType",
//                bind(s) := AddContractState.contracts,
                contractPackagesOptions(s)
            )
        }

        def contractPackagesOptions(s: AddContractState): TagMod = {
            s.contracts.map { name =>
                option((className := "selected"), name)
            }.toTagMod
        }


        def render(s: AddContractState): VdomTagOf[Div] =
            <.div(
                <.h4("Add contract"),
                <.span(<.br()),
                <.div(^.className := "form-group row",
                    <.label(^.`for` := "contractPackages", ^.className := "col-sm-2 col-form-label", "Contract packages"),
                    <.div(^.className := "col-sm-10", renderContractPackagesList(s))
                )


            )

    }


    def apply(): Unmounted[Unit, AddContractState, Backend] = component()
}
