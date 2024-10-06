import * as Symbols from "@/util/symbols";
import { createElementFromHtml } from "@/util/dom";

function init() {
  window.notificationBar = {
    OPACITY: 1,
    DELAY: 3000, // milliseconds to auto-close the notification
    div: null, // the main 'notification-bar' DIV
    token: null, // timer for cancelling auto-close
    defaultIcon: Symbols.INFO,
    defaultAlertClass: "jenkins-notification",

    SUCCESS: {
      alertClass: "jenkins-notification jenkins-notification--success",
      icon: Symbols.SUCCESS,
    },
    WARNING: {
      alertClass: "jenkins-notification jenkins-notification--warning",
      icon: Symbols.WARNING,
    },
    ERROR: {
      alertClass: "jenkins-notification jenkins-notification--error",
      icon: Symbols.ERROR,
      sticky: true,
    },

    init: function () {
      if (this.div == null) {
        this.div = document.createElement("div");
        this.div.id = "notification-bar";
        document.body.insertBefore(this.div, document.body.firstElementChild);
        const self = this;
        this.div.onclick = function () {
          self.hide();
        };
      } else {
        this.div.innerHTML = "";
      }
    },
    // cancel pending auto-hide timeout
    clearTimeout: function () {
      if (this.token) {
        window.clearTimeout(this.token);
      }
      this.token = null;
    },
    // hide the current notification bar, if it's displayed
    hide: function () {
      this.clearTimeout();
      this.div.classList.remove("jenkins-notification--visible");
      this.div.classList.add("jenkins-notification--hidden");
    },
    // show a notification bar
    show: function (text, options) {
      options = options || {};
      this.init();

      this.div.appendChild(
        createElementFromHtml(options.icon || this.defaultIcon),
      );
      const message = this.div.appendChild(document.createElement("span"));
      message.appendChild(document.createTextNode(text));

      this.div.className = options.alertClass || this.defaultAlertClass;
      this.div.classList.add("jenkins-notification--visible");

      this.clearTimeout();
      const self = this;
      if (!options.sticky) {
        this.token = window.setTimeout(function () {
          self.hide();
        }, this.DELAY);
      }
    },
  };
}

export default { init };
