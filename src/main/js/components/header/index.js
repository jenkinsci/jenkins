import updateActionsForTouch from "@/components/header/actions-touch";
import computeBreadcrumbs from "@/components/header/breadcrumbs-overflow";

function init() {
  // Recompute what actions and breadcrumbs should be visible when the viewport size is changed
  computeOverflow();
  updateActionsForTouch();
  let lastWidth = window.innerWidth;
  window.addEventListener("resize", () => {
    if (window.innerWidth !== lastWidth) {
      lastWidth = window.innerWidth;
      computeOverflow();
    }
  });

  window.addEventListener("computeHeaderOverflow", () => {
    computeOverflow();
  });

  window.addEventListener("load", () => {
    // We can't use :has due to HtmlUnit CSS Parser not supporting it, so
    // these are workarounds for that same behaviour
    if (!document.querySelector(".jenkins-breadcrumbs__list-item")) {
      document
        .querySelector(".jenkins-header")
        .classList.add("jenkins-header--no-breadcrumbs");
    }
  });
}

function computeOverflow() {
  computeBreadcrumbs();
}

init();
