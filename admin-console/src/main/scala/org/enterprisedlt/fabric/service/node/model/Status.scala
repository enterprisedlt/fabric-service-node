package org.enterprisedlt.fabric.service.node.model

/**
  * @author Maxim Fedin
  */
object Status {
    val NotInitialized = 0
    val Ready = 100

    object BootProgressStatus {
        val StateUpdateInterval = 500
        val BootstrapStarted = 11
        val BootstrapMaxValue = 21
    }

    object JoinProgressStatus {
        val StateUpdateInterval = 500
        val JoinStarted = 50
        val JoinMaxValue = 60
    }
}
