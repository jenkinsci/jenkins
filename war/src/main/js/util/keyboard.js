/**
 * @param {Element} container - the container for the items
 * @param {function(): NodeListOf<Element>} itemsFunc - function which returns the list of items
 * @param {string} selectedClass - the class to apply to the selected item
 * @param {function()} additionalBehaviours - add additional keyboard shortcuts to the focused item
 * @param hasKeyboardPriority - set if custom behaviour is needed to decide whether the element has keyboard priority
 */
export default function makeKeyboardNavigable(
  container,
  itemsFunc,
  selectedClass,
  additionalBehaviours = () => {},
  hasKeyboardPriority = () =>
    window.getComputedStyle(container).visibility === "visible"
) {
  window.addEventListener("keydown", (e) => {
    let items = Array.from(itemsFunc());
    let selectedItem = items.find((a) => a.classList.contains(selectedClass));

    // Only navigate through the list of items if the container is active on the screen
    if (container && hasKeyboardPriority(container)) {
      if (e.key === "ArrowDown") {
        e.preventDefault();

        if (selectedItem) {
          selectedItem.classList.remove(selectedClass);
          const next = items[items.indexOf(selectedItem) + 1];

          if (next) {
            selectedItem = next;
          } else {
            selectedItem = items[0];
          }
        } else {
          selectedItem = items[0];
        }

        if (selectedItem !== null) {
          selectedItem.scrollIntoView(false);
          selectedItem.classList.add(selectedClass);
        }
      } else if (e.key === "ArrowUp") {
        e.preventDefault();

        if (selectedItem) {
          selectedItem.classList.remove(selectedClass);
          const previous = items[items.indexOf(selectedItem) - 1];

          if (previous) {
            selectedItem = previous;
          } else {
            selectedItem = items[items.length - 1];
          }
        } else {
          selectedItem = items[items.length - 1];
        }

        if (selectedItem !== null) {
          selectedItem.scrollIntoView(false);
          selectedItem.classList.add(selectedClass);
        }
      } else if (e.key === "Enter") {
        if (selectedItem !== null) {
          selectedItem.click();
        }
      } else {
        additionalBehaviours(selectedItem, e.key);
      }
    }
  });
}
