/* globals wizard: true */
var jenkins = wizard.jenkins; // wizard-provided jenkins api
var pluginManager = wizard.pluginManager;

var upgradePanel = require('./templates/upgradePanel.hbs');
var upgradeSuccessPanel = require('./templates/upgradeSuccessPanel.hbs');
var upgradeSkippedPanel = require('./templates/upgradeSkippedPanel.hbs');

wizard.addActions({
	'.skip-recommended-plugins': function() {
		wizard.setPanel(upgradeSkippedPanel);
	},
	'.close': function() {
		jenkins.goTo('/setupWizard/installState/UPGRADE/hideUpgradeWizard');
	}
});

wizard.addStateHandlers({
	UPGRADE: function() {
		wizard.loadPluginCategories(function(){
			// default the set of recommended plugins
			wizard.setSelectedPluginNames(pluginManager.recommendedPluginNames());
			// show the upgrade panel
			wizard.setPanel(upgradePanel, wizard.pluginSelectionPanelData());
		});
	},
	CREATE_ADMIN_USER: function() {
		wizard.setPanel(upgradeSuccessPanel);
	},
	INITIAL_SETUP_COMPLETED: function() {
		wizard.setPanel(upgradeSuccessPanel);
	}
});

// overrides some install text
wizard.translationOverride(function(translations) {
	translations.installWizard_installing_title = translations.installWizard_upgrading_title;
});
