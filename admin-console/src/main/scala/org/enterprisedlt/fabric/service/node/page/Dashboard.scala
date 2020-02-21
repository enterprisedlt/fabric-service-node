package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.page.form.{AddOrganization,AddContract}
import org.enterprisedlt.fabric.service.node.state.{GlobalStateAware, WithGlobalState}
import org.enterprisedlt.fabric.service.node.{AppState, Context, GlobalState, Initial}
import org.scalajs.dom
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.{Blob, HTMLLinkElement, URL}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

/**
 * @author Alexey Polubelov
 */
object Dashboard {

    case class State(
        global: AppState = Initial
    ) extends WithGlobalState[AppState, State] {
        override def withGlobalState(global: AppState): State = this.copy(global = global)
    }

    private val component = ScalaComponent.builder[Unit]("Dashboard")
      .initialState(State())
      .renderBackend[Backend]
      .componentDidMount($ => Context.State.connect($.backend))
      .build

    class Backend(val $: BackendScope[Unit, State]) extends GlobalStateAware[AppState, State] {

        def createInvite: Callback = Callback {
            ServiceNodeRemote.createInvite.map { invite =>
                doDownload(invite, "invite.json")
            }
        }

        def render(s: State): VdomTagOf[Div] = s.global match {
            case g: GlobalState =>
                <.div(
                    renderTabs(
                        <.div(^.float.right,
                            <.h5(g.orgFullName)
                        ),
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

                              AddOrganization()
                          )
                        ),
                        ("users", "Users",
                          <.div(

                          )
                        ),
                        ("contracts", "Contracts",
                          <.div(^.className := "card-body aut-form-card",
                              AddContract(),

                          )
                        )
                    )
                )
            case _ => <.div()
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

    object data {
        def toggle: VdomAttr[Any] = VdomAttr("data-toggle")
    }

}
