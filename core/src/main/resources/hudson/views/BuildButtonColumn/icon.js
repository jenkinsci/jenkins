Behaviour.specify(
  ".build-button-column-icon-reference-holder",
  "build-button-column",
  0,
  function (e) {
    var url = e.getAttribute("data-url");
    var message = e.getAttribute("data-notification");
    var failure = e.getAttribute("data-failure");
    var id = e.getAttribute("data-id");
    var icon = document.getElementById(id);

    icon.onclick = function () {
      fetch(url, {
        method: "post",
        headers: crumb.wrap({}),
      }).then((rsp) => {
        if (rsp.ok) {
          notificationBar.show(message,notificationBar.SUCCESS);
        } else {
          notificationBar.show(failure, notificationBar.ERROR)
        }
      });;
      return false;
    };
  },
);
