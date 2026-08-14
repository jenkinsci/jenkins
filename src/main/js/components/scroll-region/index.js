// The page body scrolls in a nested container rather than the document, so the
// container has to be focusable for keyboard users to be able to scroll it.
const CANDIDATES = [".app-page-body__contents", "#page-body"];
const SCROLL_KEYS = [
  "End",
  "Home",
  "PageUp",
  "PageDown",
  "ArrowUp",
  "ArrowDown",
  " ",
];

function scrollsVertically(element) {
  const overflowY = getComputedStyle(element).overflowY;
  return overflowY === "auto" || overflowY === "scroll";
}

let region = null;

function update() {
  const candidates = CANDIDATES.map((selector) =>
    document.querySelector(selector),
  ).filter(Boolean);
  region = candidates.find(scrollsVertically) || null;

  candidates.forEach((element) => {
    if (element === region) {
      element.setAttribute("tabindex", "0");
      element.setAttribute("role", "region");
      if (element.dataset.scrollRegionLabel) {
        element.setAttribute("aria-label", element.dataset.scrollRegionLabel);
      }
    } else {
      element.removeAttribute("tabindex");
      element.removeAttribute("role");
      element.removeAttribute("aria-label");
    }
  });
}

function init() {
  update();

  let lastWidth = window.innerWidth;
  window.addEventListener("resize", () => {
    if (window.innerWidth !== lastWidth) {
      lastWidth = window.innerWidth;
      update();
    }
  });

  // Being focusable is not enough, the browser only scrolls the focused container.
  // Claiming focus during keydown lets the key's default action apply to the region,
  // so the first press works without stealing focus on load.
  document.addEventListener("keydown", (event) => {
    if (!region || event.target !== document.body) {
      return;
    }
    if (SCROLL_KEYS.includes(event.key)) {
      region.focus({ preventScroll: true });
    }
  });
}

export default { init };
