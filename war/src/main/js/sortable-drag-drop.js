/**
 * This module provides drag & drop functionality used by certain components,
 * such as f:repeatable or f:hetero-list.
 *
 * It does so using the SortableJS library.
 *
 * NOTE: there is another Sortable class exposed to the window namespace, this
 * corresponds to the sortable.js file that deals with table sorting.
 */

import Sortable from 'sortablejs';

function registerSortableDragDrop(e) {
  if (!e || !e.classList.contains('with-drag-drop')) return false;

  const sortableElement = new Sortable(e, {
      draggable: '.repeated-chunk',
      handle: '.dd-handle',
      ghostClass: 'repeated-chunk--sortable-ghost',
      chosenClass: 'repeated-chunk--sortable-chosen',
      scroll: true,
      bubbleScroll: true,
  });
}

/*
 * Expose the function to register drag & drop components to the window objects
 * so that other widgets can use it (repeatable, hetero-list)
 */
window.registerSortableDragDrop = registerSortableDragDrop;
