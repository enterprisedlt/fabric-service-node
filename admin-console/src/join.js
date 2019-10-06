import {inject} from 'aurelia-framework';
import {App} from "app"
import {Connector} from "./service/connector";

@inject(App, Connector)
export class Join {
  files = {};
  joinSettings = {
    network: {
      orderingNodes: [
        {
          name: "osn1",
          port: 6001
        },
        {
          name: "osn2",
          port: 6002
        },
        {
          name: "osn3",
          port: 6003
        }
      ],
      peerNodes: [
        {
          name: "peer0",
          port: 6010,
          couchDB: {
            port: 6011
          }
        }
      ]
    }
  };



  constructor(app, connector) {
    this.app = app;
    this.connector = connector;
  }

  doJoin() {
    if (this.app && this.connector && this.files) {
      let settings = this.joinSettings;
      const app = this.app;
      const connector = this.connector;
      let file = this.files[0];
      if (file) {
        console.info("Selected file:", file);
        const reader = new FileReader();
        app.goJoinProgress();
        reader.onload = function (e) {
          settings.invite = JSON.parse(e.target.result);
          connector.executeJoin(JSON.stringify(settings));
        };
        reader.readAsText(file);
      }
    }
  }

  goInit() {
    if (this.app) {
      this.app.goInit()
    }
  }
}
