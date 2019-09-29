import {inject} from 'aurelia-framework';
import {App} from "app"
import {Connector} from "./service/connector";

@inject(App, Connector)
export class Join {
  files = {};

  constructor(app, connector) {
    this.app = app;
    this.connector = connector;
  }

  doJoin() {
    if (this.app && this.connector && this.files) {
      const app = this.app;
      const connector = this.connector;
      let file = this.files[0];
      if (file) {
        console.info("Selected file:", file);
        const reader = new FileReader();
        app.goJoinProgress();
        reader.onload = function (e) {
          let content = e.target.result;
          connector.executeJoin(content);
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
