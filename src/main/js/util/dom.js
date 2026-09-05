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
