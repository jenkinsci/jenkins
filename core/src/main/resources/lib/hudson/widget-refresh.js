/* global Behaviour, refreshPart */
/**
 * Use the `widget-refresh-reference` class on an element with `data-id` and `data-url` attributes.
 * The content from the URL will be used to replace the element with the specified ID.
 * Usually the URL content is an element with the same ID as specified here, to allow continuous updates.
 * This is primarily used for sidepanel widgets, but not exclusively.
 */
Behaviour.specify(
  ".widget-refresh-reference",
  "widget-refresh",
  0,
  function (e) {
    let id = e.getAttribute("data-id");
    let url = e.getAttribute("data-url");
    refreshPart(id, url);
  },
);
