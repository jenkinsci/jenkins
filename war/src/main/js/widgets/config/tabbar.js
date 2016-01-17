var jQD = require('jquery-detached');
var tableMetadata = require('./table-metadata');

exports.addTabsOnFirst = function() {
    return exports.addTabs(tableMetadata.findConfigTables().first());
};

exports.addTabs = function(configTable) {
    var $ = jQD.getJQuery();
    var configTableMetadata;
    
    if ($.isArray(configTable)) {
        // It's a config <table> metadata block
        configTableMetadata = configTable;
    } else if (typeof configTable === 'string') {
        // It's a config <table> selector
        var configTableEl = $(configTable);
        if (configTableEl.size() === 0) {
            throw "No config table found using selector '" + configTable + "'";
        } else {
            configTableMetadata = tableMetadata.decorateConfigTable(configTableEl);
        }
    } else {
        // It's a config <table> element
        configTableMetadata = tableMetadata.decorateConfigTable(configTable);
    }
    
    var tabBar = $('<div class="tabBar"></div>');
    configTableMetadata.activatorContainer = tabBar;

    function newTab(section) {
        var tab = $('<div class="tab config-section-activator"></div>');

        tab.text(section.title);
        tab.addClass(section.id);

        return tab;
    }

    var section;
    for (var i = 0; i < configTableMetadata.sections.length; i++) {
        section = configTableMetadata.sections[i];
        var tab = newTab(section);
        tabBar.append(tab);
        section.setActivator(tab);
    }

    var tabs = $('<div class="form-config tabBarFrame"></div>');
    tabs.append(tabBar);
    tabs.insertBefore(configTableMetadata.configTable);

    // Always activate the first section by default.
    configTableMetadata.activateFirstSection();
    
    return configTableMetadata;
};