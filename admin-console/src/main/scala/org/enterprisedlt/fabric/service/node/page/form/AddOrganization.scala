package org.enterprisedlt.fabric.service.node.page.form

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ReactEventFromInput, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.FieldBinder
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model.{JoinRequest, KnownHostRecord, Organization, OrganizationCertificates}
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.{File, FileReader}

import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}

/**
  * @author Maxim Fedin
  */
object AddOrganization {

    @Lenses case class AddOrganizationState(
        joinRequest: JoinRequest,
        knownHostsCandidate: KnownHostRecord,
        caCertFileName: String,
        caCertFile: File,
        tlsCaCertFileName: String,
        tlsCaCertFile: File,
        adminCertFileName: String,
        adminCertFile: File
    )


    object AddOrganizationState {
        val Defaults: AddOrganizationState = {
            AddOrganizationState(
                JoinRequest.Defaults,
                KnownHostRecord(
                    ipAddress = "",
                    dnsName = ""
                ),
                "Choose file",
                null,
                "Choose file",
                null,
                "Choose file",
                null
            )
        }
    }

    private val component = ScalaComponent.builder[Unit]("AddOrganizationForm")
      .initialState(AddOrganizationState.Defaults)
      .renderBackend[Backend]
      .build

    class Backend(val $: BackendScope[Unit, AddOrganizationState]) extends FieldBinder[AddOrganizationState] {

        private val KnownHosts = AddOrganizationState.joinRequest / JoinRequest.organization / Organization.knownHosts

        def doJoinNetwork(addOrganizationState: AddOrganizationState): Callback = Callback {
            val r0 = new FileReader()
            r0.onload = _ => {
                val bb1: ByteBuffer = TypedArrayBuffer.wrap(r0.result.asInstanceOf[ArrayBuffer])
                println(s"b1 $bb1")
                val b1 = Base64.getEncoder.encode(bb1)
                println(s"b1 $b1")
                val caCerts = StandardCharsets.UTF_8.decode(b1).toString
                println(s"caCerts $caCerts")
                //
                val r1 = new FileReader()
                r1.onload = _ => {
                    val bb2 = TypedArrayBuffer.wrap(r1.result.asInstanceOf[ArrayBuffer])
                    val b2 = Base64.getEncoder.encode(bb2)
                    val tlsCaCert =  StandardCharsets.UTF_8.decode(b2).toString
                    //
                    val r2 = new FileReader()
                    r2.onload = _ => {
                        val bb3 = TypedArrayBuffer.wrap(r2.result.asInstanceOf[ArrayBuffer])
                        val b3 = Base64.getEncoder.encode(bb3)
                        val adminCert = StandardCharsets.UTF_8.decode(b3).toString
                        //
                        val request = addOrganizationState.joinRequest
                          .copy(
                              organizationCertificates = OrganizationCertificates(
                                  Array(caCerts),
                                  Array(tlsCaCert),
                                  Array(adminCert)
                              )
                          )
                        ServiceNodeRemote.joinNetwork(request)
                    }
                    r2.readAsArrayBuffer(addOrganizationState.tlsCaCertFile)
                }
                r1.readAsArrayBuffer(addOrganizationState.caCertFile)
            }
            r0.readAsArrayBuffer(addOrganizationState.adminCertFile)
        }

        def addCaCertFile(event: ReactEventFromInput): CallbackTo[Unit] = {
            val file: File = event.target.files(0)
            $.modState(x => x.copy(caCertFileName = file.name, caCertFile = file))
        }

        def addTlsCaCertFile(event: ReactEventFromInput): CallbackTo[Unit] = {
            val file: File = event.target.files(0)
            $.modState(x => x.copy(tlsCaCertFileName = file.name, tlsCaCertFile = file))
        }

        def addAdminCertFile(event: ReactEventFromInput): CallbackTo[Unit] = {
            val file: File = event.target.files(0)
            $.modState(x => x.copy(adminCertFileName = file.name, adminCertFile = file))
        }


        def deleteComponent(host: KnownHostRecord): CallbackTo[Unit] = {
            val state = KnownHosts.modify(_.filter(_.dnsName != host.dnsName))
            $.modState(state)
        }

