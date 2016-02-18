var jQD = require('jquery-detached');
var tableMetadata = require('./model/ConfigTableMetaData.js');
debugger;
exports.addTabsOnFirst = function() {
    return exports.addTabs(tableMetadata.findConfigTables().first());
};

exports.addTabs = function(configTable) {
    debugger;
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
            configTableMetadata = tableMetadata.fromConfigTable(configTableEl);
        }
    } else {
        // It's a config <table> element
        configTableMetadata = tableMetadata.fromConfigTable(configTable);
    }

    var tabBar = $('<div class="tabBar config-section-activators"></div>');
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
    var noTabs = $('<div class="noTabs" title="Remove configuration tabs and revert to the &quot;classic&quot; configuration view">Remove tabs</div>');

    configTableMetadata.configWidgets.append(tabs);
    configTableMetadata.configWidgets.prepend(noTabs);
    tabs.append(tabBar);

    tabs.mouseenter(function() {
        tabs.addClass('mouse-over');
    });
    tabs.mouseleave(function() {
        tabs.removeClass('mouse-over');
    });
    configTableMetadata.deactivator = noTabs;

    // Always activate the first section by default. 
    configTableMetadata.activateFirstSection();

    return configTableMetadata;
};

exports.addTabsActivator = function(configTable) {

    var $ = jQD.getJQuery();
    var configWidgets = $('<div class="jenkins-config-widgets"><div class="showTabs" title="Add configuration section tabs">Add tabs</div></div>');
    configWidgets.insertBefore(configTable.parent());
    return configWidgets;
};
