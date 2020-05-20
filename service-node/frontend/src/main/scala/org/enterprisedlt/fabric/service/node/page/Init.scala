package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ReactEventFromInput, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model.{ComponentCandidate, RegisterBoxManager}
import org.enterprisedlt.fabric.service.node.page.form._
import org.enterprisedlt.fabric.service.node.shared._
import org.enterprisedlt.fabric.service.node.state.{ApplyFor, GlobalStateAware}
import org.enterprisedlt.fabric.service.node.util.DataFunction._
import org.enterprisedlt.fabric.service.node.util.Html.data
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.{File, FileReader}

import scala.scalajs.js

/**
 * @author Alexey Polubelov
 */
object Init {

    @Lenses case class State(
        // working entities
        boxCandidate: RegisterBoxManager,
        componentCandidate: ComponentCandidate,
        // config entries
        network: NetworkConfig,
        // boot
        networkName: String,
        block: BlockConfig,
        raft: RaftConfig,
        // join
        inviteFileName: String,
        inviteFile: File,
    )

    private val component = ScalaComponent.builder[Unit]("Initial")
      .initialState(
          State(
              boxCandidate = RegisterBoxManager(
                  name = "",
                  url = ""
              ),
              componentCandidate = ComponentCandidate(
                  box = "",
                  name = "",
                  port = 0,
                  componentType = ComponentCandidate.Types.head
              ),
              //
              network = NetworkConfig.Default,
              //
              networkName = "test_net",
              block = BlockConfig.Default,
              raft = RaftConfig.Default,
              //
              inviteFileName = "",
              inviteFile = null,
          )
      )
      .renderBackend[Backend]
      .componentWillMount($ => Context.State.connect($.backend))
      .build

    class Backend(val $: BackendScope[Unit, State]) extends GlobalStateAware[AppState, State] with StateFieldBinder[State] {
        private val PeerNodes = State.network / NetworkConfig.peerNodes
        private val OsnNodes = State.network / NetworkConfig.orderingNodes

        override def connectLocal: ConnectFunction = ApplyFor(
            Seq(
                ((State.componentCandidate / ComponentCandidate.box).when(_.trim.isEmpty) <~~ GlobalState.boxes.when(_.nonEmpty)) (_.head.name)
            )
        )

        def goBootProgress(s: State): Callback = Callback {
            val options = BootstrapOptions(
                networkName = s.networkName,
                network = s.network,
                block = s.block,
                raft = s.raft
            )
            ServiceNodeRemote.executeBootstrap(options) // this call will block until bootstrap complete, so ignore the future
            Context.switchModeTo(BootstrapInProgress)
        }

        def goJoinProgress(s: State): Callback = Callback {
            val reader = new FileReader()
            reader.onload = _ => {
                val invite = upickle.default.read[Invite](reader.result.asInstanceOf[String])
                val options = JoinOptions(
                    network = s.network,
                    invite = invite,
                )
                ServiceNodeRemote.executeJoin(options)
                Context.switchModeTo(JoinInProgress)
            }
            reader.readAsText(s.inviteFile)
        }

        def changeInviteFile(event: ReactEventFromInput): CallbackTo[Unit] = {
            val file: File = event.target.files(0)
            if (file != null && file != js.undefined) {
                $.modState(x => x.copy(inviteFileName = file.name, inviteFile = file))
            } else {
                Callback()
            }
        }

        def addBox(boxCandidate: RegisterBoxManager): Callback = Callback {
            ServiceNodeRemote.registerBox(boxCandidate)
        }

        def addNetworkComponents(components: Seq[ComponentCandidate], g: GlobalState): CallbackTo[Unit] = {
            $.modState(addComponents(components, g))
        }

        def addDefaultComponents(g: GlobalState): CallbackTo[Unit] = {
            val defaultBox = g.boxes.headOption.map(_.name).getOrElse("default")
            val components =
                Seq("osn1", "osn2", "osn3")
                  .zipWithIndex.map { case (name, index) =>
                    ComponentCandidate(
                        componentType = ComponentCandidate.OSN,
                        name = name,
                        box = defaultBox,
                        port = 7001 + index
                    )
                } :+
                  ComponentCandidate(
                      componentType = ComponentCandidate.Peer,
                      name = "peer0",
                      box = defaultBox,
                      port = 7010
                  )

            addNetworkComponents(components, g)
        }

