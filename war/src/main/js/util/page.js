import $ from "jquery";
import { getWindow } from "window-handle";

var timestamp = new Date().getTime();
var loadedClass = "jenkins-loaded-" + timestamp;

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
  $(getWindow()).on("scroll", callback);
}

function pageHeaderHeight() {
  return (
    document.querySelector("#page-header").offsetHeight + breadcrumbBarHeight()
  );
}

function breadcrumbBarHeight() {
  return document.querySelector("#breadcrumbBar").offsetHeight;
}

function removeTextHighlighting(selector) {
  $("span.highlight-split", selector).each(function () {
    var highlightSplit = $(this);
    highlightSplit.before(highlightSplit.text());
    highlightSplit.remove();
  });
}

export default {
  onload,
  winScrollTop,
  onWinScroll,
  pageHeaderHeight,
  breadcrumbBarHeight,
  removeTextHighlighting,
};
