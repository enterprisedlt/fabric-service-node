package org.enterprisedlt.fabric.service.node

import cats.Functor
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ReactEventFromInput}
import monocle.Lens


import scala.util.Try

/**
 * @author Alexey Polubelov
 */
trait FieldBinder[S] {
    def $: BackendScope[_, S]

    def bind(v: S): Bind = new Bind(v)

    private def mapInputTo[X](l: Lens[S, X])(converter: AsX[X])(event: ReactEventFromInput): Callback = {
        val v: String = event.target.value
        // try convert, if not simply ignore
        converter
          .option(v)
          .map { x =>
              $.modState(l.set(x))
          }
          .getOrElse(Callback(println(s"Invalid value: $v")))
    }

    import scala.language.higherKinds

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

        def :=[X](l: Lens[S, X])(implicit converter: AsX[X]): TagMod = {
            Seq(
                ^.onChange ==> mapInputTo(l)(converter),
                ^.value := l.get(s).toString
            ).toTagMod
        }
    }

    //
    trait AsX[X] {
        def option(v: String): Option[X]
    }

    implicit val String2String: AsX[String] = (v: String) => Option(v)
    implicit val String2Int: AsX[Int] = (v: String) => Try(v.toInt).toOption

}

