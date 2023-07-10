Behaviour.specify(
  ".build-button-column-icon-reference-holder",
  "build-button-column",
  0,
  function (e) {
    var url = e.getAttribute("data-url");
    var message = e.getAttribute("data-notification");
    var id = e.getAttribute("data-id");
    var icon = document.getElementById(id);

    icon.onclick = function () {
      fetch(url, {
        method: "post",
        headers: crumb.wrap({}),
      });
      hoverNotification(message, this, -100);
      return false;
    };
  },
);
