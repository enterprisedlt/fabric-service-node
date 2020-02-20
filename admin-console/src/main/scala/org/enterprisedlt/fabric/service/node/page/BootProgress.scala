package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ScalaComponent}
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.{Context, ReadyForUse}
import org.scalajs.dom.html.Div

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

/**
 * @author Alexey Polubelov
 */
object BootProgress {

    case class State(
        readyToUse: Boolean = false,
        progress: Array[ProgressItem] = Array.empty
    )

    case class ProgressItem(
        isInProgress: Boolean,
        text: String
    )

    val StateUpdateInterval = 500
    val NotInitialized = 0
    val BootstrapStarted = 11
    val BootstrapMaxValue = 21
    val Ready = 100

    private val BootMessages = Array(
        "Starting bootstrap process",
        "Creating genesis block",
        "Starting ordering service",
        "Awaiting ordering service to initialize",
        "Starting peer nodes",
        "Creating service channel",
        "Adding peers nodes to channel",
        "Updating anchors",
        "Installing service chain code",
        "Initializing service chain code",
        "Setting up block listener"
    )

    private val component = ScalaComponent.builder[Unit]("BootProgress")
      .initialState(State())
      .renderBackend[Backend]
      .componentDidMount(_.backend.scheduleCheck)
      .build

    class Backend(val $: BackendScope[Unit, State]) {

        def goAdministration: Callback = Callback {
            Context.switchModeTo(ReadyForUse)
        }

        def checkServiceState: Callback = Callback.future {
            ServiceNodeRemote.getServiceState.map { data =>
                val stateCode = data.stateCode
                if (stateCode == NotInitialized) {
                    scheduleCheck

                } else if (stateCode >= BootstrapStarted && stateCode <= BootstrapMaxValue) {
                    progressToState(stateCode - BootstrapStarted) >> scheduleCheck

                } else if (stateCode == Ready) {
                    $.setState(
                        State(
                            progress = BootMessages.map(msg =>
                                ProgressItem(
                                    isInProgress = false,
                                    text = msg
                                )),
                            readyToUse = true
                        )
                    )

                } else {
                    println("Unexpected state code:", stateCode)
                    scheduleCheck
                }
            }
        }

        def progressToState(to: Int): CallbackTo[Unit] = {
            $.modState(
                _.copy(
                    progress = (
                      for (i <- 0 to to) yield {
                          ProgressItem(
                              isInProgress = i == to,
                              text = BootMessages(i)
                          )
                      }
                      ).toArray
                )
            )
        }

        def scheduleCheck: Callback = Callback {
            js.timers.setTimeout(StateUpdateInterval)(checkServiceState.runNow())
        }

        def render(s: State): VdomTagOf[Div] =
            <.div(^.className := "card aut-form-card",
                <.div(^.className := "card-header text-white bg-primary",
                    <.h1("Bootstrap new network ...")
                ),
                <.div(^.className := "card-body aut-form-card",
                    <.ul(
                        s.progress.map { p =>
                            <.li(
                                <.div(^.className := "d-flex align-items-center",
                                    <.strong(p.text),
                                    <.div(^.className := "spinner-border spinner-border-sm ml-auto text-primary", ^.role := "status", ^.aria.hidden := true)
                                      .when(p.isInProgress)
                                    // TODO: implement OK mark
                                    //                <span if.bind="!m.isInProgress" ^.className :="aut-form-card" ><i ^.className :="icon-ok"></i></span> -->
                                )
                            )
                        }.toTagMod
                    ),
                    <.hr().when(s.readyToUse),
                    <.button(
                        ^.tpe := "button",
                        ^.className := "btn btn-outline-success float-right",
                        ^.onClick --> goAdministration,
                        "Start using!"
                    ).when(s.readyToUse)
                )
            )
    }

    def apply(): Unmounted[Unit, State, Backend] = component()
}
