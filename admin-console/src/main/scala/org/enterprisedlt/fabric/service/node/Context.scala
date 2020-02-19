package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.state.GlobalStateManager

/**
 * @author Alexey Polubelov
 */
object Context {
    val State: GlobalStateManager[AppState] = new GlobalStateManager(Initial)
}

sealed trait AppState

case object Initial extends AppState

case object BootstrapMode extends AppState

case object JoinMode extends AppState

case object BootstrapInProgress extends AppState

case object JoinInProgress extends AppState

case object ReadyForUse extends AppState
