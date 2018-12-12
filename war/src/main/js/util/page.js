var jQD = require('jquery-detached');
var windowHandle = require('window-handle');
var timestamp = (new Date().getTime());
var loadedClass = 'jenkins-loaded-' + timestamp;

/**
 * Wait for the specified element to be added to the DOM.
 * <p>
 * A jQuery based alternative to Behaviour.specify. Grrrr.
 * @param selector The jQuery selector.
 * @param callback The callback to call after finding new elements. This
 * callback must return a boolean value of true if scanning is to continue. 
 * @param contextEl The jQuery selector context (optional).
 */
exports.onload = function(selector, callback, contextEl) {
    var $ = jQD.getJQuery();

    function registerRescan() {
        setTimeout(scan, 50);
    }
    function scan() {
        var elements = $(selector, contextEl).not(loadedClass);
        if (elements.size() > 0) {
            elements.addClass(loadedClass);
            if (callback(elements) === true) {
                registerRescan();
            }
        } else {
            registerRescan();
        }
    }
    scan();
};

exports.winScrollTop = function() {
    var $ = jQD.getJQuery();
    var win = $(windowHandle.getWindow());
    return win.scrollTop();
};

exports.onWinScroll = function(callback) {
    var $ = jQD.getJQuery();
    $(windowHandle.getWindow()).on('scroll', callback);
};

exports.pageHeaderHeight = function() {
    return elementHeight('#page-head');
};

exports.breadcrumbBarHeight = function() {
    return elementHeight('#breadcrumbBar');
};

exports.fireBottomStickerAdjustEvent = function() {
    Event.fire(window, 'jenkins:bottom-sticker-adjust'); // jshint ignore:line
};

// YUI Drag widget does not like to work on elements with a relative position.
// This tells the element to switch to static position at the start of the drag, so it can work.
exports.fixDragEvent = function(handle) {
    var $ = jQD.getJQuery();
    var isReady = false;
    var $handle = $(handle);
    var $chunk = $handle.closest('.repeated-chunk');
    $handle.add('#ygddfdiv')
	.mousedown(function(){
	    isReady = true;
	})
	.mousemove(function(){
	    if(isReady && !$chunk.hasClass('dragging')){
		$chunk.addClass('dragging');
	    }
	}).mouseup(function(){
	    isReady = false;
	    $chunk.removeClass('dragging');
	});
};

exports.removeTextHighlighting = function(selector) {
    var $ = jQD.getJQuery();
    $('span.highlight-split', selector).each(function() {
        var highlightSplit = $(this);
        highlightSplit.before(highlightSplit.text());
        highlightSplit.remove();
    });
};

function elementHeight(selector) {
    var $ = jQD.getJQuery();
    return $(selector).height();
}