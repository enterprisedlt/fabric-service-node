package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.all.{className, id, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, CallbackTo, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.model.{Box, ComponentCandidate}
import org.enterprisedlt.fabric.service.node.shared.NetworkConfig
import org.enterprisedlt.fabric.service.node.state.{ApplyFor, GlobalStateAwareP}
import org.enterprisedlt.fabric.service.node.util.DataFunction._
import org.enterprisedlt.fabric.service.node.{AppState, Context, FieldBinder, GlobalState}
import org.scalajs.dom.html.{Div, Select}

/**
 * @author Alexey Polubelov
 */
object Components {

    case class Props(
        network: NetworkConfig,
        addNetworkComponent: Seq[ComponentCandidate] => CallbackTo[Unit],
        deleteComponent: (String, String) => CallbackTo[Unit]
    )

    @Lenses case class State(
        componentCandidate: ComponentCandidate
    )

    object State {
        val ComponentTypes = Seq(ComponentCandidate.OSN, ComponentCandidate.Peer)
        val Initial: State = State(
            ComponentCandidate(
                box = "",
                name = "",
                port = 0,
                componentType = ComponentTypes.head
            )
        )
    }

    private val component = ScalaComponent.builder[Props]("Components")
      .initialState(State.Initial)
      .renderBackend[Backend]
      .componentDidMount($ => Context.State.connectP($.backend))
      .build

    class Backend(val $: BackendScope[Props, State]) extends FieldBinder[State] with GlobalStateAwareP[AppState, State, Props] {

        override def connectLocal: ConnectFunction = ApplyFor(
            Seq(
                ((State.componentCandidate / ComponentCandidate.box).when(_.trim.isEmpty) <~~ GlobalState.boxes.when(_.nonEmpty)) (_.head.name)
            )
        )

        def populateWithDefault(p: Props, g: GlobalState): CallbackTo[Unit] = {
            val components =
                Array("osn1", "osn2", "osn3")
                  .zipWithIndex.map { case (name, index) =>
                    ComponentCandidate(
                        componentType = ComponentCandidate.OSN,
                        name = name,
                        box = "default",
                        port = 7001 + index
                    )
                } :+
                  ComponentCandidate(
                      componentType = ComponentCandidate.Peer,
                      name = "peer0",
                      box = "default",
                      port = 7010
                  )

            p.addNetworkComponent(components)
        }

        override def renderWithGlobal(s: State, p: Props, as: AppState): VdomTagOf[Div] = as match {
            case g: GlobalState =>
                <.div(
                    <.button(
                        ^.className := "btn btn-light float-right",
                        "Add default components",
                        ^.onClick --> populateWithDefault(p, g)
                    ),
                    <.div(^.className := "form-group row", <.h5("Components:")),
                    <.div(^.className := "form-group row",
                        <.table(^.className := "table table-hover table-sm",
                            <.thead(
                                <.tr(
                                    <.th(^.scope := "col", "Name"),
                                    <.th(^.scope := "col", "Type"),
                                    <.th(^.scope := "col", "Server"),
                                    <.th(^.scope := "col", "Port"),
                                    <.th(^.scope := "col", "Actions"),
                                )
                            ),
                            <.tbody(
                                p.network.orderingNodes.map { osnNode =>
                                    <.tr(
                                        <.td(osnNode.name),
                                        <.td(ComponentCandidate.OSN),
                                        <.td(boxLabel(osnNode.box, g)),
                                        <.td(osnNode.port),
                                        <.td(
                                            <.button(
                                                ^.className := "btn btn-link",
                                                "Remove",
                                                ^.onClick --> p.deleteComponent(ComponentCandidate.OSN, osnNode.name)
                                            )
                                        )
                                    )
                                }.toTagMod,
                                p.network.peerNodes.map { peerNode =>
                                    <.tr(
                                        <.td(peerNode.name),
                                        <.td(ComponentCandidate.Peer),
                                        <.td(boxLabel(peerNode.box, g)),
                                        <.td(peerNode.port),
                                        <.td(
                                            <.button(
                                                ^.className := "btn btn-link",
                                                "Remove",
                                                ^.onClick --> p.deleteComponent(ComponentCandidate.Peer, peerNode.name)
                                            )
                                        )
                                    )
                                }.toTagMod

                            )
                        )
                    ),
                    <.hr(),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "componentType", ^.className := "col-sm-2 col-form-label", "Type"),
                        <.div(^.className := "col-sm-10", renderComponentType(s))
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "componentBox", ^.className := "col-sm-2 col-form-label", "Server"),
                        <.div(^.className := "col-sm-10", renderBoxesList(s, g))
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "componentName", ^.className := "col-sm-2 col-form-label", "Name"),
                        <.div(^.className := "input-group col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "componentName",
                                ^.aria.describedBy := "org-name-addon",
                                bind(s) := State.componentCandidate / ComponentCandidate.name
                            ),
                            <.div(^.className := "input-group-append",
                                <.span(^.className := "input-group-text", ^.id := "org-name-addon", g.orgFullName)
                            )
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "port", ^.className := "col-sm-2 col-form-label", "Port"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "port",
                                bind(s) := State.componentCandidate / ComponentCandidate.port
                            )
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.button(
                            ^.className := "btn btn-primary",
                            "Add component",
                            ^.onClick --> addNetworkComponent(p, s, g)
                        )
                    )
                )

            case _ => <.div()
        }

        def addNetworkComponent(p: Props, s: State, g: GlobalState): CallbackTo[Unit] =
            p.addNetworkComponent(Seq(s.componentCandidate)) >> $.modState(
                State.componentCandidate.set(
                    State.Initial.componentCandidate.copy(
                        box = g.boxes.head.name
                    )
                )
            )

        def renderBoxesList(s: State, g: GlobalState): VdomTagOf[Select] = {
            <.select(className := "form-control",
                id := "componentType",
                bind(s) := State.componentCandidate / ComponentCandidate.box,
                boxOptions(s, g)
            )
        }

        def boxOptions(s: State, g: GlobalState): TagMod = {
            g.boxes.map { box =>
                option((className := "selected").when(s.componentCandidate.box == box.name), box.name)
            }.toTagMod
        }


        def renderComponentType(s: State): VdomTagOf[Select] = {
            <.select(className := "form-control",
                id := "componentType",
                bind(s) := State.componentCandidate / ComponentCandidate.componentType,
                componentTypeOptions(s)
            )
        }

        def componentTypeOptions(s: State): TagMod = {
            State.ComponentTypes.map { name =>
                option((className := "selected").when(s.componentCandidate.componentType == name), name)
            }.toTagMod
        }

    }

    private def boxLabel(name: String, g: GlobalState): String =
        g.boxes.find(_.name == name).map(b => s"$name (${boxAddress(b)})").getOrElse("Unknown")

    private def boxAddress(b: Box): String =
        Option(b.information.externalAddress).filter(_.trim.nonEmpty).getOrElse("local")


    def apply(
        network: NetworkConfig,
        addNetworkComponent: Seq[ComponentCandidate] => CallbackTo[Unit],
        deleteComponent: (String, String) => CallbackTo[Unit]
    ): Unmounted[Props, State, Backend] = component(
        Props(network, addNetworkComponent, deleteComponent)
    )
}
