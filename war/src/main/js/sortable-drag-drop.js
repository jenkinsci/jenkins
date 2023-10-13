/**
 * This module provides drag & drop functionality used by certain components,
 * such as f:repeatable or f:hetero-list.
 *
 * It does so using the SortableJS library.
 *
 * NOTE: there is another Sortable class exposed to the window namespace, this
 * corresponds to the sortable.js file that deals with table sorting.
 */

import Sortable, { AutoScroll } from "sortablejs/modular/sortable.core.esm.js";

Sortable.mount(new AutoScroll());

function registerSortableDragDrop(e) {
  if (!e || !e.classList.contains("with-drag-drop")) {
    return false;
  }

  new Sortable(e, {
    draggable: ".repeated-chunk",
    handle: ".dd-handle",
    ghostClass: "repeated-chunk--sortable-ghost",
    chosenClass: "repeated-chunk--sortable-chosen",
    forceFallback: true, // Do not use html5 drag & drop behaviour because it does not work with autoscroll
    scroll: true,
    bubbleScroll: true,
    onChoose: function (event) {
      const draggableDiv = event.item;
      const height = draggableDiv.clientHeight;
      draggableDiv.style.height = `${height}px`;
    },
    onUnchoose: function (event) {
      event.item.style.removeProperty("height");
    },
  });
}

export function registerSortableTableDragDrop(e, onChangeFunction) {
  if (!e || !e.classList.contains("with-drag-drop")) {
    return false;
  }

  Sortable.create(e, {
    handle: ".dd-handle",
    items: "tr",
    onChange: function (event) {
      if (onChangeFunction) {
        onChangeFunction(event);
      }
    },
  });
}

/*
 * Expose the function to register drag & drop components to the window objects
 * so that other widgets can use it (repeatable, hetero-list)
 */
window.registerSortableDragDrop = registerSortableDragDrop;
