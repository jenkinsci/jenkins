var jQD = require('jquery-detached');
var windowHandle = require('window-handle');

module.exports = TabBarOverflow;

function TabBarOverflow(tabBarFrame) {
    var $ = jQD.getJQuery();

    this.tabBarFrame = tabBarFrame;
    this.tabBar = $('.tabBar', tabBarFrame);
    this.tabs = $('.tab', this.tabBar);

    // Add the taboverflow button to the tabBar frame.
    // We will make this visible as needed.
    var taboverflowBtn =
        $('<div class="taboverflow-btn">' +
            '<span class="icon">&#x25BE;&#9776;</span>' +
          '</div>');
    var taboverflowCount = $('<span class="count"></span>');
    var taboverflowPopup = $('<div class="taboverflow-popup"></div>');
    taboverflowBtn.append(taboverflowCount);
    tabBarFrame.append(taboverflowBtn);
    tabBarFrame.append(taboverflowPopup);

    taboverflowCount.text('9+');

    var win = $(windowHandle.getWindow());
    var tover = this;
    win.resize(function() {
        console.log('*** ' + tover.tabBar.width());
    });
}