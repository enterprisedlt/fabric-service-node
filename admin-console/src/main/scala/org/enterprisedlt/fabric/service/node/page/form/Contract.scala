package org.enterprisedlt.fabric.service.node.page.form

import cats.Functor
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.all.{VdomTagOf, className, id, option}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, CallbackTo, ScalaComponent}
import monocle.Lens
import monocle.macros.Lenses
import org.enterprisedlt.fabric.service.node._
import org.enterprisedlt.fabric.service.node.model.{ContractParticipant, CreateContractRequest}
import org.enterprisedlt.fabric.service.node.state.GlobalStateAware
import org.scalajs.dom.html.{Div, Select}

import scala.language.higherKinds

/**
  * @author Maxim Fedin
  */
object Contract {


    @Lenses case class ContractState(
        request: CreateContractRequest,
        chosenPackage: String,
        participantCandidate: ContractParticipant,
        initArgsCandidate: String
    )


    object ContractState {
        val Defaults: ContractState = {
            ContractState(
                CreateContractRequest.Defaults, "",
                ContractParticipant("", ""),
                ""
            )
        }
    }


    private val component = ScalaComponent.builder[Unit]("ContractForm")
      .initialState(ContractState.Defaults)
      .renderBackend[Backend]
      .componentDidMount($ => Context.State.connect($.backend))
      .build

    //    def init($: Lifecycle.ComponentDidMount[Unit, ContractState, Backend]): Callback = {
    //        Context.State.connect($.backend)
    //        ServiceNodeRemote.listContractPackages
    //    }

    class Backend(val $: BackendScope[Unit, ContractState]) extends FieldBinder[ContractState] with GlobalStateAware[AppState, ContractState] {


        private val ContractParticipantState = ContractState.request / CreateContractRequest.parties

        private val InitArgsState = ContractState.request / CreateContractRequest.initArgs


        def renderContractPackagesList(s: ContractState, g: GlobalState): VdomTagOf[Select] = {
            <.select(className := "form-control",
                id := "componentType",
                bind(s) := packageCustomLens(g),
                contractPackagesOptions(s, g)
            )
        }

        private def packageCustomLens(g: GlobalState) =
            new Lens[ContractState, String] {
                override def get(s: ContractState): String = Option(s.chosenPackage).filter(_.nonEmpty).orElse(g.packages.headOption).getOrElse("")

                override def set(b: String): ContractState => ContractState = { state =>
                    state.copy(
                        chosenPackage = b,
                        request = state.request.copy(
                            contractType = b.split("-")(0),
                            version = b.split("-")(1)
                        )
                    )
                }

                override def modifyF[F[_]](f: String => F[String])(s: ContractState)(implicit evidence$1: Functor[F]): F[ContractState] = ???

                override def modify(f: String => String): ContractState => ContractState = ???
            }

        def contractPackagesOptions(s: ContractState, g: GlobalState): TagMod = {
            g.packages.map { name =>
                option((className := "selected").when(s.chosenPackage == name), name)
            }.toTagMod
        }


        def deletePartyComponent(participant: ContractParticipant): CallbackTo[Unit] = {
            val state = ContractParticipantState.modify(_.filter(_.mspId != participant.mspId))
            $.modState(state)
        }

        def addParticipantComponent(contractState: ContractState): CallbackTo[Unit] = {
            $.modState(
                addComponentParticipant(contractState) andThen ContractState.participantCandidate.set(ContractState.Defaults.participantCandidate)
            )
        }

        private def addComponentParticipant(contractState: ContractState): ContractState => ContractState = {
            val componentCandidate = contractState.participantCandidate
            ContractParticipantState.modify(_ :+ componentCandidate)
        }


        private def deleteInitArgsComponent(arg: String): CallbackTo[Unit] = {
            val state = InitArgsState.modify(_.filter(_ != arg))
            $.modState(state)
        }

        private def addInitArgsComponent(contractState: ContractState): CallbackTo[Unit] = {
            $.modState(
                addComponentInitArgs(contractState) andThen ContractState.initArgsCandidate.set(ContractState.Defaults.initArgsCandidate)
            )

        }



        private def addComponentInitArgs(contractState: ContractState): ContractState => ContractState = {
            val componentCandidate = contractState.participantCandidate
            ContractParticipantState.modify(_ :+ componentCandidate)
        }

