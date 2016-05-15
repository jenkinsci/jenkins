/*
 * Page initialisation tasks.
 */

var $ = require('jquery-detached').getJQuery();
var jsModules = require('@jenkins-cd/js-modules');

$(function() {
    loadScripts();
    loadCSS();
});

function loadScripts() {
    $('.jenkins-js-load').each(function () {
        var scriptUrl = $(this).attr('data-src');
        if (scriptUrl) {
            // jsModules.addScript will ensure that the script is
            // loaded once and once only. So, this can be considered
            // analogous to a client-side adjunct.
            jsModules.addScript(scriptUrl);
            $(this).remove();
        }
    });
}

function loadCSS() {
    $('.jenkins-css-load').each(function () {
        var cssUrl = $(this).attr('data-src');
        if (cssUrl) {
            // jsModules.addCSSToPage will ensure that the CSS is
            // loaded once and once only. So, this can be considered
            // analogous to a client-side adjunct.
            jsModules.addCSSToPage(cssUrl);
            $(this).remove();
        }
    });
}
