package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.html_<^._
import org.enterprisedlt.fabric.service.node.model.RegisterBoxManager

/**
 * @author Alexey Polubelov
 */
object ServerForm extends StatelessForm[RegisterBoxManager]("ServerForm") {

    override def render(p: RegisterBoxManager, callback: CallbackFunction): VdomNode = {
        implicit val modState: CallbackFunction = callback
        <.div(
            <.div(^.className := "form-group row",
                <.label(^.`for` := "server-name", ^.className := "col-sm-4 col-form-label", "Name"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm", ^.id := "server-name",
                        bind(p) := RegisterBoxManager.name)
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.`for` := "server-address", ^.className := "col-sm-4 col-form-label", "Connection"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm", ^.id := "server-address",
                        bind(p) := RegisterBoxManager.url)
                )
            ),
        )
    }
}
