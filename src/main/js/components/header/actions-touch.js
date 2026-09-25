export default function updateActionsForTouch() {
  // We want to disable the User action href on touch devices so that they can still activate the overflow menu
  const link = document.querySelector("#root-action-UserAction");

  if (link) {
    const originalHref = link.getAttribute("href");
    const isTouchDevice = window.matchMedia("(hover: none)").matches;

    // HTMLUnit doesn't register itself as supporting hover, thus the href is removed when it shouldn't be
    if (isTouchDevice && !window.isRunAsTest) {
      link.removeAttribute("href");
    } else {
      link.setAttribute("href", originalHref);
    }
  }
}
