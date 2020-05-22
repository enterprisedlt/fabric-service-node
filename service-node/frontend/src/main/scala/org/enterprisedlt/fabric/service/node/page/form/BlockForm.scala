package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.html_<^._
import org.enterprisedlt.fabric.service.node.shared.BlockConfig

/**
 * @author Alexey Polubelov
 */
object BlockForm extends StatelessForm[BlockConfig]("BlockSettings") {

    override def render(p: BlockConfig)(implicit modState: CallbackFunction): VdomNode =
        <.div(
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Batch timeout"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := BlockConfig.batchTimeOut
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Max messages count"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := BlockConfig.maxMessageCount
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Absolute max bytes"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := BlockConfig.absoluteMaxBytes
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Preferred max bytes"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := BlockConfig.preferredMaxBytes
                    )
                )
            )
        )
}
