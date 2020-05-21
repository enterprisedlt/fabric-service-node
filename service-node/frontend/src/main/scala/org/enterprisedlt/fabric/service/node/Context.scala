package org.enterprisedlt.fabric.service.node

import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.shared.FabricServiceState
import org.enterprisedlt.fabric.service.node.state.GlobalStateManager

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.timers.SetTimeoutHandle

/**
 * @author Alexey Polubelov
 */
object Context {
    val stateUpdateInterval = 1000 // 1s
    val State: GlobalStateManager[AppState] = new GlobalStateManager(Initial)
    var lastStateVersion: Long = -1

    def initialize(): Unit = {
        checkUpdateState()
    }

    private def scheduleStateCheck: SetTimeoutHandle = {
        js.timers.setTimeout(stateUpdateInterval) {
            checkUpdateState()
        }
    }

    private def checkUpdateState(): Unit = {
        ServiceNodeRemote.getServiceState.foreach { state =>
            if (state.version != lastStateVersion) {
                val update = state.stateCode match {
                    case FabricServiceState.NotInitialized =>
                        ServiceNodeRemote.listBoxes.map { boxes =>
                            Initializing(
                                info = BaseInfo(
                                    stateCode = state.stateCode,
                                    mspId = state.mspId,
                                    orgFullName = state.organizationFullName,
                                    boxes = boxes
                                ),
                                inProgress = false
                            )
                        }

                    case sm if bootstrapIsInProgress(sm) || joinIsInProgress(sm) =>
                        ServiceNodeRemote.listBoxes.map { boxes =>
                            Initializing(
                                info = BaseInfo(
                                    stateCode = state.stateCode,
                                    mspId = state.mspId,
                                    orgFullName = state.organizationFullName,
                                    boxes = boxes
                                ),
                                inProgress = true
                            )
                        }

                    case FabricServiceState.Ready =>
                        for {
                            packages <- ServiceNodeRemote.listContractPackages
                            channels <- ServiceNodeRemote.listChannels
                            organizations <- ServiceNodeRemote.listOrganizations
                            contracts <- ServiceNodeRemote.listContracts
                            boxes <- ServiceNodeRemote.listBoxes
                        } yield {
                            Ready(
                                info = BaseInfo(
                                    stateCode = state.stateCode,
                                    mspId = state.mspId,
                                    orgFullName = state.organizationFullName,
                                    boxes = boxes
                                ),
                                channels = channels,
                                packages = packages,
                                organizations = organizations,
                                contracts = contracts
                            )
                        }
                }
                update.foreach { stateUpdate =>
                    State.update(_ => stateUpdate)
                    lastStateVersion = state.version
                    // schedule next state check at the end
                    scheduleStateCheck
                }
            } else {
                scheduleStateCheck
            }
        }
    }

    def bootstrapIsInProgress(code: Int): Boolean =
        code >= FabricServiceState.BootstrapStarted && code <= FabricServiceState.BootstrapMaxValue

    def joinIsInProgress(code: Int): Boolean =
        code >= FabricServiceState.JoinStarted && code <= FabricServiceState.JoinMaxValue

}

sealed trait AppState

case object Initial extends AppState

@Lenses case class Initializing(
    info: BaseInfo,
    inProgress: Boolean
) extends AppState

@Lenses case class Ready(
    info: BaseInfo,
    channels: Array[String],
    packages: Array[String],
    organizations: Array[Organization],
    contracts: Array[Contract],
) extends AppState

@Lenses case class BaseInfo(
    stateCode: Int,
    mspId: String,
    orgFullName: String,
    boxes: Array[Box]
)