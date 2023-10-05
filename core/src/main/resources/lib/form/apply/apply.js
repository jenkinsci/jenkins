Behaviour.specify(
  "INPUT.apply-button,BUTTON.apply-button",
  "apply",
  0,
  function (e) {
    var id;
    var containerId = "container" + iota++;

    var responseDialog = new YAHOO.widget.Panel("wait" + iota++, {
      fixedcenter: true,
      close: true,
      draggable: true,
      zindex: 4,
      modal: true,
      visible: false,
    });

    responseDialog.setHeader("Error");
    responseDialog.setBody("<div id='" + containerId + "'></div>");
    responseDialog.render(document.body);
    var target; // iframe

    function attachIframeOnload(target, f) {
      if (target.attachEvent) {
        target.attachEvent("onload", f);
      } else {
        target.onload = f;
      }
    }

    e.addEventListener("click", function (e) {
      var f = e.target.closest("FORM");

      // create a throw-away IFRAME to avoid back button from loading the POST result back
      id = "iframe" + iota++;
      target = document.createElement("iframe");
      target.setAttribute("id", id);
      target.setAttribute("name", id);
      target.style.height = "100%";
      target.style.width = "100%";
      document.getElementById(containerId).appendChild(target);

      attachIframeOnload(target, function () {
        if (
          target.contentWindow &&
          target.contentWindow.applyCompletionHandler
        ) {
          // apply-aware server is expected to set this handler
          target.contentWindow.applyCompletionHandler(window);
        } else {
          // otherwise this is possibly an error from the server, so we need to render the whole content.
          var doc = target.contentDocument || target.contentWindow.document;
          var error = doc.getElementById("error-description");
          var r = YAHOO.util.Dom.getClientRegion();
          var contentHeight = r.height / 5;
          var contentWidth = r.width / 2;
          if (!error) {
            // fallback if it's not a regular error dialog from oops.jelly: use the entire body
            error = document.createElement("div");
            error.setAttribute("id", "error-description");
            error.appendChild(doc.getElementsByTagName("body")[0]);
            contentHeight = (r.height * 3) / 4;
            contentWidth = (r.width * 3) / 4;
          }

          let oldError = document.getElementById("error-description");
          if (oldError) {
            // Remove old error if there is any
            document.getElementById(containerId).removeChild(oldError);
          }

          document.getElementById(containerId).appendChild(error);

          var dialogStyleHeight = contentHeight + 40;
          var dialogStyleWidth = contentWidth + 20;

          document.getElementById(containerId).style.height =
            contentHeight + "px";
          document.getElementById(containerId).style.width =
            contentWidth + "px";
          document.getElementById(containerId).style.overflow = "scroll";

          responseDialog.cfg.setProperty("width", dialogStyleWidth + "px");
          responseDialog.cfg.setProperty("height", dialogStyleHeight + "px");
          responseDialog.center();
          responseDialog.show();
        }
        window.setTimeout(function () {
          // otherwise Firefox will fail to leave the "connecting" state
          document.getElementById(id).remove();
        }, 0);
      });

      f.target = target.id;
      f.elements["core:apply"].value = "true";
      f.dispatchEvent(new Event("jenkins:apply")); // give everyone a chance to write back to DOM
      try {
        buildFormTree(f);
        f.submit();
      } finally {
        f.elements["core:apply"].value = null;
        f.target = "_self";
      }
    });
  },
);
