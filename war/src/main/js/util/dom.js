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
