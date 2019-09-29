import {inject} from 'aurelia-framework';
import {App} from "app"

@inject(App)
export class Init {

  constructor(app) {
    this.app = app;
  }

  goBootstrap() {
    if(this.app){
      this.app.goBootstrap();
    }
  }

  goJoin() {
    if(this.app){
      this.app.goJoin();
    }
  }

}
