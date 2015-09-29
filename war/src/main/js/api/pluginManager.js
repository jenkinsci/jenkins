/**
 * Provides a wrapper to interact with the plugin manager & update center
 */

var jenkins = require('../util/jenkins');

// TODO: Get plugin info (plugins + recommended plugin list) from update center.
// For now, we statically store them in the wizard. 

var plugins = require('./plugins.js');
plugins.names =[];
for (var i = 0; i < plugins.availablePlugins.length; i++) {
    var pluginCategory = plugins.availablePlugins[i];
    var categoryPlugins = pluginCategory.plugins;
    for (var ii = 0; ii < categoryPlugins.length; ii++) {
        var pluginName = categoryPlugins[ii].name;
        if (plugins.names.indexOf(pluginName) === -1) {
            plugins.names.push(pluginName);
        }
    }
}

/**
 * Get the curated list of plugins to be offered in the wizard.
 * @returns The curated list of plugins to be offered in the wizard.
 */
exports.plugins = function() {
    return plugins.availablePlugins;
};

/**
 * Get the curated list of plugins to be offered in the wizard by name only.
 * @returns The curated list of plugins to be offered in the wizard by name only.
 */
exports.pluginNames = function() {
    return plugins.names;
};

/**
 * Get the subset of plugins (subset of the plugin list) that are recommended by default.
 * <p>
 * The user can easily change this selection.
 * @returns The subset of plugins (subset of the plugin list) that are recommended by default.
 */
exports.recommendedPluginNames = function() {
    return plugins.recommendedPlugins;
}

/**
 * Call this function to install plugins, will pass a correlationId to the success callback which
 * may be used to restrict further calls getting plugin lists. Note: do not do this.
 */
exports.installPlugins = function(plugins, success) {
	jenkins.post('/pluginManager/installPlugins', { dynamicLoad: true, plugins: plugins }, function(data) {
		if(data.status != 'ok') {
			// error!
			throw data.message;
		}
		
		success(data.data.correlationId);
	});
};

/**
 * Accepts 1 or 2 arguments, if argument 2 is not provided all installing plugins will be passed 
 * to the handler function. If argument 2 is non-null, it will be treated as a correlationId, which
 * must be retrieved from a prior installPlugins call.
 */
exports.installStatus = function(handler, correlationId) {
	var url = '/updateCenter/installStatus';
	if(correlationId !== undefined) {
		url += '?correlationId=' + correlationId;
	}
	jenkins.get(url, function(data) {
		if(data.status !== 'ok') {
			// error!
			throw data.message;
		}
		
		handler(data.data);
	});	
};

/**
 * Provides a list of the available plugins, some useful properties is:
 * [
 * 	{ name, title, excerpt, dependencies[], ... },
 *  ...
 * ]
 */
exports.availablePlugins = function(handler) {
	jenkins.get('/pluginManager/plugins', function(response) {
		if(response.status !== 'ok') {
			// error!
			throw response.message;
		}

		// no status returned with this call
		handler(response.data);
	});
};
