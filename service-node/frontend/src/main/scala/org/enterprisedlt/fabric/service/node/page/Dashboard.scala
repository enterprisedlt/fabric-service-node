package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.page.form.{ComponentFormDashboard, CreateContract, RegisterOrganization, ServerForm}
import org.enterprisedlt.fabric.service.node.shared._
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
        // Servers
        boxCandidate: RegisterBoxManager,
        componentCandidate: ComponentCandidate,

        // Network
        channelName: String,
        createContractRequest: CreateContractRequest,

        // Goverment
        registerOrganizationRequest: JoinRequest,

        //        block: BlockConfig,
        //        raft: RaftConfig,
        //        networkName: String,
        //        network: NetworkConfig
    )

    private val component = ScalaComponent.builder[Ready]("dashboard")
      .initialStateFromProps { g =>
          val defaultPackage = g.contractPackages.headOption
          State(
              boxCandidate = RegisterBoxManager(
                  name = "",
                  url = ""
              ),
              componentCandidate = ComponentCandidate(
                  box = g.info.boxes.headOption.map(_.name).getOrElse(""),
                  name = "",
                  port = 0,
                  componentType = ComponentCandidate.Types.head,
                  environmentVariables = Array.empty[EnvironmentVariable],
                  ports = Array.empty[PortBind],
                  volumes = Array.empty[VolumeBind]
              ),
              channelName = "",
              createContractRequest = CreateContractRequest(
                  name = "",
                  version = "",
                  contractType = defaultPackage.map(_.name).getOrElse(""),
                  channelName = g.channels.headOption.getOrElse(""),
                  parties = Array.empty[ContractParticipant],
                  initArgs = defaultPackage.map(p => Array.fill(p.initArgsNames.length)("")).getOrElse(Array.empty[String])
              ),
              //
              registerOrganizationRequest = JoinRequest(
                  organization = Organization(
                      mspId = "",
                      memberNumber = 0,
                      knownHosts = Array.empty[KnownHostRecord]
                  ),
                  organizationCertificates = OrganizationCertificates(
                      caCerts = Array.empty[String],
                      tlsCACerts = Array.empty[String],
                      adminCerts = Array.empty[String]
                  )
              )
          )
      }
      .renderBackend[Backend]
      .build

    class Backend(val $: BackendScope[Ready, State]) extends StateFieldBinder[State] {

        def render(s: State, g: Ready): VdomTagOf[Div] = {
            val osnByBox = g.network.orderingNodes.groupBy(_.box)
            val peerByBox = g.network.peerNodes.groupBy(_.box)
            val chainCodeByChannel = g.chainCodes.groupBy(_.channelName)
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
                                          <.div(^.className := "card-header", box.name), //} [${box.information.details}]"),
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
                                                                      <.i(^.className := "fas fa-check", ^.color.green),
                                                                  )
                                                              )
                                                          }.toTagMod
                                                      }.getOrElse(TagMod.empty),
                                                      <.tr(
                                                          <.td(<.i(<.b("Peer Nodes:"))),
                                                          <.td(),
                                                          <.td(),
                                                      ).when(peerNodes.isDefined),
                                                      peerNodes.map { peerNodes =>
                                                          peerNodes.map { peer =>
                                                              <.tr(
                                                                  <.td(),
                                                                  <.td(peer.name),
                                                                  <.td(
                                                                      <.i(^.className := "fas fa-check", ^.color.green),
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
                                                <.button(^.className := "btn btn-sm btn-outline-success float-right",
                                                    ^.onClick --> progress(addBox(s.boxCandidate)),
                                                    "Add server"
                                                )
                                            )
                                        )
                                ),
                                PageAction(
                                    name = "Component",
                                    id = "component-form",
                                    _ => <.div(
                                        ComponentFormDashboard(s, State.componentCandidate, g),
                                        <.div(^.className := "form-group mt-1",
                                            <.button(
                                                ^.className := "btn btn-sm btn-outline-secondary",
                                                data.toggle := "collapse", data.target := "#component-form",
                                                ^.aria.expanded := true, ^.aria.controls := "component-form",
                                                "Cancel"
                                            ),
                                            <.button(
                                                ^.className := "btn btn-sm btn-outline-success float-right",
                                                ^.onClick --> addCustomComponent(s.componentCandidate),
                                                "Add component",
                                            )
                                        )
                                    )
                                )
                            ),
                        ),
                        Page(
                            name = "Network",
                            content =
                              <.div(
                                  ^.width := "900px",
                                  ^.marginTop := "0px",
                                  ^.marginBottom := "0px",
                                  ^.marginLeft := "auto",
                                  ^.marginRight := "auto",
                                  g.channels.map { channel =>
                                      val chainCodes = chainCodeByChannel.get(channel)
                                      <.div(
                                          ^.className := "card",
                                          ^.marginTop := "3px",
                                          <.div(^.className := "card-header", channel),
                                          <.div(^.className := "card-body",
                                              <.table(
                                                  ^.width := "100%",
                                                  <.tbody(
                                                      <.tr(
                                                          <.td(<.i(<.b("Smart contracts:"))),
                                                          <.td(),
                                                          <.td(),
                                                          <.td(),
                                                      ).when(chainCodes.isDefined),
                                                      chainCodes.map {
                                                          _.map { chainCodeInfo =>
                                                              <.tr(
                                                                  <.td(),
                                                                  <.td(chainCodeInfo.name),
                                                                  <.td(chainCodeInfo.version),
                                                                  <.td(chainCodeInfo.language),
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
                                    name = "Channel",
                                    id = "create-channel",
                                    actionForm = progress =>
                                        <.div(
                                            <.div(^.className := "form-group row",
                                                <.label(^.className := "col-sm-4 col-form-label", "Channel name"),
                                                <.div(^.className := "col-sm-8",
                                                    <.input(^.`type` := "text", ^.`className` := "form-control form-control-sm",
                                                        bind(s) := State.channelName
                                                    )
                                                )
                                            ),
                                            <.div(^.className := "form-group mt-1",
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-secondary",
                                                    data.toggle := "collapse", data.target := "#create-channel",
                                                    ^.aria.expanded := true, ^.aria.controls := "create-channel",
                                                    "Cancel"
                                                ),
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-success float-right",
                                                    ^.onClick --> progress(createChannel(s.channelName)),
                                                    "Create channel",
                                                )
                                            )
                                        )
                                ),
                                PageAction(
                                    name = "Contract",
                                    id = "create-contract",
                                    actionForm = progress =>
                                        <.div(
                                            CreateContract(s, State.createContractRequest, g),
                                            <.div(^.className := "form-group mt-1",
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-secondary",
                                                    data.toggle := "collapse", data.target := "#create-contract",
                                                    ^.aria.expanded := true, ^.aria.controls := "create-contract",
                                                    "Cancel"
                                                ),
                                                <.button(^.className := "btn btn-sm btn-outline-success float-right",
                                                    ^.onClick --> progress(createContract(s.createContractRequest)),
                                                    "Create contract"
                                                )
                                            )
                                        )
                                )
                            )
                        ),
                        Page(
                            name = "Government",
                            content =
                              <.div(

                              ),
                            actions = Seq(
                                PageAction(
                                    name = "Invite",
                                    id = "invite-organization",
                                    actionForm = _ =>
                                        <.div(
                                            <.button(
                                                ^.className := "btn btn-sm btn-outline-success",
                                                ^.onClick --> createInvite,
                                                "Invite organization"
                                            )
                                        )
                                ),
                                PageAction(
                                    name = "Register",
                                    id = "register-organization",
                                    actionForm = progress =>
                                        <.div(
                                            RegisterOrganization(s, State.registerOrganizationRequest, g),
                                            <.hr(),
                                            <.div(^.className := "form-group mt-1",
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-secondary",
                                                    data.toggle := "collapse", data.target := "#register-organization",
                                                    ^.aria.expanded := true, ^.aria.controls := "register-organization",
                                                    "Cancel"
                                                ),
                                                <.button(^.className := "btn btn-sm btn-outline-success float-right",
                                                    ^.onClick --> progress(registerOrganization(s.registerOrganizationRequest)),
                                                    "Register organization"
                                                )
                                            )
                                        )
                                )
                            )
                        ),
                        Page(
                            name = "Applications",
                            content = <.div(),
                            actions = Seq.empty,
                        ),
                        Page(
                            name = "Events",
                            content =
                              <.div(
                                  ^.width := "900px",
                                  ^.marginTop := "0px",
                                  ^.marginBottom := "0px",
                                  ^.marginLeft := "auto",
                                  ^.marginRight := "auto",
                                  g.events.contractInvitations.map { contractInvite =>
                                      <.div(^.className := "card card-body",
                                          <.h5(^.className := "card-title", s"Contract invitation (${contractInvite.name})"),
                                          <.p(
                                              <.i(
                                                  <.b(contractInvite.initiator),
                                                  " has invited you to join contract ",
                                                  <.b(contractInvite.name),
                                                  " of type ", <.b(s"${contractInvite.chainCodeName}:${contractInvite.chainCodeVersion}"),
                                                  " with participants ",
                                                  <.b(contractInvite.participants.mkString("[", ", ", "]"))
                                              )
                                          ),
                                          <.div(
                                              <.button(^.className := "btn btn-sm btn-outline-success float-right",
                                                  ^.onClick --> joinContract(contractInvite.initiator, contractInvite.name),
                                                  "Join"
                                              )
                                          )
                                      )
                                  }.toTagMod
                              ),
                            actions = Seq(
                                PageAction(
                                    name = "Message",
                                    id = "send-message",
                                    actionForm = progress =>
                                        <.div(
                                            //                                          <.div(^.className := "form-group row",
                                            //                                              "TODO"
                                            //                                          ),
                                            <.div(^.className := "form-group mt-1",
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-secondary",
                                                    data.toggle := "collapse", data.target := "#send-message",
                                                    ^.aria.expanded := true, ^.aria.controls := "send-message",
                                                    "Cancel"
                                                ),
                                                <.button(
                                                    ^.className := "btn btn-sm btn-outline-success float-right",
                                                    //                                                  ^.onClick --> progress(sendPrivateMessage(...)),
                                                    "Send",
                                                )
                                            )
                                        )
                                ),
                            ),
                        )
                    )
                )
            )
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

        def addBox(boxCandidate: RegisterBoxManager)(r: Callback): Callback = Callback.future {
            ServiceNodeRemote.registerBox(boxCandidate).map(_ => r)
        }

        def addCustomComponent(component: ComponentCandidate): Callback =  Callback.future {
            ServiceNodeRemote.addCustomComponent(component).map(_ => Callback())
        }

        def createInvite: Callback = Callback {
            ServiceNodeRemote.createInvite.map { invite =>
                doDownload(invite, "invite.json")
            }
        }

        def createChannel(channelName: String)(r: Callback): Callback = Callback.future {
            ServiceNodeRemote.createChannel(channelName).map(_ => Callback{
                println("create channel call back done")
            }).map(_ => r)
        }

        def createContract(request: CreateContractRequest)(r: Callback): Callback = Callback.future {
            ServiceNodeRemote.createContract(request).map(_ => r)
        }

        def registerOrganization(request: JoinRequest)(r: Callback): Callback = Callback.future {
            ServiceNodeRemote.joinNetwork(request).map(_ => r)
        }

        def joinContract(initiator: String, name: String): Callback = Callback.future {
            ServiceNodeRemote.contractJoin(ContractJoinRequest(name, initiator)).map(Callback(_))
        }
    }

    def apply(g: Ready): Unmounted[Ready, State, Backend] = component(g)
}
