package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ReactEventFromInput, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model.ComponentCandidate
import org.enterprisedlt.fabric.service.node.page.form._
import org.enterprisedlt.fabric.service.node.shared._
import org.enterprisedlt.fabric.service.node.state.{ApplyFor, GlobalStateAware}
import org.enterprisedlt.fabric.service.node.util.DataFunction._
import org.enterprisedlt.fabric.service.node.util.Html.data
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.{File, FileReader}
import org.scalajs.dom.FormData

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.UndefOr

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
        // upload component
        componentFile: File,
        componentName: String,
    )

    private val component = ScalaComponent.builder[Initializing]("initial-screen")
      .initialStateFromProps { g =>
          State(
              boxCandidate = RegisterBoxManager(
                  name = "",
                  url = ""
              ),
              componentCandidate = ComponentCandidate(
                  box = "",
                  name = "",
                  port = 0,
                  componentType = g.info.customComponentDescriptors.headOption.map(_.componentType).getOrElse(""),
                  //
                  properties = Array.empty[Property]
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
              //
              componentFile = null,
              componentName = "Choose file",
          )
      }
      .renderBackend[Backend]
      .componentWillMount($ => Context.State.connect($.backend))
      .build

    class Backend(val $: BackendScope[Initializing, State]) extends GlobalStateAware[AppState, State] with StateFieldBinder[State] {
        private val PeerNodes = State.network / NetworkConfig.peerNodes
        private val OsnNodes = State.network / NetworkConfig.orderingNodes

        override def connectLocal: ConnectFunction = ApplyFor(
            Seq(
                ((State.componentCandidate / ComponentCandidate.box).when(_.trim.isEmpty) <~~ (Initializing.info / BaseInfo.boxes).when(_.nonEmpty)) (_.head.name)
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
            //Context.switchModeTo(BootstrapInProgress)
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
                //Context.switchModeTo(JoinInProgress)
            }
            reader.readAsText(s.inviteFile)
        }

        def changeInviteFile(event: ReactEventFromInput): CallbackTo[Unit] = {
            val file: UndefOr[File] = event.target.files(0)
            file.map { f =>
                $.modState(x => x.copy(inviteFileName = f.name, inviteFile = f))
            }.getOrElse(Callback())
        }

        def addBox(boxCandidate: RegisterBoxManager)(f: Callback): Callback = Callback.future {
            ServiceNodeRemote.registerBox(boxCandidate).map(_ => f)
        }

        def addNetworkComponents(components: Seq[ComponentCandidate], g: Initializing): CallbackTo[Unit] = {
            $.modState(addComponents(components, g))
        }

        def addDefaultComponents(g: Initializing): CallbackTo[Unit] = {
            val defaultBox = g.info.boxes.headOption.map(_.name).getOrElse("default")
            val components =
                Seq("osn1", "osn2", "osn3")
                  .zipWithIndex.map { case (name, index) =>
                    ComponentCandidate(
                        componentType = "osn",
                        name = name,
                        box = defaultBox,
                        port = 7001 + index,
                        properties = Array.empty[Property]
                    )
                } :+
                  ComponentCandidate(
                      componentType = "peer",
                      name = "peer0",
                      box = defaultBox,
                      port = 7010,
                      properties = Array.empty[Property]
                  )

            addNetworkComponents(components, g)
        }

        private def addComponents(components: Seq[ComponentCandidate], g: Initializing): State => State = {
            val byType = components.groupBy(_.componentType)
            val addPeers: State => State = byType.get("peer").map { peers =>
                val peerConfigs = peers.map { componentCandidate =>
                    PeerConfig(
                        box = componentCandidate.box,
                        name = s"${componentCandidate.name}.${g.info.orgFullName}",
                        port = componentCandidate.port,
                        //                        couchDB = null
                    )
                }
                PeerNodes.modify(_ ++ peerConfigs)
            }.getOrElse(s => s)

            val addOSNs: State => State = byType.get("osn").map { osns =>
                val osnConfigs = osns.map { componentCandidate =>
                    OSNConfig(
                        box = componentCandidate.box,
                        name = s"${componentCandidate.name}.${g.info.orgFullName}",
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
            case g: Initializing =>
                val osnByBox = s.network.orderingNodes.groupBy(_.box)
                val peerByBox = s.network.peerNodes.groupBy(_.box)
                <.div(
                    FSNSPA(
                        g.info.orgFullName,
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
                                      g.info.boxes.map { box =>
                                          val osnNodes = osnByBox.get(box.name)
                                          val peerNodes = peerByBox.get(box.name)
                                          <.div(
                                              ^.className := "card",
                                              ^.marginTop := "3px",
                                              <.div(^.className := "card-header", box.name), //s"${box.name} [${box.information.details}]"),
                                              <.div(^.className := "card-body",
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
                                        actionForm = _ =>
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
                                        actionForm = _ =>
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
                                                    <.div(^.className := "form-group row",
                                                        <.div(^.className := "col-sm-12 h-separator", ^.color := "Gray", <.i("Block settings"))
                                                    ),
                                                    BlockForm(s, State.block),
                                                    <.div(^.className := "form-group row",
                                                        <.div(^.className := "col-sm-12 h-separator", ^.color := "Gray", <.i("Raft settings"))
                                                    ),
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
                                        actionForm = progress =>
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
                                                        ^.onClick --> progress(addBox(s.boxCandidate)),
                                                        "Add server"
                                                    )
                                                )
                                            )
                                    ),
                                    PageAction(
                                        name = "Component",
                                        id = "component-form",
                                        actionForm = _ =>
                                            <.div(
                                                ComponentForm(s, State.componentCandidate, g.info),
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
                                    ),
                                    PageAction(
                                        name = "Upload",
                                        id = "upload-component",
                                        actionForm = progress =>
                                            <.div(
                                                <.div(^.className := "form-group row",
                                                    <.label(^.className := "col-sm-4 col-form-label", "Component"),
                                                    <.div(^.className := "input-group input-group-sm col-sm-8",
                                                        <.div(^.className := "custom-file",
                                                            <.input(^.`type` := "file", ^.className := "custom-file-input",
                                                                ^.onChange ==> changeCustomComponentFile
                                                            ),
                                                            <.label(^.className := "custom-file-label", s.componentName)
                                                        )
                                                    )
                                                ),
                                                <.div(^.className := "form-group mt-1",
                                                    <.button(
                                                        ^.className := "btn btn-sm btn-outline-secondary",
                                                        data.toggle := "collapse", data.target := "#upload-component",
                                                        ^.aria.expanded := true, ^.aria.controls := "upload-component",
                                                        "Cancel"
                                                    ),
                                                    <.button(
                                                        ^.className := "btn btn-sm btn-outline-success float-right",
                                                        ^.onClick --> progress(uploadCustomComponent(s)),
                                                        "Upload component",
                                                    )
                                                )
                                            )
                                    ),
                                ),
                            ),
                        )
                    )
                )

            case _ => <.div()
        }

        def changeCustomComponentFile(event: ReactEventFromInput): CallbackTo[Unit] = {
            val file: UndefOr[File] = event.target.files(0)
            file.map { f =>
                $.modState(x => x.copy(componentName = f.name, componentFile = f))
            }.getOrElse(Callback())
        }

        def uploadCustomComponent(s: State)(r: Callback): Callback = Callback.future {
            val formData = new FormData
            formData.append("componentFile", s.componentFile)
            ServiceNodeRemote.uploadCustomComponent(formData).map(_ => r)
        }
    }

    def apply(g: Initializing): Unmounted[Initializing, State, Backend] = component(g)
}
