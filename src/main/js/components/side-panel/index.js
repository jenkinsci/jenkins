import behaviorShim from "@/util/behavior-shim";
import { createElementFromHtml } from "@/util/dom";
import { SIDEPANEL } from "@/util/symbols";

const EXPANDED_CLASS = "app-page-body__sidebar--expanded";

function setExpanded(expanded) {
  const sidebar = document.querySelector("#side-panel");
  sidebar.classList.toggle(EXPANDED_CLASS, expanded);
  document.querySelectorAll(".app-side-panel-toggle").forEach((toggle) => {
    toggle.setAttribute("aria-expanded", expanded);
  });
}

function createToggle() {
  return createElementFromHtml(`
    <button type="button"
            class="jenkins-button jenkins-button--tertiary app-side-panel-toggle"
            aria-controls="side-panel"
            aria-expanded="false"
            aria-label="Toggle sidebar"
            tooltip="Toggle sidebar">
      ${SIDEPANEL}
    </button>
  `);
}

function injectToggle(appBarContent, position) {
  if (
    !appBarContent ||
    appBarContent.querySelector(":scope > .app-side-panel-toggle")
  ) {
    return;
  }

  const toggle = createToggle();
  appBarContent.insertAdjacentElement(position, toggle);
  behaviorShim.applySubtree(toggle, true);
}

function init() {
  behaviorShim.specify(
    ".app-side-panel-toggle",
    "side-panel-toggle",
    0,
    (button) => {
      button.addEventListener("click", () => {
        const sidebar = document.querySelector("#side-panel");
        setExpanded(!sidebar.classList.contains(EXPANDED_CLASS));
      });
    },
  );

  behaviorShim.specify("#side-panel", "side-panel-toggle-inject", 0, () => {
    injectToggle(
      document.querySelector("#main-panel .jenkins-app-bar__content"),
      "afterbegin",
    );
    injectToggle(
      document.querySelector("#side-panel .jenkins-app-bar__content"),
      "beforeend",
    );
  });

  document.addEventListener(
    "click",
    (event) => {
      const sidebar = document.querySelector("#side-panel");
      if (
        !sidebar?.classList.contains(EXPANDED_CLASS) ||
        sidebar.contains(event.target) ||
        event.target.closest(".app-side-panel-toggle")
      ) {
        return;
      }

      event.preventDefault();
      event.stopPropagation();
      setExpanded(false);
    },
    true,
  );
}

export default { init };
