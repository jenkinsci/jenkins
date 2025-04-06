/**
 * Provides a wrapper to interact with the plugin manager & update center
 */
import jenkins from "../util/jenkins"; // retained for reference, but not used now

// Get plugin info (plugins + recommended plugin list) from update centers.
var plugins;

var pluginManager = {};

// Default 10 seconds for AJAX responses to return before triggering an error condition
// (Note: The fetch API does not have a built-in timeout, so this option is omitted)
var pluginManagerErrorTimeoutMillis = 10 * 1000;

/**
 * Get the initial plugin list from the update center.
 */
pluginManager.initialPluginList = function (handler) {
  fetch("/setupWizard/platformPluginList")
    .then(response => {
      if (!response.ok) {
        return response.json().then(data => {
          handler.call({ isError: true, data: data });
          throw new Error('Platform plugin list fetch failed');
        });
      }
      return response.json();
    })
    .then(data => {
      handler.call({ isError: false }, data);
    })
    .catch(error => {
      handler.call({ isError: true, errorMessage: error.message });
    });
};

// Call this to initialize the plugin list
pluginManager.init = function (handler) {
  pluginManager.initialPluginList(function (initialPluginCategories) {
    plugins = {};
    plugins.names = [];
    plugins.recommendedPlugins = [];
    plugins.availablePlugins = initialPluginCategories;
    for (var i = 0; i < initialPluginCategories.length; i++) {
      var pluginCategory = initialPluginCategories[i];
      var categoryPlugins = pluginCategory.plugins;
      for (var ii = 0; ii < categoryPlugins.length; ii++) {
        var plugin = categoryPlugins[ii];
        var pluginName = plugin.name;
        if (plugins.names.indexOf(pluginName) === -1) {
          plugins.names.push(pluginName);
          if (plugin.suggested) {
            plugins.recommendedPlugins.push(pluginName);
          } else if (pluginCategory.category === "Languages") {
            var language = window.navigator.userLanguage || window.navigator.language;
            var code = language.toLocaleLowerCase();
            if (pluginName === "localization-" + code) {
              plugins.recommendedPlugins.push(pluginName);
            }
          }
        }
      }
    }
    handler();
  });
};

/**
 * Get the curated list of plugins to be offered in the wizard.
 * @returns The curated list of plugins.
 */
pluginManager.plugins = function () {
  return plugins.availablePlugins;
};

/**
 * Get the curated list of plugin names.
 * @returns An array of plugin names.
 */
pluginManager.pluginNames = function () {
  return plugins.names;
};

/**
 * Get the subset of recommended plugin names.
 * @returns An array of recommended plugin names.
 */
pluginManager.recommendedPluginNames = function () {
  return plugins.recommendedPlugins.slice(); // copy this
};

/**
 * Install plugins and return a correlationId via the handler.
 */
pluginManager.installPlugins = function (pluginsList, handler) {
  fetch("/pluginManager/installPlugins", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ dynamicLoad: true, plugins: pluginsList })
  })
    .then(response => {
      if (!response.ok) {
        return response.json().then(data => { throw new Error(data.message || "Install plugins failed"); });
      }
      return response.json();
    })
    .then(data => {
      if (data.status !== "ok") {
        handler.call({ isError: true, errorMessage: data.message });
        return;
      }
      handler.call({ isError: false }, data.data.correlationId);
    })
    .catch(error => {
      handler.call({ isError: true, errorMessage: error.message });
    });
};

/**
 * Get the install status, optionally filtering by correlationId.
 */
pluginManager.installStatus = function (handler, correlationId) {
  var url = "/updateCenter/installStatus";
  if (correlationId !== undefined) {
    url += "?correlationId=" + correlationId;
  }
  fetch(url)
    .then(response => {
      if (!response.ok) {
        return response.json().then(data => { throw new Error(data.message || "Install status fetch failed"); });
      }
      return response.json();
    })
    .then(data => {
      if (data.status !== "ok") {
        handler.call({ isError: true, errorMessage: data.message });
        return;
      }
      handler.call({ isError: false }, data.data);
    })
    .catch(error => {
      handler.call({ isError: true, errorMessage: error.message });
    });
};

