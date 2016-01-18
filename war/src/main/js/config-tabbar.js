var $ = require('jquery-detached').getJQuery();

$(function() {
    // Horrible ugly hack...
    // We need to use Behaviour.js to wait until after radioBlock.js Behaviour.js rules
    // have been applied, otherwise row-set rows become visible across sections.
    var done = false;            
    Behaviour.specify(".block-control", 'row-set-block-control', 1000, function() {
        if (done) {
            return;
        }
        done = true;

        // Only do job configs for now.
        var configTables = $('.job-config.tabbed');
        if (configTables.size() > 0) {
            var tabBarWidget = require('./widgets/config/tabbar.js');
            
            configTables.each(function() {
                tabBarWidget.addTabs($(this));
            });
        }    
    });
});
