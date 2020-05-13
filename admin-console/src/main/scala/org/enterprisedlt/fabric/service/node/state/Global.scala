package org.enterprisedlt.fabric.service.node.state

import japgolly.scalajs.react.internal.Effect.Id
import japgolly.scalajs.react.vdom.html_<^.{VdomTagOf, _}
import japgolly.scalajs.react.{BackendScope, Callback}
import org.scalajs.dom.html.Div

import scala.collection.mutable
import scala.reflect.ClassTag

/**
 * @author Alexey Polubelov
 */

trait GlobalStateAware[GS, S] {
    type ConnectFunction = (S, GS) => S

    def $: BackendScope[_, S]

    private lazy val updater: BackendScope[_, S]#WithEffect[Id] = $.withEffectsImpure
    private var gs: GS = _

    def onGlobalStateUpdate(g: GS): Unit = {
        gs = g
        updater.modState(s => connectLocal(s, g))
    }

    def connectLocal: ConnectFunction = (s, _) => s

    def render(s: S): VdomTagOf[Div] = {
        gs match {
            case null => <.div()
            case g => renderWithGlobal(s, g)
        }
    }

    def renderWithGlobal(s: S, g: GS): VdomTagOf[Div]

    implicit def CFs2CF[X, Y]: Seq[(X, Y) => X] => (X, Y) => X = fs => { (s, gs) =>
        fs.foldRight(s) { case (f, c) => f(c, gs) }
    }
}

trait GlobalStateAwareP[GS, S, P] {
    type ConnectFunction = (S, GS) => S

    def $: BackendScope[P, S]

    private lazy val updater: BackendScope[P, S]#WithEffect[Id] = $.withEffectsImpure
    private var gs: GS = _

    def onGlobalStateUpdate(g: GS): Unit = {
        gs = g
        updater.modState((s, p) => connectLocal(s, g))
    }

    def connectLocal: ConnectFunction = (s, _) => s

    def render(p: P, s: S): VdomTagOf[Div] = {
        gs match {
            case null => <.div()
            case g => renderWithGlobal(s, p, g)
        }
    }

    def renderWithGlobal(s: S, p: P, g: GS): VdomTagOf[Div]

    // convert collection of connect functions to one
    implicit def CFs2CF[X, Y]: Seq[(X, Y) => X] => (X, Y) => X = fs => { (s, gs) =>
        fs.foldRight(s) { case (f, c) => f(c, gs) }
    }
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

    def unsubscribe(f: GS => Unit): Unit = {
        subscribers -= f
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

    def unpause(): Unit = {
        paused = false
        subscribers.foreach(_ (current_))
    }

    def connect[T <: GlobalStateAware[GS, _]](x: T): Callback = Callback(subscribe(x.onGlobalStateUpdate))

    def connectP[T <: GlobalStateAwareP[GS, _, _]](x: T): Callback = Callback(subscribe(x.onGlobalStateUpdate))

    def disconnect[T <: GlobalStateAware[GS, _]](x: T): Callback = Callback(unsubscribe(x.onGlobalStateUpdate))

    def disconnectP[T <: GlobalStateAwareP[GS, _, _]](x: T): Callback = Callback(unsubscribe(x.onGlobalStateUpdate))

}

object ApplyFor {

    def apply[GS, T <: GS : ClassTag, S](f: (S, T) => S): (S, GS) => S = { (s: S, gs: GS) =>
        gs match {
            case t: T => f(s, t)
            case _ => s
        }
    }
}
