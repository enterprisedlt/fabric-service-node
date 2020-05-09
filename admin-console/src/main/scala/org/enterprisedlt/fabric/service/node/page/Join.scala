package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.all.VdomTagOf
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ReactEventFromInput, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.page.form.{Boxes, Components}
import org.enterprisedlt.fabric.service.node.state.GlobalStateAware
import org.enterprisedlt.fabric.service.node.util.DataFunction._
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.{File, FileReader}

/**
  * @author Maxim Fedin
  */
object Join {

    @Lenses case class JoinState(
        joinOptions: JoinOptions,
        joinFile: File,
        joinFileName: String,
    )

    object JoinState {
        val Defaults: JoinState =
            JoinState(
                JoinOptions.Defaults,
                null,
                "Choose file"
            )
    }

    private val component = ScalaComponent.builder[Unit]("JoinMode")
      .initialState(JoinState.Defaults)
      .renderBackend[Backend]
      .componentDidMount($ => Context.State.connect($.backend))
      .build

    class Backend(val $: BackendScope[Unit, JoinState]) extends FieldBinder[JoinState] with GlobalStateAware[AppState, JoinState] {

        private val PeerNodes = JoinState.joinOptions / JoinOptions.network / NetworkConfig.peerNodes
        private val OsnNodes = JoinState.joinOptions / JoinOptions.network / NetworkConfig.orderingNodes


        def goInit: Callback = Callback {
            Context.switchModeTo(InitMode)
        }

        def goJoinProgress(joinState: JoinState): Callback = Callback {
            val reader = new FileReader()
            reader.onload = _ => {
                val invite = upickle.default.read[Invite](reader.result.asInstanceOf[String])
                val updatedJoinOptions = joinState.joinOptions.copy(invite = invite)
                ServiceNodeRemote.executeJoin(updatedJoinOptions)
                Context.switchModeTo(JoinInProgress)
            }
            reader.readAsText(joinState.joinFile)
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

        private def addComponents(components: Seq[ComponentCandidate], g: GlobalState): JoinState => JoinState = {
            val byType = components.groupBy(_.componentType)
            val addPeers: JoinState => JoinState = byType.get(ComponentCandidate.Peer).map { peers =>
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

            val addOSNs: JoinState => JoinState = byType.get(ComponentCandidate.OSN).map { osns =>
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

        def addFile(event: ReactEventFromInput): CallbackTo[Unit] = {
            val file: File = event.target.files(0)
            $.modState(x => x.copy(joinFileName = file.name, joinFile = file))
        }

        def populateWithDefault(g: GlobalState): CallbackTo[Unit] = {
            val defaultOSNList = Array("osn1", "osn2", "osn3")
            val addDefaultOSNs =
                OsnNodes.modify { x =>
                    x ++ defaultOSNList.zipWithIndex.map { case (name, index) =>
                        OSNConfig(
                            box = "default",
                            name = s"$name.${g.orgFullName}",
                            port = 6001 + index
                        )
                    }
                }

            val addDefaultPeer =
                PeerNodes.modify { x =>
                    x :+ PeerConfig(
                        box = "default",
                        name = s"peer0.${g.orgFullName}",
                        port = 6010,
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

        def footerButtons(s: JoinState): VdomTagOf[Div] = {
            <.div(^.className := "form-group mt-1",
                <.button(^.`type` := "button", ^.className := "btn btn-outline-secondary", ^.onClick --> goInit, "Back"),
                <.button(^.`type` := "button", ^.className := "btn btn-outline-success float-right", ^.onClick --> goJoinProgress(s), "Bootstrap")
            )
        }


        def renderWithGlobal(s: JoinState, global: AppState): VdomTagOf[Div] = global match {
            case g: GlobalState =>
                <.div(
                    <.div(^.className := "card aut-form-card",
                        <.div(^.className := "card-header text-white bg-primary",
                            <.div(^.float.right,
                                <.h4(g.orgFullName)
                            ),
                            <.h1("Join to new network")
                        ),
                        renderTabs(
                            <.div(^.float.right,
                                <.button(
                                    ^.className := "btn",
                                    ^.onClick --> refresh(g),
                                    <.i(^.className := "fas fa-sync")
                                )
                            ),
                            ("components", "Invite/Components",
                              <.div(^.className := "card aut-form-card",
                                  <.div(^.className := "card-body aut-form-card",
                                      <.div(^.className := "form-group row",
                                          <.label(^.className := "col-sm-2 col-form-label", "Invite:"),
                                          <.div(^.className := "input-group col-sm-10",
                                              <.div(^.`class` := "custom-file",
                                                  <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "inviteInput", ^.onChange ==> addFile),
                                                  <.label(^.`class` := "custom-file-label", s.joinFileName)
                                              )
                                          )
                                      ),
                                      <.span(<.br()),
                                      Components(
                                          network = s.joinOptions.network,
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
                            )
                        ),
                        footerButtons(s)
                    )
                )
            case _ => <.div()
        }


    }

    def apply(): Unmounted[Unit, JoinState, Backend] = component()

}


