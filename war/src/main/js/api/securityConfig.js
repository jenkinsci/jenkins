/**
 * Provides a wrapper to interact with the security configuration
 */

var jenkins = require('../util/jenkins');

/**
 * Calls a stapler post method to save the first user settings
 */
exports.saveFirstUser = function($form, success, error) {
	jenkins.staplerPost(
			'/setupWizard/createAdminUser',
		$form,
		success, {
			dataType: 'html',
			error: error
		});
};

/**
 * Calls a stapler post method to save the first user settings
 */
exports.saveProxy = function($form, success, error) {
	jenkins.staplerPost(
			'/pluginManager/proxyConfigure',
		$form,
		success, {
			dataType: 'html',
			error: error
		});
};
