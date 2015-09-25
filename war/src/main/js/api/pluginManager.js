/**
 * Provides a wrapper to interact with the plugin manager & update center
 */

var jenkins = require('../util/jenkins');

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
	if(correlationId != undefined) {
		url += '?correlationId=' + correlationId;
	}
	jenkins.get(url, function(data) {
		if(data.status != 'ok') {
			// error!
			throw data.message;
		}
		
		var jobs = data.data;
		var installJobs = [];
		
		// omit non-install jobs
		for(var i = 0; i < jobs.length; i++) {
			if(jobs[i].type != 'ConnectionCheckJob') {
				installJobs.push(jobs[i]);
			}
		}
		
		handler(installJobs);
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
	jenkins.get('/updateCenter/api/json?tree=availables[*,*[*]]', function(data) {
		// no status returned with this call
		handler(data.availables);
	});
};
