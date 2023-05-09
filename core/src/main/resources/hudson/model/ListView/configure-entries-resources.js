Behaviour.specify("#recurse", "ListView", 0, function (e) {
  var nestedElements = document.querySelectorAll("SPAN.nested");
  e.onclick = function () {
    nestedElements.forEach(function (el) {
      el.style.display = e.checked ? "" : "none";
    });
  };
});
