package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import org.enterprisedlt.fabric.service.node.shared.FabricServiceState
import org.enterprisedlt.fabric.service.node.{Context, Initializing}
import org.scalajs.dom.html.LI

import scala.collection.immutable

/**
 * @author Alexey Polubelov
 */
object Progress {

    private val BootMessages = Array(
        "Starting bootstrap process",
        "Creating genesis block",
        "Starting ordering service",
        "Awaiting ordering service to initialize",
        "Starting peer nodes",
        "Creating service channel",
        "Adding peer nodes to channel",
        "Updating anchors",
        "Installing service chain code",
        "Initializing service chain code",
        "Setting up block listener"
    )

    private val JoinMessages = Array(
        "Starting join process",
        "Creating join request",
        "Awaiting join response",
        "Starting ordering service",
        "Connecting to network",
        "Starting peer nodes",
        "Adding peer nodes to channel",
        "Updating anchors",
        "Installing service chain code",
        "Initializing service chain code",
        "Setting up block listener"
    )

    private def renderProgressItems(state: Initializing): immutable.Seq[VdomTagOf[LI]] = {
        val code = state.info.stateCode
        val (current, messages) =
            if (Context.bootstrapIsInProgress(code)) {
                (code - FabricServiceState.BootstrapStarted, BootMessages)
            } else if (Context.joinIsInProgress(code)) {
                (code - FabricServiceState.JoinStarted, JoinMessages)
            } else {
                (0, Array.empty)
            }

        for (i <- 0 to current) yield {
            <.li(
                <.div(^.className := "d-flex align-items-center",
                    <.strong(messages(i)),
                    <.div(^.className := "spinner-border spinner-border-sm ml-auto").when(i == current),
                    <.div(^.className := "ml-auto", <.i(^.className := "fas fa-check", ^.color.green)).when(i != current)
                )
            )
        }
    }

    private val component = ScalaComponent.builder[Initializing]("Progress")
      .render_P { state =>
          <.div(
              FSNSPA(
                  state.info.orgFullName,
                  0,
                  Seq(
                      Page(
                          name = "Progress",
                          content =
                            <.div(
                                ^.width := "900px",
                                ^.marginTop := "5px",
                                ^.marginBottom := "0px",
                                ^.marginLeft := "auto",
                                ^.marginRight := "auto",
                                <.div(^.className := "card card-body",
                                    <.ul(
                                        renderProgressItems(state).toTagMod
                                    ),
                                ),
                            ),
                          actions = Seq.empty
                      )
                  )
              )
          )
      }
      .build

    def apply(state: Initializing): Unmounted[Initializing, Unit, Unit] = component(state)
}
