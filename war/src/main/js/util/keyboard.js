/**
 * @param {Element} container - the container for the items
 * @param {function(): NodeListOf<Element>} itemsFunc - function which returns the list of items
 * @param {string} selectedClass - the class to apply to the selected item
 */
export default function makeKeyboardNavigable(
  container,
  itemsFunc,
  selectedClass
) {
  window.addEventListener("keydown", (e) => {
    let items = itemsFunc();
    let selectedItem = Array.from(items).find((a) =>
      a.classList.contains(selectedClass)
    );
    const isVisible =
      window.getComputedStyle(container).visibility === "visible";

    // Only navigate through the list of items if the container is active on the screen
    if (container && isVisible) {
      if (e.key === "ArrowDown") {
        if (selectedItem) {
          selectedItem.classList.remove(selectedClass);
          const next = selectedItem.nextSibling;

          if (next) {
            selectedItem = next;
          } else {
            selectedItem = items[0];
          }
        } else {
          selectedItem = items[0];
        }

        selectedItem?.classList.add(selectedClass);
      } else if (e.key === "ArrowUp") {
        if (selectedItem) {
          selectedItem.classList.remove(selectedClass);
          const previous = selectedItem.previousSibling;

          if (previous) {
            selectedItem = previous;
          } else {
            selectedItem = items[items.length - 1];
          }
        } else {
          selectedItem = items[items.length - 1];
        }

        selectedItem?.classList.add(selectedClass);
      } else if (e.key === "Enter") {
        selectedItem?.click();
      }
    }
  });
}
