import { createElementFromHtml } from "@/util/dom";

function init() {
  window.dialog = {
    dialog: null,
    options: {
      title: null,
      message: null,
      cancel: false,
      dialogtype: null,
      okText: "OK",
      cancelText: "Cancel",
      maxWidth: "475px",
      minWidth: "",
      type: "default",
    },

    typeClassMap: {
      default: "",
      destructive: "jenkins-!-destructive-color",
    },

    _init: function (options) {
      options = Object.assign({}, this.options, options);
      this.dialog = createElementFromHtml(
        `<dialog class='jenkins-dialog'>
          <div data-id="title" class='jenkins-dialog__title'></div>
          <div data-id="message" class='jenkins-dialog__contents'></div>
          <div class="jenkins-dialog__input">
            <input data-id="input" type="text" class='jenkins-input'></div>
          </div>
          <div class="jenkins-buttons-row jenkins-buttons-row--equal-width">
            <button data-id="ok" class="jenkins-button jenkins-button--primary ${
              this.typeClassMap[options.type]
            }">${options.okText}</button>
            <button data-id="cancel" class="jenkins-button">${
              options.cancelText
            }</button>
          </div>
        </dialog>`
      );
      document.body.appendChild(this.dialog);
      this.dialog.style.maxWidth = options.maxWidth;
      this.dialog.style.maxWidth = options.minWidth;
      this.ok = this.dialog.querySelector("[data-id=ok]");
      this.cancel = this.dialog.querySelector("[data-id=cancel]");
      this.cancel.addEventListener("click", () => {
        this._close();
      });
      this.title = this.dialog.querySelector("[data-id=title]");
      this.message = this.dialog.querySelector("[data-id=message]");
      this.input = this.dialog.querySelector("[data-id=input]");

      if (!options.cancel) {
        this.cancel.style.display = "none";
      }
      if (options.title != null) {
        this.title.innerText = options.title;
      } else {
        this.title.style.display = "none";
      }
      if (options.message != null) {
        this.message.innerText = options.message;
      } else {
        this.message.hidden = true;
      }

      this.dialogtype = options.dialogtype;
      if (this.dialogtype === "prompt") {
        this.input.focus();
      } else {
        this.input.parentNode.style.display = "none";
        this.ok.focus();
      }

      this.dialog.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
          this.ok.dispatchEvent(new Event("click"));
        }
        if (e.key === "Escape") {
          this._close();
        }
      });
    },

    _close: function () {
      this.dialog.remove();
    },

    _show: function () {
      this.dialog.showModal();
      return new Promise((resolve, cancel) => {
        this.dialog.addEventListener(
          "cancel",
          () => {
            cancel();
            this._close();
          },
          { once: true }
        );
        this.ok.addEventListener(
          "click",
          () => {
            let value = true;
            if (this.dialogtype === "prompt") {
              value = this.input.value;
            }
            resolve(value);
            this._close();
          },
          { once: true }
        );
      });
    },

    alert: function (message, options) {
      const defaults = {
        message: message,
        dialogtype: "alert",
      };
      options = { ...defaults, ...options };
      this._init(options);
      this._show()
        .then()
        .catch(() => {});
    },

    confirm: function (message, options) {
      const defaults = {
        message: message,
        dialogtype: "confirm",
        okText: "Yes",
        cancel: true,
      };
      options = { ...defaults, ...options };
      this._init(options);
      return this._show();
    },

    prompt: function (message, options) {
      const defaults = {
        message: message,
        dialogtype: "prompt",
        minWidth: "400px",
        cancel: true,
      };
      options = { ...defaults, ...options };
      this._init(options);
      return this._show();
    },
  };
}

export default { init };
