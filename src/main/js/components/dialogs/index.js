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
  preventCloseOnOutsideClick: false,
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

  // Append title element
  const title = createElementFromHtml(
    `<div class='jenkins-dialog__title'><span></span></div>`,
  );
  this.dialog.appendChild(title);
  title.querySelector("span").innerText = this.options.title;

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
          <button class="jenkins-dialog__title__button jenkins-dialog__title__close-button jenkins-button">
            <span class="jenkins-visually-hidden">Close</span>
            ${CLOSE}
          </button>
        `);
      title.append(closeButton);
      closeButton.addEventListener("click", () =>
        this.dialog.dispatchEvent(new Event("cancel")),
      );
    }
    if (!this.options.preventCloseOnOutsideClick) {
      this.dialog.addEventListener("click", function (e) {
        if (e.target !== e.currentTarget) {
          return;
        }
        this.dispatchEvent(new Event("cancel"));
      });
    }
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
    if (this.dialogType !== "form") {
      const message = createElementFromHtml(
        `<div class='jenkins-dialog__contents'/>`,
      );
      if (this.options.content != null && this.dialogType === "alert") {
        message.appendChild(this.options.content);
        this.dialog.appendChild(message);
      } else if (this.options.message != null && this.dialogType !== "prompt") {
        const message = createElementFromHtml(
          `<div class='jenkins-dialog__contents'/>`,
        );
        this.dialog.appendChild(message);
        message.innerText = this.options.message;
      }
    }

    if (this.dialogType === "prompt") {
      let inputDiv = createElementFromHtml(`<div class="jenkins-dialog__input">
          <input data-id="input" type="text" class='jenkins-input'></div>`);
      this.dialog.appendChild(inputDiv);
      this.input = inputDiv.querySelector("[data-id=input]");
      if (this.options.message != null) {
        const message = document.createElement("div");
        inputDiv.insertBefore(message, this.input);
        message.innerText = this.options.message;
      }
      if (this.options.promptValue) {
        this.input.value = this.options.promptValue;
      }
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

  if (
    this.dialogType === "prompt" &&
    !this.options.allowEmpty &&
    (this.options.promptValue == null || this.options.promptValue.trim() === "")
  ) {
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

        // Clear any hash
        history.pushState(
          "",
          document.title,
          window.location.pathname + window.location.search,
        );

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
          resolve(value);
          this.dialog.dispatchEvent(new Event("cancel"));
        },
        { once: true },
      );
    }
  });
};

function renderOnDemandDialog(dialogId) {
  const templateId = "dialog-" + dialogId + "-template";

  function render() {
    const template = document.querySelector("#" + templateId);
    const title = template.dataset.title;
    const hash = template.dataset.dialogHash;
    const content = template.content.firstElementChild.cloneNode(true);

    if (hash) {
      window.location.hash = hash;
    }

    behaviorShim.applySubtree(content, false);
    dialog.modal(content, {
      maxWidth: "550px",
      title: title,
    });
  }

  if (document.querySelector("#" + templateId)) {
    render();
    return;
  }

  renderOnDemand(document.querySelector("." + templateId), render);
}

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

  behaviorShim.specify(
    "[data-type='dialog-opener']",
    "-dialog-",
    1000,
    (element) => {
      element.addEventListener("click", () => {
        if (element.dataset.dialogUrl != null) {
          loadDialogWizard(element.dataset.dialogUrl);
        } else {
          renderOnDemandDialog(element.dataset.dialogId);
        }
      });
    },
  );

  // Open the relevant dialog if the hash is set
  if (window.location.hash) {
    const element = document.querySelector(
      ".dialog-" + window.location.hash.substring(1) + "-hash",
    );
    if (element) {
      renderOnDemandDialog(
        element.className.match(/dialog-(id\d+)-template/)[1],
      );
    }
  }
}

window.dialog2 = {
  wizard: (initialUrl, options) => {
    dialog.modal(document.createElement("template"), options);

    navigateToNextPage(initialUrl, "");
  },
};

function mergeUrlParams(url, params) {
  const base = new URL(url, window.location.href);
  if (params) {
    const newParams = new URLSearchParams(params);
    for (const [key, value] of newParams.entries()) {
      base.searchParams.set(key, value);
    }
  }
  return base.toString();
}

function navigateToNextPage(url, params) {
  const dialog = document.querySelector(
    ".jenkins-dialog .jenkins-dialog__contents",
  );

  const finalUrl = mergeUrlParams(url, params);

  fetch(finalUrl, {
    method: "GET",
    headers: crumb.wrap({}),
  }).then((rsp) => {
    if (rsp.ok) {
      rsp.text().then((responseText) => {
        Array.from(dialog.children)
          .filter((el) => el.tagName === "FORM")
          .forEach((form) => form.classList.add("jenkins-hidden"));

        const newDialog = document.createElement("div");
        newDialog.innerHTML = responseText;

        const form = newDialog.querySelector("form");

        // Resolve relative form action against the fetch URL, not the current page URL,
        // since the dialog HTML is inserted into the current page's DOM
        const formAction = form.getAttribute("action");
        if (
          formAction &&
          !formAction.startsWith("/") &&
          !formAction.startsWith("http")
        ) {
          form.action = new URL(formAction, finalUrl).toString();
        }

        // Remove the latter selector after baseline is higher than https://github.com/jenkinsci/jenkins/pull/26033
        const title =
          document.querySelector(
            ".jenkins-dialog .jenkins-dialog__title > span",
          ) || document.querySelector(".jenkins-dialog .jenkins-dialog__title");
        title.textContent = rsp.headers.get("X-Wizard-Title");

        if (form.method.toLowerCase() === "get") {
          form.addEventListener("submit", (e) => {
            e.preventDefault();

            const form = e.target;
            const fd = new FormData(form);
            const params = new URLSearchParams();

            fd.forEach(function (value, key) {
              // FormData can include File objects. Query strings cannot.
              if (value instanceof File) {
                // choose one:
                // params.append(key, value.name); // store filename only
                return; // or skip files entirely
              }
              params.append(key, String(value));
            });

            const queryString = params.toString(); // "username=alice&password=secret"

            showBackButtonInDialog();

            navigateToNextPage(form.action, queryString);
          });
        } else {
          // window.credentials.form = form;
          // form.addEventListener("submit", (e) => {
          //   e.preventDefault();
          //   window.credentials.dialogSubmit();
          // });
        }

        dialog.appendChild(form);
        recreateScripts(form);
      });
    }
  });
}

function showBackButtonInDialog() {
  const dialog = document.querySelector(".jenkins-dialog");
  // Remove the latter selector after baseline is higher than https://github.com/jenkinsci/jenkins/pull/26033
  const title =
    dialog.querySelector(".jenkins-dialog__title > span") ||
    dialog.querySelector(".jenkins-dialog__title");
  const backButton = document.createElement("button");
  backButton.classList.add("jenkins-button");
  backButton.classList.add("jenkins-dialog__back-button");
  backButton.ariaLabel = "Back";
  backButton.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="48" d="M328 112L184 256l144 144"/></svg>`;
  title.style.transition = "var(--standard-transition)";
  title.style.marginLeft = "2.75rem";
  dialog.appendChild(backButton);

  backButton.addEventListener("click", () => {
    dialog
      .querySelector(".jenkins-dialog__contents form:first-of-type")
      .classList.remove("jenkins-hidden");
    dialog
      .querySelector(".jenkins-dialog__contents form:last-of-type")
      .remove();
    title.style.marginLeft = "0";
    title.textContent = "Add Credentials";
    backButton.remove();
  });
}