        def addKnownHostComponent(joinState: AddOrganizationState): CallbackTo[Unit] = {
            $.modState(
                addComponent(joinState) andThen AddOrganizationState.knownHostsCandidate.set(AddOrganizationState.Defaults.knownHostsCandidate)
            )
        }

        private def addComponent(dashboardState: AddOrganizationState): AddOrganizationState => AddOrganizationState = {
            val componentCandidate = dashboardState.knownHostsCandidate
            KnownHosts.modify(_ :+ componentCandidate)
        }

        def render(s: AddOrganizationState): VdomTagOf[Div] =
            <.div(
                <.h4("Add organization"),
                <.div(^.float.right, ^.verticalAlign.`text-top`,
                    <.button(^.`type` := "button", ^.className := "btn btn-outline-secondary", ^.onClick --> doJoinNetwork(s), "Add organization")
                ),
                <.span(<.br()),
                <.h5("Certificates"),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Ca Cert:"),
                    <.div(^.className := "input-group col-sm-10",
                        <.div(^.`class` := "custom-file",
                            <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "caCerts", ^.onChange ==> addCaCertFile),
                            <.label(^.`class` := "custom-file-label", s.caCertFileName)
                        )
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "TLS Ca Cert:"),
                    <.div(^.className := "input-group col-sm-10",
                        <.div(^.`class` := "custom-file",
                            <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "tlsCACerts", ^.onChange ==> addTlsCaCertFile),
                            <.label(^.`class` := "custom-file-label", s.tlsCaCertFileName)
                        )
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Admin cert:"),
                    <.div(^.className := "input-group col-sm-10",
                        <.div(^.`class` := "custom-file",
                            <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "adminCerts", ^.onChange ==> addAdminCertFile),
                            <.label(^.`class` := "custom-file-label", s.adminCertFileName)
                        )
                    )
                ),
                <.hr(),
                <.h5("Organization Info"),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Organization msp Id"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control",
                            bind(s) := AddOrganizationState.joinRequest / JoinRequest.organization / Organization.mspId)
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.className := "col-sm-2 col-form-label", "Organization name"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control",
                            bind(s) := AddOrganizationState.joinRequest / JoinRequest.organization / Organization.name)
                    )
                ),
                <.hr(),
                <.h5("Known hosts"),

                <.div(^.className := "form-group row",
                    <.table(^.className := "table table-hover table-sm",
                        <.thead(
                            <.tr(
                                <.th(^.scope := "col", "#"),
                                <.th(^.scope := "col", "IP address"),
                                <.th(^.scope := "col", "DNS name"),
                                <.th(^.scope := "col", "Actions"),
                            )
                        ),
                        <.tbody(
                            s.joinRequest.organization.knownHosts.zipWithIndex.map { case (host, index) =>
                                <.tr(
                                    <.td(^.scope := "row", s"${index + 1}"),
                                    <.td(host.ipAddress),
                                    <.td(host.dnsName),
                                    <.td(
                                        <.button(
                                            ^.className := "btn btn-primary",
                                            "Remove",
                                            ^.onClick --> deleteComponent(host))
                                    )
                                )
                            }.toTagMod
                        )
                    )
                ),
                <.div(^.className := "form-group row",
                    <.label(^.`for` := "componentName", ^.className := "col-sm-2 col-form-label", "IP address"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control", ^.id := "ipAddress",
                            bind(s) := AddOrganizationState.knownHostsCandidate / KnownHostRecord.ipAddress)
                    )),
                <.div(^.className := "form-group row",
                    <.label(^.`for` := "port", ^.className := "col-sm-2 col-form-label", "DNS name"),
                    <.div(^.className := "col-sm-10",
                        <.input(^.`type` := "text", ^.className := "form-control", ^.id := "dnsName",
                            bind(s) := AddOrganizationState.knownHostsCandidate / KnownHostRecord.dnsName))),
                <.div(^.className := "form-group row",
                    <.button(
                        ^.className := "btn btn-primary",
                        ^.onClick --> addKnownHostComponent(s),
                        "Add host")
                ),
            )
    }

    def apply(): Unmounted[Unit, AddOrganizationState, Backend] = component()
}
