Behaviour.specify(
  "#scheduleRestartCheckbox",
  "scheduleRestartCheckbox",
  0,
  function (el) {
    el.addEventListener("change", function () {
      var form = document.getElementById("scheduleRestart");
      form.action = el.checked ? "safeRestart" : "cancelRestart";
      crumb.appendToForm(form);
      form.submit();
    });
  },
);

function refresh() {
  window.setTimeout(function () {
    fetch("./body", {
      method: "post",
      headers: crumb.wrap({}),
    }).then((rsp) => {
      if (rsp.ok) {
        rsp.text().then((responseText) => {
          var div = document.createElement("div");
          div.innerHTML = responseText;

          var rows = div.children[0].rows;
          for (var i = 0; i < rows.length; i++) {
            var row = rows[i];
            var target = document.getElementById(row.id);
            if (target == null) {
              document.getElementById("log").appendChild(row);
            } else {
              var tcell = target.cells[1];
              var scell = row.cells[1];
              if (scell.id !== tcell.id) {
                tcell.innerHTML = scell.innerHTML;
                tcell.id = scell.id;
              }
            }
          }
          var scheduleDiv = document.getElementById("scheduleRestartBlock");
          scheduleDiv.innerHTML = div.lastElementChild.innerHTML;
          // we need to call applySubtree for parentNode so that click listeners for "Details"
          // button in Failure/status.jelly are added as buttons get rendered
          Behaviour.applySubtree(scheduleDiv.parentNode);
          refresh();
        });
      }
    });
  }, 5000);
}

window.scrollTo(0, 10000);
refresh();
