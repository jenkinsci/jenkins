var jQD = require('jquery-detached');
var windowHandle = require('window-handle');

module.exports = TabBarOverflow;

function TabBarOverflow(tabBarFrame, tabs) {
    var $ = jQD.getJQuery();

    this.tabBarFrame = tabBarFrame;
    this.tabBar = $('.tabBar', tabBarFrame);
    this.tabs = tabs;

    // Add the taboverflow button to the tabBar frame.
    // We will make this visible as needed.
    var taboverflowBtn =
        $('<div class="taboverflow-btn">' +
            '<span class="icon">&#x25BE;&#9776;</span>' +
          '</div>');
    var taboverflowCount = $('<span class="count"></span>');
    var taboverflowPopup = $('<div class="taboverflow-popup hidden"></div>');
    taboverflowBtn.append(taboverflowCount);
    tabBarFrame.append(taboverflowBtn);
    tabBarFrame.append(taboverflowPopup);

    this.taboverflowBtn = taboverflowBtn;
    this.taboverflowCount = taboverflowCount;
    this.taboverflowPopup = taboverflowPopup;
    this.setTabCount();

    // Show and hide the dropdown.
    var tabOverflow = this;
    taboverflowBtn.click(function() {
        if (taboverflowPopup.hasClass('hidden')) {
            tabOverflow.showDropdown();
        } else {
            tabOverflow.hideDropdown();
        }
    });

    var win = $(windowHandle.getWindow());
    win.resize(function() {
        if (tabOverflow.activeTab) {
            tabOverflow.onTabActivate(tabOverflow.activeTab, true);
        }
    });
}

TabBarOverflow.prototype.setTabCount = function() {
    if (this.tabs.length > 9) {
        this.taboverflowCount.text('9+');
    } else {
        this.taboverflowCount.text(this.tabs.length);
    }
};

TabBarOverflow.prototype.showDropdown = function() {
    this.setDropdownContent();
    this.taboverflowPopup.removeClass('hidden');
};

TabBarOverflow.prototype.hideDropdown = function() {
    this.taboverflowPopup.addClass('hidden');
};

TabBarOverflow.prototype.setDropdownContent = function() {
    var $ = jQD.getJQuery();
    var tabOverflow = this;

    tabOverflow.taboverflowPopup.empty();
    function addDropTab(tab) {
        var dropTab = $('<div class="drop-tab">');
        dropTab.text(tab.text());
        tabOverflow.taboverflowPopup.append(dropTab);
        dropTab.click(function() {
            tabOverflow.hideDropdown();
            tab.click();
        });
    }

    for (var i = 0; i < this.tabs.length; i++) {
        addDropTab(this.tabs[i]);
    }
};

TabBarOverflow.prototype.trackTabVisibility = function(tabVisibleChecker) {
    this.tabVisibleChecker = tabVisibleChecker;
};

TabBarOverflow.prototype.isOverflown = function() {
    return (this.tabBar.width() > this.tabBarFrame.width());
};

TabBarOverflow.prototype.onTabActivate = function(activatedTab, refresh) {
    // We need to check if this table is visible to the user and if not,
    // we need to make it visible.

    if (refresh === undefined || refresh === false) {
        if (!this.isOverflown() && activatedTab.hasClass('taboverflow-tab-visible')) {
            // This tab is already visible.
            return;
        }
    }

    var $ = jQD.getJQuery();

    // Ok, the tab that's just been activated is not visible to the user. So,
    // we make it (and local sibling tabs) visible by hiding other tabs before it.
    // We start by hiding all tabs. Then, we start showing tabs (starting from
    // the supplied tab). We stop showing tabs once we show a tab that overflows the tabBar.

    // Hide them all..
    for (var i = 0; i < this.tabs.length; i++) {
        $(this.tabs[i]).removeClass('taboverflow-tab-visible');
    }

    // Start showing tabs, starting from the tab that was just activated...
    var addTabs = false;
    var addedCount = 0;
    var activeTabIdx = 0;
    for (var i = 0; i < this.tabs.length; i++) {
        var tab = this.tabs[i];
        if (tab === activatedTab) {
            addTabs = true;
            activeTabIdx = i;
        }
        if (addTabs) {
            $(tab).addClass('taboverflow-tab-visible');
            if (this.isOverflown()) {
                $(tab).removeClass('taboverflow-tab-visible');
                break;
            }
            addedCount++;
        }
    }

    if (!this.isOverflown() && activeTabIdx > 0) {
        // There's room for more tabs. If there are tabs before the "active"
        // tab, then lets show some of those too.
        for (var i = activeTabIdx - 1; i >= 0 ; i--) {
            var tab = this.tabs[i];
            $(tab).addClass('taboverflow-tab-visible');
            if (this.isOverflown()) {
                $(tab).removeClass('taboverflow-tab-visible');
                break;
            }
            addedCount++;
        }
    }

    if (addedCount < this.tabs.length) {
        this.taboverflowBtn.show();
    } else {
        this.taboverflowBtn.hide();
    }

    this.activeTab = activatedTab;
};