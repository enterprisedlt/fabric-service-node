package org.enterprisedlt.fabric.service.node.page.form

import japgolly.scalajs.react.vdom.all.{className, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{Callback, CallbackTo}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.shared.{JoinApplicationRequest, Property}
import org.enterprisedlt.fabric.service.node.util.DataFunction._

/**
 * @author Maxim Fedin
 */

@Lenses case class JoinApplicationState(
    applicationProperties: Array[Property],
    propertyCandidate: Property
)

object JoinApplication extends StateFullFormExt[JoinApplicationRequest, Ready, JoinApplicationState]("join-application-form") {

    private def stateFor(appType: String, data: Ready): JoinApplicationState = {
        data.events.applications.find(_.applicationType == appType).map { descriptor =>
            JoinApplicationState(
                applicationProperties = descriptor.properties,
                propertyCandidate = descriptor.properties.headOption.getOrElse(Property("","")), // TODO
            )
        }.getOrElse( // could be only if package list is empty or something got wrong :(
            JoinApplicationState(
                applicationProperties = Array.empty,
                propertyCandidate = Property("",""),
            )
        )
    }

    override def initState(p: JoinApplicationRequest, data: Ready): JoinApplicationState = stateFor(p.name, data)

    override def render(s: JoinApplicationState, p: JoinApplicationRequest, data: Ready)
      (implicit modP: (JoinApplicationRequest => JoinApplicationRequest) => Callback, modS: (JoinApplicationState => JoinApplicationState) => Callback)
    : VdomNode = {
            <.div(
                <.div(^.className := "form-group row",
                    <.div(^.className := "col-sm-12 h-separator", ^.color := "Gray", <.i("Properties"))
                ),
                <.table(^.className := "table table-sm",
                    <.thead(^.className := "thead-light",
                        <.tr(
                            <.th(^.scope := "col", "Name", ^.width := "45%"),
                            <.th(^.scope := "col", "Value", ^.width := "45%"),
                            <.th(^.scope := "col", "", ^.width := "10%"),
                        )
                    ),
                    <.tbody(
                        p.properties.map { property =>
                            <.tr(
                                <.td(property.key),
                                <.td(property.value),
                                <.td(
                                    <.button(^.className := "btn btn-sm btn-outline-danger float-right", //^.aria.label="remove">
                                        ^.onClick --> removeProperty(property),
                                        <.i(^.className := "fas fa-minus-circle")
                                    )
                                )
                            )
                        }.toTagMod,
                        <.tr(
                            <.td(
                                <.select(className := "form-control form-control-sm",
                                    bind(s) := JoinApplicationState.propertyCandidate / Property.key,
                                    s.applicationProperties.map { property =>
                                        option((className := "selected").when(s.propertyCandidate.key == property.key), property.key)
                                    }.toTagMod
                                ),
                            ),
                            <.td(
                                <.input(^.`type` := "text", ^.className := "form-control form-control-sm",
                                    bind(s) := JoinApplicationState.propertyCandidate / Property.value
                                )
                            ),
                            <.td(
                                <.button(^.className := "btn btn-sm btn-outline-success float-right", //^.aria.label="remove">
                                    ^.onClick --> addProperty(s),
                                    <.i(^.className := "fas fa-plus-circle")
                                )
                            )
                        )
                    )
                )
        )
    }

    private def removeProperty(environmentVariable: Property)
      (implicit modState: (JoinApplicationRequest => JoinApplicationRequest) => Callback)
    : CallbackTo[Unit] =
        modState(
            JoinApplicationRequest.properties.modify(
                _.filter(p =>
                    !(p.value == environmentVariable.value && p.key == environmentVariable.key)
                )
            )
        )

    private def addProperty(s: JoinApplicationState)
      (implicit modP: (JoinApplicationRequest => JoinApplicationRequest) => Callback, modS: (JoinApplicationState => JoinApplicationState) => Callback)
    : CallbackTo[Unit] =
        modP(
            JoinApplicationRequest.properties.modify(_ :+ s.propertyCandidate)
        ) >>
          modS(
              JoinApplicationState.propertyCandidate.modify(_ =>
                  Property(
                      key = "",
                      value = ""
                  )
              )
          )
}
