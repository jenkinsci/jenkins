var windowHandle = require('window-handle');
var storage = require('./localStorage.js');

/** 
 * Store a Jenkins globally scoped value.
 */
exports.setGlobalItem = function(name, value) {
    storage.setItem('jenkins:' + name, value);
};

/** 
 * Get a Jenkins globally scoped value.
 */
exports.getGlobalItem = function(name, defaultVal) {
    return storage.getItem('jenkins:' + name, defaultVal);
};

/** 
 * Store a Jenkins page scoped value.
 */
exports.setPageItem = function(name, value) {
    name = 'jenkins:' + name + ':' + windowHandle.getWindow().location.href;
    storage.setItem(name, value);
};

/** 
 * Get a Jenkins page scoped value.
 */
exports.getPageItem = function(name, defaultVal) {
    name = 'jenkins:' + name + ':' + windowHandle.getWindow().location.href;
    return storage.getItem(name, defaultVal);
};