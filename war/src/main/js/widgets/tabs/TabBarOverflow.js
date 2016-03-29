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
    this.activeTabIndex = undefined;
    this.visibleStartIndex = undefined;
    this.visibleEndIndex = undefined;
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
    win.on('resize.jenkins.taboverflow', function() {
        tabOverflow.doRefresh();
    });

    function trackOverflow() {
        try {
            if (tabOverflow.isOverflown()) {
                tabOverflow.doRefresh();
            }
        } finally {
            setTimeout(trackOverflow, 200);
        }
    }
    trackOverflow();
}

TabBarOverflow.prototype.doRefresh = function() {
    // If this is a refresh, then just slide backward to the first visible
    // tab i.e. fill forward from it.
    this.slideBackward(this.visibleStartIndex);
    this.setTabCount();
    this.toggleOverflowBtn();
};

TabBarOverflow.prototype.setTabCount = function() {
    var showableTabCount = this.showableTabCount();
    if (showableTabCount > 9) {
        this.taboverflowCount.text('9+');
    } else {
        this.taboverflowCount.text(showableTabCount);
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
        if (tabOverflow.isTabShowable(tab)) {
            var dropTab = $('<div class="drop-tab">');
            dropTab.text(tab.text());
            tabOverflow.taboverflowPopup.append(dropTab);
            dropTab.click(function() {
                tabOverflow.hideDropdown();
                tab.click();
            });
        }
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

TabBarOverflow.prototype.isTabShowable = function(tab) {
    return (this.tabVisibleChecker === undefined || this.tabVisibleChecker(tab));
};

TabBarOverflow.prototype.activeTab = function() {
    if (this.activeTabIndex !== undefined) {
        return this.tabs[this.activeTabIndex];
    } else {
        return undefined;
    }
};

TabBarOverflow.prototype.tabIndex = function(tab) {
    for (var i = 0; i < this.tabs.length; i++) {
        if (this.tabs[i] === tab) {
            return i;
        }
    }
    throw 'Unknown tab.';
};

TabBarOverflow.prototype.hideAllTabs = function() {
    var $ = jQD.getJQuery();
    for (var i = 0; i < this.tabs.length; i++) {
        $(this.tabs[i]).removeClass('taboverflow-tab-visible');
    }
};

TabBarOverflow.prototype.visibleTabCount = function() {
    var $ = jQD.getJQuery();
    return $('.taboverflow-tab-visible', this.tabBar).size();
};

TabBarOverflow.prototype.showableTabCount = function() {
    var count = 0;
    for (var i = 0; i < this.tabs.length; i++) {
        if (this.isTabShowable(this.tabs[i])) {
            count++;
        }
    }
    return count;
};

TabBarOverflow.prototype.toggleOverflowBtn = function() {
    if (this.visibleTabCount() < this.showableTabCount()) {
        this.taboverflowBtn.show();
    } else {
        this.taboverflowBtn.hide();
    }
};

TabBarOverflow.prototype.fillForward = function(fromTabIndex) {
    var $ = jQD.getJQuery();
    for (var i = fromTabIndex; i < this.tabs.length; i++) {
        var tab = this.tabs[i];

        if (!this.isTabShowable(tab)) {
            continue;
        }

        $(tab).addClass('taboverflow-tab-visible');
        if (this.isOverflown()) {
            $(tab).removeClass('taboverflow-tab-visible');
            return true;
        }
        this.visibleEndIndex = i;
    }
    return false;
};

TabBarOverflow.prototype.slideForward = function(toTabIndex) {
    // Hide them all..
    this.hideAllTabs();

    // Move the visible tab window such that the last (end) visible tab
    // is the toTabIndex. Then, fill backward from that tab.
    this.visibleEndIndex = toTabIndex;
    this.fillBackward(toTabIndex);
};

TabBarOverflow.prototype.fillBackward = function(fromTabIndex) {
    for (var i = fromTabIndex; i >= 0 ; i--) {
        var tab = this.tabs[i];

        if (!this.isTabShowable(tab)) {
            continue;
        }

        $(tab).addClass('taboverflow-tab-visible');
        if (this.isOverflown()) {
            $(tab).removeClass('taboverflow-tab-visible');
            return true;
        }
        this.visibleStartIndex = i;
    }
    return false;
};

TabBarOverflow.prototype.slideBackward = function(toTabIndex) {
    // Hide them all..
    this.hideAllTabs();

    // Move the visible tab window such that the first (start) visible tab
    // is the toTabIndex. Then, fill forward from that tab.
    this.visibleStartIndex = toTabIndex;
    this.fillForward(toTabIndex);
};

TabBarOverflow.prototype.isActiveTabVisible = function() {
    return (this.activeTabIndex >= this.visibleStartIndex && this.activeTabIndex <= this.visibleEndIndex);
};

TabBarOverflow.prototype.onTabActivate = function(activatedTab) {
    var lastActiveTab = this.activeTabIndex;
    this.activeTabIndex = this.tabIndex(activatedTab);

    if (this.isActiveTabVisible()) {
        // Nothing to do. The tab is already visible.
        return;
    }

    if (this.activeTabIndex === 0 || lastActiveTab === undefined) {
        // First time activating a tab. Just "slide back" to that tab i.e. show it
        // and all tabs "forward" of it.
        this.slideBackward(this.activeTabIndex);
    } else {
        // We need to decide if we want to "slide forward" or "slide backward".
        // We do that based on what the last active tab was and if it was to the
        // left (backward) or to the right (forward) of the newly activated tab.

        if (this.activeTabIndex < lastActiveTab) {
            // The newly activated tab it to the left (backward).
            this.slideBackward(this.activeTabIndex);
        } else if (this.activeTabIndex > lastActiveTab) {
            this.slideForward(this.activeTabIndex);
        } else {
            // Do nothing
        }
    }
};