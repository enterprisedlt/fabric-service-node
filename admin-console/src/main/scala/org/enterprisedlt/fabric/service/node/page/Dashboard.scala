package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ReactEventFromInput, ScalaComponent}
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.util.Tags._
import org.scalajs.dom
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.{Blob, HTMLLinkElement, URL}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

/**
  * @author Alexey Polubelov
  */
object Dashboard {

    case class State()

    private val component = ScalaComponent.builder[Unit]("Dashboard")
      .initialState(State())
      .renderBackend[Backend]
      .build

    class Backend(val $: BackendScope[Unit, State]) {

        def createInvite: Callback = Callback {
            ServiceNodeRemote.createInvite.map { invite =>
                doDownload(invite, "invite.json")
            }
        }


        def addFile(event: ReactEventFromInput): CallbackTo[Unit] = ???

        def render(s: State): VdomTagOf[Div] =
            <.div(
                Tabs(
                    ("organizations", "Organizations",
                      <.div(^.className := "card-body aut-form-card",
                          <.h4("Invite organization"),
                          <.div(^.float.right, ^.verticalAlign.`text-top`,
                              <.button(^.tpe := "button", ^.className := "btn btn-outline-secondary", ^.onClick --> createInvite, "Invite organization")
                          ),
                          <.ul(
                              <.li("Org1"),
                              <.li("Org2")
                          ),

                          <.hr(),
                          <.h4("Add organization"),
                          <.div(^.float.right, ^.verticalAlign.`text-top`,
                              <.button(^.tpe := "button", ^.className := "btn btn-outline-secondary",  "Add organization")
                          ),

                          <.span(<.br()),
                          <.h5("Certificates"),

                          <.div(^.className := "form-group row",
                              <.label(^.className := "col-sm-2 col-form-label", "Ca Cert:"),
                              <.div(^.className := "input-group col-sm-10",
                                  <.div(^.`class` := "custom-file",
                                      <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "caCerts", ^.onChange ==> addFile),
                                      <.label(^.`class` := "custom-file-label", "Choose file")
                                  )
                              )
                          ),
                          <.div(^.className := "form-group row",
                              <.label(^.className := "col-sm-2 col-form-label", "TLS Ca Cert:"),
                              <.div(^.className := "input-group col-sm-10",
                                  <.div(^.`class` := "custom-file",
                                      <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "tlsCACerts", ^.onChange ==> addFile),
                                      <.label(^.`class` := "custom-file-label", "Choose file")
                                  )
                              )
                          ),
                          <.div(^.className := "form-group row",
                              <.label(^.className := "col-sm-2 col-form-label", "Admin cert:"),
                              <.div(^.className := "input-group col-sm-10",
                                  <.div(^.`class` := "custom-file",
                                      <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "adminCerts", ^.onChange ==> addFile),
                                      <.label(^.`class` := "custom-file-label", "Choose file")
                                  )
                              )
                          ),
                          <.hr(),
                          <.h5("Organization Info"),

                          <.div(^.className := "form-group row",
                              <.label(^.className := "col-sm-2 col-form-label", "Organization msp Id"),
                              <.div(^.className := "col-sm-10",
                                  <.input(^.`type` := "text", ^.className := "form-control",
                                  )
                              )
                          ),

                          <.div(^.className := "form-group row",
                              <.label(^.className := "col-sm-2 col-form-label", "Organization name"),
                              <.div(^.className := "col-sm-10",
                                  <.input(^.`type` := "text", ^.className := "form-control",
                                  )
                              )
                          ),
                          <.div(^.className := "form-group row",
                              <.label(^.className := "col-sm-2 col-form-label", "Member number"),
                              <.div(^.className := "col-sm-10",
                                  <.input(^.`type` := "text", ^.className := "form-control",
                                  )
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
                                  <.tbody()
                              )
                          ),

                          <.div(^.className := "form-group row",
                              <.label(^.`for` := "componentName", ^.className := "col-sm-2 col-form-label", "IP address"),
                              <.div(^.className := "col-sm-10",
                                  <.input(^.`type` := "text", ^.className := "form-control", ^.id := "ipAddress")
                              )),
                          <.div(^.className := "form-group row",
                              <.label(^.`for` := "port", ^.className := "col-sm-2 col-form-label", "DNS name"),
                              <.div(^.className := "col-sm-10",
                                  <.input(^.`type` := "text", ^.className := "form-control", ^.id := "dnsName"))),
                          <.div(^.className := "form-group row",
                              <.button(
                                  ^.className := "btn btn-primary",
                                  "Add host")
                          ),


                      )
                    ),


                    ("users", "Users",
                      <.div(

                      )
                    ),
                    ("contracts", "Contracts",
                      <.div(

                      )
                    )
                )
            )

        def doDownload(content: String, name: String): Unit = {
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
