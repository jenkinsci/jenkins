/* global replaceDescription */
(function () {
  document.addEventListener("DOMContentLoaded", function () {
    const descriptionPane = document.querySelector("#description");
    const dataUrl = descriptionPane.dataset.url;
    const dataDescription = descriptionPane.dataset.description;
    let descriptionLink = document.getElementById(descriptionPane.dataset.buttonSetter);

    descriptionLink.addEventListener("click", function (e) {
      e.preventDefault();

      if (dataUrl == null && dataDescription == null) {
        return replaceDescription();
      } else {
        return replaceDescription(dataDescription, dataUrl);
      }
    });
  });
})();
