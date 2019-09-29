import {inject} from 'aurelia-framework';
import {HttpClient} from 'aurelia-fetch-client';

@inject(HttpClient)
export class Connector {

  constructor(http) {
    this.http = http;
  }

  executeBootstrap(bootstrapSettings) {
    return this.http.fetch('/admin/bootstrap',
      {
        method: "POST",
        body: JSON.stringify(bootstrapSettings)
      }
    );
  }

  getServiceState() {
    return this.http.fetch('/service/state');
  }

  createInvite() {
    return this.http.fetch('/admin/create-invite')
      .then(response => response.blob())
  }

  executeJoin(invite) {
    return this.http.fetch('/admin/request-join',
      {
        method: "POST",
        body: invite
      }
    );
  }
}
