var rootUrlField = document.getElementById("root-url");

rootUrlField.focus();
rootUrlField.onkeydown = function (event) {
  if (event.keyCode == 13) {
    event.preventDefault();
  }
};

(function setInitialRootUrlFieldValue() {
  var iframeUrl = window.location.href;
  // will let the trailing slash in the rootUrl
  var iframeRelativeUrl = "setupWizard/setupWizardConfigureInstance";
  var rootUrl = iframeUrl.substr(
    0,
    iframeUrl.length - iframeRelativeUrl.length,
  );
  // to keep only the root url
  rootUrlField.value = rootUrl;
  // to adjust the width of the input,
  rootUrlField.size = Math.min(50, rootUrl.length);
})();
