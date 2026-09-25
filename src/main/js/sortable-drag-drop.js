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

export function registerSortableDragDrop(e, onChangeFunction) {
  if (!e || !e.classList.contains("with-drag-drop")) {
    return false;
  }

  let initialX, currentItem;
  const maxRotation = 2; // Maximum rotation in degrees
  const maxDistance = 150; // Maximum distance for the full rotation effect

  function onPointerMove(evt) {
    if (!currentItem) {
      return;
    }

    const currentX = evt.clientX + window.scrollX;
    const distanceX = currentX - initialX - 20;

    // Calculate rotation angle based on the distance moved
    const rotation = Math.max(
      -maxRotation,
      Math.min(maxRotation, (distanceX / maxDistance) * maxRotation),
    );

    currentItem.style.rotate = `${rotation}deg`;
    currentItem.style.translate = distanceX * -0.75 + "px";
  }

  new Sortable(e, {
    animation: 200,
    draggable: ".repeated-chunk",
    handle: ".dd-handle",
    ghostClass: "repeated-chunk--sortable-ghost",
    chosenClass: "repeated-chunk--sortable-chosen",
    forceFallback: true, // Do not use html5 drag & drop behaviour because it does not work with autoscroll
    scroll: true,
    bubbleScroll: true,
    onStart: function (evt) {
      const rect = evt.item.getBoundingClientRect();
      initialX = rect.left + window.scrollX;
      currentItem = document.querySelector(".sortable-drag");
      document.addEventListener("pointermove", onPointerMove);
    },
    onEnd: function () {
      document.removeEventListener("pointermove", onPointerMove);
      if (currentItem) {
        currentItem.style.rotate = "";
        currentItem = null;
      }
    },
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
