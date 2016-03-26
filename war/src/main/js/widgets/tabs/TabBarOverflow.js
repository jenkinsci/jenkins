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

    this.taboverflowCount = taboverflowCount;
    this.taboverflowPopup = taboverflowPopup;
    this.setTabCount();
    this.setDropdownContent();

    // Show and hide the dropdown.
    taboverflowBtn.click(function() {
        if (taboverflowPopup.hasClass('hidden')) {
            taboverflowPopup.removeClass('hidden');
        } else {
            taboverflowPopup.addClass('hidden');
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

TabBarOverflow.prototype.setDropdownContent = function() {
    var $ = jQD.getJQuery();
    var tabOverflow = this;

    tabOverflow.taboverflowPopup.empty();
    function addDropTab(tab) {
        var dropTab = $('<div class="drop-tab">');
        dropTab.text(tab.text());
        tabOverflow.taboverflowPopup.append(dropTab);
        dropTab.click(function() {
            tabOverflow.taboverflowPopup.addClass('hidden');
            tab.click();
        });
    }

    for (var i = 0; i < this.tabs.length; i++) {
        addDropTab(this.tabs[i]);
    }
};

TabBarOverflow.prototype.trackTabVisibility = function() {
    // TODO
};