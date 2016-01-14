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
            configTableMetadata = tableMetadata.markConfigTable(configTableEl);
        }
    } else {
        // It's a config <table> element
        configTableMetadata = tableMetadata.markConfigTable(configTable);
    }
    
    var tabBar = $('<div class="tabBar"></div>');

    function newTab(section) {
        var tab = $('<div class="tab"></div>');

        tab.text(section.title);
        tab.addClass(section.id);
        tab.click(function() {
            $('.tab.active', tabBar).removeClass('active');
            tab.addClass('active');
            tableMetadata.filterRows(configTableMetadata.topRows, '.' + section.id);
        });

        section.tab = tab;

        return tab;
    }

    var section;
    for (var i = 0; i < configTableMetadata.length; i++) {
        section = configTableMetadata[i];
        var tab = newTab(section);
        tabBar.append(tab);
    }

    var tabs = $('<div class="form-config tabBarFrame"></div>');
    tabs.append(tabBar);
    tabs.insertBefore(configTableMetadata.configTable);

    if (activateTabId) {
        for (var ii = 0; ii < configTableMetadata.length; ii++) {
            section = configTableMetadata[ii];
            if (section.id === activateTabId) {
                configTableMetadata[ii].tab.click();
                return;
            }
        }
    }
    configTableMetadata[0].tab.click();
    
    return configTableMetadata;
};