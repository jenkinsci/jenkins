import { createElementFromHtml } from "@/util/dom";
import { CLOSE } from "@/util/symbols";

const defaults = {
  maxWidth: undefined,
  hideCloseButton: false,
};

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

  modal.querySelector("div").appendChild(contents);

  document.querySelector("body").appendChild(modal);

  modal.addEventListener("cancel", (e) => {
    e.preventDefault();

    closeModal();
  });

  modal.addEventListener("click", function (e) {
    if (e.target !== e.currentTarget) {
      return;
    }

    closeModal();
  });

  function closeModal() {
    modal.classList.add("jenkins-modal--hidden");

    modal.addEventListener("webkitAnimationEnd", () => {
      modal.remove();
    });
  }

  modal.showModal();

  if (closeButton !== undefined) {
    closeButton.blur();
  }
}
