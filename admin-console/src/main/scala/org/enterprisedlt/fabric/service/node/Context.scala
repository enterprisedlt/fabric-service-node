package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model.Status
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
                    ReadyForUse
            }
            ServiceNodeRemote.getOrganisationFullName.map { orgFullName =>
                ServiceNodeRemote.listContractPackages.map { packages =>
                    State.update { _ =>
                        GlobalState(
                            mode = stateMode,
                            orgFullName = orgFullName,
                            packages = packages
                        )
                    }
                }
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

case class GlobalState(
    mode: AppMode,
    orgFullName: String,
    packages: Array[String]
) extends AppState

sealed trait AppMode

case object InitMode extends AppMode

case object BootstrapMode extends AppMode

case object JoinMode extends AppMode

case object BootstrapInProgress extends AppMode

case object JoinInProgress extends AppMode

case object ReadyForUse extends AppMode
