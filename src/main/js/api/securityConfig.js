import jenkins from "../util/jenkins";
import { getWindow } from "window-handle";

/**
 * A utility function to handle the common task of sending a stapler post request.
 * @param {string} url The endpoint to which the post request is sent.
 * @param {Object} $form The form data to be sent in the post request.
 * @param {Function} success Callback function to handle the success response.
 * @param {Function} error Callback function to handle the error response.
 * @throws {Error} Throws an error if the post request fails.
 */
function staplerPostRequest(url, $form, success, error) {
  try {
    jenkins.staplerPost(
      url,
      $form,
      function (response) {
        var crumbRequestField = response.data.crumbRequestField;
        if (crumbRequestField) {
          getWindow().crumb.init(crumbRequestField, response.data.crumb);
        }
        success(response);
      },
      {
        error: error,
      },
    );
  } catch (err) {
    console.error(`Error while sending post request to ${url}:`, err);
    throw new Error(`Failed to send post request to ${url}`);
  }
}

/**
 * Calls a stapler post method to save the first user settings.
 * @param {Object} $form The form data to be sent.
 * @param {Function} success Callback function for success response.
 * @param {Function} error Callback function for error response.
 */
function saveFirstUser($form, success, error) {
  if (typeof $form !== "object" || !($form instanceof Object)) {
    console.error("Invalid form data passed to saveFirstUser.");
    error("Invalid form data.");
    return;
  }

  staplerPostRequest(
    "/setupWizard/createAdminUser",
    $form,
    success,
    error
  );
}

/**
 * Calls a stapler post method to save the instance configuration.
 * @param {Object} $form The form data to be sent.
 * @param {Function} success Callback function for success response.
 * @param {Function} error Callback function for error response.
 */
function saveConfigureInstance($form, success, error) {
  if (typeof $form !== "object" || !($form instanceof Object)) {
    console.error("Invalid form data passed to saveConfigureInstance.");
    error("Invalid form data.");
    return;
  }

  staplerPostRequest(
    "/setupWizard/configureInstance",
    $form,
    success,
    error
  );
}

/**
 * Calls a stapler post method to save the proxy configuration.
 * @param {Object} $form The form data to be sent.
 * @param {Function} success Callback function for success response.
 * @param {Function} error Callback function for error response.
 */
function saveProxy($form, success, error) {
  if (typeof $form !== "object" || !($form instanceof Object)) {
    console.error("Invalid form data passed to saveProxy.");
    error("Invalid form data.");
    return;
  }

  staplerPostRequest(
    "/pluginManager/proxyConfigure",
    $form,
    success,
    error
  );
}

export default {
  saveFirstUser,
  saveConfigureInstance,
  saveProxy,
};
