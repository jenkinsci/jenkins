var jsTest = require("jenkins-js-test");

// mock the behaviors stuff.
// var behaviorShim = jsTest.requireSrcModule('util/behavior-shim');
var behaviorShim = require('../../../../main/js/util/behavior-shim');
behaviorShim.specify = function(selector, id, priority, behavior) {
    behavior();
};

// Mock out the fireBottomStickerAdjustEvent function ... it accesses Event.
// var page = jsTest.requireSrcModule('../../../../main/js/util/page');
var page = require('../../../../main/js/util/page');
page.fireBottomStickerAdjustEvent = function() {};

var windowHandle = require('window-handle');
windowHandle.getWindow(function() {
    // var localStorage = jsTest.requireSrcModule('../../../../main/js/util/localStorage');
    var localStorage = require('../../../../main/js/util/localStorage');
    localStorage.setMock();
});
