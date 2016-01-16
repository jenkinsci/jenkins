var jQD = require('jquery-detached');
var tableMetadata = require('./table-metadata');

exports.addTabsOnFirst = function(activateTabId) {
    return exports.addTabs(tableMetadata.findConfigTables().first(), activateTabId);
};

exports.addTabs = function(configTable, activateTabId) {
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

    function newTab(section) {
        var tab = $('<div class="tab"></div>');

        tab.text(section.title);
        tab.addClass(section.id);
        tab.click(function() {
            $('.tab.active', tabBar).removeClass('active');
            tab.addClass('active');
            configTableMetadata.showRows('.' + section.id);
        });

        section.tab = tab;

        return tab;
    }

    var section;
    for (var i = 0; i < configTableMetadata.sections.length; i++) {
        section = configTableMetadata.sections[i];
        var tab = newTab(section);
        tabBar.append(tab);
    }

    var tabs = $('<div class="form-config tabBarFrame"></div>');
    tabs.append(tabBar);
    tabs.insertBefore(configTableMetadata.configTable);

    if (activateTabId) {
        for (var ii = 0; ii < configTableMetadata.sections.length; ii++) {
            section = configTableMetadata.sections[ii];
            if (section.id === activateTabId) {
                configTableMetadata.sections[ii].tab.click();
                return;
            }
        }
    }
    configTableMetadata.sections[0].tab.click();
    
    return configTableMetadata;
};