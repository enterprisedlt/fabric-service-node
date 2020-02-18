package org.enterprisedlt.fabric.service.node

import cats.Functor
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ReactEventFromInput}
import monocle.Lens

/**
 * @author Alexey Polubelov
 */
trait FieldBinder[S] {
    def $: BackendScope[_, S]

    def bind(v: S): Bind = new Bind(v)

    private def mapInputTo[X](l: Lens[S, X])(event: ReactEventFromInput): Callback = {
        val v: String = event.target.value
        $.modState(l.set(v.asInstanceOf[X]))
    }

    implicit class ComposeLens[A, B](self: Lens[A, B]) {

        def /[D](other: Lens[B, D]): Lens[A, D] =
            new Lens[A, D] {
                def get(s: A): D =
                    other.get(self.get(s))

                def set(d: D): A => A =
                    self.modify(other.set(d))

                def modifyF[F[_] : Functor](f: D => F[D])(s: A): F[A] =
                    self.modifyF(other.modifyF(f))(s)

                def modify(f: D => D): A => A =
                    self.modify(other.modify(f))
            }
    }

    class Bind(s: S) {
        def :=[X](l: Lens[S, X]): TagMod = {
            Seq(
                ^.onChange ==> mapInputTo(l),
                ^.value := l.get(s).toString
            ).toTagMod
        }
    }

}

