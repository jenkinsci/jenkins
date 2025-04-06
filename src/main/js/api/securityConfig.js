import jenkins from "../util/jenkins";
import { getWindow } from "window-handle";

/**
 * Provides a wrapper to interact with the security configuration
 */

/*
 * Calls a stapler post method to save the first user settings
 */
function saveFirstUser($form, success, error) {
  jenkins.staplerPost(
    "/setupWizard/createAdminUser",
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
}

function saveConfigureInstance($form, success, error) {
  if (typeof $form !== "object" || !($form instanceof Object)) {
    console.error("Invalid form data passed to saveConfigureInstance.");
    error("Invalid form data.");
    return;
  }

  staplerPostRequest(
    "/setupWizard/configureInstance",
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
}

/**
 * Calls a stapler post method to save the first user settings
 */
function saveProxy($form, success, error) {
  jenkins.staplerPost("/pluginManager/proxyConfigure", $form, success, {
    dataType: "html",
    error: error,
  });
}

export default {
  saveFirstUser,
  saveConfigureInstance,
  saveProxy,
};
