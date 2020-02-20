package org.enterprisedlt.fabric.service.node.page

import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.all.{VdomTagOf, className, id, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ReactEventFromInput, ScalaComponent}
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node.connect.ServiceNodeRemote
import org.enterprisedlt.fabric.service.node.model._
import org.enterprisedlt.fabric.service.node.{Context, FieldBinder, InitMode, JoinInProgress}
import org.scalajs.dom.html.{Div, Select}
import org.scalajs.dom.raw.{File, FileReader}

/**
 * @author Maxim Fedin
 */
object Join {

    @Lenses case class JoinState(
        joinOptions: JoinOptions,
        componentCandidate: ComponentCandidate,
        file: File,
        fileName: String
    )

    object JoinState {
        val ComponentTypes = Seq("orderer", "peer")
        val Defaults: JoinState =
            JoinState(
                JoinOptions.Defaults,
                ComponentCandidate(
                    name = "",
                    port = 0,
                    componentType = ComponentTypes.head
                ),
                null,
                "Choose file"
            )
    }

    private val component = ScalaComponent.builder[Unit]("JoinMode")
      .initialState(JoinState.Defaults)
      .renderBackend[Backend]
      .build


    class Backend(val $: BackendScope[Unit, JoinState]) extends FieldBinder[JoinState] {

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
            reader.readAsText(joinState.file)
        }

        def deleteComponent(componentConfig: ComponentConfig): CallbackTo[Unit] = {
            val state = componentConfig match {
                case oc: OSNConfig =>
                    val l = JoinState.joinOptions / JoinOptions.network / NetworkConfig.orderingNodes
                    l.modify(_.filter(_.name != oc.name))
                case pc: PeerConfig =>
                    val l = JoinState.joinOptions / JoinOptions.network / NetworkConfig.peerNodes
                    l.modify(_.filter(_.name != pc.name))
            }
            $.modState(state)
        }


        def addNetworkComponent(joinState: JoinState): CallbackTo[Unit] = {
            $.modState(
                addComponent(joinState) andThen JoinState.componentCandidate.set(JoinState.Defaults.componentCandidate)
            )
        }

        private def addComponent(joinState: JoinState): JoinState => JoinState = {
            val componentCandidate = joinState.componentCandidate
            componentCandidate.componentType match {
                case "peer" =>
                    PeerNodes.modify { x =>
                        x :+ PeerConfig(
                            name = componentCandidate.name,
                            port = componentCandidate.port,
                            couchDB = null
                        )
                    }
                case "orderer" =>
                    OsnNodes.modify { x =>
                        x :+ OSNConfig(
                            name = componentCandidate.name,
                            port = componentCandidate.port
                        )
                    }
                case _ => throw new Exception("Unknown component type")
            }
        }

        def renderComponentType(s: JoinState): VdomTagOf[Select] = {
            <.select(className := "form-control",
                id := "componentType",
                bind(s) := JoinState.componentCandidate / ComponentCandidate.componentType,
                componentTypeOptions(s)
            )
        }

        def componentTypeOptions(s: JoinState): TagMod = {
            JoinState.ComponentTypes.map { name =>
                option((className := "selected").when(s.componentCandidate.componentType == name), name)
            }.toTagMod
        }

        def addFile(event: ReactEventFromInput): CallbackTo[Unit] = {
            val file: File = event.target.files(0)
            $.modState(x => x.copy(fileName = file.name, file = file))
        }

        def render(s: JoinState): VdomTagOf[Div] =
            <.div(^.className := "card aut-form-card",
                <.div(^.className := "card-header text-white bg-primary",
                    <.h1("Join to new network")
                ),
                <.hr(),
                <.div(^.className := "card-body aut-form-card",
                    <.h5("Join settings"),
                    <.div(^.className := "form-group row",
                        <.label(^.className := "col-sm-2 col-form-label", "Invite:"),
                        <.div(^.className := "input-group col-sm-10",
                            <.div(^.`class` := "custom-file",
                                <.input(^.`type` := "file", ^.`class` := "custom-file-input", ^.id := "inviteInput", ^.onChange ==> addFile),
                                <.label(^.`class` := "custom-file-label", s.fileName)
                            )
                        )
                    ),
                    <.hr(),
                    <.h5("Network settings"),
                    <.div(^.className := "form-group row",
                        <.table(^.className := "table table-hover table-sm",
                            <.thead(
                                <.tr(
                                    <.th(^.scope := "col", "#"),
                                    <.th(^.scope := "col", "Component type"),
                                    <.th(^.scope := "col", "Component name"),
                                    <.th(^.scope := "col", "Port"),
                                    <.th(^.scope := "col", "Actions"),
                                )
                            ),
                            <.tbody(
                                s.joinOptions.network.orderingNodes.zipWithIndex.map { case (osnNode, index) =>
                                    <.tr(
                                        <.td(^.scope := "row", s"${index + 1}"),
                                        <.td("orderer"),
                                        <.td(osnNode.name),
                                        <.td(osnNode.port),
                                        <.td(
                                            <.button(
                                                ^.className := "btn btn-primary",
                                                "Remove",
                                                ^.onClick --> deleteComponent(osnNode))
                                        )
                                    )
                                }.toTagMod,
                                s.joinOptions.network.peerNodes.zipWithIndex.map { case (peerNode, index) =>
                                    <.tr(
                                        <.td(^.scope := "row", s"${s.joinOptions.network.orderingNodes.length + index + 1}"),
                                        <.td("peer"),
                                        <.td(peerNode.name),
                                        <.td(peerNode.port),
                                        <.td(
                                            <.button(
                                                ^.className := "btn btn-primary",
                                                "Remove",
                                                ^.onClick --> deleteComponent(peerNode))
                                        )
                                    )
                                }.toTagMod

                            )
                        )
                    ),
                    <.hr(),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "componentType", ^.className := "col-sm-2 col-form-label", "Component type"),
                        <.div(^.className := "col-sm-10", renderComponentType(s),
                            //                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "componentType",
                            //                                bind(s) := JoinState.componentCandidate / ComponentCandidate.componentType
                            //                            )
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "componentName", ^.className := "col-sm-2 col-form-label", "Component name"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "componentName",
                                bind(s) := JoinState.componentCandidate / ComponentCandidate.name)
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "port", ^.className := "col-sm-2 col-form-label", "Port"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "port",
                                bind(s) := JoinState.componentCandidate / ComponentCandidate.port)
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.button(
                            ^.className := "btn btn-primary",
                            "Add component",
                            ^.onClick --> addNetworkComponent(s)
                        )
                    ),
                    <.hr(),
                    <.div(^.className := "form-group mt-1",
                        <.button(^.`type` := "button", ^.className := "btn btn-outline-secondary", ^.onClick --> goInit, "Back"),
                        <.button(^.`type` := "button", ^.className := "btn btn-outline-success float-right", ^.onClick --> goJoinProgress(s), "Join")
                    )
                    //                                                                                                        </form>
                )
            )
    }

    def apply(): Unmounted[Unit, JoinState, Backend] = component()

}
