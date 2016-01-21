/*
 * Some internal jQuery extensions.
 */

var jQD = require('jquery-detached');
var $ext;

exports.getJQuery = function() {
    if (!$ext) {
        initJQueryExt();
    }
    return $ext;
};

/*
 * Clear the $ext instance if hte window changes. Primarily for unit testing.
 */
var windowHandle = require('window-handle');
windowHandle.getWindow(function() {
    $ext = undefined;
});

function initJQueryExt() {
// We are going to be adding "stuff" to jQuery. We create a totally new jQuery instance
// because we do NOT want to run the risk of polluting the shared instance.
    $ext = jQD.newJQuery();

    /**
     * A pseudo selector that performs a case insensitive text contains search i.e. the same
     * as the standard ':contains' selector, but case insensitive.
     */
    $ext.expr[":"].containsci = $ext.expr.createPseudo(function (text) {
        return function (element) {
            var elementText = $ext(element).text();
            var result = (elementText.toUpperCase().indexOf(text.toUpperCase()) !== -1);
            return result;
        };
    });
}
initJQueryExt();
