import hotkeys from "hotkeys-js";

window.addEventListener("load", () => {
  const openCommandPaletteButton = document.querySelector(
    "#root-action-SearchAction",
  );
  if (openCommandPaletteButton) {
    hotkeys(translateModifierKeysForUsersPlatform("CMD+K"), () => {
      openCommandPaletteButton.click();

      // Returning false stops the event and prevents default browser events
      return false;
    });
  }

  const pageSearchBar = document.querySelectorAll(
    "#page-body .jenkins-search__input",
  );
  if (pageSearchBar.length === 1) {
    hotkeys("/", () => {
      pageSearchBar[0].focus();

      // Returning false stops the event and prevents default browser events
      return false;
    });
  }
});

/**
 * Given a keyboard shortcut, e.g. CMD+K, replace any included modifier keys for the user's
 * platform e.g. output will be CMD+K for macOS/iOS, CTRL+K for Windows/Linux
 * @param {string} keyboardShortcut The shortcut to translate
 */
function translateModifierKeysForUsersPlatform(keyboardShortcut) {
  const useCmdKey =
    navigator.platform.toUpperCase().indexOf("MAC") >= 0 ||
    navigator.platform.toUpperCase() === "IPHONE" ||
    navigator.platform.toUpperCase() === "IPAD";
  return keyboardShortcut.replace(/CMD|CTRL/gi, useCmdKey ? "CMD" : "CTRL");
}
