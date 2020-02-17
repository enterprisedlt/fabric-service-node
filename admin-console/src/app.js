import {inject} from 'aurelia-framework';
import {Connector} from "./service/connector";

const NotInitialized = 0;
const BootstrapStarted = 11;
const BootstrapMaxValue = 21;
const JoinStarted = 50;
const JoinMaxValue = 50;
const Ready = 100;

@inject(Connector)
export class App {
  composeRef;
  model = {};
  currentViewModelPath;
  organisationFullName;

  constructor(connector) {
    this.connector = connector;
    this.organisationFullName = "";
  }

  activate() {
    return Promise.all([
      this.resolveOrganisationFullName(),
      this.resolveServiceState()
    ])
  }


  resolveOrganisationFullName() {
    return this.connector.getOrganisationFullName()
      .then(response => response.json())
      .then(data => {
        console.log(data);
        this.organisationFullName = data;
      })
  }

  resolveServiceState() {
    return this.connector.getServiceState()
      .then(response => response.json())
      .then(data => {
        let stateCode = data.stateCode;
        if (stateCode === NotInitialized) {
          this.currentViewModelPath = 'init';
        } else if (stateCode >= BootstrapStarted && stateCode <= BootstrapMaxValue) {
          this.currentViewModelPath = 'boot-progress';
        } else if (stateCode >= JoinStarted && stateCode <= JoinMaxValue) {
        } else if (stateCode === Ready) {
          this.currentViewModelPath = 'admin';
        }
      })
  }

  goInit() {
    this.currentViewModelPath = 'init';
  }

  goBootstrap() {
    this.currentViewModelPath = 'boot';
  }

  goJoin() {
    this.currentViewModelPath = 'join';
  }

  goBootstrapProgress() {
    this.currentViewModelPath = 'boot-progress';
  }

  goJoinProgress() {
    this.currentViewModelPath = 'join-progress';
  }

  goAdministration() {
    this.currentViewModelPath = 'admin';
  }

}
