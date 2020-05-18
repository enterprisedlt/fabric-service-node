package org.enterprisedlt.fabric.service.node.util

import japgolly.scalajs.react.vdom.html_<^.VdomAttr

/**
 * @author Alexey Polubelov
 */
object Html {

    object flex {
        def direction = VdomAttr("flex-direction")
    }

    object data {
        def toggle: VdomAttr[Any] = VdomAttr("data-toggle")

        def target: VdomAttr[Any] = VdomAttr("data-target")

        def parent: VdomAttr[Any] = VdomAttr("data-parent")

        def backdrop: VdomAttr[Any] = VdomAttr("data-backdrop")

        def keyboard: VdomAttr[Any] = VdomAttr("data-keyboard")

        def dismiss: VdomAttr[Any] = VdomAttr("data-dismiss")
    }

}
