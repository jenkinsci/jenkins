var jQuery = require('jquery-detached');

exports.mockAjax = function(mock) {
    var $ = jQuery.getJQuery();
    
    if(!mock) {
        mock = function () {
            // no-op
        };
    }    
    $.ajax = mock;
};