var defaultSiteId = document.querySelector("#default-site-id");
var defaultUpdateSiteIdAttribute = defaultSiteId.getAttribute(
  "data-default-update-site-id"
);
// eslint-disable-next-line no-unused-vars
var defaultUpdateSiteId = defaultUpdateSiteIdAttribute
  ? defaultUpdateSiteIdAttribute.replace("'", "")
  : "default";

var setupWizardExtensions = [];
// eslint-disable-next-line no-unused-vars
var onSetupWizardInitialized = function (extension) {
  setupWizardExtensions.push(extension);
};
