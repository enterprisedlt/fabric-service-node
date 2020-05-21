package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model.{ComponentCandidate, RegisterBoxManager}
import org.enterprisedlt.fabric.service.node.page.form.{CompD, ComponentForm, ServerForm}
import org.enterprisedlt.fabric.service.node.shared._
import org.enterprisedlt.fabric.service.node.state.{ApplyFor, GlobalStateAware}
import org.enterprisedlt.fabric.service.node.util.DataFunction._
import org.enterprisedlt.fabric.service.node.util.Html.data
import org.scalajs.dom
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.{Blob, HTMLLinkElement, URL}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

/**
 * @author Alexey Polubelov
 */
object Dashboard {

    @Lenses case class State(
        // working entities
        boxCandidate: RegisterBoxManager,
        componentCandidate: ComponentCandidate,
        //        // config entries
        //        block: BlockConfig,
        //        raft: RaftConfig,
        //        networkName: String,
        network: NetworkConfig
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
              //              networkName = "test_net",
              network = NetworkConfig.Default,
              //              block = BlockConfig.Default,
              //              raft = RaftConfig.Default
          )
      )
      .renderBackend[Backend]
      .componentWillMount($ => Context.State.connect($.backend))
      .build

    class Backend(val $: BackendScope[Unit, State]) extends GlobalStateAware[AppState, State] with StateFieldBinder[State] {
        override def connectLocal: ConnectFunction = ApplyFor(
            Seq(
                ((State.componentCandidate / ComponentCandidate.box).when(_.trim.isEmpty) <~~ (Ready.info / BaseInfo.boxes).when(_.nonEmpty)) (_.head.name)
            )
        )

        def addBox(boxCandidate: RegisterBoxManager): Callback = Callback {
            ServiceNodeRemote.registerBox(boxCandidate)
        }

        def addNetworkComponents(components: Seq[ComponentCandidate], g: Ready): CallbackTo[Unit] = {
            //TODO: implement
            println("not yet :(")
            Callback()
        }

        def createInvite: Callback = Callback {
            ServiceNodeRemote.createInvite.map { invite =>
                doDownload(invite, "invite.json")
            }
        }

        def renderWithGlobal(s: State, global: AppState): VdomTagOf[Div] = global match {
            case g: Ready =>
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
                                              <.div(^.className := "card-body",
                                                  <.h5(^.className := "card-title", s"${box.name} [${box.information.details}]"),
                                                  <.table(
                                                      ^.width := "100%",
                                                      <.tbody(
                                                          <.tr(
                                                              <.td(^.colSpan := 2, <.i(<.b("Ordering Nodes:"))),
                                                              <.td(),
                                                          ).when(osnNodes.isDefined),
                                                          osnNodes.map { orderingNodes =>
                                                              orderingNodes.map { osn =>
                                                                  <.tr(
                                                                      <.td(),
                                                                      <.td(osn.name),
                                                                      <.td(
                                                                          <.i(^.className := "far fa-edit"), //, ^.color := "#996633"),
                                                                          <.i(^.className := "fas fa-minus-circle") //, ^.color := "#cc0000"),
                                                                      )
                                                                  )
                                                              }.toTagMod
                                                          }.getOrElse(TagMod.empty),
                                                          <.tr(
                                                              <.td(^.colSpan := 2, <.i(<.b("Peer Nodes:"))),
                                                              <.td(),
                                                          ).when(peerNodes.isDefined),
                                                          peerNodes.map { peerNodes =>
                                                              peerNodes.map { peer =>
                                                                  <.tr(
                                                                      <.td(),
                                                                      <.td(peer.name),
                                                                      <.td(
                                                                          <.i(^.className := "far fa-edit"), //, ^.color := "#996633"),
                                                                          <.i(^.className := "fas fa-minus-circle") //, ^.color := "#cc0000"),
                                                                      )
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
                                                <.button(^.className := "btn btn-sm btn-outline-success float-right",
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
                                            ComponentForm(s, State.componentCandidate, CompD(g.info.orgFullName, g.info.boxes)),
                                            <.div(^.className := "form-group mt-1",
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-secondary",
                                                    data.toggle := "collapse", data.target := "#component-form",
                                                    ^.aria.expanded := true, ^.aria.controls := "component-form",
                                                    "Cancel"
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
                            Page(
                                name = "Network",
                                content = <.div(),
                                actions = Seq(
                                    PageAction(
                                        name = "Channel",
                                        id = "add-channel",
                                        actionForm = <.div(<.h1("TODO"))
                                    ),
                                    PageAction(
                                        name = "Chaincode",
                                        id = "add-chain-code",
                                        actionForm = <.div(<.h1("TODO"))
                                    )
                                )
                            ),
                            Page(
                                name = "Government",
                                content = <.div(),
                                actions = Seq(
                                    PageAction(
                                        name = "Invite",
                                        id = "invite-organization",
                                        actionForm =
                                          <.div(
                                              <.button(
                                                  ^.className := "btn btn-sm btn-outline-success",
                                                  ^.onClick --> createInvite, "Invite organization")
                                          )
                                    ),
                                    PageAction(
                                        name = "Register",
                                        id = "register-organization",
                                        actionForm = <.div(<.h1("TODO"))
                                    )
                                )
                            )
                        )
                    )
                )

            case _ => <.div()
        }

        //        def boxInfo(box: Box): String = {
        //            box.information.details
        //            if (box.information.externalAddress.trim.nonEmpty) box.information.externalAddress else "local"
        //        }

        private def doDownload(content: String, name: String): Unit = {
            val blob = new Blob(js.Array(content))
            val dataUrl = URL.createObjectURL(blob)
            val anchor = dom.document.createElement("a").asInstanceOf[HTMLLinkElement]
            anchor.href = dataUrl
            anchor.setAttribute("download", name)
            anchor.innerHTML = "Downloading..."
            anchor.style.display = "none"
            dom.document.body.appendChild(anchor)
            js.timers.setTimeout(1) {
                anchor.click()
                dom.document.body.removeChild(anchor)
                js.timers.setTimeout(500) {
                    URL.revokeObjectURL(anchor.href)
                }
            }
        }
    }

    def apply(): Unmounted[Unit, State, Backend] = component()
}

//object Dashboard {
//
//    private val component = ScalaComponent.builder[Unit]("Dashboard")
//      .renderBackend[Backend]
//      .componentDidMount($ => Context.State.connect($.backend))
//      .build
//
//    class Backend(val $: BackendScope[Unit, Unit]) extends GlobalStateAware[AppState, Unit] {
//

//
//        def renderWithGlobal(s: Unit, global: AppState): VdomTagOf[Div] = global match {
//            case g: GlobalState =>
//                <.div(
//                    renderTabs(
//                        <.div(^.float.right,
//                            <.h5(g.orgFullName)
//                        ),
//                        ("organizations", "Organizations",
//                          <.div(^.className := "card-body aut-form-card",
//                              <.h4("Invite organization"),
//                              <.div(^.float.right, ^.verticalAlign.`text-top`,
//                                  <.button(^.tpe := "button", ^.className := "btn btn-outline-secondary", ^.onClick --> createInvite, "Invite organization")
//                              ),
//                              <.ul(
//                                  <.li("Org1"),
//                                  <.li("Org2")
//                              ),
//
//                              <.hr(),
//
//                              AddOrganization()
//                          )
//                        ),
//                        ("users", "Users",
//                          <.div(
//
//                          )
//                        ),
//                        ("contracts", "Contracts",
//                          <.div(^.className := "card-body aut-form-card",
//                              Contracts(),
//
//                          )
//                        )
//                    )
//                )
//            case _ => <.div()
//        }
//
//        // name, title, content
//        def renderTabs(heading: TagMod, tabs: (String, String, TagMod)*): VdomTagOf[Div] =
//            <.div(^.className := "card ",
//                <.div(^.className := "card-header", //bg-primary text-white
//                    //                    <.h1("Fabric service node"),
//                    heading,
//                    <.div(^.className := "nav nav-tabs card-header-tabs", ^.id := "nav-tab", ^.role := "tablist", //
//                        tabs.zipWithIndex.map { case ((name, title, _), index) =>
//                            <.a(
//                                ^.className := s"nav-link${if (index == 0) " active" else ""}",
//                                ^.id := s"nav-$name-tab",
//                                data.toggle := "tab",
//                                ^.href := s"#nav-$name",
//                                ^.role.tab,
//                                ^.aria.controls := s"nav-$name",
//                                ^.aria.selected := false,
//                                title
//                            )
//                        }.toTagMod
//                    ),
//                ),
//                <.div(^.className := "card-body ", //aut-form-card
//                    <.div(^.className := "tab-content", ^.id := "nav-tabContent",
//                        tabs.zipWithIndex.map { case ((name, _, content), index) =>
//                            <.div(^.className := s"tab-pane${if (index == 0) " active" else ""}", ^.id := s"nav-$name", ^.role.tabpanel, ^.aria.labelledBy := s"nav-$name-tab", content)
//                        }.toTagMod
//                    )
//                )
//            )
//

//
//    def apply(): Unmounted[Unit, Unit, Backend] = component()
//
//}
