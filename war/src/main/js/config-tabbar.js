var $ = require('jquery-detached').getJQuery();
var jenkinsLocalStorage = require('./util/jenkinsLocalStorage.js');
var configMetadata = require('./widgets/config/model/ConfigTableMetaData.js');

$(function() {
    // Horrible ugly hack...
    // We need to use Behaviour.js to wait until after radioBlock.js Behaviour.js rules
    // have been applied, otherwise row-set rows become visible across sections.
    var done = false;
    Behaviour.specify(".block-control", 'row-set-block-control', 1000, function() { // jshint ignore:line
        if (done) {
            return;
        }
        done = true;
        // Only do job configs for now.
        var configTables = $('.job-config.tabbed');
        if (configTables.size() > 0) {
            var tabBarShowPreferenceKey = 'config:usetabs';
            var tabBarShowPreference = jenkinsLocalStorage.getGlobalItem(tabBarShowPreferenceKey, "yes");

            var tabBarWidget = require('./widgets/config/tabbar.js');
            if (tabBarShowPreference === "yes") {
                configTables.each(function() {
                    var configTable = $(this);
                    var tabBar = tabBarWidget.addTabs(configTable);

                    // We want to merge some sections together.
                    // Merge the "Advanced" section into the "General" section.
                    var generalSection = tabBar.getSection('config_general');
                    if (generalSection) {
                        generalSection.adoptSection('config_advanced_project_options');
                    }
                    
                    addFinderToggle(tabBar);
                    tabBar.onShowSection(function() {
                        // Hook back into hudson-behavior.js
                        fireBottomStickerAdjustEvent();
                    });
                    tabBar.deactivator.click(function() {
                        jenkinsLocalStorage.setGlobalItem(tabBarShowPreferenceKey, "no");
                        require('window-handle').getWindow().location.reload();
                    });
                    $('.jenkins-config-widgets .find-container input').focus(function() {
                        fireBottomStickerAdjustEvent();
                    });

                    if (tabBar.hasSections()) {
                        var tabBarLastSectionKey = 'config:' + tabBar.configForm.attr('name') + ':last-tab';
                        var tabBarLastSection = jenkinsLocalStorage.getPageItem(tabBarLastSectionKey, tabBar.sections[0].id);
                        tabBar.onShowSection(function() {
                            jenkinsLocalStorage.setPageItem(tabBarLastSectionKey, this.id);
                        });
                        tabBar.showSection(tabBarLastSection);
                    }
                });
            } else {
                configTables.each(function() {
                    var configTable = $(this);
                    var activator = tabBarWidget.addTabsActivator(configTable);
                    configMetadata.markConfigTableParentForm(configTable);
                    activator.click(function() {
                        jenkinsLocalStorage.setGlobalItem(tabBarShowPreferenceKey, "yes");
                        require('window-handle').getWindow().location.reload();
                    });
                });
            }
        }
    });
});

function addFinderToggle(configTableMetadata) {
    var findToggle = $('<div class="find-toggle" title="Find"></div>');
    var finderShowPreferenceKey = 'config:showfinder';
    
    $('.tabBar', configTableMetadata.configWidgets).append(findToggle);
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
}

function fireBottomStickerAdjustEvent() {
    Event.fire(window, 'jenkins:bottom-sticker-adjust'); // jshint ignore:line
}