        def renderWithGlobal(s: ContractState, global: AppState): VdomTagOf[Div] = global match {
            case g: GlobalState =>
                <.div(
                    <.h4("Add contract"),
                    <.span(<.br()),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "contractPackages", ^.className := "col-sm-2 col-form-label", "Contract packages"),
                        <.div(^.className := "col-sm-10", renderContractPackagesList(s, g))
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.className := "col-sm-2 col-form-label", "Contract Type"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control",
                                bind(s) := ContractState.request / CreateContractRequest.contractType
                            )
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.className := "col-sm-2 col-form-label", "Version"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control",
                                bind(s) := ContractState.request / CreateContractRequest.version
                            )
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.className := "col-sm-2 col-form-label", "Name"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control",
                                bind(s) := ContractState.request / CreateContractRequest.name
                            )
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.className := "col-sm-2 col-form-label", "Channel type"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control",
                                bind(s) := ContractState.request / CreateContractRequest.channelName
                            )
                        )
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.className := "col-sm-2 col-form-label", "Parties"),
                        <.div(^.className := "col-sm-10",
                            <.table(^.className := "table table-hover table-sm",^.id :="parties",
                                <.thead(
                                    <.tr(
                                        <.th(^.scope := "col", "#"),
                                        <.th(^.scope := "col", "MSP ID"),
                                        <.th(^.scope := "col", "role"),
                                        <.th(^.scope := "col", "Actions"),
                                    )
                                ),
                                <.tbody(
                                    s.request.parties.zipWithIndex.map { case (party, index) =>
                                        <.tr(
                                            <.td(^.scope := "row", s"${index + 1}"),
                                            <.td(party.mspId),
                                            <.td(party.role),
                                            <.td(
                                                <.button(
                                                    ^.className := "btn btn-primary",
                                                    "Remove",
                                                    ^.onClick --> deletePartyComponent(party))
                                            )
                                        )
                                    }.toTagMod
                                )
                            ))
                    ),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "componentName", ^.className := "col-sm-2 col-form-label", "MSP ID"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "mspid",
                                bind(s) := ContractState.participantCandidate / ContractParticipant.mspId
                            )
                        )),
                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "port", ^.className := "col-sm-2 col-form-label", "Role"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "role",
                                bind(s) := ContractState.participantCandidate / ContractParticipant.role
                            )
                        )),
                    <.div(^.className := "form-group row",
                        <.button(
                            ^.className := "btn btn-primary",
                            ^.onClick --> addParticipantComponent(s),
                            "Add host")
                    ),


                    <.div(^.className := "form-group row",
                        <.label(^.className := "col-sm-2 col-form-label", "Init args"),
                        <.div(^.className := "col-sm-10",
                            <.table(^.className := "table table-hover table-sm",^.id :="initArgs",
                                <.thead(
                                    <.tr(
                                        <.th(^.scope := "col", "#"),
                                        <.th(^.scope := "col", "Arg")
                                    )
                                ),
                                <.tbody(
                                    s.request.initArgs.zipWithIndex.map { case (arg, index) =>
                                        <.tr(
                                            <.td(^.scope := "row", s"${index + 1}"),
                                            <.td(arg),
                                            <.td(
                                                <.button(
                                                    ^.className := "btn btn-primary",
                                                    "Remove",
                                                    ^.onClick --> deleteInitArgsComponent(arg)
                                                )
                                            )
                                        )
                                    }.toTagMod
                                )
                            ))
                    ),

                    <.div(^.className := "form-group row",
                        <.label(^.`for` := "port", ^.className := "col-sm-2 col-form-label", "Arg"),
                        <.div(^.className := "col-sm-10",
                            <.input(^.`type` := "text", ^.className := "form-control", ^.id := "arg",
                                bind(s) := ContractState.initArgsCandidate
                            )
                        )),
                    <.div(^.className := "form-group row",
                        <.button(
                            ^.className := "btn btn-primary",
                            ^.onClick --> addInitArgsComponent(s),
                            "Add arg")
                    )


                )
            case _ => <.div()

        }
    }


    def apply(): Unmounted[Unit, ContractState, Backend] = component()
}
