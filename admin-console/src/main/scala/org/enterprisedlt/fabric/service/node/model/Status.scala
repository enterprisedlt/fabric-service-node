package org.enterprisedlt.fabric.service.node.model

/**
  * @author Maxim Fedin
  */
object Status {
    val NotInitialized = 0
    val Ready = 100

    object BootProgressStatus {
        val BootstrapStarted = 11
        val BootstrapMaxValue = 21
    }

    object JoinProgressStatus {
        val JoinStarted = 50
        val JoinMaxValue = 60
    }

}


object StateUpdate {
    val Interval = 500
}