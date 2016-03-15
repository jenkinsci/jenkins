var $ = require('jquery-detached').getJQuery();
var page = require('./util/page.js');
var windowHandle = require('window-handle');

$(function() {
    var tabBarWidget = require('./widgets/config/tabbar.js');

    tabBarWidget.addPageTabs('.config-table.scrollspy', function(tabBar) {
        tabBarWidget.addFinderToggle(tabBar);
        tabBar.onShowSection(function() {
            // Hook back into hudson-behavior.js
            page.fireBottomStickerAdjustEvent();
        });

        watchScroll(tabBar);
        $(windowHandle.getWindow()).on('scroll',function(){watchScroll(tabBar);});
    });
});

function watchScroll(tabControl) {
    var $window = $(windowHandle.getWindow());
    var $tabBox = tabControl.configWidgets;
    var $tabs = $tabBox.find('.tab');
    var $table = tabControl.configTable;
    var $jenkTools = $('#breadcrumbBar');
    var winScoll = $window.scrollTop();
    var categories = tabControl.sections;
    var jenkToolOffset = ($jenkTools.height() + $jenkTools.offset().top);

    // reset tabs to start...
    $tabs.find('.active').removeClass('active');

    function getCatTop($cat) {
        return ($cat.length > 0) ?
        $cat.offset().top - jenkToolOffset
            : 0;
    }

    // calculate the top and height of each section to know where to switch the tabs...
    $.each(categories, function (i, cat) {
        var $cat = $(cat.headerRow);
        var $nextCat = (i + 1 < categories.length) ?
            $(categories[i + 1].headerRow) :
            $cat;
        // each category enters the viewport at its distance down the page, less the height of the toolbar, which hangs down the page...
        // or it is zero if the category doesn't match or was removed...
        var catTop = getCatTop($cat);
        // height of this one is the top of the next, less the top of this one.
        var catHeight = getCatTop($nextCat) - catTop;

        // the trigger point to change the tab happens when the scroll position passes below the height of the category...
        // ...but we want to wait to advance the tab until the existing category is 75% off the top...
        if (winScoll < (catTop + (0.75 * catHeight))) {
            var $thisTab = $($tabs.get(i));
            var $nav = $thisTab.closest('.tabBar');
            $nav.find('.active').removeClass('active');
            $thisTab.addClass('active');
            return false;
        }
    });

    if (winScoll > $('#page-head').height() - 5) {
        $tabBox.width($tabBox.width()).css({
            'position': 'fixed',
            'top': ($jenkTools.height() - 5 ) + 'px'
        });
        $table.css({'margin-top': $tabBox.outerHeight() + 'px'});

    } else {
        $tabBox.add($table).removeAttr('style');
    }
}