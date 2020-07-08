package org.enterprisedlt.fabric.service.node.model

import java.util.concurrent.locks.ReentrantLock

import org.enterprisedlt.fabric.service.node.shared._

/**
 * @author Alexey Polubelov
 */
object FabricServiceStateHolder {
    type StateChangeFunction = FabricServiceStateFull => Option[FabricServiceStateFull]
    private val lock = new ReentrantLock()
    private var state: FabricServiceState = FabricServiceState(
        mspId = "",
        organizationFullName = "",
        stateCode = -1,
        version = -1
    )

    private var fabricServiceStateFull: FabricServiceStateFull = FabricServiceStateFull()

    def updateStateFull(u: FabricServiceStateFull => FabricServiceStateFull): Unit = {
        lock.lock()
        try {
            val s = fabricServiceStateFull
            fabricServiceStateFull = u(s)
            state = state.copy(version = state.version + 1)
        } finally lock.unlock()
    }


    def updateStateFullIf(condition: FabricServiceStateFull => Boolean)(u: FabricServiceStateFull => FabricServiceStateFull): Unit = {
        lock.lock()
        try {
            val s = fabricServiceStateFull
            if (condition(s)) {
                fabricServiceStateFull = u(s)
                state = state.copy(version = state.version + 1)
            }
        } finally lock.unlock()
    }

    def compose(u: (FabricServiceStateFull => Option[FabricServiceStateFull])*): FabricServiceStateFull => Option[FabricServiceStateFull] = { s =>
        val (r, changed) = u.foldLeft((s, false)) { case ((c, changed), f) =>
            f(c) match {
                case Some(newState) => (newState, true)
                case _ => (c, changed)
            }
        }
        if (changed) Some(r) else None
    }

    def updateStateFullOption(u: FabricServiceStateFull => Option[FabricServiceStateFull]): Unit = {
        lock.lock()
        try {
            u(fabricServiceStateFull).foreach { n =>
                fabricServiceStateFull = n
                state = state.copy(version = state.version + 1)
            }
        } finally lock.unlock()
    }

    def fullState: FabricServiceStateFull = {
        lock.lock()
        try {
            fabricServiceStateFull
        } finally lock.unlock()
    }


    def update(u: FabricServiceState => FabricServiceState): Unit = {
        lock.lock()
        try {
            val s = state
            state = u(s.copy(version = s.version + 1))
        } finally lock.unlock()
    }

    def get: FabricServiceState = {
        lock.lock()
        try {
            state
        } finally lock.unlock()
    }

    def incrementVersion(): Unit = {
        lock.lock()
        try {
            state = state.copy(version = state.version + 1)
        } finally lock.unlock()
    }

}


case class FabricServiceStateFull(
    applications: Array[ApplicationDescriptor] = Array.empty[ApplicationDescriptor],
    deployedApplications: Array[ApplicationInfo] = Array.empty[ApplicationInfo],
    customComponentDescriptors: Array[CustomComponentDescriptor] = Array.empty[CustomComponentDescriptor],
    applicationState: Array[ApplicationState] = Array.empty[ApplicationState],
    events: Events = Events(
        messages = Array.empty[PrivateMessageEvent],
        contractInvitations = Array.empty[ContractInvitation],
        applicationInvitations = Array.empty[ApplicationInvitation]
    )
)
