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

  const content = createElementFromHtml(
    `<div class='jenkins-dialog__contents'/>`,
  );

  if (this.dialogType === "modal") {
    if (this.options.content != null) {
      content.appendChild(this.options.content);
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

    // Add content to the dialog
    this.dialog.appendChild(content);
  } else {
    this.form = null;
    if (this.options.form != null && this.dialogType === "form") {
      this.form = this.options.form;
      content.appendChild(this.options.form);
      behaviorShim.applySubtree(content, true);
    }
    if (this.dialogType !== "form") {
      if (this.options.content != null && this.dialogType === "alert") {
        content.appendChild(this.options.content);
      } else if (this.options.message != null && this.dialogType !== "prompt") {
        const messageContents = createElementFromHtml(
          `<div class="jenkins-form-item jenkins-!-text-color-secondary" style="line-height: 1.66" />`,
        );
        content.appendChild(messageContents);
        messageContents.innerText = this.options.message;
      }
    }

    if (this.dialogType === "prompt") {
      let inputDiv = createElementFromHtml(`
          <div class="jenkins-form-item"><input data-id="input" type="text" class='jenkins-input'></div>`);
      content.appendChild(inputDiv);
      this.input = inputDiv.querySelector("[data-id=input]");
      if (this.options.message != null) {
        const message = createElementFromHtml(
          `<div class="jenkins-form-label" />`,
        );
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

    // Add content to the dialog
    this.dialog.appendChild(content);

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
  const shadow = createElementFromHtml(`
    <div class="jenkins-bottom-app-bar__shadow jenkins-bottom-app-bar__shadow--borderless"></div>`);
  const buttons = createElementFromHtml(`
<div id="bottom-sticker">
    <div class="bottom-sticker-inner jenkins-buttons-row">
      <button data-id="cancel" class="jenkins-button">${
        this.options.cancelText
      }</button>
      <button data-id="ok" type="${
        this.options.submitButton ? "submit" : "button"
      }" class="jenkins-button jenkins-button--primary ${
        _typeClassMap[this.options.type]
      }">${this.options.okText}</button>
    </div></div>`);

  // Append both
  this.dialog.querySelector(".jenkins-dialog__contents").appendChild(shadow);
  this.dialog.querySelector(".jenkins-dialog__contents").appendChild(buttons);

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

    wizard: function (initialUrl, options) {
      dialog.modal(document.createElement("template"), options);

      navigateToNextPage(initialUrl, "");
    },
  };

  behaviorShim.specify(
    "[data-type='dialog-opener']",
    "-dialog-",
    1000,
    (element) => {
      element.addEventListener("click", () => {
        if (element.dataset.dialogUrl != null) {
          window.dialog.wizard(element.dataset.dialogUrl, {
            minWidth: "min(550px, 100vw)",
            preventCloseOnOutsideClick: true,
          });
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

function updateWizardTitle(titleText) {
  if (titleText == null) {
    return;
  }

  const title = document.querySelector(
    "body > .jenkins-dialog .jenkins-dialog__title > span",
  );
  if (title != null) {
    title.textContent = titleText;
  }
}

/** Resolve a relative wizard form action against the current step URL. */
function resolveWizardFormAction(form, baseUrl) {
  const formAction = form.getAttribute("action");
  if (
    formAction &&
    !formAction.startsWith("/") &&
    !formAction.startsWith("http")
  ) {
    form.action = new URL(formAction, baseUrl).toString();
  }
}

function submitWizardForm(form) {
  const jsonInputName = "json";
  let jsonInput = form.elements.namedItem(jsonInputName);

  if (jsonInput == null) {
    jsonInput = document.createElement("input");
    jsonInput.type = "hidden";
    jsonInput.name = jsonInputName;
    form.appendChild(jsonInput);
  }

  buildFormTree(form);

  let body = new FormData(form);
  const hasFileInput = Array.from(form.elements).some(
    (element) => element instanceof HTMLInputElement && element.type === "file",
  );

  if (!hasFileInput) {
    body = new URLSearchParams(body);
  }

  fetch(form.action, {
    method: form.method.toUpperCase(),
    headers: crumb.wrap({}),
    body: body,
  }).then((rsp) => {
    if (rsp.redirected) {
      window.location.assign(rsp.url);
      return;
    }

    rsp.text().then((responseText) => {
      const replacementForm = renderWizardForm({
        responseText,
        requestUrl: rsp.url,
        titleText: rsp.headers.get("X-Dialog-Title"),
        replaceExistingForm: form,
      });

      if (replacementForm == null) {
        window.location.assign(rsp.url);
      }
    });
  });
}

function configureWizardForm(form) {
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    submitWizardForm(e.currentTarget);
  });
}

function renderWizardForm({
  responseText,
  requestUrl,
  titleText,
  replaceExistingForm = null,
  hideExistingForms = false,
}) {
  const dialogContents = document.querySelector(
    "body > .jenkins-dialog .jenkins-dialog__contents",
  );
  const newDialog = document.createElement("div");
  newDialog.innerHTML = responseText;

  const form = newDialog.querySelector("form");
  if (form == null) {
    return null;
  }

  if (hideExistingForms) {
    Array.from(dialogContents.children)
      .filter((element) => element.tagName === "FORM")
      .forEach((existingForm) => existingForm.classList.add("jenkins-hidden"));
  }

  resolveWizardFormAction(form, requestUrl);
  updateWizardTitle(titleText);
  configureWizardForm(form);

  // Recreate script tags while the form is still detached, so each script
  // executes exactly once, at the moment the form is inserted into the dialog.
  recreateScripts(form);

  if (replaceExistingForm != null) {
    replaceExistingForm.replaceWith(form);
  } else {
    dialogContents.appendChild(form);
  }

  wireCancelButton(form);

  return form;
}

function wireCancelButton(form) {
  const dialog = form.closest("dialog");
  form.querySelector("[data-id=cancel]")?.addEventListener("click", (e) => {
    e.preventDefault();
    dialog?.dispatchEvent(new Event("cancel"));
  });
}

function navigateToNextPage(url) {
  fetch(url, {
    method: "GET",
    headers: crumb.wrap({}),
  }).then((rsp) => {
    if (rsp.ok) {
      rsp.text().then((responseText) => {
        const form = renderWizardForm({
          responseText,
          requestUrl: rsp.url,
          titleText: rsp.headers.get("X-Dialog-Title"),
          hideExistingForms: true,
        });

        if (form == null) {
          window.location.assign(rsp.url);
        }
      });
    } else {
      console.error(
        "Failed to load dialog content, response from API is:",
        rsp,
      );
    }
  });
}

/*
 * Recreate script tags to ensure they are executed, as innerHTML does not execute scripts.
 *
 */
function recreateScripts(form) {
  const scripts = Array.from(form.getElementsByTagName("script"));
  if (scripts.length === 0) {
    Behaviour.applySubtree(form, true);
    return;
  }
  for (let i = 0; i < scripts.length; i++) {
    const original = scripts[i];
    const script = document.createElement("script");

    for (let j = 0; j < original.attributes.length; j++) {
      script.setAttribute(
        original.attributes[j].name,
        original.attributes[j].value,
      );
    }
    if (original.text) {
      script.text = original.text;
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

    original.parentNode.replaceChild(script, original);
  }
}

export default { init };
