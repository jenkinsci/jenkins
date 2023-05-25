export function getI18n(text) {
  const i18n = document.querySelector("#i18n");
  return i18n.getAttribute("data-" + text);
}
