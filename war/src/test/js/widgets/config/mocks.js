var jsTest = require("@jenkins-cd/js-test");

// mock the behaviors stuff.
var behaviorShim = jsTest.requireSrcModule('util/behavior-shim');
behaviorShim.specify = function(selector, id, priority, behavior) {
    behavior();
};

// Mock out the fireBottomStickerAdjustEvent function ... it accesses Event.
var page = jsTest.requireSrcModule('util/page');
page.fireBottomStickerAdjustEvent = function() {};

var windowHandle = require('window-handle');
windowHandle.getWindow(function() {
    var localStorage = jsTest.requireSrcModule('util/localStorage');
    localStorage.setMock();
});
