import jenkins from "../util/jenkins";
import { getWindow } from "window-handle";

/**
 * Provides a wrapper to interact with the security configuration.
 */

/**
 * Save the first user configuration.
 *
 * @param {object} $form - The form data for the new admin user.
 * @param {Function} success - Callback invoked on a successful response.
 * @param {Function} error - Callback invoked on error.
 */
function saveFirstUser($form, success, error) {
  jenkins.staplerPost(
    "/setupWizard/createAdminUser",
    $form,
    function(response) {
      const crumbRequestField = response.data.crumbRequestField;
      if (crumbRequestField) {
        getWindow().crumb.init(crumbRequestField, response.data.crumb);
      }
      success(response);
    },
    { error: error }
  );
}

/**
 * Save the instance configuration.
 *
 * @param {object} $form - The form data for configuring the instance.
 * @param {Function} success - Callback invoked on a successful response.
 * @param {Function} error - Callback invoked on error.
 */
function saveConfigureInstance($form, success, error) {
  jenkins.staplerPost(
    "/setupWizard/configureInstance",
    $form,
    function(response) {
      const crumbRequestField = response.data.crumbRequestField;
      if (crumbRequestField) {
        getWindow().crumb.init(crumbRequestField, response.data.crumb);
      }
      success(response);
    },
    { error: error }
  );
}

/**
 * Save the proxy configuration.
 *
 * @param {object} $form - The form data for proxy configuration.
 * @param {Function} success - Callback invoked on a successful response.
 * @param {Function} error - Callback invoked on error.
 */
function saveProxy($form, success, error) {
  jenkins.staplerPost(
    "/pluginManager/proxyConfigure",
    $form,
    success,
    {
      dataType: "html",
      error: error,
    }
  );
}

export default {
  saveFirstUser,
  saveConfigureInstance,
  saveProxy,
};
