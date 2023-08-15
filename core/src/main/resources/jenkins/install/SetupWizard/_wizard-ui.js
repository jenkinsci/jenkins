// all variables declared here have to be in global scope
window.defaultUpdateSiteId = (function () {
  var defaultSiteId = document
    .querySelector("#default-site-id")
    .getAttribute("data-default-update-site-id");
  return defaultSiteId ? defaultSiteId.replace("'", "") : "default";
})();

window.setupWizardExtensions = [];
window.onSetupWizardInitialized = function (extension) {
  setupWizardExtensions.push(extension);
};
