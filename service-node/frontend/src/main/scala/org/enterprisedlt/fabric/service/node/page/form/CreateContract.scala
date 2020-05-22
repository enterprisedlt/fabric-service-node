package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.vdom.VdomNode
import org.enterprisedlt.fabric.service.node.Ready
import org.enterprisedlt.fabric.service.node.model.CreateContractRequest
import japgolly.scalajs.react.vdom.html_<^._

/**
 * @author Alexey Polubelov
 */
object CreateContract extends StatelessFormExt[CreateContractRequest, Ready]("CreateContract") {
    override def render(p: CreateContractRequest, data: Ready)(implicit modState: CreateContract.CallbackFunction): VdomNode = {
        <.div()
    }
}
