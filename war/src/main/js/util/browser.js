var system = require('./system');
var wh = require('window-handle');

exports.goTo = function(url) {
    var qualifiedUrl = system.getRootUrl() + url;
    wh.getWindow().location.replace(qualifiedUrl);
};