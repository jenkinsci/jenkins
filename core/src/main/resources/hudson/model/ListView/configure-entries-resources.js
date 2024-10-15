Behaviour.specify("#recurse", "ListView", 0, function (e) {
  // SPAN.nested is kept for backwards compatability
  var nestedElements = document.querySelectorAll(
    ".listview-jobs--nested, SPAN.nested",
  );
  e.onclick = function () {
    nestedElements.forEach(function (el) {
      el.style.display = e.checked ? "" : "none";
    });
  };
});
