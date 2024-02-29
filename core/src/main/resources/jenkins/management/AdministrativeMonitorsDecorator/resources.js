(function () {
  function initializeAmMonitor(amMonitorRoot, options) {
    var button = amMonitorRoot.querySelector(".am-button");
    var amList = amMonitorRoot.querySelector(".am-list");
    if (button === null || amList === null) {
      return null;
    }

    var url = button.getAttribute("data-href");

    function onClose(e) {
      var list = amList;
      var el = e.target;
      while (el) {
        if (el === list) {
          return; // clicked in the list
        }
        el = el.parentElement;
      }
      close();
    }

    function onEscClose(e) {
      var escapeKeyCode = 27;
      if (e.keyCode === escapeKeyCode) {
        close();
      }
    }

    function show() {
      if (options.closeAll) {
        options.closeAll();
      }

      fetch(url).then((rsp) => {
        if (rsp.ok) {
          rsp.text().then((responseText) => {
            var popupContent = responseText;
            amList.innerHTML = popupContent;
            amMonitorRoot.classList.add("visible");
            amMonitorRoot.classList.remove("am-hidden");
            document.addEventListener("click", onClose);
            document.addEventListener("keydown", onEscClose);

            // Applies all initialization code to the elements within the popup
            // Among other things, this sets the CSRF crumb to the forms within
            Behaviour.applySubtree(amList);
          });
        }
      });
    }

    function close() {
      if (amMonitorRoot.classList.contains("visible")) {
        amMonitorRoot.classList.add("am-hidden");
      }
      amMonitorRoot.classList.remove("visible");
      document.removeEventListener("click", onClose);
      document.removeEventListener("keydown", onEscClose);
    }

    function toggle(e) {
      if (amMonitorRoot.classList.contains("visible")) {
        close();
      } else {
        show();
      }
      e.preventDefault();
    }

    function startListeners() {
      button.addEventListener("click", toggle);
    }

    return {
      close: close,
      startListeners: startListeners,
    };
  }

  document.addEventListener("DOMContentLoaded", function () {
    var monitorWidgets;

    function closeAll() {
      monitorWidgets.forEach(function (widget) {
        widget.close();
      });
    }

    var normalMonitors = initializeAmMonitor(
      document.getElementById("visible-am-container"),
      {
        closeAll: closeAll,
      },
    );
    var securityMonitors = initializeAmMonitor(
      document.getElementById("visible-sec-am-container"),
      {
        closeAll: closeAll,
      },
    );
    monitorWidgets = [normalMonitors, securityMonitors].filter(
      function (widget) {
        return widget !== null;
      },
    );

    monitorWidgets.forEach(function (widget) {
      widget.startListeners();
    });
  });
})();

document.addEventListener("DOMContentLoaded", function () {
  var amContainer = document.getElementById("visible-am-container");
  var amInsertion = document.getElementById("visible-am-insertion");

  if (amInsertion) {
    amInsertion.appendChild(amContainer);
  }

  var secAmContainer = document.getElementById("visible-sec-am-container");
  var secAmInsertion = document.getElementById("visible-sec-am-insertion");

  if (secAmInsertion) {
    secAmInsertion.appendChild(secAmContainer);
  }
});
