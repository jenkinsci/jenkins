import $ from 'jquery';
import { getWindow } from 'window-handle';
import page from '../../util/page';
import tableMetadata from './model/ConfigTableMetaData';
import behaviorShim from '../../util/behavior-shim';
import jenkinsLocalStorage from '../../util/jenkinsLocalStorage';

/**
 * Extracting this call from outside of the addPageTabs due to a regression
 * in 2.216/2.217 (see JENKINS-61429)
 *
 * The proxied call to Behaviour.specify needs to be called from outside of the
 * addPageTabs function. Otherwise, it will not apply to existing draggable
 * elements on the form. It would only only apply to new elements.
 *
 * Extracting this Behaviour.specify call to the module level causes it to be executed
 * on script load, and this seems to set up the event listeners properly.
 */
behaviorShim.specify(".dd-handle", 'config-drag-start', 1000, function(el) {
    page.fixDragEvent(el);
});

export var tabBarShowPreferenceKey = 'config:usetabs';

export var addPageTabs = function(configSelector, onEachConfigTable, options) {
    $(function() {

        // We need to wait until after radioBlock.js Behaviour.js rules
        // have been applied, otherwise row-set rows become visible across sections.
        page.onload('.block-control', function() {
            // Only do job configs for now.
            var configTables = $(configSelector);
            if (configTables.length > 0) {
                var tabBarShowPreference = jenkinsLocalStorage.getGlobalItem(tabBarShowPreferenceKey, "yes");

                page.fixDragEvent(configTables);

                if (tabBarShowPreference === "yes") {
                    configTables.each(function() {
                        var configTable = $(this);
                        var tabBar = addTabs(configTable, options);

                        onEachConfigTable.call(configTable, tabBar);

                        tabBar.deactivator.click(function() {
                            jenkinsLocalStorage.setGlobalItem(tabBarShowPreferenceKey, "no");
                            getWindow().location.reload();
                        });
                    });
                } else {
                    configTables.each(function() {
                        var configTable = $(this);
                        var activator = addTabsActivator(configTable);
                        tableMetadata.markConfigTableParentForm(configTable);
                        activator.click(function() {
                            jenkinsLocalStorage.setGlobalItem(tabBarShowPreferenceKey, "yes");
                            getWindow().location.reload();
                        });
                    });
                }
            }
        }, configSelector);
    });
};

export var addTabsOnFirst = function() {
    return addTabs(tableMetadata.findConfigTables().first());
};

export var addTabs = function(configTable, options) {
    var configTableMetadata;
    var tabOptions = (options || {});
    var trackSectionVisibility = (tabOptions.trackSectionVisibility || false);

    if ($.isArray(configTable)) {
        // It's a config <table> metadata block
        configTableMetadata = configTable;
    } else if (typeof configTable === 'string') {
        // It's a config <table> selector
        var configTableEl = $(configTable);
        if (configTableEl.length === 0) {
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

    if (trackSectionVisibility === true) {
        configTableMetadata.trackSectionVisibility();
    }

    return configTableMetadata;
};

export var addTabsActivator = function(configTable) {
    var configWidgets = $('<div class="jenkins-config-widgets"><div class="showTabs" title="Add configuration section tabs">Add tabs</div></div>');
    configWidgets.insertBefore(configTable.parent());
    return configWidgets;
};


export var addFinderToggle = function(configTableMetadata) {
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
