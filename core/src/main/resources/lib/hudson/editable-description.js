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
    }
  });

  Behaviour.specify(".description-cancel-button", "description-cancel-button", 0, function (b) {
    b.onclick = function() {
      const descriptionLink = document.getElementById("description-link");
      const descriptionContent = document.getElementById("description-content");
      const descriptionEditForm = document.getElementById("description-edit-form");
      descriptionEditForm.innerHTML = "";
      descriptionEditForm.classList.add("jenkins-hidden");
      descriptionContent.classList.remove("jenkins-hidden");
      descriptionLink.classList.remove("jenkins-hidden");
    };
  });
})();
