// Initialize all modules by requiring them. Also makes sure they get bundled (see gulpfile.js).
var $ = require('jquery-detached').getJQuery();

// This is the main module
var pluginSetupWizard = require('./pluginSetupWizardGui');

// This entry point for the bundle only bootstraps the main module in a browser
$(function() {
	$('.plugin-setup-wizard-container').each(function() {
		var $container = $(this);
		if($container.children().length === 0) { // this may get double-initialized
			pluginSetupWizard.init($container);
		}
	});
});
