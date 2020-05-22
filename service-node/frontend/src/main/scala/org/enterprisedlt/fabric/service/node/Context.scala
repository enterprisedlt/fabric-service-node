package org.enterprisedlt.fabric.service.node

import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.shared.{Box, ChainCodeInfo, ContractDescriptor, FabricServiceState, NetworkConfig}
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

    private def scheduleStateCheck(): Unit = {
        js.timers.setTimeout(stateUpdateInterval) {
            checkUpdateState()
        }
        println(s"state check scheduled")
    }

    private def checkUpdateState(): Unit = {
        println("checking state ...")
        ServiceNodeRemote.getServiceState.foreach { state =>
            println(s"Current version is ${lastStateVersion}, got: $state")
            if (state.version != lastStateVersion) {
                val update = state.stateCode match {
                    case FabricServiceState.NotInitialized =>
                        println(s"State is NotInitialized")
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
                        println(s"State is Initializing")
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
                        println(s"State is Ready")
                        for {
                            network <- ServiceNodeRemote.getNetworkConfig
                            packages <- ServiceNodeRemote.listContractPackages
                            channels <- ServiceNodeRemote.listChannels
                            organizations <- ServiceNodeRemote.listOrganizations
                            contracts <- ServiceNodeRemote.listContracts
                            chainCodes <- ServiceNodeRemote.listChainCodes
                            boxes <- ServiceNodeRemote.listBoxes
                        } yield {
                            Ready(
                                info = BaseInfo(
                                    stateCode = state.stateCode,
                                    mspId = state.mspId,
                                    orgFullName = state.organizationFullName,
                                    boxes = boxes
                                ),
                                network = network,
                                channels = channels,
                                packages = packages,
                                organizations = organizations,
                                contracts = contracts,
                                chainCodes = chainCodes
                            )
                        }
                }
                update.failed.foreach { ex =>
                    println(s"Update failed:")
                    ex.printStackTrace()
                }
                update.foreach { stateUpdate =>
                    println(s"Updating state to: $stateUpdate")
                    State.update(_ => stateUpdate)
                    lastStateVersion = state.version
                    // schedule next state check at the end
                    scheduleStateCheck()
                }
            } else {
                scheduleStateCheck()
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
    network: NetworkConfig,
    channels: Array[String],
    packages: Array[ContractDescriptor],
    organizations: Array[Organization],
    contracts: Array[Contract],
    chainCodes: Array[ChainCodeInfo]
) extends AppState

@Lenses case class BaseInfo(
    stateCode: Int,
    mspId: String,
    orgFullName: String,
    boxes: Array[Box]
)