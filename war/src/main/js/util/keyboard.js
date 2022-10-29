export default function navigableList(container, items, selectedClass) {
  window.addEventListener("keydown", (e) => {
    let items2 = items();
    let liSelected = [...items2].find(a => a.classList.contains(selectedClass));
    const isVisible = window.getComputedStyle(container).visibility === 'visible';

    if (container && isVisible) {
      if (e.keyCode === 40) {
        if (liSelected) {
          liSelected.classList.remove(selectedClass);
          const next = liSelected.nextSibling;

          if (next) {
            liSelected = next;
          } else {
            liSelected = items2[0];
          }
        } else {
          liSelected = items2[0];
        }

        liSelected?.classList.add(selectedClass);
      } else if (e.keyCode === 38) {
        if (liSelected) {
          liSelected.classList.remove(selectedClass);
          const previous = liSelected.previousSibling;

          if (previous) {
            liSelected = previous;
          } else {
            liSelected = items2[items2.length - 1];
          }
        } else {
          liSelected = items2[items2.length - 1];
        }

        liSelected?.classList.add(selectedClass);
      }
    }
  });
}
