package org.enterprisedlt.fabric.service.node.state

import japgolly.scalajs.react.vdom.html_<^.VdomTagOf
import japgolly.scalajs.react.{BackendScope, Callback}
import org.scalajs.dom.html.Div
import japgolly.scalajs.react.vdom.html_<^._

import scala.collection.mutable

/**
 * @author Alexey Polubelov
 */

//trait WithGlobalState[GS, X] {
//    self: X =>
//    def withGlobalState(global: GS): X
//}

trait GlobalStateAware[GS, S] {
    def $: BackendScope[_, S]

    private lazy val updater = $.withEffectsImpure
    private var gs: GS = _

    def onGlobalStateUpdate(g: GS): Unit = {
        gs = g
        updater.forceUpdate
    }

    def render(s: S): VdomTagOf[Div] = {
        gs match {
            case null => <.div()
            case g => renderWithGlobal(s, g)
        }
    }

    def renderWithGlobal(s: S, g: GS): VdomTagOf[Div]
}

class GlobalStateManager[GS](
    initial: GS
) {
    private var current_ : GS = initial
    private val subscribers = mutable.ArrayBuffer.empty[GS => Unit]
    private var paused = false

    def subscribe(f: GS => Unit): Unit = {
        subscribers += f
        f(current_)
    }

    def update(f: GS => GS): Unit = {
        current_ = f(current_)
        if (!paused) {
            subscribers.foreach(_ (current_))
        }
    }

    def pause(): Unit = {
        paused = true
    }

    // for now unpause will always trigger "global state notification" even if there were no changes ...
    def unpause(): Unit = {
        paused = false
        subscribers.foreach(_ (current_))
    }

    def connect[T <: GlobalStateAware[GS, _]](x: T): Callback = Callback(subscribe(x.onGlobalStateUpdate))
}

