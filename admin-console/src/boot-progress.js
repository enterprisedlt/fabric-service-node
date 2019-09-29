import {inject} from 'aurelia-framework';
import {App} from "app"
import {Connector} from "./service/connector";

const NotInitialized = 0;
const BootstrapStarted = 11;
const BootstrapMaxValue = 21;
const Ready = 100;

const StateUpdateInterval = 500;

@inject(App, Connector)
export class BootProgress {

  currentProgress = [];

  bootMessages = [
    "Starting bootstrap process",
    "Creating genesis block",
    "Starting ordering service",
    "Awaiting ordering service to initialize",
    "Starting peer nodes",
    "Creating service channel",
    "Adding peers nodes to channel",
    "Updating anchors",
    "Installing service chain code",
    "Initializing service chain code",
    "Setting up block listener"
  ];

  readyToUse = false;

  constructor(app, connector) {
    this.app = app;
    this.connector = connector;
  }

  attached() {
    this.checkServiceState(this.connector);
  }

  checkServiceState(connector) {
    connector.getServiceState()
      .then(response => response.json())
      .then(data => {
        let stateCode = data.stateCode;
        if (stateCode === NotInitialized) {
          this.scheduleCheck();

        } else if (stateCode >= BootstrapStarted && stateCode <= BootstrapMaxValue) {
          this.progressToState(this.bootMessages, stateCode - BootstrapStarted);
          this.scheduleCheck();

        } else if (stateCode === Ready) {
          this.currentProgress = this.bootMessages.map(msg => {
            return {
              isInProgress: false,
              text: msg
            };
          });
          this.readyToUse = true;

        } else {
          console.error("Unexpected state code:", stateCode);
          this.scheduleCheck();
        }
      });
  }

  scheduleCheck() {
    setTimeout(() => this.checkServiceState(this.connector), StateUpdateInterval);
  }

  progressToState(messages, to) {
    let progress = [];
    for (let i = 0; i <= to; i++) {
      let inProgress = i === to;
      progress.push({
        isInProgress: inProgress,
        text: messages[i]
      });
    }
    this.currentProgress = progress;
  }

  cleanProgressFlag() {
    this.currentProgress.forEach(task => {
      task.isInProgress = false;
    });
  }

  goAdministration() {
    this.app.goAdministration();
  }
}
