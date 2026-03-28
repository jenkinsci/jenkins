/* global replaceDescription */
(function () {
  document.addEventListener("DOMContentLoaded", function () {
    const descriptionLink = document.querySelector("#description-link");
    const description = document.getElementById("description");
    if (description != null && descriptionLink != null) {
      descriptionLink.classList.remove("jenkins-hidden");
      descriptionLink.addEventListener("click", function (e) {
        e.preventDefault();

        let url = descriptionLink.getAttribute("data-url");
        let initialDescription =
          descriptionLink.getAttribute("data-description");
        let title = descriptionLink.getAttribute("data-title");

        let parameters = {};
        if (initialDescription !== null && initialDescription !== "") {
          parameters["description"] = initialDescription;
        }
        if (url !== null && url !== "") {
          parameters["submissionUrl"] = url;
        }

        fetch("./descriptionForm", {
          method: "post",
          headers: crumb.wrap({
            "Content-Type": "application/x-www-form-urlencoded",
          }),
          body: new URLSearchParams(parameters),
        }).then((rsp) => {
          rsp.text().then((responseText) => {
            const template = document.createElement("template");
            template.innerHTML = responseText;
            const form = template.content.querySelector("form");

            // Remove the legacy buttons row as the dialog will provide its own
            const buttonsRow = form.querySelector(".jenkins-buttons-row");
            if (buttonsRow) {
              buttonsRow.remove();
            }

            window.dialog
              .form(form, {
                title: title,
                okText: window.dialog.translations.save,
              })
              .then((formData) => {
                fetch(form.action, {
                  method: "post",
                  body: formData,
                  headers: crumb.wrap({}),
                }).then((rsp) => {
                  if (rsp.ok) {
                    window.location.reload();
                  }
                });
              });
          });
        });
      });
    }
  });

  Behaviour.specify(
    ".description-cancel-button",
    "description-cancel-button",
    0,
    function (b) {
      b.onclick = function () {
        const descriptionLink = document.getElementById("description-link");
        const descriptionContent = document.getElementById(
          "description-content",
        );
        const descriptionEditForm = document.getElementById(
          "description-edit-form",
        );
        descriptionEditForm.innerHTML = "";
        descriptionEditForm.classList.add("jenkins-hidden");
        descriptionContent.classList.remove("jenkins-hidden");
        descriptionLink.classList.remove("jenkins-hidden");
      };
    },
  );
})();
