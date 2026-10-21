import * as Symbols from "@/util/symbols";
import { createElementFromHtml } from "@/util/dom";

function init() {
  window.notificationBar = {
    OPACITY: 1,
    DELAY: 3000,
    div: null,
    token: null,
    defaultIcon: Symbols.INFO,
    defaultAlertClass: "jenkins-notification",

    SUCCESS: {
      alertClass: "jenkins-notification jenkins-!-success-color",
      icon: Symbols.SUCCESS,
    },
    WARNING: {
      alertClass: "jenkins-notification jenkins-!-warning-color",
      icon: Symbols.WARNING,
    },
    ERROR: {
      alertClass: "jenkins-notification jenkins-!-error-color",
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

    clearTimeout: function () {
      if (this.token) {
        window.clearTimeout(this.token);
      }
      this.token = null;
    },

    hide: function () {
      this.clearTimeout();
      this.div.classList.remove("jenkins-notification--visible");
      this.div.classList.add("jenkins-notification--hidden");
    },

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

  tryShowPageLoadNotification();

  const pending = sessionStorage.getItem("jenkins-dropdown-notification");

  if (pending) {
    sessionStorage.removeItem("jenkins-dropdown-notification");

    const notification = JSON.parse(pending);

    window.notificationBar.show(
      notification.message,
      window.notificationBar[notification.type],
    );
  }
}

function tryShowPageLoadNotification() {
  const { notificationMessage, notificationType } = document.body.dataset;

  if (!notificationMessage) {
    return;
  }

  window.notificationBar.show(
    notificationMessage,
    window.notificationBar[notificationType],
  );
}

export default { init };