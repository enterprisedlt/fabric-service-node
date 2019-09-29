import {inject} from 'aurelia-framework';
import {Connector} from "./service/connector";

@inject(Connector)
export class Admin {

  tabsModel = [
    {id: 'tab-general', label: 'General', active: true},
    {id: 'tab-users', label: 'Users'},
    {id: 'tab-contracts', label: 'Contracts'}
  ];

  constructor(connector) {
    this.connector = connector;
  }

  doCreateInvite() {
    this.connector.createInvite()
      .then(blob => this.doDownload(blob, 'invite.json'));
  }

  doDownload(content, name) {
    let dataUrl = URL.createObjectURL(content);
    let anchor = document.createElement("a");
    anchor.href = dataUrl;
    anchor.setAttribute('download', name);
    anchor.innerHTML = "Downloading...";
    anchor.style.display = "none";
    document.body.appendChild(anchor);
    setTimeout(function () {
      anchor.click();
      document.body.removeChild(anchor);
      setTimeout(function () {
        self.URL.revokeObjectURL(anchor.href);
      }, 500);
    }, 1);
  }
}
