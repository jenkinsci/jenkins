import behaviorShim from "@/util/behavior-shim";

const EXPANDED_CLASS = "app-page-body__sidebar--expanded";

function setExpanded(expanded) {
  const sidebar = document.querySelector("#side-panel");
  sidebar.classList.toggle(EXPANDED_CLASS, expanded);
  document.querySelectorAll(".app-side-panel-toggle").forEach((toggle) => {
    toggle.setAttribute("aria-expanded", expanded);
  });
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

  behaviorShim.specify("#side-panel", "side-panel-backdrop", 0, (sidebar) => {
    sidebar.addEventListener("click", (event) => {
      if (event.target === sidebar) {
        setExpanded(false);
      }
    });
  });
}

export default { init };
