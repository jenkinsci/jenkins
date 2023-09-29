Behaviour.specify(
  ".widget-refresh-reference",
  "widget-refresh",
  0,
  function (e) {
    var id = e.getAttribute("data-id");
    var url = e.getAttribute("data-url");
    refreshPart(id, url);
  },
);
