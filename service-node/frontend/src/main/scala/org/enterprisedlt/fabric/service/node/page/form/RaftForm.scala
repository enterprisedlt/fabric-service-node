package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.html_<^._
import org.enterprisedlt.fabric.service.node.shared.RaftConfig

/**
 * @author Alexey Polubelov
 */
object RaftForm extends StatelessForm[RaftConfig]("RaftSettings") {

    override def render(p: RaftConfig)(implicit modState: CallbackFunction): VdomNode =
        <.div(
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Tick interval"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := RaftConfig.tickInterval
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Election tick"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := RaftConfig.electionTick
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Heartbeat tick"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := RaftConfig.heartbeatTick
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Max inflight blocks"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := RaftConfig.maxInflightBlocks
                    )
                )
            ),
            <.div(^.className := "form-group row",
                <.label(^.className := "col-sm-4 col-form-label", "Snapshot interval size"),
                <.div(^.className := "col-sm-8",
                    <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                        bind(p) := RaftConfig.snapshotIntervalSize
                    )
                )
            )
        )
}