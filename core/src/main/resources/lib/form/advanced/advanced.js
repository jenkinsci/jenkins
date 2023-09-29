Behaviour.specify(
  "INPUT.advanced-button,BUTTON.advanced-button",
  "advanced",
  0,
  function (e) {
    e.addEventListener("click", ({ currentTarget: button }) => {
      const parentContainer = button.parentNode;
      let expanded = button.dataset.expanded;

      if (expanded === undefined) {
        let hiddenContent = parentContainer.nextElementSibling;
        while (hiddenContent && !hiddenContent.matches("table.advancedBody")) {
          hiddenContent = hiddenContent.nextElementSibling;
        }
        let tr;

        if (hiddenContent) {
          hiddenContent = hiddenContent.firstElementChild; // TABLE -> TBODY
          tr = parentContainer.closest("TR");
        } else {
          hiddenContent = parentContainer.nextElementSibling;
          while (hiddenContent && !hiddenContent.matches("div.advancedBody")) {
            hiddenContent = hiddenContent.nextElementSibling;
          }
          tr = parentContainer.closest(".tr");
        }

        // move the contents of the advanced portion into the main table
        const nameRef = tr.getAttribute("nameref");
        while (hiddenContent.lastElementChild != null) {
          const row = hiddenContent.lastElementChild;
          // to handle inner rowSets, don't override existing values
          if (nameRef != null && row.getAttribute("nameref") == null) {
            row.setAttribute("nameref", nameRef);
          }
          tr.parentNode.insertBefore(row, tr.nextElementSibling);
        }

        const oneOrMoreFieldsEditedNotice = parentContainer.querySelector(
          ".jenkins-edited-section-label",
        );

        if (oneOrMoreFieldsEditedNotice.classList.contains("jenkins-hidden")) {
          // Show the label if the form contents change
          const inputs =
            parentContainer.parentNode.nextSibling.querySelectorAll("input");
          const form = document.createElement("form");
          inputs.forEach((node) => {
            form.appendChild(node.cloneNode(true));
          });
          const originalFormData = new FormData(form);

          inputs.forEach((input) => {
            input.addEventListener("input", () => {
              const updatedForm = document.createElement("form");
              inputs.forEach((node) => {
                updatedForm.appendChild(node.cloneNode(true));
              });
              const formData = new FormData(updatedForm);

              const result =
                JSON.stringify(Object.fromEntries(originalFormData)) ===
                JSON.stringify(Object.fromEntries(formData));
              oneOrMoreFieldsEditedNotice.classList.toggle(
                "jenkins-hidden",
                result,
              );
            });
          });
        }
      }

      const hiddenContent = parentContainer.parentNode.nextSibling;

      if (expanded === "true") {
        hiddenContent.style.display = "none";
        button.dataset.expanded = "false";
      } else {
        hiddenContent.style.display = "block";
        button.dataset.expanded = "true";
      }

      layoutUpdateCallback.call();
    });
    e = null; // avoid memory leak
  },
);

Behaviour.specify(
  ".advanced-customized-fields-info",
  "advanced",
  0,
  function (element) {
    const id = element.getAttribute("data-id");
    const oneOrMoreFieldsEditedNotice = document.getElementById(id);
    if (oneOrMoreFieldsEditedNotice != null) {
      oneOrMoreFieldsEditedNotice.classList.remove("jenkins-hidden");
    } else if (console && console.log) {
      const customizedFields = element.getAttribute("data-customized-fields");
      console.log("no element " + id + " for " + customizedFields);
    }
  },
);
