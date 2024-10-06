/* global replaceDescription */
(function () {
  document.addEventListener("DOMContentLoaded", function () {
    let descriptionLink = document.querySelector("#description-link");
    let description = document.getElementById("description");
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
})();
