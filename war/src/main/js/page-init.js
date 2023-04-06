import jsModules from "jenkins-js-modules";

/*
 * Page initialisation tasks.
 */
document.addEventListener("DOMContentLoaded", () => {
  loadScripts();
  loadCSS();
});

function loadScripts() {
  document.querySelectorAll(".jenkins-js-load").forEach((element) => {
    const scriptUrl = element.dataset.src;
    if (scriptUrl) {
      // jsModules.addScript will ensure that the script is
      // loaded once and once only. So, this can be considered
      // analogous to a client-side adjunct.
      jsModules.addScript(scriptUrl);
      element.remove();
    }
  });
}

function loadCSS() {
  document.querySelectorAll(".jenkins-css-load").forEach((element) => {
    const cssUrl = element.dataset.src;
    if (cssUrl) {
      // jsModules.addCSSToPage will ensure that the CSS is
      // loaded once and once only. So, this can be considered
      // analogous to a client-side adjunct.
      jsModules.addCSSToPage(cssUrl);
      element.remove();
    }
  });
}
