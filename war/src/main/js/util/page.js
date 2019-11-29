import $ from 'jquery';
import windowHandle from 'window-handle'

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
export var onload =  function(selector, callback, contextEl) {
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

export var winScrollTop = function() {
    var win = $(windowHandle.getWindow());
    return win.scrollTop();
};

export var onWinScroll = function(callback) {
    $(windowHandle.getWindow()).on('scroll', callback);
};

export var pageHeaderHeight = function() {
    return elementHeight('#page-head');
};

export var breadcrumbBarHeight = function() {
    return elementHeight('#breadcrumbBar');
};

export var fireBottomStickerAdjustEvent = function() {
    Event.fire(window, 'jenkins:bottom-sticker-adjust'); // jshint ignore:line
};

// YUI Drag widget does not like to work on elements with a relative position.
// This tells the element to switch to static position at the start of the drag, so it can work.
export var fixDragEvent = function(handle) {
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

export var removeTextHighlighting = function(selector) {
    $('span.highlight-split', selector).each(function() {
        var highlightSplit = $(this);
        highlightSplit.before(highlightSplit.text());
        highlightSplit.remove();
    });
};

function elementHeight(selector) {
    return $(selector).height();
}