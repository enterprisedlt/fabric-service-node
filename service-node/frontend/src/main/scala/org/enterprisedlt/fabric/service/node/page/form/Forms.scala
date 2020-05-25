package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.{Callback, ScalaComponent}
import monocle.Lens
import org.enterprisedlt.fabric.service.node.FieldBinder
import org.enterprisedlt.fabric.service.node.util.DataFunction.GetSetModifyFunctions

/**
 * @author Alexey Polubelov
 */

abstract class StatelessForm[T](name: String) extends FieldBinder {
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

abstract class StatelessFormExt[T, D](name: String) extends FieldBinder {
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

abstract class StateFullFormExt[T, E, S](name: String) extends FieldBinder {

    case class Props(
        value: T,
        ext: E,
        callback: (T => T) => Callback
    )

    def initState(p: T, data: E): S

    private val component = ScalaComponent.builder[Props](name)
      .initialStateFromProps(p => initState(p.value, p.ext))
      .renderPS { ($, p, s) =>
          render(s, p.value, p.ext)(p.callback, $.modState)
      }
      .build

    def render(s: S, p: T, data: E)(implicit modP: (T => T) => Callback, modS: (S => S) => Callback): VdomNode

    def apply(value: T, data: E, callback: (T => T) => Callback): Unmounted[Props, S, Unit] = component(Props(value, data, callback))

    def apply[X](state: X, mf: GetSetModifyFunctions[X, T], data: E)
      (implicit modState: (X => X) => Callback)
    : Unmounted[Props, S, Unit] =
        apply(
            mf.get(state),
            data,
            x => modState(mf.modify(x))
        )

    def apply[X](state: X, mf: Lens[X, T], data: E)
      (implicit modState: (X => X) => Callback)
    : Unmounted[Props, S, Unit] =
        apply(
            mf.get(state),
            data,
            x => modState(mf.modify(x))
        )

}
