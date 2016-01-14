var $ = require('jquery-detached').getJQuery(); 

// Only do job configs for now.
var configTables = $('.job-config.tabbed');
if (configTables.size() > 0) {
    var tabbar = require('./widgets/config/tabbar.js');
    configTables.each(function() {
        tabbar.addTabs($(this));
    });
}
