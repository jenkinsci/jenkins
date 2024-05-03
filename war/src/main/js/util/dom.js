export function createElementFromHtml(html) {
  const template = document.createElement("template");
  template.innerHTML = html.trim();
  return template.content.firstElementChild;
}

export function toId(string) {
  return string
    .trim()
    .replace(/[\W_]+/g, "-")
    .toLowerCase();
}

export function getStyle(e, a) {
  if (document.defaultView && document.defaultView.getComputedStyle) {
    return document.defaultView
      .getComputedStyle(e, null)
      .getPropertyValue(a.replace(/([A-Z])/g, "-$1"));
  }
  if (e.currentStyle) {
    return e.currentStyle[a];
  }
  return null;
}
