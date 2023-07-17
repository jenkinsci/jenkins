/* global replaceDescription */
(function () {
  document.addEventListener("DOMContentLoaded", function () {
    let descriptionLink = document.querySelector("#description-link");
    descriptionLink.addEventListener("click", function (e) {
      e.preventDefault();
      let url = descriptionLink.getAttribute("data-url");
      let description = descriptionLink.getAttribute("data-description");
      if (url == null && description == null) {
        return replaceDescription();
      } else {
        return replaceDescription(description, url);
      }
    });
  });
})();
