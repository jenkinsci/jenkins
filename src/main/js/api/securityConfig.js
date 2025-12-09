import jenkins from "../util/jenkins";
import { getWindow } from "window-handle";

/**
 * Provides a wrapper to interact with the security configuration
 */

/*
 * Calls a stapler post method to save the first user settings
 */
function saveFirstUser($form, success, error) {
  // Fetch a fresh crumb before submission to ensure it matches the current session
  // This prevents issues where the crumb becomes stale during setup wizard initialization
  // (see https://github.com/jenkinsci/jenkins/issues/...)
  jenkins.get(
    "/crumbIssuer/api/json",
    function (crumbData) {
      // Update the form with the fresh crumb if crumb field exists
      if (crumbData && crumbData.crumbRequestField && crumbData.crumb) {
        // Look for an existing crumb input in the form
        var $form_elem = $form.jquery ? $form : $($form);
        var crumbInputs = $form_elem.find("input[name='" + crumbData.crumbRequestField + "']");
        
        if (crumbInputs.length > 0) {
          // Update existing crumb inputs
          crumbInputs.val(crumbData.crumb);
        } else {
          // If no crumb input exists in the form, add one as a hidden input
          $form_elem.prepend(
            $("<input>")
              .attr("type", "hidden")
              .attr("name", crumbData.crumbRequestField)
              .val(crumbData.crumb)
          );
        }
      }
      // Now submit the form with the fresh crumb
      submitFirstUserForm($form, success, error);
    },
    {
      error: function () {
        // If fetching crumb fails, still try to submit (in case crumb issuer is disabled)
        submitFirstUserForm($form, success, error);
      },
    },
  );
}

function submitFirstUserForm($form, success, error) {
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
  // Fetch a fresh crumb before submission to ensure it matches the current session
  // This prevents issues where the crumb becomes stale during setup wizard initialization
  // (see https://github.com/jenkinsci/jenkins/issues/...)
  jenkins.get(
    "/crumbIssuer/api/json",
    function (crumbData) {
      // Update the form with the fresh crumb if crumb field exists
      if (crumbData && crumbData.crumbRequestField && crumbData.crumb) {
        // Look for an existing crumb input in the form
        var $form_elem = $form.jquery ? $form : $($form);
        var crumbInputs = $form_elem.find("input[name='" + crumbData.crumbRequestField + "']");
        
        if (crumbInputs.length > 0) {
          // Update existing crumb inputs
          crumbInputs.val(crumbData.crumb);
        } else {
          // If no crumb input exists in the form, add one as a hidden input
          $form_elem.prepend(
            $("<input>")
              .attr("type", "hidden")
              .attr("name", crumbData.crumbRequestField)
              .val(crumbData.crumb)
          );
        }
      }
      // Now submit the form with the fresh crumb
      submitConfigureInstanceForm($form, success, error);
    },
    {
      error: function () {
        // If fetching crumb fails, still try to submit (in case crumb issuer is disabled)
        submitConfigureInstanceForm($form, success, error);
      },
    },
  );
}

function submitConfigureInstanceForm($form, success, error) {
  jenkins.staplerPost(
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
  saveFirstUser: saveFirstUser,
  saveConfigureInstance: saveConfigureInstance,
  saveProxy: saveProxy,
};
