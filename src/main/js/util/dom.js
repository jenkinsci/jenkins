export function createElementFromHtml(html) {
  const template = document.createElement("template");
  template.innerHTML = html.trim();
  return template.content.firstElementChild;
}

export function toId(string) {
  const trimmed = string.trim();
  const ascii = trimmed.replace(/[\W_]+/g, "-").toLowerCase();

  // If ASCII stripping produces something meaningful, keep old behavior
  if (ascii && ascii !== "-") {
    return ascii;
  }

  // Fallback for non-ASCII locales (CJK, etc.)
  return Array.from(trimmed)
    .map(c => c.codePointAt(0).toString(16))
    .join("-");
}
