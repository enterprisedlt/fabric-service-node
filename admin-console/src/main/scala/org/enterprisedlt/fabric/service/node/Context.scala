package org.enterprisedlt.fabric.service.node

import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model.{Contract, Organization, Status}
import org.enterprisedlt.fabric.service.node.state.GlobalStateManager

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * @author Alexey Polubelov
  */
object Context {
    val State: GlobalStateManager[AppState] = new GlobalStateManager(Initial)

    def initialize: Future[Unit] = {
        ServiceNodeRemote.getServiceState.map { state =>
            val stateMode = state.stateCode match {
                case sm if sm == Status.NotInitialized =>
                    InitMode
                case sm if sm >= Status.JoinProgressStatus.JoinStarted && sm <= Status.JoinProgressStatus.JoinMaxValue =>
                    JoinInProgress
                case sm if sm >= Status.BootProgressStatus.BootstrapStarted && sm <= Status.BootProgressStatus.BootstrapMaxValue =>
                    BootstrapInProgress
                case sm if sm == Status.Ready =>
                    Context.fetchUpdate
                    ReadyForUse
            }
            ServiceNodeRemote.getOrganisationFullName.map { orgFullName =>
                ServiceNodeRemote.getOrganisationMspId.map { mspId =>
                    State.update { _ =>
                        GlobalState(
                            mode = stateMode,
                            orgFullName = orgFullName,
                            mspId = mspId,
                            packages = Array.empty[String],
                            organizations = Array.empty[Organization],
                            contracts = Array.empty[Contract]
                        )
                    }
                }
            }
        }
    }


    def fetchUpdate: Future[Unit] = {
        for {
            packages <- ServiceNodeRemote.listContractPackages
            organizations <- ServiceNodeRemote.listOrganizations
            contracts <- ServiceNodeRemote.listContracts
        } yield {
            State.update {
                case gs: GlobalState =>
                    gs.copy(
                        packages = packages,
                        organizations = organizations,
                        contracts = contracts
                    )
                case _ => throw new Exception
            }
        }
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
    packages: Array[String],
    organizations: Array[Organization],
    contracts: Array[Contract]
) extends AppState

sealed trait AppMode

case object InitMode extends AppMode

case object BootstrapMode extends AppMode

case object JoinMode extends AppMode

case object BootstrapInProgress extends AppMode

case object JoinInProgress extends AppMode

case object ReadyForUse extends AppMode
