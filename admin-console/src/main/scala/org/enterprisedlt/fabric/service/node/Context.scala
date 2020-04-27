package org.enterprisedlt.fabric.service.node

import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model.{Box, Contract, FabricServiceState, Organization, Status}
import org.enterprisedlt.fabric.service.node.state.GlobalStateManager

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * @author Alexey Polubelov
  */
object Context {
    val State: GlobalStateManager[AppState] = new GlobalStateManager(Initial)

    def initialize: Future[Unit] = {
        for {
            state <- ServiceNodeRemote.getServiceState
            stateMode = getStateMode(state)
            states <- if (stateMode == ReadyForUse) updateState else Future.successful(None)
            orgFullName <- ServiceNodeRemote.getOrganisationFullName
            mspId <- ServiceNodeRemote.getOrganisationMspId
            boxes <- ServiceNodeRemote.listBoxes
        } yield {
            State.update { _ =>
                val (packages, channels, organizations, contracts) = states.getOrElse((Array.empty[String], Array.empty[String], Array.empty[Organization], Array.empty[Contract]))
                GlobalState(
                    mode = stateMode,
                    orgFullName = orgFullName,
                    mspId = mspId,
                    channels = channels,
                    packages = packages,
                    organizations = organizations,
                    contracts = contracts,
                    boxes = boxes
                )
            }
        }
    }

    def getStateMode(fabricServiceState: FabricServiceState): AppMode = fabricServiceState.stateCode match {
        case sm if sm == Status.NotInitialized =>
            InitMode
        case sm if sm >= Status.JoinProgressStatus.JoinStarted && sm <= Status.JoinProgressStatus.JoinMaxValue =>
            JoinInProgress
        case sm if sm >= Status.BootProgressStatus.BootstrapStarted && sm <= Status.BootProgressStatus.BootstrapMaxValue =>
            BootstrapInProgress
        case sm if sm == Status.Ready =>
            ReadyForUse
    }


    def updateState: Future[Option[(Array[String], Array[String], Array[Organization], Array[Contract])]] = {
        for {
            packages <- ServiceNodeRemote.listContractPackages
            channels <- ServiceNodeRemote.listChannels
            organizations <- ServiceNodeRemote.listOrganizations
            contracts <- ServiceNodeRemote.listContracts
        } yield Some(packages, channels, organizations, contracts)
    }

    def switchModeTo(mode: AppMode): Unit = {
        State.update {
            case x: GlobalState => x.copy(mode = mode)
            case s => throw new IllegalStateException(s"Unexpected state $s")
        }
    }
}

sealed trait AppState

case object Initial extends AppState

@Lenses case class GlobalState(
    mode: AppMode,
    orgFullName: String,
    mspId: String,
    channels: Array[String],
    packages: Array[String],
    organizations: Array[Organization],
    contracts: Array[Contract],
    boxes: Array[Box]
) extends AppState

sealed trait AppMode

case object InitMode extends AppMode

case object BootstrapMode extends AppMode

case object JoinMode extends AppMode

case object BootstrapInProgress extends AppMode

case object JoinInProgress extends AppMode

case object ReadyForUse extends AppMode
