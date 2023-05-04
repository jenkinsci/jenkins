import { createElementFromHtml } from "@/util/dom";
import { CLOSE } from "@/util/symbols";

const defaults = {
  maxWidth: undefined,
  hideCloseButton: false,
  okButtonColor: "",
  closeOnClick: true,
};

export function confirmationLink(message, title, href, post) {
  var content = createElementFromHtml("<div>" + message + "</div>");
  var confirmed = function () {
    var form = document.createElement("form");
    form.setAttribute("method", post === "true" ? "POST" : "GET");
    form.setAttribute("action", href);
    if (post) {
      crumb.appendToForm(form);
    }
    document.body.appendChild(form);
    form.submit();
  };
  var options = {
    maxWidth: "550px",
    okButton: "Yes",
    cancelButton: "Cancel",
    hideCloseButton: true,
    title: title,
    callback: confirmed,
    okButtonColor: "jenkins-!-destructive-color",
    closeOnClick: false,
  };
  showModal(content, options);
  return false;
}

export function showModal(contents, options = {}) {
  options = Object.assign({}, defaults, options);
  const modal = createElementFromHtml(
    `<dialog class='jenkins-modal'>
      <div class='jenkins-modal__contents'></div>
    </dialog>`
  );
  modal.style.maxWidth = options.maxWidth;

  if ("title" in options) {
    const titleElement = createElementFromHtml(
      `<h1 class="jenkins-modal__title"></h1>`
    );
    titleElement.append(options.title);
    modal.prepend(titleElement);
  }

  let closeButton;
  if (options.hideCloseButton !== true) {
    closeButton = createElementFromHtml(`
        <button class="jenkins-modal__close-button jenkins-button">
          <span class="jenkins-visually-hidden">Close</span>
          ${CLOSE}
        </button>
      `);
    modal.appendChild(closeButton);
    closeButton.addEventListener("click", () => closeModal());
  }

  let okButton;
  let cancelButton;
  let footer;
  if ("okButton" in options) {
    footer = createElementFromHtml(`
        <div class="jenkins-modal__footer jenkins-buttons-row jenkins-buttons-row--equal-width"></div>
    `);
    okButton = createElementFromHtml(
      `
        <button class="jenkins-button jenkins-button--primary">` +
        options.okButton +
        `</button>
    `
    );
    okButton.classList.add(options.okButtonColor);
    okButton.addEventListener("click", () => ok());
    footer.appendChild(okButton);
    if ("cancelButton" in options) {
      cancelButton = createElementFromHtml(
        `
          <button class="jenkins-button">` +
          options.cancelButton +
          `</button>
      `
      );
      cancelButton.addEventListener("click", () => closeModal());
      footer.appendChild(cancelButton);
    }
    modal.appendChild(footer);
  }

  modal.querySelector("div").append(contents);

  document.querySelector("body").appendChild(modal);

  modal.addEventListener("cancel", (e) => {
    e.preventDefault();

    closeModal();
  });

  if (options.closeOnClick) {
    modal.addEventListener("click", function (e) {
      if (e.target !== e.currentTarget) {
        return;
      }

      closeModal();
    });
  }

  function closeModal() {
    modal.classList.add("jenkins-modal--hidden");

    modal.addEventListener("webkitAnimationEnd", () => {
      modal.remove();
    });
  }

  function ok() {
    if ("callback" in options) {
      options.callback(modal);
    }
    closeModal();
  }

  modal.showModal();

  if (closeButton !== undefined) {
    closeButton.blur();
  }

  if (okButton !== undefined) {
    okButton.blur();
  }

  if (cancelButton !== undefined) {
    cancelButton.blur();
  }
}
