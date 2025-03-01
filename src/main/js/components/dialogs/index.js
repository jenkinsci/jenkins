import { createElementFromHtml } from "@/util/dom";
import { CLOSE } from "@/util/symbols";
import behaviorShim from "@/util/behavior-shim";
import jenkins from "@/util/jenkins";

let _defaults = {
  title: null,
  message: null,
  cancel: true,
  maxWidth: "475px",
  minWidth: "450px",
  type: "default",
  hideCloseButton: false,
  allowEmpty: false,
  submitButton: false,
};

let _typeClassMap = {
  default: "",
  destructive: "jenkins-!-destructive-color",
};

jenkins.loadTranslations("jenkins.dialogs", function (localizations) {
  window.dialog.translations = localizations;
  _defaults.cancelText = localizations.cancel;
  _defaults.okText = localizations.ok;
});

function Dialog(dialogType, options) {
  this.dialogType = dialogType;
  this.options = Object.assign({}, _defaults, options);
  this.init();
}

Dialog.prototype.init = function () {
  this.dialog = document.createElement("dialog");
  this.dialog.classList.add("jenkins-dialog");
  this.dialog.style.maxWidth = this.options.maxWidth;
  this.dialog.style.minWidth = this.options.minWidth;
  document.body.appendChild(this.dialog);

  if (this.options.title != null) {
    const title = createElementFromHtml(`<div class='jenkins-dialog__title'/>`);
    this.dialog.appendChild(title);
    title.innerText = this.options.title;
  }

  if (this.dialogType === "modal") {
    if (this.options.content != null) {
      const content = createElementFromHtml(
        `<div class='jenkins-dialog__contents jenkins-dialog__contents--modal'/>`,
      );
      content.appendChild(this.options.content);
      this.dialog.appendChild(content);
    }
    if (this.options.hideCloseButton !== true) {
      const closeButton = createElementFromHtml(`
          <button class="jenkins-dialog__close-button jenkins-button">
            <span class="jenkins-visually-hidden">Close</span>
            ${CLOSE}
          </button>
        `);
      this.dialog.appendChild(closeButton);
      closeButton.addEventListener("click", () =>
        this.dialog.dispatchEvent(new Event("cancel")),
      );
    }
    this.dialog.addEventListener("click", function (e) {
      if (e.target !== e.currentTarget) {
        return;
      }
      this.dispatchEvent(new Event("cancel"));
    });
    this.ok = null;
  } else {
    this.form = null;
    if (this.options.form != null && this.dialogType === "form") {
      const contents = createElementFromHtml(
        `<div class='jenkins-dialog__contents'/>`,
      );
      this.form = this.options.form;
      contents.appendChild(this.options.form);
      this.dialog.appendChild(contents);
      behaviorShim.applySubtree(contents, true);
    }
    if (this.options.message != null && this.dialogType !== "form") {
      const message = createElementFromHtml(
        `<div class='jenkins-dialog__contents'/>`,
      );
      this.dialog.appendChild(message);
      message.innerText = this.options.message;
    }

    if (this.dialogType === "prompt") {
      let inputDiv = createElementFromHtml(`<div class="jenkins-dialog__input">
          <input data-id="input" type="text" class='jenkins-input'></div>`);
      this.dialog.appendChild(inputDiv);
      this.input = inputDiv.querySelector("[data-id=input]");
      if (!this.options.allowEmpty) {
        this.input.addEventListener("input", () => this.checkInput());
      }
    }

    this.appendButtons();

    this.dialog.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        if (this.ok.disabled == false) {
          this.ok.dispatchEvent(new Event("click"));
        }
      }
      if (e.key === "Escape") {
        e.preventDefault();
        this.dialog.dispatchEvent(new Event("cancel"));
      }
    });
  }
};

Dialog.prototype.checkInput = function () {
  if (this.input.value.trim()) {
    this.ok.disabled = false;
  } else {
    this.ok.disabled = true;
  }
};

Dialog.prototype.appendButtons = function () {
  const buttons = createElementFromHtml(`<div
      class="jenkins-buttons-row jenkins-buttons-row--equal-width jenkins-dialog__buttons">
      <button data-id="ok" type="${
        this.options.submitButton ? "submit" : "button"
      }" class="jenkins-button jenkins-button--primary ${
        _typeClassMap[this.options.type]
      }">${this.options.okText}</button>
      <button data-id="cancel" class="jenkins-button">${
        this.options.cancelText
      }</button>
    </div>`);

  if (this.dialogType === "form") {
    this.form.appendChild(buttons);
  } else {
    this.dialog.appendChild(buttons);
  }

  this.ok = buttons.querySelector("[data-id=ok]");
  this.cancel = buttons.querySelector("[data-id=cancel]");
  if (!this.options.cancel) {
    this.cancel.style.display = "none";
  } else {
    this.cancel.addEventListener("click", (e) => {
      e.preventDefault();
      this.dialog.dispatchEvent(new Event("cancel"));
    });
  }
  if (this.dialogType === "prompt" && !this.options.allowEmpty) {
    this.ok.disabled = true;
  }
};

Dialog.prototype.show = function () {
  return new Promise((resolve, cancel) => {
    this.dialog.showModal();
    this.dialog.addEventListener(
      "cancel",
      (e) => {
        e.preventDefault();

        this.dialog.setAttribute("closing", "");

        this.dialog.addEventListener(
          "animationend",
          () => {
            this.dialog.removeAttribute("closing");
            this.dialog.remove();
          },
          { once: true },
        );

        cancel();
      },
      { once: true },
    );
    this.dialog.focus();
    if (this.input != null) {
      this.input.focus();
    }
    if (
      this.ok != null &&
      (this.dialogType != "form" || !this.options.submitButton)
    ) {
      this.ok.addEventListener(
        "click",
        (e) => {
          e.preventDefault();

          let value = true;
          if (this.dialogType === "prompt") {
            value = this.input.value;
          }
          if (this.dialogType === "form") {
            value = new FormData(this.form);
          }
          this.dialog.dispatchEvent(new Event("cancel"));
          resolve(value);
        },
        { once: true },
      );
    }
  });
};

function init() {
  window.dialog = {
    modal: function (content, options) {
      const defaults = {
        content: content,
      };
      options = Object.assign({}, defaults, options);
      let dialog = new Dialog("modal", options);
      dialog
        .show()
        .then()
        .catch(() => {});
    },

    alert: function (title, options) {
      const defaults = {
        title: title,
        cancel: false,
      };
      options = Object.assign({}, defaults, options);
      let dialog = new Dialog("alert", options);
      dialog
        .show()
        .then()
        .catch(() => {});
    },

    confirm: function (title, options) {
      const defaults = {
        title: title,
        okText: window.dialog.translations.yes,
      };
      options = Object.assign({}, defaults, options);
      let dialog = new Dialog("confirm", options);
      return dialog.show();
    },

    prompt: function (title, options) {
      const defaults = {
        title: title,
      };
      options = Object.assign({}, defaults, options);
      let dialog = new Dialog("prompt", options);
      return dialog.show();
    },

    form: function (form, options) {
      const defaults = {
        form: form,
        minWidth: "600px",
        maxWidth: "900px",
        submitButton: true,
        okText: window.dialog.translations.submit,
      };
      options = Object.assign({}, defaults, options);
      let dialog = new Dialog("form", options);
      return dialog.show();
    },
  };
}

export default { init };
