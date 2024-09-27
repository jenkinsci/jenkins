Behaviour.specify(
  "INPUT.apply-button,BUTTON.apply-button",
  "apply",
  0,
  function (e) {
    e.addEventListener("click", function (e) {
      const f = e.target.closest("FORM");

      // create a throw-away IFRAME to avoid back button from loading the POST result back
      const id = "iframe" + iota++;
      const target = document.createElement("iframe");
      target.setAttribute("id", id);
      target.setAttribute("name", id);
      target.style.height = "100%";
      target.style.width = "100%";
      document.querySelector("body").appendChild(target);

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

      target.addEventListener("load", () => {
        if (
          target.contentWindow &&
          target.contentWindow.applyCompletionHandler
        ) {
          // apply-aware server is expected to set this handler
          target.contentWindow.applyCompletionHandler(window);
          // Remove the iframe from the DOM
          target.remove();
          return;
        }

        // otherwise this is possibly an error from the server, so we need to render the whole content.
        const doc = target.contentDocument || target.contentWindow.document;
        let error = doc.getElementById("error-description");

        if (!error) {
          // Fallback if it's not a regular error dialog from oops.jelly: use the entire body
          error = document.createElement("div");
          error.appendChild(doc.querySelector("#page-body"));
        }

        dialog.modal(error, {
          minWidth: "850px",
        });

        // Remove the iframe from the DOM
        target.remove();
      });
    });
  },
);
