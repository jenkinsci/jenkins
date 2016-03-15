var jQD = require('jquery-detached');
var page = require('../../util/page.js');
var jenkinsLocalStorage = require('../../util/jenkinsLocalStorage.js');
var tableMetadata = require('./model/ConfigTableMetaData.js');
var behaviorShim = require('../../util/behavior-shim');

exports.tabBarShowPreferenceKey = 'config:usetabs';

exports.addPageTabs = function(selector, forEachConfigTable) {
    var $ = jQD.getJQuery();

    $(function() {
        // Horrible ugly hack...
        // We need to use Behaviour.js to wait until after radioBlock.js Behaviour.js rules
        // have been applied, otherwise row-set rows become visible across sections.
        var done = false;

        behaviorShim.specify(".dd-handle", 'config-drag-start', 1000, function() {
            page.fixDragEvent();
        });

        behaviorShim.specify(".block-control", 'row-set-block-control', 1000, function() {
            if (done) {
                return;
            }
            done = true;

            // Only do job configs for now.
            var configTables = $(selector);
            if (configTables.size() > 0) {
                var tabBarShowPreference = jenkinsLocalStorage.getGlobalItem(exports.tabBarShowPreferenceKey, "yes");

                page.fixDragEvent(configTables);

                if (tabBarShowPreference === "yes") {
                    configTables.each(function() {
                        var configTable = $(this);
                        var tabBar = exports.addTabs(configTable);

                        forEachConfigTable.call(configTable, tabBar);

                        tabBar.deactivator.click(function() {
                            jenkinsLocalStorage.setGlobalItem(exports.tabBarShowPreferenceKey, "no");
                            require('window-handle').getWindow().location.reload();
                        });
                    });
                } else {
                    configTables.each(function() {
                        var configTable = $(this);
                        var activator = exports.addTabsActivator(configTable);
                        tableMetadata.markConfigTableParentForm(configTable);
                        activator.click(function() {
                            jenkinsLocalStorage.setGlobalItem(exports.tabBarShowPreferenceKey, "yes");
                            require('window-handle').getWindow().location.reload();
                        });
                    });
                }
            }
        });
    });
};

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


exports.addFinderToggle = function(configTableMetadata) {
    var $ = jQD.getJQuery();
    var findToggle = $('<div class="find-toggle" title="Find"></div>');
    var finderShowPreferenceKey = 'config:showfinder';

    findToggle.click(function() {
        var findContainer = $('.find-container', configTableMetadata.configWidgets);
        if (findContainer.hasClass('visible')) {
            findContainer.removeClass('visible');
            jenkinsLocalStorage.setGlobalItem(finderShowPreferenceKey, "no");
        } else {
            findContainer.addClass('visible');
            $('input', findContainer).focus();
            jenkinsLocalStorage.setGlobalItem(finderShowPreferenceKey, "yes");
        }
    });

    if (jenkinsLocalStorage.getGlobalItem(finderShowPreferenceKey, "yes") === 'yes') {
        findToggle.click();
    }
};