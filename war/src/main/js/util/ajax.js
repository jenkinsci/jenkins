var jQuery = require('jquery-detached');
var system = require('./system');

exports.execAsyncGET = function (url, params, onsuccess) {
    var $ = jQuery.getJQuery();

    var rootUrl = system.getRootUrl();
    $.ajax({
        url: (rootUrl + url),
        type: 'get',
        data: params,
        success: onsuccess
    });
};