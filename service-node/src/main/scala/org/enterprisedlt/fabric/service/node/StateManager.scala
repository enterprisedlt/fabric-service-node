package org.enterprisedlt.fabric.service.node

import org.enterprisedlt.fabric.service.node.model.RestoredState

/**
 * @author
 */
trait StateManager {

    def storeState(): Either[String, Unit]

    def restoreState(): Either[String, RestoredState]

}
