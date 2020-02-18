package org.enterprisedlt.fabric.service.node

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, CallbackTo, ReactEventFromInput}

/**
 * @author Alexey Polubelov
 */
trait FieldBinder[S] {
    def $: BackendScope[_, S]

    def bind(s: S)(read: S => String)(updater: String => S => S): TagMod = {
        Seq(
            ^.onChange ==> update(updater),
            ^.value := read(s)
        ).toTagMod
    }

    def update(update: String => S => S)(event: ReactEventFromInput): CallbackTo[Unit] = {
        val v: String = event.target.value
        $.modState(update(v))
    }
}
