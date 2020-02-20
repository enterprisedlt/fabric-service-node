package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.state.GlobalStateManager

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * @author Alexey Polubelov
 */
object Context {
    val State: GlobalStateManager[AppState] = new GlobalStateManager(Initial)

    def initialize: Future[Unit] = {
        ServiceNodeRemote.getOrganisationFullName.map { orgFullName =>
            State.update { _ =>
                GlobalState(
                    mode = InitMode,
                    orgFullName = orgFullName
                )
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
    orgFullName: String
) extends AppState

sealed trait AppMode

case object InitMode extends AppMode

case object BootstrapMode extends AppMode

case object JoinMode extends AppMode

case object BootstrapInProgress extends AppMode

case object JoinInProgress extends AppMode

case object ReadyForUse extends AppMode