        private def addComponents(components: Seq[ComponentCandidate], g: GlobalState): State => State = {
            val byType = components.groupBy(_.componentType)
            val addPeers: State => State = byType.get(ComponentCandidate.Peer).map { peers =>
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

            val addOSNs: State => State = byType.get(ComponentCandidate.OSN).map { osns =>
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

        private def removeOsn(config: OSNConfig): CallbackTo[Unit] =
            $.modState(OsnNodes.modify(_.filter(_.name != config.name)))

        private def removePeer(config: PeerConfig): CallbackTo[Unit] =
            $.modState(PeerNodes.modify(_.filter(_.name != config.name)))

        def renderWithGlobal(s: State, global: AppState): VdomTagOf[Div] = global match {
            case g: GlobalState =>
                val osnByBox = s.network.orderingNodes.groupBy(_.box)
                val peerByBox = s.network.peerNodes.groupBy(_.box)
                <.div(
                    FSNSPA(
                        g.orgFullName,
                        0,
                        Seq(
                            Page(
                                name = "Servers",
                                content =
                                  <.div(
                                      ^.width := "900px",
                                      ^.marginTop := "0px",
                                      ^.marginBottom := "0px",
                                      ^.marginLeft := "auto",
                                      ^.marginRight := "auto",
                                      g.boxes.map { box =>
                                          val osnNodes = osnByBox.get(box.name)
                                          val peerNodes = peerByBox.get(box.name)
                                          <.div(
                                              ^.className := "card",
                                              ^.marginTop := "3px",
                                              <.div(^.className := "card-body",
                                                  <.h5(^.className := "card-title", s"${box.name} [${box.information.details}]"),
                                                  <.table(
                                                      ^.width := "100%",
                                                      <.tbody(
                                                          <.tr(
                                                              <.td(<.i(<.b("Ordering Nodes:"))),
                                                              <.td(),
                                                              <.td(),
                                                          ).when(osnNodes.isDefined),
                                                          osnNodes.map { orderingNodes =>
                                                              orderingNodes.map { osn =>
                                                                  <.tr(
                                                                      <.td(),
                                                                      <.td(osn.name),
                                                                      <.td(
                                                                          <.button(^.className := "btn btn-sm btn-outline-secondary", //^.aria.label="edit">
                                                                              <.i(^.className := "far fa-edit")
                                                                          ),
                                                                          <.button(^.className := "btn btn-sm btn-outline-danger", //^.aria.label="remove">
                                                                              ^.onClick --> removeOsn(osn),
                                                                              <.i(^.className := "fas fa-minus-circle")
                                                                          ),
                                                                      )
                                                                  )
                                                              }.toTagMod
                                                          }.getOrElse(TagMod.empty),
                                                          <.tr(
                                                              <.td(<.i(<.b("Peer Nodes:"))), //^.colSpan := 2,
                                                              <.td(),
                                                              <.td(),
                                                          ).when(peerNodes.isDefined),
                                                          peerNodes.map { peerNodes =>
                                                              peerNodes.map { peer =>
                                                                  <.tr(
                                                                      <.td(),
                                                                      <.td(peer.name),
                                                                      <.td(
                                                                          <.button(^.className := "btn btn-sm btn-outline-secondary", //^.aria.label="edit">
                                                                              <.i(^.className := "far fa-edit")
                                                                          ),
                                                                          <.button(^.className := "btn btn-sm btn-outline-danger", //^.aria.label="remove">
                                                                              ^.onClick --> removePeer(peer),
                                                                              <.i(^.className := "fas fa-minus-circle")
                                                                          ),
                                                                      )
//                                                                      <.td(
//                                                                          <.i(^.className := "far fa-edit"), //, ^.color := "#996633"),
//                                                                          <.i(^.className := "fas fa-minus-circle") //, ^.color := "#cc0000"),
//                                                                      )
                                                                  )
                                                              }.toTagMod
                                                          }.getOrElse(TagMod.empty),
                                                      )
                                                  )
                                              )
                                          )
                                      }.toTagMod
                                  ),
                                actions = Seq(
                                    PageAction(
                                        name = "Join",
                                        id = "join-form",
                                        Seq(
                                            <.div(^.className := "form-group row",
                                                <.label(^.className := "col-sm-4 col-form-label", "Invite"),
                                                <.div(^.className := "input-group input-group-sm col-sm-8",
                                                    <.div(^.className := "custom-file",
                                                        <.input(^.`type` := "file",
                                                            ^.className := "custom-file-input", ^.id := "inviteInput",
                                                            ^.onChange ==> changeInviteFile
                                                        ),
                                                        <.label(^.`class` := "custom-file-label", s.inviteFileName)
                                                    )
                                                )
                                            ),
                                            <.div(^.className := "form-group mt-1",
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-secondary",
                                                    data.toggle := "collapse", data.target := "#join-form",
                                                    ^.aria.expanded := true, ^.aria.controls := "join-form",
                                                    "Cancel"
                                                ),
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-success float-right",
                                                    ^.onClick --> goJoinProgress(s),
                                                    "Join consortium"
                                                )
                                            )
                                        ).toTagMod
                                    ),
                                    PageAction(
                                        name = "Bootstrap",
                                        id = "bootstrap-form",
                                        Seq(
                                            <.div(^.className := "form-group row",
                                                <.label(^.className := "col-sm-4 col-form-label", "Consortium"),
                                                <.div(^.className := "col-sm-8",
                                                    <.input(^.`type` := "text", ^.`className` := "form-control form-control-sm",
                                                        bind(s) := State.networkName
                                                    )
                                                )
                                            ),
                                            <.div(^.id := "boot-options-advanced", ^.className := "collapse",
                                                <.hr(),
                                                BlockForm(s, State.block),
                                                <.hr(),
                                                RaftForm(s, State.raft)
                                            ),
                                            <.div(^.className := "form-group mt-1",
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-secondary",
                                                    data.toggle := "collapse", data.target := "#bootstrap-form",
                                                    ^.aria.expanded := true, ^.aria.controls := "bootstrap-form",
                                                    "Cancel"
                                                ),
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-secondary",
                                                    data.toggle := "collapse", data.target := "#boot-options-advanced",
                                                    ^.aria.expanded := false, ^.aria.controls := "boot-options-advanced",
                                                    "Advanced"),

                                                <.button(^.className := "btn btn-sm btn-outline-success float-right",
                                                    ^.onClick --> goBootProgress(s),
                                                    "Bootstrap"
                                                )
                                            )
                                        ).toTagMod
                                    ),
                                    PageAction(
                                        name = "Server",
                                        id = "server-form",
                                        <.div(
                                            ServerForm(s, State.boxCandidate),
                                            <.div(^.className := "form-group mt-1",
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-secondary",
                                                    data.toggle := "collapse", data.target := "#server-form",
                                                    ^.aria.expanded := true, ^.aria.controls := "server-form",
                                                    "Cancel"
                                                ),
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-success float-right",
                                                    ^.onClick --> addBox(s.boxCandidate),
                                                    "Add server"
                                                )
                                            )
                                        )
                                    ),
                                    PageAction(
                                        name = "Component",
                                        id = "component-form",
                                        <.div(
                                            ComponentForm(s, State.componentCandidate, CompD(g.orgFullName, g.boxes)),
                                            <.div(^.className := "form-group mt-1",
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-secondary",
                                                    data.toggle := "collapse", data.target := "#component-form",
                                                    ^.aria.expanded := true, ^.aria.controls := "component-form",
                                                    "Cancel"
                                                ),
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-secondary",
                                                    ^.onClick --> addDefaultComponents(g),
                                                    "Default components",
                                                ),
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-success float-right",
                                                    ^.onClick --> addNetworkComponents(Seq(s.componentCandidate), g),
                                                    "Add component",
                                                )
                                            )
                                        )
                                    )
                                ),
                            ),
                        )
                    )
                )

            case _ => <.div()
        }

        //        def boxInfo(box: Box): String = {
        //            box.information.details
        //            if (box.information.externalAddress.trim.nonEmpty) box.information.externalAddress else "local"
        //        }
    }

    def apply(): Unmounted[Unit, State, Backend] = component()
}
