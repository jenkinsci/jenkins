import $ from "jquery";
// This is the main module
import pluginSetupWizard from "./pluginSetupWizardGui";

// This entry point for the bundle only bootstraps the main module in a browser
$(function () {
  $(".plugin-setup-wizard-container").each(function () {
    var $container = $(this);
    if ($container.children().length === 0) {
      // this may get double-initialized
      pluginSetupWizard.init($container);
    }
  });
});
