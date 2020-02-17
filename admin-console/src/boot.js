import {inject} from 'aurelia-framework';
import {App} from "app"
import {Connector} from "./service/connector";

@inject(App, Connector)
export class Boot {


  constructor(app, connector) {
    this.app = app;
    this.connector = connector;
    this.bootstrapSettings = this.mkBootrstrapSettings()
  }

  mkBootrstrapSettings(){
    return {
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
      },
      networkName: "test_net",
      network: {
        orderingNodes: [
          {
            name: this.mkFullName("osn1", this.app.organisationFullName),
            port: 7001
          },
          {
            name: this.mkFullName("osn2", this.app.organisationFullName),
            port: 7002
          },
          {
            name: this.mkFullName("osn3", this.app.organisationFullName),
            port: 7003
          }
        ],
        peerNodes: [
          {
            name: this.mkFullName("peer0", this.app.organisationFullName),
            port: 7010,
            couchDB: {
              port: 7011
            }
          }
        ]
      }
    };
  }


  doBootstrap() {
    if (this.app) {
      this.app.goBootstrapProgress();
      let control = this.app;
      this.connector.executeBootstrap(this.bootstrapSettings)

    }
  }

  goInit() {
    if (this.app) {
      this.app.goInit()
    }
  }

  mkFullName(name, fullName) {
    return name + "." + fullName
  }

}
