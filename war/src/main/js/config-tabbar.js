var $ = require('jquery-detached').getJQuery();

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
            var localStorage = require('./util/localStorage.js');
            var tabBarShowPreferenceKey = 'jenkins:config:usetabs';
            var tabBarShowPreference = localStorage.getItem(tabBarShowPreferenceKey, "yes");

            var tabBarWidget = require('./widgets/config/tabbar.js');
            if (tabBarShowPreference === "yes") {
                configTables.each(function() {
                    var configTable = $(this);
                    var tabBar = tabBarWidget.addTabs(configTable);

                    addFinderToggle(tabBar);
                    tabBar.onShowSection(function() {
                        // Hook back into hudson-behavior.js
                        fireBottomStickerAdjustEvent();
                    });
                    tabBar.deactivator.click(function() {
                        localStorage.setItem(tabBarShowPreferenceKey, "no");
                        require('window-handle').getWindow().location.reload();
                    });
                    $('.jenkins-config-widgets .find-container input').focus(function() {
                        fireBottomStickerAdjustEvent();
                    });

                    if (tabBar.hasSections()) {
                        var tabBarLastSectionKey = 'jenkins:config:' + tabBar.configForm.attr('name') + ':last-tab:' + window.location.href;
                        var tabBarLastSection = localStorage.getItem(tabBarLastSectionKey, tabBar.sections[0].id);
                        tabBar.onShowSection(function() {
                            localStorage.setItem(tabBarLastSectionKey, this.id);
                        });
                        tabBar.showSection(tabBarLastSection);
                    }
                });
            } else {
                configTables.each(function() {
                    var configTable = $(this);
                    var activator = tabBarWidget.addTabsActivator(configTable);
                    require('./widgets/config/table-metadata.js').markConfigForm(configTable);
                    activator.click(function() {
                        localStorage.setItem(tabBarShowPreferenceKey, "yes");
                        require('window-handle').getWindow().location.reload();
                    });
                });
            }
        }
    });
});

function addFinderToggle(configTableMetadata) {
    var findToggle = $('<div class="find-toggle" title="Find"></div>');
    $('.tabBar', configTableMetadata.configWidgets).append(findToggle);
    findToggle.click(function() {
        var findContainer = $('.find-container', configTableMetadata.configWidgets);
        if (findContainer.hasClass('visible')) {
            findContainer.removeClass('visible');
        } else {
            findContainer.addClass('visible');
            $('input', findContainer).focus();
        }
    });
}

function fireBottomStickerAdjustEvent() {
    Event.fire(window, 'jenkins:bottom-sticker-adjust'); // jshint ignore:line
}
