var targetDiv = document.querySelector('#target-div');
var defaultUpdateSiteIdAttribute = targetDiv.getAttribute('data-default-update-site-id');
var defaultUpdateSiteId = defaultUpdateSiteIdAttribute ? defaultUpdateSiteIdAttribute.replace("'", "") : "default";

var setupWizardExtensions = [];
var onSetupWizardInitialized = function(extension) {
  setupWizardExtensions.push(extension);
};
