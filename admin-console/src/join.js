import {inject} from 'aurelia-framework';
import {App} from "app"
import {Connector} from "./service/connector";

@inject(App, Connector)
export class Join {
  files = {};

  constructor(app, connector) {
    this.app = app;
    this.connector = connector;
    this.joinSettings = this.mkJoinSettings(app.organisationFullName)
  }

  mkJoinSettings(orgName) {
    return {
      network: {
        orderingNodes: [
          {
            name: this.mkFullName("osn1", orgName),
            port: 6001
          },
          {
            name: this.mkFullName("osn2", orgName),
            port: 6002
          },
          {
            name: this.mkFullName("osn3", orgName),
            port: 6003
          }
        ],
          peerNodes: [
          {
            name: this.mkFullName("peer0", orgName),
            port: 6010,
            couchDB: {
              port: 6011
            }
          }
        ]
      }
    };
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

  mkFullName(name, fullName) {
    return name + "." + fullName
  }

}
