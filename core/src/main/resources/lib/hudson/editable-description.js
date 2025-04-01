/* global replaceDescription */
(function () {
  document.addEventListener("DOMContentLoaded", function () {
    const descriptionLink = document.querySelector("#description-link");
    const description = document.getElementById("description");
    if (description != null) {
      descriptionLink.classList.remove("jenkins-hidden");
      descriptionLink.addEventListener("click", function (e) {
        e.preventDefault();
        descriptionLink.classList.add("jenkins-hidden");
        let url = descriptionLink.getAttribute("data-url");
        let description = descriptionLink.getAttribute("data-description");
        return replaceDescription(description, url);
      });
      // Adding a  new line whenever 'enter' key is detected.
      description.addEventListener("keydown", function (event) {
        if (event.key === "Enter") {
          event.preventDefault();
          let cursorPos = this.selectionStart;
          let textBefore = this.value.substring(0, cursorPos);
          let textAfter = this.value.substring(cursorPos);
          this.value = textBefore + "\n" + textAfter;
          this.selectionStart = this.selectionEnd = cursorPos + 1;
        }
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
