/**
 * Adds an executor to the built-in node from the dashboard empty state.
 *
 * Done in the background rather than by submitting a form, so that the page does not appear to reload without
 * anything indicating that the action took effect. On success a notification confirms what happened, the section
 * that no longer applies is collapsed and removed, and the build executor status widget is refreshed immediately
 * instead of at its next periodic refresh.
 */
Behaviour.specify(
  ".empty-state-add-executor",
  "empty-state-add-executor",
  0,
  function (element) {
    element.onclick = function () {
      fetch(element.getAttribute("href"), {
        method: "post",
        headers: crumb.wrap({}),
      }).then((rsp) => {
        if (!rsp.ok) {
          notificationBar.show(
            element.getAttribute("data-failure"),
            notificationBar.ERROR,
          );
          return;
        }

        notificationBar.show(
          element.getAttribute("data-notification"),
          notificationBar.SUCCESS,
        );

        // The widget still says that nothing is configured, so bring it up to date.
        const widget = document.querySelector(
          ".widget-refresh-reference[data-id='executors']",
        );
        if (widget !== null) {
          refreshPartNow(
            widget.getAttribute("data-id"),
            widget.getAttribute("data-url"),
          );
        }

        // Any build capacity means setting it up is done, so drop the whole section.
        const section = element.closest(".empty-state-section");
        if (section !== null) {
          collapseAndRemove(section);
        }
      });
      return false;
    };
  },
);

// Keep in sync with $empty-state-exit-animation in components/_content-blocks.scss.
const EXIT_ANIMATION_MS = 1000;

function collapseAndRemove(section) {
  let removed = false;
  const remove = function () {
    if (removed) {
      return;
    }
    removed = true;
    section.remove();
    layoutUpdateCallback.call();
  };

  // Only react to the section's own collapse. transitionend bubbles, and the links inside the section transition
  // their background and shadow on hover, so an unfiltered listener removes the section almost immediately.
  section.addEventListener("transitionend", (event) => {
    if (event.target === section && event.propertyName === "max-height") {
      remove();
    }
  });
  section.style.maxHeight = section.offsetHeight + "px";
  section.classList.add("empty-state-section--removing");
  // Force a reflow so that the starting height is in effect before it is changed. Without this the browser may
  // only ever see the final value and skip the transition, collapsing the section instantly.
  void section.offsetHeight;
  section.style.maxHeight = "0";
  // Not every environment delivers transitionend, for example when animations are disabled or in tests, so make
  // sure the section is removed either way.
  setTimeout(remove, EXIT_ANIMATION_MS * 2);
}
