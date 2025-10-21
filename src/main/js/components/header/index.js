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

  // Fade in the page header on scroll, increasing opacity and intensity of the backdrop blur
  window.addEventListener("scroll", () => {
    const navigation = document.querySelector("#page-header");
    const scrollY = Math.max(0, window.scrollY);
    navigation.style.setProperty(
      "--background-opacity",
      Math.min(70, scrollY) + "%",
    );
    navigation.style.setProperty(
      "--background-blur",
      Math.min(40, scrollY) + "px",
    );
    if (
      !document.querySelector(".jenkins-search--app-bar") &&
      !document.querySelector(".app-page-body__sidebar--sticky")
    ) {
      const prefersContrast = window.matchMedia(
        "(prefers-contrast: more)",
      ).matches;
      navigation.style.setProperty(
        "--border-opacity",
        Math.min(
          prefersContrast ? 100 : 15,
          prefersContrast ? scrollY * 3 : scrollY,
        ) + "%",
      );
    }
  });

  window.addEventListener("load", () => {
    // We can't use :has due to HtmlUnit CSS Parser not supporting it, so
    // these are workarounds for that same behaviour
    if (document.querySelector(".jenkins-app-bar--sticky")) {
      document
        .querySelector(".jenkins-header")
        .classList.add("jenkins-header--has-sticky-app-bar");
    }

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
