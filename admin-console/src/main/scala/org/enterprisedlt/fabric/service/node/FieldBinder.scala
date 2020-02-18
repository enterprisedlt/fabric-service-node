package org.enterprisedlt.fabric.service.node

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, CallbackTo, ReactEventFromInput}

/**
 * @author Alexey Polubelov
 */
trait FieldBinder[S] {
    def $: BackendScope[_, S]

    def bind(v: String)(updater: String => S => S): TagMod = {
        Seq(
            ^.onChange ==> update(updater),
            ^.value := v
        ).toTagMod
    }

    def update(update: String => S => S)(event: ReactEventFromInput): CallbackTo[Unit] = {
        val v: String = event.target.value
        $.modState(update(v))
    }
}
