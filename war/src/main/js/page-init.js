/*
 * Page initialisation tasks.
 */

var $ = require('jquery-detached').getJQuery();
var jsModules = require('jenkins-js-modules');

$(function() {
    loadScripts();
    loadCSS();
});

function loadScripts() {    
    $('.jenkins-js-load').each(function () {
        var scriptUrl = $(this).attr('data-src');
        if (scriptUrl) {
            // This will ensure that the script is loaded once and once only.
            jsModules.addScript(scriptUrl);
        }
    });
}

function loadCSS() {    
    $('.jenkins-css-load').each(function () {
        var cssUrl = $(this).attr('data-src');
        if (cssUrl) {
            // This will ensure that the CSS is loaded once and once only.
            jsModules.addCSSToPage(cssUrl);
        }
    });
}

