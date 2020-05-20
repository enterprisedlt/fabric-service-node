package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.TagMod
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, CallbackTo, ScalaComponent}
import org.enterprisedlt.fabric.service.node.state.GlobalStateAwareP
import org.enterprisedlt.fabric.service.node.util.Html.data
import org.enterprisedlt.fabric.service.node.{AppState, Context}
import org.scalajs.dom.html.Div

/**
 * @author Alexey Polubelov
 */
object FSNSPA {

    case class Props(
        orgName: String,
        active: Int,
        pages: Seq[Page]
    )

    case class State(
        activePage: Int
    )

    private val component = ScalaComponent.builder[Props]("Initial")
      .initialState(State(0))
      .renderBackend[Backend]
      .componentWillMount($ => Context.State.connectP($.backend))
      .build

    class Backend(val $: BackendScope[Props, State]) extends GlobalStateAwareP[AppState, State, Props] {

        def switchTo(page: Int): CallbackTo[Unit] = {
            $.modState(s => s.copy(activePage = page))
        }

        override def renderWithGlobal(s: State, p: Props, g: AppState): VdomTagOf[Div] = {
            val indexedPages = p.pages.zipWithIndex
            val activePage = p.pages(s.activePage)
            <.div(
                <.div(
                    ^.className := "fixed-top",
                    <.nav(^.className := "navbar navbar-expand-lg navbar-dark bg-dark",
                        <.a(
                            ^.className := "navbar-brand",
                            ^.href := "#",
                            <.img(
                                ^.src := "logo_full.png",
                                ^.width := "140px",
                                ^.height := "40px",
                                ^.className := "d-inline-block align-top",
                                ^.alt := ""
                            ),
                        ),
                        <.button(^.className := "navbar-toggler", ^.`type` := "button",
                            data.toggle := "collapse", data.target := "#navbarNavAltMarkup",
                            ^.aria.controls := "navbarNavAltMarkup", ^.aria.expanded := false, ^.aria.label := "Toggle navigation",
                            <.span(^.className := "navbar-toggler-icon"),
                        ),
                        <.div(^.className := "collapse navbar-collapse", ^.id := "navbarNavAltMarkup",
                            <.div(^.className := "navbar-nav",
                                if (indexedPages.size > 1) {
                                    indexedPages.map { case (page, index) =>
                                        <.a(
                                            ^.className := s"nav-item nav-link ${if (s.activePage == index) "active" else ""}",
                                            ^.href := "#",
                                            ^.onClick --> switchTo(index),
                                            page.name
                                        )
                                    }.toTagMod
                                } else TagMod.empty
                            )
                        ),
                        <.span(^.className := "navbar-text",
                            <.h5(p.orgName)
                        ),
                    ),
                    <.nav(^.className := "navbar navbar-light bg-light navbar-expand",
                        <.div(^.className := "navbar-nav",
                            activePage.actions.map { action =>
                                <.a(^.className := "nav-item nav-link",
                                    ^.href := s"#${action.id}", ^.role := "button", ^.aria.expanded := false, ^.aria.controls := action.id, data.toggle := "collapse",
                                    action.name
                                )
                            }.toTagMod
                        )
                    ).when(activePage.actions.nonEmpty),
                    <.div(^.width := "500px", ^.id := "action-forms",
                        activePage.actions.map { action =>
                            <.div(^.className := "collapse", ^.id := action.id, data.parent := "#action-forms",
                                <.div(^.className := "card card-body",
                                    action.actionForm
                                )
                            )
                        }.toTagMod
                    ).when(activePage.actions.nonEmpty),
                ),
                <.div(
                    ^.marginTop := (if (activePage.actions.nonEmpty) "160px" else "80px"),
                    activePage.content
                )
            )
        }

    }

    def apply(orgName: String, active: Int, pages: Seq[Page]): Unmounted[Props, State, Backend] = component(Props(orgName, active, pages: Seq[Page]))
}

case class Page(
    name: String,
    content: TagMod,
    actions: Seq[PageAction]
)

case class PageAction(
    name: String,
    id: String,
    actionForm: TagMod
)
