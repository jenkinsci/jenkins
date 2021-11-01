function submitScheduleForm(el) {
    var form = document.getElementById("scheduleRestart");
    form.action = el.checked ? "safeRestart" : "cancelRestart";
    crumb.appendToForm(form);
    form.submit();
}

function refresh() {
    window.setTimeout(function() {
        new Ajax.Request("./body", {
            onSuccess: function(rsp) {
                var div = document.createElement('div');
                div.innerHTML = rsp.responseText;

                var rows = div.children[0].rows;
                for(var i=0; i<rows.length; i++ ) {
                  var row = rows[i];
                  var target = document.getElementById(row.id);
                  if(target==null) {
                    document.getElementById("log").appendChild(row);
                  } else {
                    var tcell = target.cells[1];
                    var scell = row.cells[1];
                    if(scell.id!=tcell.id) {
                      tcell.innerHTML = scell.innerHTML;
                      tcell.id = scell.id;
                    }
                  }
                }
                var scheduleDiv = document.getElementById('scheduleRestartBlock');
                scheduleDiv.innerHTML = div.lastElementChild.innerHTML;
                refresh();
            }
        });
    }, 5000);
}
window.scrollTo(0,10000);
refresh();