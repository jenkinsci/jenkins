/**
 * Jenkins JS Modules common utility functions
 */

// Get the modules

var jquery = require('jquery-detached');
var wh = require('window-handle');

var debug = false;

// gets the base Jenkins URL including context path
exports.baseUrl = function() {
	var $ = jquery.getJQuery();
	var u = $('head').attr('data-rooturl');
	if(!u) {
		u = '';
	}
	return u;
};

// awful hack to get around JSONifying things with Prototype taking over wrong. ugh. Prototype is the worst.
exports.stringify = function(o) {
	if(Array.prototype.toJSON) { // Prototype f's this up something bad
		var protoJSON = {
			a: Array.prototype.toJSON,
			o: Object.prototype.toJSON,
			h: Hash.prototype.toJSON,
			s: String.prototype.toJSON
		};
		try {
			delete Array.prototype.toJSON;
		delete Object.prototype.toJSON;
			delete Hash.prototype.toJSON;
			delete String.prototype.toJSON;

		return JSON.stringify(o);
		}
		finally {
		if(protoJSON.a) {
			Array.prototype.toJSON = protoJSON.a;
		}
		if(protoJSON.o) {
			Object.prototype.toJSON = protoJSON.o;
		}
		if(protoJSON.h) {
			Hash.prototype.toJSON = protoJSON.h;
		}
		if(protoJSON.s) {
			String.prototype.toJSON = protoJSON.s;
		}
		}
	}
	else {
		return JSON.stringify(o);
	}
};

/**
 * Take a string and replace non-id characters to make it a friendly-ish XML id
 */
exports.idIfy = function(str) {
	return (''+str).replace(/\W+/g, '_');
};

/**
 * redirect
 */
exports.goTo = function(url) {
	wh.getWindow().location.replace(exports.baseUrl() + url);
};

/**
 * Jenkins AJAX GET callback.
 * If last parameter is an object, will be extended to jQuery options (e.g. pass { error: function() ... } to handle errors)
 */
exports.get = function(url, success, options) {
	if(debug) {
		console.log('get: ' + url);
	}
	var $ = jquery.getJQuery();
	var args = {
		url: exports.baseUrl() + url,
		type: 'GET',
		cache: false,
		dataType: 'json',
		success: success
	};
	if(options instanceof Object) {
		$.extend(args, options);
	}
	$.ajax(args);
};

/**
 * Jenkins AJAX POST callback, formats data as a JSON object post (note: works around prototype.js ugliness using stringify() above)
 * If last parameter is an object, will be extended to jQuery options (e.g. pass { error: function() ... } to handle errors)
 */
exports.post = function(url, data, success, options) {
	if(debug) {
		console.log('post: ' + url);
	}
	
	var $ = jquery.getJQuery();
	
	// handle crumbs
	var headers = {};
	var wnd = wh.getWindow();
	var crumb;
	if('crumb' in options) {
		crumb = options.crumb;
	}
	else if('crumb' in wnd) {
		crumb = wnd.crumb;
	}
	
	if(crumb) {
		headers[crumb.fieldName] = crumb.value;
	}
	
	var formBody = data;
	if(formBody instanceof Object) {
		if(crumb) {
			formBody = $.extend({}, formBody);
			formBody[crumb.fieldName] = crumb.value;
		}
		formBody = exports.stringify(formBody);
	}
	
	var args = {
		url: exports.baseUrl() + url,
		type: 'POST',
		cache: false,
		dataType: 'json',
		data: formBody,
		contentType: "application/json",
		success: success,
		headers: headers
	};
	if(options instanceof Object) {
		$.extend(args, options);
	}
	$.ajax(args);
};

/**
 *  handlebars setup, this does not seem to actually work or get called by the require() of this file, so have to explicitly call it
 */
exports.initHandlebars = function() {
	var Handlebars = require('handlebars');

	Handlebars.registerHelper('ifeq', function(o1, o2, options) {
		if(o1 === o2) {
			return options.fn();
		}
	});

	Handlebars.registerHelper('ifneq', function(o1, o2, options) {
		if(o1 !== o2) {
			return options.fn();
		}
	});

	Handlebars.registerHelper('in-array', function(arr, val, options) {
		if(arr.indexOf(val) >= 0) {
			return options.fn();
		}
	});

	Handlebars.registerHelper('id', exports.idIfy);

	return Handlebars;
};

/**
 * Load translations for the given bundle ID, provide the message object to the handler.
 * Optional error handler as the last argument.
 */
exports.loadTranslations = function(bundleName, handler, onError) {
	exports.get('/i18n/resourceBundle?baseName='  +bundleName, function(res) {
		if(res.status !== 'ok') {
			if(onError) {
				onError(res.message);
			}
			throw 'Unable to load localization data: ' + res.message;
		}

		handler(res.data);
	});
};

/**
 * Runs a connectivity test, calls handler with a boolean whether there is sufficient connectivity to the internet
 */
exports.testConnectivity = function(siteId, handler) {
	// check the connectivity api
	var testConnectivity = function() {
		exports.get('/updateCenter/connectionStatus?siteId=' + siteId, function(response) {
			if(response.status !== 'ok') {
				handler(false, true, response.message);
			}
			
			var uncheckedStatuses = ['PRECHECK', 'CHECKING', 'UNCHECKED'];
			if(uncheckedStatuses.indexOf(response.data.updatesite) >= 0  || uncheckedStatuses.indexOf(response.data.internet) >= 0) {
				setTimeout(testConnectivity, 100);
			}
			else {
				if(response.status !== 'ok' || response.data.updatesite !== 'OK' || response.data.internet !== 'OK') {
					// no connectivity, but not fatal
					handler(false, false);
				}
				else {
					handler(true);
				}
			}
		});
	};
	testConnectivity();
};

/**
 * gets the window containing a form, taking in to account top-level iframes
 */
exports.getWindow = function($form) {
	var $ = jquery.getJQuery();
	$form = $($form);
	var wnd = wh.getWindow();
	$(top.document).find('iframe').each(function() {
		var windowFrame = this.contentWindow;
		var $f = $(this).contents().find('form');
		if($f.length > 0 && $form[0] === $f[0]) {
			wnd = windowFrame;
		}
	});
	return wnd;
};

/**
 * Builds a stapler form post
 */
exports.buildFormPost = function($form) {
	var $ = jquery.getJQuery();
	$form = $($form);
	var wnd = exports.getWindow($form);
	var form = $form[0];
	if(wnd.buildFormTree(form)) {
		return $form.serialize() +
			'&core:apply=&Submit=Save&json=' + $form.find('input[name=json]').val();
	}
	return '';
};

/**
 * Gets the crumb, if crumbs are enabled
 */
exports.getFormCrumb = function($form) {
	var $ = jquery.getJQuery();
	$form = $($form);
	var wnd = exports.getWindow($form);
	return wnd.crumb;
};

/**
 * Jenkins Stapler JSON POST callback
 * If last parameter is an object, will be extended to jQuery options (e.g. pass { error: function() ... } to handle errors)
 */
exports.staplerPost = function(url, $form, success, options) {
	var $ = jquery.getJQuery();
	$form = $($form);
	var postBody = exports.buildFormPost($form);
	var crumb = exports.getFormCrumb($form);
	exports.post(
		url,
		postBody,
		success, $.extend({
			processData: false,
			contentType: 'application/x-www-form-urlencoded',
			crumb: crumb
		}, options));
};
