/**
 * Provides a wrapper to interact with the security configuration
 */

var jquery = require('jquery-detached');
var jenkins = require('../util/jenkins');

/**
 * Calls a stapler post method to save the first user settings
 */
exports.saveFirstUser = function($form, success, error) {
	jenkins.staplerPost(
			'/setupWizard/createAdminUser',
		$form,
		function(response) {
        		var crumbRequestField = response.data.crumbRequestField;
			if (crumbRequestField) {
				require('window-handle').getWindow().crumb.init(crumbRequestField, response.data.crumb);
			}
			success(response);
		}, {
			error: error
		});
};

exports.saveRootUrl = function($rootUrlForm, successOrError, options) {
	var $ = jquery.getJQuery();
	var $form = $($rootUrlForm);
	var crumb = jenkins.getFormCrumb($form);

	var rootUrlAsFormEncoded = $form.serialize() + '&core:apply=&Submit=Save';
	jenkins.post(
		'/setupWizard/configureRootUrl',
		rootUrlAsFormEncoded,
		successOrError,
		$.extend({
			processData: false,
			contentType: 'application/x-www-form-urlencoded',
			crumb: crumb,
			error: successOrError
		}, options)
	);
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
