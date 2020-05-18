package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ScalaComponent}
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model.Status.BootProgressStatus
import org.enterprisedlt.fabric.service.node.model.{StateUpdate, Status}
import org.enterprisedlt.fabric.service.node.state.GlobalStateAware
import org.enterprisedlt.fabric.service.node.{AppState, Context, GlobalState, ReadyForUse}
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

    private val component = ScalaComponent.builder[Unit]("BootProgress")
      .initialState(State())
      .renderBackend[Backend]
      .componentWillMount($ => Context.State.connect($.backend))
      .componentDidMount($ => $.backend.scheduleCheck)
      .build

    class Backend(val $: BackendScope[Unit, State]) extends GlobalStateAware[AppState, State] {

        def goAdministration: Callback = Callback {
            Context.updateState()
            Context.switchModeTo(ReadyForUse)
        }

        def checkServiceState: Callback = Callback.future {
            ServiceNodeRemote.getServiceState.map { data =>
                val stateCode = data.stateCode
                if (stateCode == Status.NotInitialized) {
                    scheduleCheck

                } else if (stateCode >= BootProgressStatus.BootstrapStarted && stateCode <= BootProgressStatus.BootstrapMaxValue) {
                    progressToState(stateCode - BootProgressStatus.BootstrapStarted) >> scheduleCheck

                } else if (stateCode == Status.Ready) {
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
            js.timers.setTimeout(StateUpdate.Interval)(checkServiceState.runNow())
        }

        def renderWithGlobal(s: State, global: AppState): VdomTagOf[Div] = global match {
            case g: GlobalState =>
                <.div(
                    FSNSPA(
                        g.orgFullName,
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
                                              s.progress.map { p =>
                                                  <.li(
                                                      <.div(^.className := "d-flex align-items-center",
                                                          <.strong(p.text),
                                                          <.div(^.className := "spinner-border spinner-border-sm ml-auto").when(p.isInProgress),
                                                          <.div(^.className := "ml-auto", <.i(^.className := "fas fa-check", ^.color.green)).when(!p.isInProgress)
                                                      )
                                                  )
                                              }.toTagMod
                                          ),
                                          <.hr().when(s.readyToUse),
                                          <.button(
                                              ^.tpe := "button",
                                              ^.className := "btn btn-sm btn-outline-success float-right",
                                              ^.onClick --> goAdministration,
                                              "Start using!"
                                          ).when(s.readyToUse)
                                      ),
                                  ),
                                actions = Seq.empty
                            )
                        )
                    )
                )

            case _ => <.div()
        }

    }

    def apply(): Unmounted[Unit, State, Backend] = component()
}
