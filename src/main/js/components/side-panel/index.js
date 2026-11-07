import behaviorShim from "@/util/behavior-shim";

function init() {
  behaviorShim.specify(
    ".app-page-body__sidebar-toggle",
    "side-panel-toggle",
    0,
    (button) => {
      const sidebar = button.closest(".app-page-body__sidebar");

      const setExpanded = (expanded) => {
        sidebar.classList.toggle("app-page-body__sidebar--expanded", expanded);
        button.setAttribute("aria-expanded", expanded);
      };

      button.addEventListener("click", () => {
        setExpanded(button.getAttribute("aria-expanded") !== "true");
      });

      sidebar.addEventListener("click", (event) => {
        if (event.target === sidebar) {
          setExpanded(false);
        }
      });
    },
  );
}

export default { init };