/*
 * Recreate script tags to ensure they are executed, as innerHTML does not execute scripts.
 */
function recreateScripts(form) {
  const scripts = form.getElementsByTagName("script");
  if (scripts.length === 0) {
    Behaviour.applySubtree(form, true);
    return;
  }
  for (let i = 0; i < scripts.length; i++) {
    const script = document.createElement("script");
    if (scripts[i].text) {
      script.text = scripts[i].text;
    } else {
      for (let j = 0; j < scripts[i].attributes.length; j++) {
        if (scripts[i].attributes[j].name in HTMLScriptElement.prototype) {
          script[scripts[i].attributes[j].name] =
            scripts[i].attributes[j].value;
        }
      }
    }

    // only attach the load listener to the last script to avoid multiple calls to Behaviour.applySubtree
    if (i === scripts.length - 1) {
      script.addEventListener("load", () => {
        setTimeout(() => {
          Behaviour.applySubtree(form, true);
          if (form.method.toLowerCase() !== "get") {
            form.onsubmit = null; // clear any existing handler
          }
        }, 50);
      });
    }

    scripts[i].parentNode.replaceChild(script, scripts[i]);
  }
}

function loadDialogWizard(url) {
  window.dialog2.wizard(url, {
    title: "",
    minWidth: "min(550px, 100vw)",
    preventCloseOnOutsideClick: true,
  });
}

export default { init };
