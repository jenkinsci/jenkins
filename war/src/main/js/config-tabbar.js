var $ = require('jquery-detached').getJQuery();
var page = require('./util/page.js');
var jenkinsLocalStorage = require('./util/jenkinsLocalStorage.js');

$(function() {
    var tabBarWidget = require('./widgets/config/tabbar.js');

    tabBarWidget.addPageTabs('.config-table.tabbed', function(tabBar) {
        // We want to merge some sections together.
        // Merge the "Advanced" section into the "General" section.
        var generalSection = tabBar.getSection('config_general');
        if (generalSection) {
            generalSection.adoptSection('config_advanced_project_options');
        }

        tabBarWidget.addFinderToggle(tabBar);
        tabBar.onShowSection(function() {
            // Hook back into hudson-behavior.js
            page.fireBottomStickerAdjustEvent();
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
});