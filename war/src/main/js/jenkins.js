
// Initialise all modules by requiring them. Also makes sure they get bundled (see gulpfile.js).

var jquery = require('jquery-detached-2.1.4');
var $ = jquery.getJQuery();

$(document).ready(function() {
    require('./formsub').init();
    
    //common form elements from core...
    require('./section').init();
    
        
});


// Example of using bootstrap.
// NB: Don't use it here though, Gus!!!
var bootstrap = require('bootstrap-detached-3.3');
var $bs = bootstrap.getBootstrap();
console.log('Bootstrap version: ' + $bs.fn.tooltip.Constructor.VERSION);

// Example of using moment.
// NB: Don't use it here though, Gus!!!
var moment = require('moment');
console.log('Today is: ' + moment().format("MMM Do YY"));