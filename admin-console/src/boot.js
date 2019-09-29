import {inject} from 'aurelia-framework';
import {App} from "app"
import {Connector} from "./service/connector";

@inject(App, Connector)
export class Boot {
  bootstrapSettings = {
    block: {
      maxMessageCount: 150,
      absoluteMaxBytes: 103809024,
      preferredMaxBytes: 524288,
      batchTimeOut: "1s"
    },
    raftSettings: {
      tickInterval: "500ms",
      electionTick: 10,
      heartbeatTick: 1,
      maxInflightBlocks: 5,
      snapshotIntervalSize: 20971520
    }
  };


  constructor(app, connector) {
    this.app = app;
    this.connector = connector;
  }

  doBootstrap() {
    if (this.app) {
      this.app.goBootstrapProgress();
      let control = this.app;
      this.connector.executeBootstrap(this.bootstrapSettings)
      //   .then(response => {
      //   control.goAdministration();
      // });
    }
  }

  goInit() {
    if (this.app) {
      this.app.goInit()
    }
  }
}
