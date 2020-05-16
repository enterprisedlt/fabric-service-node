package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.page.form.{Boxes, Components}
import org.enterprisedlt.fabric.service.node.shared._
import org.enterprisedlt.fabric.service.node.state.GlobalStateAware
import org.enterprisedlt.fabric.service.node.util.DataFunction._
import org.scalajs.dom.html.Div

/**
 * @author Alexey Polubelov
 */
object Boot {

    @Lenses case class BootstrapState(
        bootstrapOptions: BootstrapOptions
    )

    object BootstrapState {
        val Defaults: BootstrapState =
            BootstrapState(
                BootstrapOptions.Defaults)
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

        def deleteComponent(cType: String, name: String): CallbackTo[Unit] = {
            val state = cType match {
                case ComponentCandidate.OSN =>
                    OsnNodes.modify(_.filter(_.name != name))
                case ComponentCandidate.Peer =>
                    PeerNodes.modify(_.filter(_.name != name))
            }
            $.modState(state)
        }


        def refresh(globalState: GlobalState): Callback = Callback {
            Context.refreshState(globalState, BootstrapMode)
        }

        def addNetworkComponents(components: Seq[ComponentCandidate], g: GlobalState): CallbackTo[Unit] = {
            $.modState(addComponents(components, g))
        }

        private def addComponents(components: Seq[ComponentCandidate], g: GlobalState): BootstrapState => BootstrapState = {
            val byType = components.groupBy(_.componentType)
            val addPeers: BootstrapState => BootstrapState = byType.get(ComponentCandidate.Peer).map { peers =>
                val peerConfigs = peers.map { componentCandidate =>
                    PeerConfig(
                        box = componentCandidate.box,
                        name = s"${componentCandidate.name}.${g.orgFullName}",
                        port = componentCandidate.port,
                        couchDB = null
                    )
                }
                PeerNodes.modify(_ ++ peerConfigs)
            }.getOrElse(s => s)

            val addOSNs: BootstrapState => BootstrapState = byType.get(ComponentCandidate.OSN).map { osns =>
                val osnConfigs = osns.map { componentCandidate =>
                    OSNConfig(
                        box = componentCandidate.box,
                        name = s"${componentCandidate.name}.${g.orgFullName}",
                        port = componentCandidate.port
                    )
                }
                OsnNodes.modify(_ ++ osnConfigs)
            }.getOrElse(s => s)

            addPeers andThen addOSNs
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
                                      Components(
                                          network = s.bootstrapOptions.network,
                                          addNetworkComponent = addNetworkComponents(_, g),
                                          deleteComponent = deleteComponent
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

    def apply(): Unmounted[Unit, BootstrapState, Backend] = component()


}

object data {
    def toggle: VdomAttr[Any] = VdomAttr("data-toggle")
}
