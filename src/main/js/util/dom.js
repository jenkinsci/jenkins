export function createElementFromHtml(html) {
  const template = document.createElement("template");
  template.innerHTML = html.trim();
  return template.content.firstElementChild;
}

export function toId(string) {
  const trimmed = string.trim();
  return Array.from(trimmed)
    .map((c) => c.codePointAt(0).toString(16))
    .join("-");
}

const SCROLLABLE_OVERFLOW = ["auto", "scroll", "overlay"];

// The page body scrolls in a nested container rather than the document
export function getScrollContainer(element) {
  for (let node = element.parentElement; node; node = node.parentElement) {
    if (SCROLLABLE_OVERFLOW.includes(getComputedStyle(node).overflowY)) {
      return node;
    }
  }
  return document.scrollingElement || document.documentElement;
}
