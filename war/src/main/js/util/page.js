import $ from 'jquery';
import { getWindow } from 'window-handle';

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
function onload(selector, callback, contextEl) {
    function registerRescan() {
        setTimeout(scan, 50);
    }
    function scan() {
        var elements = $(selector, contextEl).not(loadedClass);
        if (elements.length > 0) {
            elements.addClass(loadedClass);
            if (callback(elements) === true) {
                registerRescan();
            }
        } else {
            registerRescan();
        }
    }
    scan();
}

function winScrollTop() {
    var win = $(getWindow());
    return win.scrollTop();
}

function onWinScroll(callback) {
    $(getWindow()).on('scroll', callback);
}

function pageHeaderHeight() {
    return elementHeight('#page-head');
}

function breadcrumbBarHeight() {
    return elementHeight('#breadcrumbBar');
}

function fireBottomStickerAdjustEvent() {
    Event.fire(window, 'jenkins:bottom-sticker-adjust'); // jshint ignore:line
}

// YUI Drag widget does not like to work on elements with a relative position.
// This tells the element to switch to static position at the start of the drag, so it can work.
function fixDragEvent(handle) {
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
}

function removeTextHighlighting(selector) {
    $('span.highlight-split', selector).each(function() {
        var highlightSplit = $(this);
        highlightSplit.before(highlightSplit.text());
        highlightSplit.remove();
    });
}

function elementHeight(selector) {
    return $(selector).height();
}

export default {
    onload,
    winScrollTop,
    onWinScroll,
    pageHeaderHeight,
    breadcrumbBarHeight,
    fireBottomStickerAdjustEvent,
    fixDragEvent,
    removeTextHighlighting
}
