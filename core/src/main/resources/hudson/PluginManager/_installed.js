(function () {
  const restartRequired = document
    .querySelector("#is-restart-required-for-completion")
    .getAttribute("data-is-restart-required");

  document.addEventListener("DOMContentLoaded", function () {
    document
      .querySelectorAll(".plugin-manager-toggle-switch")
      .forEach(function (toggle) {
        toggle.addEventListener("click", flip);
      });
  });

  function flip(o) {
    let btn = Event.element(o);

    // trigger
    fetch(
      btn.getAttribute("url") +
        "/make" +
        (btn.checked ? "Enabled" : "Disabled"),
      {
        method: "post",
        headers: crumb.wrap({}),
      }
    ).then((rsp) => {
      if (!rsp.ok) {
        rsp.text().then((responseText) => {
          $("needRestart").innerHTML = responseText;
        });
      }
      updateMsg(btn);
    });
  }

  function updateMsg() {
    // has anything changed since its original state?
    // eslint-disable-next-line no-undef
    let e = $A(Form.getInputs("plugins", "checkbox")).find(function (e) {
      return String(e.checked) !== e.getAttribute("original");
    });

    if (restartRequired === "true") {
      e = true;
    }

    $("needRestart").style.display = e != null ? "block" : "none";
  }

  updateMsg(); // set the initial state
})();