/**
 * Get the list of available plugins.
 */
pluginManager.availablePlugins = function (handler) {
  fetch("/pluginManager/plugins")
    .then(response => {
      if (!response.ok) {
        return response.json().then(data => { throw new Error(data.message || "Available plugins fetch failed"); });
      }
      return response.json();
    })
    .then(data => {
      if (data.status !== "ok") {
        handler.call({ isError: true, errorMessage: data.message });
        return;
      }
      handler.call({ isError: false }, data.data);
    })
    .catch(error => {
      handler.call({ isError: true, errorMessage: error.message });
    });
};

/**
 * Search available plugins based on query and limit.
 */
pluginManager.availablePluginsSearch = function (query, limit, handler) {
  const url = `/pluginManager/pluginsSearch?query=${query}&limit=${limit}`;
  fetch(url)
    .then(response => {
      if (!response.ok) {
        return response.json().then(data => { throw new Error(data.message || "Plugins search failed"); });
      }
      return response.json();
    })
    .then(data => {
      if (data.status !== "ok") {
        handler.call({ isError: true, errorMessage: data.message });
        return;
      }
      handler.call({ isError: false }, data.data);
    })
    .catch(error => {
      handler.call({ isError: true, errorMessage: error.message });
    });
};

/**
 * Get the incomplete install status, optionally filtering by correlationId.
 */
pluginManager.incompleteInstallStatus = function (handler, correlationId) {
  var url = "/updateCenter/incompleteInstallStatus";
  if (correlationId !== undefined) {
    url += "?correlationId=" + correlationId;
  }
  fetch(url)
    .then(response => {
      if (!response.ok) {
        return response.json().then(data => { throw new Error(data.message || "Incomplete install status failed"); });
      }
      return response.json();
    })
    .then(data => {
      if (data.status !== "ok") {
        handler.call({ isError: true, errorMessage: data.message });
        return;
      }
      handler.call({ isError: false }, data.data);
    })
    .catch(error => {
      handler.call({ isError: true, errorMessage: error.message });
    });
};

/**
 * Complete the installation without installing additional plugins.
 */
pluginManager.completeInstall = function (handler) {
  fetch("/setupWizard/completeInstall", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({})
  })
    .then(response => {
      if (!response.ok) {
        return response.json().then(data => { throw new Error(data.message || "Complete install failed"); });
      }
      return response.json();
    })
    .then(() => {
      handler.call({ isError: false });
    })
    .catch(error => {
      handler.call({ isError: true, message: error.message });
    });
};

/**
 * Get the restart status to determine if a restart is required.
 */
pluginManager.getRestartStatus = function (handler) {
  fetch("/setupWizard/restartStatus")
    .then(response => {
      if (!response.ok) {
        return response.json().then(data => { throw new Error(data.message || "Restart status failed"); });
      }
      return response.json();
    })
    .then(data => {
      handler.call({ isError: false }, data.data);
    })
    .catch(error => {
      handler.call({ isError: true, message: error.message });
    });
};

/**
 * Skip failed plugins installation and continue.
 */
pluginManager.installPluginsDone = function (handler) {
  fetch("/pluginManager/installPluginsDone", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({})
  })
    .then(response => {
      if (!response.ok) {
        return response.json().then(data => { throw new Error(data.message || "Install plugins done failed"); });
      }
      return response.json();
    })
    .then(() => {
      handler();
    })
    .catch(error => {
      handler.call({ isError: true, message: error.message });
    });
};

/**
 * Restart Jenkins safely.
 */
pluginManager.restartJenkins = function (handler) {
  fetch("/updateCenter/safeRestart", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({})
  })
    .then(response => {
      if (!response.ok) {
        return response.json().then(data => { throw new Error(data.message || "Restart failed"); });
      }
      return response.json();
    })
    .then(() => {
      handler.call({ isError: false });
    })
    .catch(error => {
      handler.call({ isError: true, message: error.message });
    });
};

export default pluginManager;
