package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.component.Scala.Unmounted
import monocle.Lens
import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.{Callback, ScalaComponent}
import org.enterprisedlt.fabric.service.node.FieldBinder
import org.enterprisedlt.fabric.service.node.util.DataFunction.GetSetModifyFunctions

/**
 * @author Alexey Polubelov
 */
abstract class StatelessForm[T](name: String) extends FieldBinder[T] {
    type CallbackFunction = (T => T) => Callback

    case class Props(
        value: T,
        callback: CallbackFunction
    )

    private val component = ScalaComponent.builder[Props](name)
      .stateless
      .render_P(p => render(p.value)(p.callback))
      .build

    def render(p: T)(implicit modState: CallbackFunction): VdomNode

    def apply(value: T, callback: CallbackFunction): Unmounted[Props, Unit, Unit] = component(Props(value, callback))

    def apply[S](state: S, mf: GetSetModifyFunctions[S, T])
      (implicit modState: (S => S) => Callback)
    : Unmounted[Props, Unit, Unit] =
        apply(
            mf.get(state),
            x => modState(mf.modify(x))
        )

    def apply[S](state: S, mf: Lens[S, T])
      (implicit modState: (S => S) => Callback)
    : Unmounted[Props, Unit, Unit] =
        apply(
            mf.get(state),
            x => modState(mf.modify(x))
        )

}

abstract class StatelessFormExt[T, D](name: String) extends FieldBinder[T] {
    type CallbackFunction = (T => T) => Callback

    case class Props(
        value: T,
        data: D,
        callback: CallbackFunction
    )

    private val component = ScalaComponent.builder[Props](name)
      .stateless
      .render_P(p => render(p.value, p.data)(p.callback))
      .build

    def render(p: T, data: D)(implicit modState: CallbackFunction): VdomNode

    def apply(value: T, data: D, callback: CallbackFunction): Unmounted[Props, Unit, Unit] = component(Props(value, data, callback))

    def apply[S](state: S, mf: GetSetModifyFunctions[S, T], data: D)
      (implicit modState: (S => S) => Callback)
    : Unmounted[Props, Unit, Unit] =
        apply(
            mf.get(state),
            data,
            x => modState(mf.modify(x))
        )

    def apply[S](state: S, mf: Lens[S, T], data: D)
      (implicit modState: (S => S) => Callback)
    : Unmounted[Props, Unit, Unit] =
        apply(
            mf.get(state),
            data,
            x => modState(mf.modify(x))
        )

}
