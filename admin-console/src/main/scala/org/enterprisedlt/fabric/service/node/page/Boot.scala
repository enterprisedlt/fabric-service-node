package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.all.{className, id, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.page.form.Boxes
import org.enterprisedlt.fabric.service.node.state.{ApplyFor, GlobalStateAware}
import org.enterprisedlt.fabric.service.node.util.DataFunction._
import org.scalajs.dom.html.{Div, Select}

/**
 * @author Alexey Polubelov
 */
object Boot {

    @Lenses case class BootstrapState(
        bootstrapOptions: BootstrapOptions,
        componentCandidate: ComponentCandidate,
        boxCandidate: RegisterBoxManager
    )

    object BootstrapState {
        val ComponentTypes = Seq("Orderer", "Peer")
        val Defaults: BootstrapState =
            BootstrapState(
                BootstrapOptions.Defaults,
                ComponentCandidate(
                    box = "",
                    name = "",
                    port = 0,
                    componentType = ComponentTypes.head
                ),
                RegisterBoxManager.Defaults
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


        override def connectLocal: ConnectFunction = ApplyFor(
            Seq(
                ((BootstrapState.componentCandidate / ComponentCandidate.box).when(_.trim.isEmpty) <~~ GlobalState.boxes.when(_.nonEmpty)) (_.head.name)
            )
        )

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


        def refresh(globalState: GlobalState): Callback = Callback {
            Context.refreshState(globalState, BootstrapMode)
        }

        def addNetworkComponent(bootstrapState: BootstrapState, g: GlobalState): CallbackTo[Unit] = {
            $.modState(
                addComponent(bootstrapState, g) andThen BootstrapState.componentCandidate.set(
                    BootstrapState.Defaults.componentCandidate.copy(
                        box = g.boxes.head.name
                    )
                )
            )
        }

        private def addComponent(bootstrapState: BootstrapState, g: GlobalState): BootstrapState => BootstrapState = {
            val componentCandidate = bootstrapState.componentCandidate
            componentCandidate.componentType match {
                case "Peer" =>
                    val peerConfig = PeerConfig(
                        box = componentCandidate.box,
                        name = s"${componentCandidate.name}.${g.orgFullName}",
                        port = componentCandidate.port,
                        couchDB = null
                    )
                    PeerNodes.modify(_ :+ peerConfig)
                case "Orderer" =>
                    val osnConfig = OSNConfig(
                        box = componentCandidate.box,
                        name = s"${componentCandidate.name}.${g.orgFullName}",
                        port = componentCandidate.port
                    )
                    OsnNodes.modify(_ :+ osnConfig)
                case _ => throw new Exception("Unknown component type")
            }
        }

        def renderBoxesList(s: BootstrapState, g: GlobalState): VdomTagOf[Select] = {
            <.select(className := "form-control",
                id := "componentType",
                bind(s) := BootstrapState.componentCandidate / ComponentCandidate.box,
                boxOptions(s, g)
            )
        }

        def boxOptions(s: BootstrapState, g: GlobalState): TagMod = {
            g.boxes.map { box =>
                option((className := "selected").when(s.componentCandidate.box == box.name), box.name)
            }.toTagMod
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

        def populateWithDefault(g: GlobalState): CallbackTo[Unit] = {
            val defaultOSNList = Array("osn1", "osn2", "osn3")
            val addDefaultOSNs =
                OsnNodes.modify { x =>
                    x ++ defaultOSNList.zipWithIndex.map { case (name, index) =>
                        OSNConfig(
                            box = "default", // TODO: box!
                            name = s"$name.${g.orgFullName}",
                            port = 7001 + index
                        )
                    }
                }

            val addDefaultPeer =
                PeerNodes.modify { x =>
                    x :+ PeerConfig(
                        box = "default", // TODO: box!
                        name = s"peer0.${g.orgFullName}",
                        port = 7010,
                        couchDB = null
                    )
                }

            $.modState(addDefaultOSNs andThen addDefaultPeer)
        }

        // name, title, content
        def renderTabs(heading: TagMod, tabs: (String, String, TagMod)*): VdomTagOf[Div] =
            <.div(^.className := "card ",
                <.div(^.className := "card-header", //bg-primary text-white
                    //                    <.h1("Fabric service node"),
                    heading,
                    <.div(^.className := "nav nav-tabs card-header-tabs", ^.id := "nav-tab", ^.role := "tablist", //
                        tabs.zipWithIndex.map { case ((name, title, _), index) =>
                            <.a(
                                ^.className := s"nav-link${if (index == 0) " active" else ""}",
                                ^.id := s"nav-$name-tab",
                                data.toggle := "tab",
                                ^.href := s"#nav-$name",
                                ^.role.tab,
                                ^.aria.controls := s"nav-$name",
                                ^.aria.selected := false,
                                title
                            )
                        }.toTagMod
                    ),
                ),
                <.div(^.className := "card-body ", //aut-form-card
                    <.div(^.className := "tab-content", ^.id := "nav-tabContent",
                        tabs.zipWithIndex.map { case ((name, _, content), index) =>
                            <.div(^.className := s"tab-pane${if (index == 0) " active" else ""}", ^.id := s"nav-$name", ^.role.tabpanel, ^.aria.labelledBy := s"nav-$name-tab", content)
                        }.toTagMod
                    )
                )
            )

        def footerButtons(s: BootstrapState): VdomTagOf[Div] = {
            <.div(^.className := "form-group mt-1",
                <.button(^.`type` := "button", ^.className := "btn btn-outline-secondary", ^.onClick --> goInit, "Back"),
                <.button(^.`type` := "button", ^.className := "btn btn-outline-success float-right", ^.onClick --> goBootProgress(s.bootstrapOptions), "Bootstrap")
            )
        }

        def renderWithGlobal(s: BootstrapState, global: AppState): VdomTagOf[Div] = global match {
            case g: GlobalState =>
                <.div(
                    <.div(^.className := "card aut-form-card",
                        <.div(^.className := "card-header text-white bg-primary",
                            <.div(^.float.right,
                                <.h4(g.orgFullName)
                            ),
                            <.h1("Bootstrap new network")
                        ),
                        renderTabs(
                            <.div(^.float.right,
                                <.button(
                                    ^.className := "btn",
                                    ^.onClick --> refresh(g),
                                    <.i(^.className := "fas fa-sync")
                                )
                            ),
                            ("components", "Network/Components",
                              <.div(
                                  <.div(^.className := "card aut-form-card",
                                      <.div(^.className := "card-body aut-form-card",
//                                          <.h4("Network:"),
//                                          <.span(<.br()),
                                          <.div(^.className := "form-group row",
                                              <.label(^.className := "col-sm-2 col-form-label", "Consortium"),
                                              <.div(^.className := "col-sm-10",
                                                  <.input(^.`type` := "text", ^.`className` := "form-control",
                                                      bind(s) := BootstrapState.bootstrapOptions / BootstrapOptions.networkName
                                                  )
                                              )
                                          ),
                                          <.span(<.br()),
                                          <.button(
                                              ^.className := "btn btn-light float-right",
                                              "Add default components",
                                              ^.onClick --> populateWithDefault(g)
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
                                                      s.bootstrapOptions.network.orderingNodes.zipWithIndex.map { case (osnNode, index) =>
                                                          <.tr(
                                                              //                                                              <.td(^.scope := "row", s"${index + 1}"),
                                                              <.td(osnNode.name),
                                                              <.td("Orderer"),
                                                              <.td(boxLabel(osnNode.box, g)),
                                                              <.td(osnNode.port),
                                                              <.td(
                                                                  <.button(
                                                                      ^.className := "btn btn-link",
                                                                      "Remove",
                                                                      ^.onClick --> deleteComponent(osnNode))
                                                              )
                                                          )
                                                      }.toTagMod,
                                                      s.bootstrapOptions.network.peerNodes.zipWithIndex.map { case (peerNode, index) =>
                                                          <.tr(
                                                              //                                                              <.td(^.scope := "row", s"${s.bootstrapOptions.network.orderingNodes.length + index + 1}"),
                                                              <.td(peerNode.name),
                                                              <.td("Peer"),
                                                              <.td(boxLabel(peerNode.box, g)),
                                                              <.td(peerNode.port),
                                                              <.td(
                                                                  <.button(
                                                                      ^.className := "btn btn-link",
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
                                              <.label(^.`for` := "componentType", ^.className := "col-sm-2 col-form-label", "Type"),
                                              <.div(^.className := "col-sm-10", renderComponentType(s))
                                          ),
                                          <.div(^.className := "form-group row",
                                              <.label(^.`for` := "componentBox", ^.className := "col-sm-2 col-form-label", "Server"),
                                              <.div(^.className := "col-sm-10", renderBoxesList(s, g))
                                          ),
                                          <.div(^.className := "form-group row",
                                              <.label(^.`for` := "componentName", ^.className := "col-sm-2 col-form-label", "Name"),
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
                                          )
                                      )
                                  )
                              )
                            ),
                            ("box", "Servers",
                              <.div(^.className := "card aut-form-card",
                                  <.div(^.className := "card-body aut-form-card",
                                      Boxes(),
                                  )
                              )
                            ),
                            ("block", "Block",
                              <.div(^.className := "card aut-form-card",
                                  <.div(^.className := "card-body aut-form-card",
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
                                      )
                                  )
                              )
                            ),
                            ("raft", "Raft",
                              <.div(^.className := "card aut-form-card",
                                  <.div(^.className := "card-body aut-form-card",
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
                                  )
                              )
                            )
                        ),
                        footerButtons(s)
                    )
                )
            case _ => <.div()
        }
    }

    private def boxLabel(name: String, g: GlobalState): String =
        g.boxes.find(_.name == name).map(b => s"$name (${boxAddress(b)})").getOrElse("Unknown")

    private def boxAddress(b: Box): String =
        Option(b.information.externalAddress).filter(_.trim.nonEmpty).getOrElse("local")

    def apply(): Unmounted[Unit, BootstrapState, Backend] = component()


}

object data {
    def toggle: VdomAttr[Any] = VdomAttr("data-toggle")
}
