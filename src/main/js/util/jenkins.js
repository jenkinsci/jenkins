/**
 * Jenkins JS Modules common utility functions
 */
import $ from "jquery";
import wh from "window-handle";
import Handlebars from "handlebars";

var debug = false;
var jenkins = {};

// gets the base Jenkins URL including context path
jenkins.baseUrl = function () {
  return document.head.dataset.rooturl;
};

/**
 * redirect
 */
jenkins.goTo = function (url) {
  wh.getWindow().location.replace(jenkins.baseUrl() + url);
};

/**
 * Jenkins AJAX GET callback.
 * If last parameter is an object, will be extended to jQuery options (e.g. pass { error: function() ... } to handle errors)
 */
jenkins.get = function (url, success, options) {
  if (debug) {
    console.log("get: " + url);
  }
  var args = {
    url: jenkins.baseUrl() + url,
    type: "GET",
    cache: false,
    dataType: "json",
    success: success,
  };
  if (options instanceof Object) {
    $.extend(args, options);
  }
  $.ajax(args);
};

/**
 * Jenkins AJAX POST callback, formats data as a JSON object post
 * If last parameter is an object, will be extended to jQuery options (e.g. pass { error: function() ... } to handle errors)
 */
jenkins.post = function (url, data, success, options) {
  if (debug) {
    console.log("post: " + url);
  }

  // handle crumbs
  var headers = {};
  var wnd = wh.getWindow();
  var crumb;
  if ("crumb" in options) {
    crumb = options.crumb;
  } else if ("crumb" in wnd) {
    crumb = wnd.crumb;
  }

  if (crumb) {
    headers[crumb.fieldName] = crumb.value;
  }

  var formBody = data;
  if (formBody instanceof Object) {
    if (crumb) {
      formBody = $.extend({}, formBody);
      formBody[crumb.fieldName] = crumb.value;
    }
    formBody = JSON.stringify(formBody);
  }

  var args = {
    url: jenkins.baseUrl() + url,
    type: "POST",
    cache: false,
    dataType: "json",
    data: formBody,
    contentType: "application/json",
    success: success,
    headers: headers,
  };
  if (options instanceof Object) {
    $.extend(args, options);
  }
  $.ajax(args);
};

/**
 *  handlebars setup, done for backwards compatibility because some plugins depend on it
 */
jenkins.initHandlebars = function () {
  return Handlebars;
};

/**
 * Load translations for the given bundle ID, provide the message object to the handler.
 * Optional error handler as the last argument.
 */
jenkins.loadTranslations = function (bundleName, handler, onError) {
  jenkins.get("/i18n/resourceBundle?baseName=" + bundleName, function (res) {
    if (res.status !== "ok") {
      if (onError) {
        onError(res.message);
      }
      throw "Unable to load localization data: " + res.message;
    }

    var translations = res.data;

    if ("undefined" !== typeof Proxy) {
      translations = new Proxy(translations, {
        get: function (target, property) {
          if (property in target) {
            return target[property];
          }
          if (debug) {
            console.log('"' + property + '" not found in translation bundle.');
          }
          return property;
        },
      });
    }

    handler(translations);
  });
};

/**
 * Runs a connectivity test, calls handler with a boolean whether there is sufficient connectivity to the internet
 */
jenkins.testConnectivity = function (siteId, handler) {
  // check the connectivity api
  var testConnectivity = function () {
    jenkins.get(
      "/updateCenter/connectionStatus?siteId=" + siteId,
      function (response) {
        if (response.status !== "ok") {
          handler(false, true, response.message);
        }

        // Define statuses, which need additional check iteration via async job on the Jenkins master
        // Statuses like "OK" or "SKIPPED" are considered as fine.
        var uncheckedStatuses = ["PRECHECK", "CHECKING", "UNCHECKED"];
        if (
          uncheckedStatuses.indexOf(response.data.updatesite) >= 0 ||
          uncheckedStatuses.indexOf(response.data.internet) >= 0
        ) {
          setTimeout(testConnectivity, 100);
        } else {
          // Update site should be always reachable, but we do not require the internet connection
          // if it's explicitly skipped by the update center
          if (
            response.status !== "ok" ||
            response.data.updatesite !== "OK" ||
            (response.data.internet !== "OK" &&
              response.data.internet !== "SKIPPED")
          ) {
            // no connectivity, but not fatal
            handler(false, false);
          } else {
            handler(true);
          }
        }
      },
      {
        error: function (xhr, textStatus, errorThrown) {
          if (xhr.status === 403) {
            jenkins.goTo("/login");
          } else {
            handler.call({ isError: true, errorMessage: errorThrown });
          }
        },
      },
    );
  };
  testConnectivity();
};

/**
 * gets the window containing a form, taking in to account top-level iframes
 */
jenkins.getWindow = function ($form) {
  $form = $($form);
  var wnd = wh.getWindow();
  $(top.document)
    .find("iframe")
    .each(function () {
      var windowFrame = this.contentWindow;
      var $f = $(this).contents().find("form");
      $f.each(function () {
        if ($form[0] === this) {
          wnd = windowFrame;
        }
      });
    });
  return wnd;
};

/**
 * Builds a stapler form post
 */
jenkins.buildFormPost = function ($form) {
  $form = $($form);
  var wnd = jenkins.getWindow($form);
  var form = $form[0];
  if (wnd.buildFormTree(form)) {
    return (
      $form.serialize() +
      "&" +
      $.param({
        "core:apply": "",
        Submit: "Save",
        json: $form.find("input[name=json]").val(),
      })
    );
  }
  return "";
};

/**
 * Gets the crumb, if crumbs are enabled
 */
jenkins.getFormCrumb = function ($form) {
  $form = $($form);
  var wnd = jenkins.getWindow($form);
  return wnd.crumb;
};

/**
 * Jenkins Stapler JSON POST callback
 * If last parameter is an object, will be extended to jQuery options (e.g. pass { error: function() ... } to handle errors)
 */
jenkins.staplerPost = function (url, $form, success, options) {
  $form = $($form);
  var postBody = jenkins.buildFormPost($form);
  var crumb = jenkins.getFormCrumb($form);
  jenkins.post(
    url,
    postBody,
    success,
    $.extend(
      {
        processData: false,
        contentType: "application/x-www-form-urlencoded",
        crumb: crumb,
      },
      options,
    ),
  );
};

export default jenkins;
