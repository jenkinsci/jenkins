/* global replaceDescription */
(function () {
  document.addEventListener("DOMContentLoaded", function () {
    const descriptionPane = document.querySelector("#description");
    const dataUrl = descriptionPane.getAttribute("data-url");
    const dataDescription = descriptionPane.getAttribute("data-description");

    Behaviour.specify("#" + descriptionPane.dataset.buttonSetter, "description-setter", 0, function (e) {
      e.addEventListener('click', () => {
        return replaceDescription(dataDescription, dataUrl);
      })
    });
  });
})();
