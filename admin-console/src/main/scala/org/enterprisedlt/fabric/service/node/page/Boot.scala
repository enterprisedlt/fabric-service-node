package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.all.{className, id, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.state.{GlobalStateAware, WithGlobalState}
import org.scalajs.dom.html.{Div, Select}

/**
  * @author Alexey Polubelov
  */
object Boot {

    @Lenses case class BootstrapState(
        bootstrapOptions: BootstrapOptions,
        componentCandidate: ComponentCandidate,
        global: AppState
    ) extends WithGlobalState[AppState, BootstrapState] {
        override def withGlobalState(g: AppState): BootstrapState = this.copy(global = g)
    }

    object BootstrapState {
        val ComponentTypes = Seq("orderer", "peer")
        val Defaults: BootstrapState =
            BootstrapState(
                BootstrapOptions.Defaults,
                ComponentCandidate(
                    name = "",
                    port = 0,
                    componentType = ComponentTypes.head
                ),
                global = Initial
            )
    }

    private val component = ScalaComponent.builder[Unit]("BootstrapMode")
      .initialState(BootstrapState.Defaults)
      .renderBackend[Backend]
      .componentDidMount($ => Context.State.connect($.backend))
      .build

    class Backend(val $: BackendScope[Unit, BootstrapState]) extends FieldBinder[BootstrapState] with GlobalStateAware[AppState, BootstrapState] {

        private val PeerNodes = BootstrapState.bootstrapOptions / BootstrapOptions.network / NetworkConfig.peerNodes
        private val OsnNodes = BootstrapState.bootstrapOptions / BootstrapOptions.network / NetworkConfig.orderingNodes


        def goInit: Callback = Callback {
            Context.switchModeTo(InitMode)
        }

        def goBootProgress(s: BootstrapOptions): Callback = Callback {
            ServiceNodeRemote.executeBootstrap(s) // this call will block until bootstrap complete, so ignore the future
            Context.switchModeTo(BootstrapInProgress)
        }


        def deleteComponent(componentConfig: ComponentConfig): CallbackTo[Unit] = {
            val state = componentConfig match {
                case oc: OSNConfig =>
                    OsnNodes.modify(_.filter(_.name != oc.name))
                case pc: PeerConfig =>
                    PeerNodes.modify(_.filter(_.name != pc.name))
            }
            $.modState(state)
        }


        def addNetworkComponent(bootstrapState: BootstrapState, g: GlobalState): CallbackTo[Unit] = {
            $.modState(addComponent(bootstrapState, g) andThen BootstrapState.componentCandidate.set(BootstrapState.Defaults.componentCandidate))
        }

        private def addComponent(bootstrapState: BootstrapState, g: GlobalState): BootstrapState => BootstrapState = {
            val componentCandidate = bootstrapState.componentCandidate
            componentCandidate.componentType match {
                case "peer" =>
                    PeerNodes.modify { x =>
                        x :+ PeerConfig(
                            name = s"${componentCandidate.name}.${g.orgFullName}",
                            port = componentCandidate.port,
                            couchDB = null
                        )
                    }
                case "orderer" =>
                    OsnNodes.modify { x =>
                        x :+ OSNConfig(
                            name = s"${componentCandidate.name}.${g.orgFullName}",
                            port = componentCandidate.port
                        )
                    }
                case _ => throw new Exception("Unknown component type")
            }
        }

        def renderComponentType(s: BootstrapState): VdomTagOf[Select] = {
            <.select(className := "form-control",
                id := "componentType",
                bind(s) := BootstrapState.componentCandidate / ComponentCandidate.componentType,
                componentTypeOptions(s)
            )
        }

        def componentTypeOptions(s: BootstrapState): TagMod = {
            BootstrapState.ComponentTypes.map { name =>
                option((className := "selected").when(s.componentCandidate.componentType == name), name)
            }.toTagMod
        }


        def render(s: BootstrapState): VdomTagOf[Div] =
            s.global match {
                case g: GlobalState =>
                    <.div(^.className := "card aut-form-card",
                        <.div(^.className := "card-header text-white bg-primary",
                            <.h1("Bootstrap new network")
                        ),
                        <.div(^.className := "card-body aut-form-card",
                            <.h5("Network settings:"),
                            <.span(<.br()),
                            <.div(^.className := "form-group row",
                                <.label(^.className := "col-sm-2 col-form-label", "Network name"),
                                <.div(^.className := "col-sm-10",
                                    <.input(^.`type` := "text", ^.`className` := "form-control",
                                        bind(s) := BootstrapState.bootstrapOptions / BootstrapOptions.networkName
                                    )
                                )
                            ),
                            <.div(^.className := "form-group row",
                                <.table(^.className := "table table-hover table-sm",
                                    <.thead(
                                        <.tr(
                                            <.th(^.scope := "col", "#"),
                                            <.th(^.scope := "col", "Component type"),
                                            <.th(^.scope := "col", "Component name"),
                                            <.th(^.scope := "col", "Port"),
                                            <.th(^.scope := "col", "Actions"),
                                        )
                                    ),
                                    <.tbody(
                                        s.bootstrapOptions.network.orderingNodes.zipWithIndex.map { case (osnNode, index) =>
                                            <.tr(
                                                <.td(^.scope := "row", s"${index + 1}"),
                                                <.td("orderer"),
                                                <.td(osnNode.name),
                                                <.td(osnNode.port),
                                                <.td(
                                                    <.button(
                                                        ^.className := "btn btn-primary",
                                                        "Remove",
                                                        ^.onClick --> deleteComponent(osnNode))
                                                )
                                            )
                                        }.toTagMod,
                                        s.bootstrapOptions.network.peerNodes.zipWithIndex.map { case (peerNode, index) =>
                                            <.tr(
                                                <.td(^.scope := "row", s"${s.bootstrapOptions.network.orderingNodes.length + index + 1}"),
                                                <.td("peer"),
                                                <.td(peerNode.name),
                                                <.td(peerNode.port),
                                                <.td(
                                                    <.button(
                                                        ^.className := "btn btn-primary",
                                                        "Remove",
                                                        ^.onClick --> deleteComponent(peerNode))
                                                )
                                            )
                                        }.toTagMod

                                    )
                                )
                            ),
                            <.hr(),
                            <.div(^.className := "form-group row",
                                <.label(^.`for` := "componentType", ^.className := "col-sm-2 col-form-label", "Component type"),
                                <.div(^.className := "col-sm-10", renderComponentType(s),
                                    //                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "componentType",
                                    //                                bind(s) := JoinState.componentCandidate / ComponentCandidate.componentType
                                    //                            )
                                )
                            ),
                            <.div(^.className := "form-group row",
                                <.label(^.`for` := "componentName", ^.className := "col-sm-2 col-form-label", "Component name"),
                                <.div(^.className := "col-sm-10",
                                    <.input(^.`type` := "text", ^.className := "form-control", ^.id := "componentName",
                                        bind(s) := BootstrapState.componentCandidate / ComponentCandidate.name)
                                )
                            ),
                            <.div(^.className := "form-group row",
                                <.label(^.`for` := "port", ^.className := "col-sm-2 col-form-label", "Port"),
                                <.div(^.className := "col-sm-10",
                                    <.input(^.`type` := "text", ^.className := "form-control", ^.id := "port",
                                        bind(s) := BootstrapState.componentCandidate / ComponentCandidate.port)
                                )
                            ),
                            <.div(^.className := "form-group row",
                                <.button(
                                    ^.className := "btn btn-primary",
                                    "Add component",
                                    ^.onClick --> addNetworkComponent(s, g)
                                )
                            ),
                            <.hr(),
                            <.h5("Block settings:"),
                            <.span(<.br()),
                            <.div(^.className := "form-group row",
                                <.label(^.className := "col-sm-2 col-form-label", "Batch timeout"),
                                <.div(^.className := "col-sm-10",
                                    <.input(^.`type` := "text", ^.className := "form-control",
                                        bind(s) := BootstrapState.bootstrapOptions / BootstrapOptions.block / BlockConfig.batchTimeOut
                                    )
                                )
                            ),
                            <.div(^.className := "form-group row",
                                <.label(^.className := "col-sm-2 col-form-label", "Max messages count"),
                                <.div(^.className := "col-sm-10",
                                    <.input(^.`type` := "text", ^.className := "form-control",
                                        bind(s) := BootstrapState.bootstrapOptions / BootstrapOptions.block / BlockConfig.maxMessageCount
                                    )
                                )
                            ),
                            <.div(^.className := "form-group row",
                                <.label(^.className := "col-sm-2 col-form-label", "Absolute max bytes"),
                                <.div(^.className := "col-sm-10",
                                    <.input(^.`type` := "text", ^.className := "form-control",
                                        bind(s) := BootstrapState.bootstrapOptions / BootstrapOptions.block / BlockConfig.absoluteMaxBytes
                                    )
                                )
                            ),
                            <.div(^.className := "form-group row",
                                <.label(^.className := "col-sm-2 col-form-label", "Preferred max bytes"),
                                <.div(^.className := "col-sm-10",
                                    <.input(^.`type` := "text", ^.className := "form-control",
                                        bind(s) := BootstrapState.bootstrapOptions / BootstrapOptions.block / BlockConfig.preferredMaxBytes
                                    )
                                )
                            ),
                            <.hr(),
                            <.h5("Raft settings:"),
                            <.span(<.br()),
                            <.div(^.className := "form-group row",
                                <.label(^.className := "col-sm-2 col-form-label", "Tick interval"),
                                <.div(^.className := "col-sm-10",
                                    <.input(^.`type` := "text", ^.className := "form-control",
                                        bind(s) := BootstrapState.bootstrapOptions / BootstrapOptions.raft / RaftConfig.tickInterval
                                    )
                                )
                            ),
                            <.div(^.className := "form-group row",
                                <.label(^.className := "col-sm-2 col-form-label", "Election tick"),
                                <.div(^.className := "col-sm-10",
                                    <.input(^.`type` := "text", ^.className := "form-control",
                                        bind(s) := BootstrapState.bootstrapOptions / BootstrapOptions.raft / RaftConfig.electionTick
                                    )
                                )
                            ),
                            <.div(^.className := "form-group row",
                                <.label(^.className := "col-sm-2 col-form-label", "Heartbeat tick"),
                                <.div(^.className := "col-sm-10",
                                    <.input(^.`type` := "text", ^.className := "form-control",
                                        bind(s) := BootstrapState.bootstrapOptions / BootstrapOptions.raft / RaftConfig.heartbeatTick
                                    )
                                )
                            ),
                            <.div(^.className := "form-group row",
                                <.label(^.className := "col-sm-2 col-form-label", "Max inflight blocks"),
                                <.div(^.className := "col-sm-10",
                                    <.input(^.`type` := "text", ^.className := "form-control",
                                        bind(s) := BootstrapState.bootstrapOptions / BootstrapOptions.raft / RaftConfig.maxInflightBlocks
                                    )
                                )
                            ),
                            <.div(^.className := "form-group row",
                                <.label(^.className := "col-sm-2 col-form-label", "Snapshot interval size"),
                                <.div(^.className := "col-sm-10",
                                    <.input(^.`type` := "text", ^.className := "form-control",
                                        bind(s) := BootstrapState.bootstrapOptions / BootstrapOptions.raft / RaftConfig.snapshotIntervalSize
                                    )
                                )
                            )
                        ),
                        <.hr(),
                        <.div(^.className := "form-group mt-1",
                            <.button(^.`type` := "button", ^.className := "btn btn-outline-secondary", ^.onClick --> goInit, "Back"),
                            <.button(^.`type` := "button", ^.className := "btn btn-outline-success float-right", ^.onClick --> goBootProgress(s.bootstrapOptions), "Bootstrap")
                        )
                        //                                                                                                        </form>
                    )
                case _ => <.div()
            }


    }

    def apply(): Unmounted[Unit, BootstrapState, Backend] = component()
}
