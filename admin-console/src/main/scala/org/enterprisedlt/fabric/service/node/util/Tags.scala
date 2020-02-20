package org.enterprisedlt.fabric.service.node.util

import japgolly.scalajs.react.vdom.all.VdomAttr
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.html.Div

/**
 * @author Alexey Polubelov
 */
object Tags {

    object data {
        def toggle: VdomAttr[Any] = VdomAttr("data-toggle")
    }

    object Tabs {

        // name, title, content
        def apply(tabs: (String, String, TagMod)*): VdomTagOf[Div] =
            <.div(^.className := "card ",
                <.div(^.className := "card-header", //bg-primary text-white
                    //                    <.h1("Fabric service node"),
                                        <.div(^.float.right,
                                            <.h5("org1.example.com")
                                        ),
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
    }

}
