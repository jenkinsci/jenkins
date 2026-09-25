/**
 * Provides a wrapper to interact with the plugin manager & update center
 */
import jenkins from "../util/jenkins";

//Get plugin info (plugins + recommended plugin list) from update centers.
var plugins;

var pluginManager = {};

pluginManager.initialPluginList = function (handler) {
  jenkins.get(
    "/setupWizard/platformPluginList",
    function (response) {
      if (response.status !== "ok") {
        handler.call({ isError: true, data: response.data });
        return;
      }

      handler.call({ isError: false }, response.data);
    },
    {
      timeout: pluginManagerErrorTimeoutMillis,
      error: function (xhr, textStatus, errorThrown) {
        handler.call({ isError: true, errorMessage: errorThrown });
      },
    },
  );
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
            var language =
              window.navigator.userLanguage || window.navigator.language;
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

// default 10 seconds for AJAX responses to return before triggering an error condition
var pluginManagerErrorTimeoutMillis = 10 * 1000;

/**
 * Get the curated list of plugins to be offered in the wizard.
 * @returns The curated list of plugins to be offered in the wizard.
 */
pluginManager.plugins = function () {
  return plugins.availablePlugins;
};

/**
 * Get the curated list of plugins to be offered in the wizard by name only.
 * @returns The curated list of plugins to be offered in the wizard by name only.
 */
pluginManager.pluginNames = function () {
  return plugins.names;
};

/**
 * Get the subset of plugins (subset of the plugin list) that are recommended by default.
 * <p>
 * The user can easily change this selection.
 * @returns The subset of plugins (subset of the plugin list) that are recommended by default.
 */
pluginManager.recommendedPluginNames = function () {
  return plugins.recommendedPlugins.slice(); // copy this
};

/**
 * Call this function to install plugins, will pass a correlationId to the complete callback which
 * may be used to restrict further calls getting plugin lists. Note: do not use the correlation id.
 * If handler is called with this.isError, there will be a corresponding this.errorMessage indicating
 * the failure reason
 */
pluginManager.installPlugins = function (plugins, handler) {
  jenkins.post(
    "/pluginManager/installPlugins",
    { dynamicLoad: true, plugins: plugins },
    function (response) {
      if (response.status !== "ok") {
        handler.call({ isError: true, errorMessage: response.message });
        return;
      }

      handler.call({ isError: false }, response.data.correlationId);
    },
    {
      timeout: pluginManagerErrorTimeoutMillis,
      error: function (xhr, textStatus, errorThrown) {
        handler.call({ isError: true, errorMessage: errorThrown });
      },
    },
  );
};

/**
 * Accepts 1 or 2 arguments, if argument 2 is not provided all installing plugins will be passed
 * to the handler function. If argument 2 is non-null, it will be treated as a correlationId, which
 * must be retrieved from a prior installPlugins call.
 */
pluginManager.installStatus = function (handler, correlationId) {
  var url = "/updateCenter/installStatus";
  if (correlationId !== undefined) {
    url += "?correlationId=" + correlationId;
  }
  jenkins.get(
    url,
    function (response) {
      if (response.status !== "ok") {
        handler.call({ isError: true, errorMessage: response.message });
        return;
      }

      handler.call({ isError: false }, response.data);
    },
    {
      timeout: pluginManagerErrorTimeoutMillis,
      error: function (xhr, textStatus, errorThrown) {
        handler.call({ isError: true, errorMessage: errorThrown });
      },
    },
  );
};

/**
 * Provides a list of the available plugins, some useful properties is:
 * [
 * 	{ name, title, excerpt, dependencies[], ... },
 *  ...
 * ]
 */
pluginManager.availablePlugins = function (handler) {
  jenkins.get(
    "/pluginManager/plugins",
    function (response) {
      if (response.status !== "ok") {
        handler.call({ isError: true, errorMessage: response.message });
        return;
      }

      handler.call({ isError: false }, response.data);
    },
    {
      timeout: pluginManagerErrorTimeoutMillis,
      error: function (xhr, textStatus, errorThrown) {
        handler.call({ isError: true, errorMessage: errorThrown });
      },
    },
  );
};

pluginManager.availablePluginsSearch = function (query, limit, handler) {
  jenkins.get(
    "/pluginManager/pluginsSearch?query=" + query + "&limit=" + limit,
    function (response) {
      if (response.status !== "ok") {
        handler.call({ isError: true, errorMessage: response.message });
        return;
      }

      handler.call({ isError: false }, response.data);
    },
    {
      timeout: pluginManagerErrorTimeoutMillis,
      error: function (xhr, textStatus, errorThrown) {
        handler.call({ isError: true, errorMessage: errorThrown });
      },
    },
  );
};

/**
 * Accepts 1 or 2 arguments, if argument 2 is not provided all installing plugins will be passed
 * to the handler function. If argument 2 is non-null, it will be treated as a correlationId, which
 * must be retrieved from a prior installPlugins call.
 */
pluginManager.incompleteInstallStatus = function (handler, correlationId) {
  var url = "/updateCenter/incompleteInstallStatus";
  if (correlationId !== undefined) {
    url += "?correlationId=" + correlationId;
  }
  jenkins.get(
    url,
    function (response) {
      if (response.status !== "ok") {
        handler.call({ isError: true, errorMessage: response.message });
        return;
      }

      handler.call({ isError: false }, response.data);
    },
    {
      timeout: pluginManagerErrorTimeoutMillis,
      error: function (xhr, textStatus, errorThrown) {
        handler.call({ isError: true, errorMessage: errorThrown });
      },
    },
  );
};

/**
 * Call this to complete the installation without installing anything
 */
pluginManager.completeInstall = function (handler) {
  jenkins.post(
    "/setupWizard/completeInstall",
    {},
    function () {
      handler.call({ isError: false });
    },
    {
      timeout: pluginManagerErrorTimeoutMillis,
      error: function (xhr, textStatus, errorThrown) {
        handler.call({ isError: true, message: errorThrown });
      },
    },
  );
};

/**
 * Indicates there is a restart required to complete plugin installations
 */
pluginManager.getRestartStatus = function (handler) {
  jenkins.get(
    "/setupWizard/restartStatus",
    function (response) {
      handler.call({ isError: false }, response.data);
    },
    {
      timeout: pluginManagerErrorTimeoutMillis,
      error: function (xhr, textStatus, errorThrown) {
        handler.call({ isError: true, message: errorThrown });
      },
    },
  );
};

/**
 * Skip failed plugins, continue
 */
pluginManager.installPluginsDone = function (handler) {
  jenkins.post(
    "/pluginManager/installPluginsDone",
    {},
    function () {
      handler();
    },
    {
      timeout: pluginManagerErrorTimeoutMillis,
      error: function (xhr, textStatus, errorThrown) {
        handler.call({ isError: true, message: errorThrown });
      },
    },
  );
};

/**
 * Restart Jenkins
 */
pluginManager.restartJenkins = function (handler) {
  jenkins.post(
    "/updateCenter/safeRestart",
    {},
    function () {
      handler.call({ isError: false });
    },
    {
      timeout: pluginManagerErrorTimeoutMillis,
      error: function (xhr, textStatus, errorThrown) {
        handler.call({ isError: true, message: errorThrown });
      },
    },
  );
};

export default pluginManager;
