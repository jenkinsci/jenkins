var jQD = require('jquery-detached');

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
        console.log('remove');
        var highlightSplit = $(this);
        highlightSplit.before(highlightSplit.text());
        highlightSplit.remove();
    });
};