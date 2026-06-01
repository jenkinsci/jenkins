(function () {
  function editDescription(button) {
    const template = document.getElementById("edit-description-template");
    const title = button.dataset.title;
    const form = template.content.firstElementChild.cloneNode(true);
    const textarea = form.querySelector("#description-textarea");
    let actualDescription = template.dataset.description;
    if (actualDescription == null) {
      actualDescription = "";
    }
    textarea.value = actualDescription;
    form.addEventListener("keydown", function (e) {
      if (e.key === "Enter") {
        e.stopPropagation();
      }
    });

    dialog
      .form(form, {
        title: title,
        okText: dialog.translations.save,
        submitButton: false,
      })
      .then((formData) => {
        const description = formData.get("description");
        const url = button.dataset.url;
        fetch(url, {
          method: "POST",
          headers: crumb.wrap({
            "Content-Type": "application/x-www-form-urlencoded",
          }),
          body: new URLSearchParams({ description }),
        }).then((response) => {
          if (response.ok) {
            template.dataset.description = description;
            fetch(rootURL + "/markupFormatter/previewDescription", {
              method: "post",
              headers: crumb.wrap({
                "Content-Type": "application/x-www-form-urlencoded",
              }),
              body: new URLSearchParams({
                text: textarea.value,
              }),
            }).then((rsp) => {
              rsp.text().then((responseText) => {
                if (rsp.ok) {
                  const descriptionDiv = document.getElementById(
                    "description-content",
                  );
                  if (descriptionDiv != null) {
                    descriptionDiv.innerHTML = responseText;
                  }
                  let label = button.dataset.addLabel;
                  if (description !== null && description !== "") {
                    label = button.dataset.editLabel;
                  }
                  if (button.dataset.compact === "true") {
                    button.setAttribute("tooltip", label);
                    Behaviour.applySubtree(button, true);
                  } else {
                    button.querySelector("span").textContent = label;
                  }
                } else {
                  window.location.reload();
                }
              });
            });
          } else {
            notificationBar.show(
              "Failed to save description",
              notificationBar.WARNING,
            );
          }
        });
      });
  }

  document.addEventListener("DOMContentLoaded", function () {
    const descriptionLink = document.querySelector("#description-link");
    const description = document.getElementById("description");
    if (description != null) {
      descriptionLink.classList.remove("jenkins-hidden");
      descriptionLink.addEventListener("click", function (e) {
        e.preventDefault();
        editDescription(descriptionLink);
      });
    }
  });
})();
