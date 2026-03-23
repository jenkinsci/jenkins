import behaviorShim from "@/util/behavior-shim";

function registerAjaxDialogLink(element) {
  element.addEventListener("click", function (e) {
    e.preventDefault();
    const href = element.getAttribute("href") || element.getAttribute("data-url");

    // Suggestion 2: Show loading cursor immediately
    document.body.style.cursor = "wait";

    fetch(href, {
      headers: {
        "X-Requested-With": "XMLHttpRequest",
      },
    })
      .then((response) => response.text())
      .then((html) => {
        const parser = new DOMParser();
        const doc = parser.parseFromString(html, "text/html");

        const mainPanel = doc.querySelector("#main-panel") || doc.querySelector(".jenkins-app-bar")?.nextElementSibling || doc.body;

        const content = document.createElement("div");

        // Suggestion 3: Wrap warnings in a scope to preserve CSS rules
        const warningsWrapper = document.createElement("div");
        warningsWrapper.className = "jenkins-dialog-warnings";
        const messages = mainPanel.querySelectorAll(".warning, .error");
        messages.forEach(msg => warningsWrapper.appendChild(msg.cloneNode(true)));
        if (warningsWrapper.childElementCount > 0) {
          content.appendChild(warningsWrapper);
        }

        let titleText = element.getAttribute("data-title") || element.getAttribute("title") || "Confirm";

        const form = mainPanel.querySelector("form");
        if (form) {
          const actionUrl = new URL(form.getAttribute("action"), new URL(href, window.location.origin)).href;
          const clonedForm = form.cloneNode(true);
          clonedForm.setAttribute("action", actionUrl);

          if (typeof crumb !== "undefined") {
            crumb.appendToForm(clonedForm);
          }

          const span = clonedForm.querySelector("span");
          if (span) {
            titleText = span.innerText;
            span.remove();
          }

          // Suggestion 4: Add explicit Cancel button next to the Delete button
          const submitBtn = clonedForm.querySelector("input[type='submit'], button[type='submit'], .jenkins-button");
          if (submitBtn) {
            const cancelBtn = document.createElement("button");
            cancelBtn.innerText = "Cancel";
            cancelBtn.type = "button";
            cancelBtn.className = "jenkins-button";
            cancelBtn.style.marginRight = "1rem";
            cancelBtn.addEventListener("click", (e) => {
              e.preventDefault();
              const d = cancelBtn.closest("dialog");
              if (d) {
                d.dispatchEvent(new Event("cancel"));
                if (typeof d.close === "function") d.close();
              }
            });
            // Insert Cancel before Submit
            submitBtn.parentNode.insertBefore(cancelBtn, submitBtn);
          }

          content.appendChild(clonedForm);
        } else {
          const fallbackMsg = document.createElement("p");
          fallbackMsg.innerText = "Are you sure you want to proceed?";
          content.appendChild(fallbackMsg);
        }

        window.dialog.modal(content, {
          maxWidth: "550px",
          title: titleText
        });
      })
      .catch((err) => {
        console.error("AJAX dialog fetch failed", err);
        window.location.href = href;
      })
      .finally(() => {
        // Clear loading cursor
        document.body.style.cursor = "";
      });

    return false;
  });
}

function init() {
  behaviorShim.specify(
    "A[data-ajax-dialog='true']",
    "ajax-dialog",
    0,
    (element) => {
      registerAjaxDialogLink(element);
    }
  );
}

export default { init };
