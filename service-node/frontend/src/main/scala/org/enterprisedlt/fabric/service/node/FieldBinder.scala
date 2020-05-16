package org.enterprisedlt.fabric.service.node

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ReactEventFromInput}
import monocle.Lens
import org.enterprisedlt.fabric.service.node.util.DataFunction._

import scala.util.Try

/**
 * @author Alexey Polubelov
 */
trait FieldBinder[S] {
    def $: BackendScope[_, S]

    def bind(v: S): Bind = new Bind(v)

    private def mapInputTo[X](l: GetSetModifyFunctions[S, X])(converter: AsX[X])(event: ReactEventFromInput): Callback = {
        val v: String = event.target.value
        // try convert, if not simply ignore
        converter
          .option(v)
          .map { x =>
              $.modState(l.set(x))
          }
          .getOrElse(Callback(println(s"Invalid value: $v")))
    }

    class Bind(s: S) {

        def :=[X](l: Lens[S, X])(implicit converter: AsX[X]): TagMod = {
            this.:=(new LensWrapper(l))
        }

        def :=[X](l: GetSetModifyFunctions[S, X])(implicit converter: AsX[X]): TagMod = {
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

