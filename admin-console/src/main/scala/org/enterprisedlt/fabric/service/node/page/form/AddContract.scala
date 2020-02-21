package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.{VdomTagOf, _}
import japgolly.scalajs.react.{BackendScope, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.FieldBinder
import org.scalajs.dom.html.Div

/**
  * @author Maxim Fedin
  */
object AddContract {


    @Lenses case class AddContractState(
    )


    object AddContractState {
        val Defaults: AddContractState = {
            AddContractState()
        }
    }


    private val component = ScalaComponent.builder[Unit]("AddContractForm")
      .initialState(AddContractState.Defaults)
      .renderBackend[Backend]
      .build


    class Backend(val $: BackendScope[Unit, AddContractState]) extends FieldBinder[AddContractState] {
        def render(s: AddContractState): VdomTagOf[Div] = <.div()

    }


    def apply(): Unmounted[Unit, AddContractState, Backend] = component()
}
