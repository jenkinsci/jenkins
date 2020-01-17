// mock the behaviors stuff.
var behaviorShim = require('../../../../main/js/util/behavior-shim');
behaviorShim.specify = function(selector, id, priority, behavior) {
    behavior();
};

// Mock out the fireBottomStickerAdjustEvent function ... it accesses Event.
var page = require('../../../../main/js/util/page');
page.fireBottomStickerAdjustEvent = function() {};

var windowHandle = require('window-handle');
windowHandle.getWindow(function() {
    var localStorage = require('../../../../main/js/util/localStorage');
    localStorage.setMock();
});
