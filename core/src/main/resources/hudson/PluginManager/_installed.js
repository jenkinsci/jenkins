(function () {
  const restartRequired = document
    .getElementById("is-restart-required-for-completion")
    .getAttribute("data-is-restart-required");

  document.addEventListener("DOMContentLoaded", function () {
    document
      .querySelectorAll(".plugin-manager-toggle-switch")
      .forEach(function (toggle) {
        toggle.addEventListener("click", flip);
      });
  });

  function flip(event) {
    let btn = event.target;

    // trigger
    fetch(
      btn.getAttribute("url") +
        "/make" +
        (btn.checked ? "Enabled" : "Disabled"),
      {
        method: "post",
        headers: crumb.wrap({}),
      },
    ).then((rsp) => {
      if (!rsp.ok) {
        rsp.text().then((responseText) => {
          document.getElementById("needRestart").innerHTML = responseText;
        });
      }
      updateMsg();
    });
  }

  function updateMsg() {
    // has anything changed since its original state?
    let e = Array.from(
      document.querySelectorAll("#plugins input[type='checkbox']"),
    ).find(function (e) {
      return String(e.checked) !== e.getAttribute("original");
    });

    if (restartRequired === "true") {
      e = true;
    }

    document.getElementById("needRestart").style.display =
      e != null ? "block" : "none";
  }

  updateMsg(); // set the initial state
})();
