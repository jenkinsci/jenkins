/**
 * Jenkins JS Modules common utility functions
 */

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
  window.location.replace(jenkins.baseUrl() + url);
};

/**
 * Jenkins AJAX GET callback.
 * If last parameter is an object, will be extended to the request options (e.g. pass { error: function() ... } to handle errors)
 */
jenkins.get = function (url, success, options) {
  if (debug) {
    console.log("get: " + url);
  }
  request(
    Object.assign(
      {
        url: jenkins.baseUrl() + url,
        type: "GET",
        dataType: "json",
        success: success,
      },
      options,
    ),
  );
};

/**
 * Jenkins AJAX POST callback, formats data as a JSON object post
 * If last parameter is an object, will be extended to the request options (e.g. pass { error: function() ... } to handle errors)
 */
jenkins.post = function (url, data, success, options) {
  if (debug) {
    console.log("post: " + url);
  }

  var headers = {};
  var crumb;
  if (options && "crumb" in options) {
    crumb = options.crumb;
  } else if ("crumb" in window) {
    crumb = window.crumb;
  }

  if (crumb) {
    headers[crumb.fieldName] = crumb.value;
  }

  var formBody = data;
  if (formBody instanceof Object) {
    if (crumb) {
      formBody = Object.assign({}, formBody);
      formBody[crumb.fieldName] = crumb.value;
    }
    formBody = JSON.stringify(formBody);
  }

  request(
    Object.assign(
      {
        url: jenkins.baseUrl() + url,
        type: "POST",
        dataType: "json",
        data: formBody,
        contentType: "application/json",
        success: success,
        headers: headers,
      },
      options,
    ),
  );
};

function request(args) {
  var headers = Object.assign(
    { "X-Requested-With": "XMLHttpRequest" },
    args.headers,
  );
  if (args.data !== undefined) {
    headers["Content-Type"] = args.contentType;
  }

  var controller = new AbortController();
  var timedOut = false;
  var timer;
  if (args.timeout > 0) {
    timer = setTimeout(function () {
      timedOut = true;
      controller.abort();
    }, args.timeout);
  }

  var fail = function (xhr, textStatus, errorThrown) {
    if (args.error) {
      args.error(xhr, textStatus, errorThrown);
    }
  };

  fetch(args.url, {
    method: args.type,
    headers: headers,
    body: args.data,
    cache: "no-store",
    signal: controller.signal,
  })
    .then(function (response) {
      return response.text().then(function (responseText) {
        return { response: response, responseText: responseText };
      });
    })
    .then(
      function (result) {
        clearTimeout(timer);
        var response = result.response;
        var xhr = {
          status: response.status,
          statusText: response.statusText,
          responseText: result.responseText,
        };
        if (!response.ok) {
          fail(xhr, "error", response.statusText);
          return;
        }
        var data = result.responseText;
        if (args.dataType === "json" && response.status !== 204) {
          try {
            data = JSON.parse(result.responseText);
          } catch (e) {
            fail(xhr, "parsererror", e);
            return;
          }
        }
        if (args.success) {
          args.success(data);
        }
      },
      function (e) {
        clearTimeout(timer);
        var xhr = { status: 0, statusText: "error", responseText: "" };
        if (timedOut) {
          fail(xhr, "timeout", "timeout");
          return;
        }
        fail(xhr, "error", e.message);
      },
    );
}

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

function toForm(form) {
  return form.jquery ? form[0] : form;
}

/**
 * gets the window containing a form, taking in to account top-level iframes
 */
jenkins.getWindow = function (form) {
  return toForm(form).ownerDocument.defaultView;
};

/**
 * Builds a stapler form post
 */
jenkins.buildFormPost = function (form) {
  form = toForm(form);
  var wnd = jenkins.getWindow(form);
  if (!wnd.buildFormTree(form)) {
    return "";
  }
  var params = new URLSearchParams(new FormData(form));
  params.append("core:apply", "");
  params.append("Submit", "Save");
  params.append("json", form.querySelector("input[name=json]").value);
  return params.toString();
};

/**
 * Gets the crumb, if crumbs are enabled
 */
jenkins.getFormCrumb = function (form) {
  return jenkins.getWindow(form).crumb;
};

/**
 * Jenkins Stapler JSON POST callback
 * If last parameter is an object, will be extended to the request options (e.g. pass { error: function() ... } to handle errors)
 */
jenkins.staplerPost = function (url, form, success, options) {
  form = toForm(form);
  var postBody = jenkins.buildFormPost(form);
  var crumb = jenkins.getFormCrumb(form);
  jenkins.post(
    url,
    postBody,
    success,
    Object.assign(
      {
        contentType: "application/x-www-form-urlencoded",
        crumb: crumb,
      },
      options,
    ),
  );
};

export default jenkins;
