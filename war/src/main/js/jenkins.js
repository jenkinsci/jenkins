
// Initialise all modules by requiring them. Also makes sure they get bundled (see gulpfile.js).

var jquery = require('jquery-detached-2.1.4');
var $ = jquery.getJQuery();

$(document).ready(function() {
    require('./formsub').init();
});