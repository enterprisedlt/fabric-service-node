package org.enterprisedlt.fabric.service.node.model

import java.util.concurrent.locks.ReentrantLock

import org.enterprisedlt.fabric.service.node.shared.{ApplicationInfo, FabricServiceState}

/**
 * @author Alexey Polubelov
 */
object FabricServiceStateHolder {
    private val lock = new ReentrantLock()
    private var state: FabricServiceState = FabricServiceState("", "", -1, -1)

    var fabricServiceStateFull: FabricServiceStateFull = FabricServiceStateFull()

    def updateStateFull(u: FabricServiceStateFull => FabricServiceStateFull): Unit = {
        lock.lock()
        try {
            val s = fabricServiceStateFull
            fabricServiceStateFull = u(s)
            state = state.copy(version = state.version + 1)
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
     deployedApplications: Array[ApplicationInfo]= Array.empty[ApplicationInfo]
)
