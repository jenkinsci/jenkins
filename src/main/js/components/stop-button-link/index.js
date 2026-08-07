import behaviorShim from "@/util/behavior-shim";

function registerStopButton(link) {
  let question = link.getAttribute("data-confirm");
  let url = link.getAttribute("href");
  link.addEventListener("click", function (e) {
    e.preventDefault();
    var execute = function () {
      fetch(url, {
        method: "post",
        headers: crumb.wrap({}),
      });
    };
    if (question != null) {
      dialog.confirm(question).then(() => {
        execute();
      });
    } else {
      execute();
    }
  });
}

function init() {
  behaviorShim.specify(
    ".stop-button-link",
    "stop-button-link",
    0,
    (element) => {
      registerStopButton(element);
    },
  );
}

export default { init };
