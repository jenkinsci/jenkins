var jQuery = require('jquery-detached');

exports.getRootUrl = function() {
    var $ = jQuery.getJQuery();
    return $('head').attr('data-rooturl');
};

exports.getResUrl = function() {
    var $ = jQuery.getJQuery();
    return $('head').attr('data-resurl');
};