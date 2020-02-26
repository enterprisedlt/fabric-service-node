package org.enterprisedlt.fabric.service.node.state

import japgolly.scalajs.react.vdom.html_<^.{VdomTagOf, _}
import japgolly.scalajs.react.{BackendScope, Callback}
import monocle.Lens
import org.scalajs.dom.html.Div

import scala.collection.mutable
import scala.reflect.ClassTag

/**
 * @author Alexey Polubelov
 */

trait GlobalStateAware[GS, S] {
    type ConnectFunction = (S, GS) => S

    def $: BackendScope[_, S]

    private lazy val updater = $.withEffectsImpure
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


    //
    // utilities
    //

    implicit class LensesLinks[B, Y](dst: Lens[B, Y]) {
        // lens + lens
        def <~~[A, X](src: Lens[A, X])(mapping: X => Y): (B, A) => B = (s, g) => dst.set(mapping(src.get(g)))(s)

        // lens + (lens & condition)
        def <~~[A, X](source: LensWithCondition[A, X])(mapping: X => Y): (B, A) => B = { (s, g) =>
            val v = source.lens.get(g)
            if (source.condition(v)) dst.set(mapping(v))(s) else s
        }

        def when(condition: Y => Boolean): LensWithCondition[B, Y] = LensWithCondition(dst, condition)

    }

    implicit class LensWithConditionLinks[B, Y](dest: LensWithCondition[B, Y]) {
        // (lens & condition) + lens
        def <~~[A, X](src: Lens[A, X])(mapping: X => Y): (B, A) => B = { (s, g) =>
            val x = src.get(g)
            val y = dest.lens.get(s)
            if (dest.condition(y)) dest.lens.set(mapping(x))(s) else s
        }

        // (lens & condition) + lens
        def <~~[A, X](source: LensWithCondition[A, X])(mapping: X => Y): (B, A) => B = { (s, g) =>
            val x = source.lens.get(g)
            val y = dest.lens.get(s)
            if (source.condition(x) && dest.condition(y)) dest.lens.set(mapping(x))(s) else s
        }

    }

    case class LensWithCondition[A, X](
        lens: Lens[A, X],
        condition: X => Boolean
    )

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

    // for now unpause will always trigger "global state notification" even if there were no changes ...
    def unpause(): Unit = {
        paused = false
        subscribers.foreach(_ (current_))
    }

    def connect[T <: GlobalStateAware[GS, _]](x: T): Callback = Callback(subscribe(x.onGlobalStateUpdate))

    def disconnect[T <: GlobalStateAware[GS, _]](x: T): Callback = Callback(unsubscribe(x.onGlobalStateUpdate))
}

object ApplyFor {

    def apply[GS, T <: GS : ClassTag, S](f: (S, T) => S): (S, GS) => S = { (s: S, gs: GS) =>
        gs match {
            case t: T => f(s, t)
            case _ => s
        }
    }
}
