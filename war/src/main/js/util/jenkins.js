/**
 * Jenkins JS Modules common utility functions
 */

// Get the modules

var jquery = require('jquery-detached');
var wh = require('window-handle');

// gets the base Jenkins URL including context path
var baseUrl = function() {
	var $ = jquery.getJQuery();
	var u = $('head').attr('data-rooturl');
	if(!u) {
		u = '';
	}
	return u;
};

// awful hack to get around JSONifying things with Prototype taking over wrong. ugh. Prototype is the worst.
var stringify = function(o) {
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
 * redirect
 */
exports.go = function(url) {
    wh.getWindow().location.replace(baseUrl() + url);
};

/**
 * Jenkins AJAX GET callback
 */
exports.get = function(url, success) {
	var $ = jquery.getJQuery();
	$.ajax({
		url: baseUrl() + url,
		type: 'GET',
		success: success
	});
};

/**
 * Jenkins AJAX POST callback, formats data as a JSON object post (note: works around prototype.js ugliness using stringify() above)
 */
exports.post = function(url, data, success) {
	var $ = jquery.getJQuery();
    $.ajax({
		url: baseUrl() + url,
		type: "POST",
	    data: stringify(data),
	    contentType: "application/json",
		success: success
	});
};

/**
 *  handlebars setup, this does not seem to actually work or get called by the require() of this file, so have to explicitly call it
 */
exports.initHandlebars = function() {
	var Handlebars = require('handlebars');
	
	Handlebars.registerHelper('ifeq', function(o1, o2, options) {
		if(o1 == o2) { return options.fn(); }
	});

	Handlebars.registerHelper('ifneq', function(o1, o2, options) {
		if(o1 != o2) { return options.fn(); }
	});

	Handlebars.registerHelper('in-array', function(arr, val, options) {
		if(arr.indexOf(val) >= 0) { return options.fn(); }
	});

	Handlebars.registerHelper('id', function(str) {
		return (''+str).replace(/\W+/g, '_');
	});
	
	return Handlebars;
};
