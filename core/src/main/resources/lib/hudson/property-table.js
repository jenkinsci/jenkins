(function () {
  document.addEventListener("DOMContentLoaded", function () {
    document
      .querySelectorAll(".app-hidden-info-reveal .jenkins-button")
      .forEach(function (elem) {
        elem.addEventListener("click", function () {
          elem.parentElement.classList.add("jenkins-hidden");
          elem.parentElement.nextSibling.classList.remove("jenkins-hidden");
        });
      });
    document
      .querySelectorAll(".app-hidden-info-hide .jenkins-button")
      .forEach(function (elem) {
        elem.addEventListener("click", function () {
          const selection = window.getSelection();
          // Don't hide value while it's being selected
          if (selection === null || window.getSelection().type !== "Range") {
            elem.parentElement.classList.add("jenkins-hidden");
            elem.parentElement.previousSibling.classList.remove(
              "jenkins-hidden",
            );
          }
        });
      });

    document
      .querySelectorAll(".app-all-hidden-reveal-all")
      .forEach(function (elem) {
        elem.addEventListener("click", function () {
          elem.classList.add("jenkins-hidden");
          elem.nextSibling.classList.remove("jenkins-hidden");
          let tableId = elem.getAttribute("data-table-id");
          document
            .getElementById(tableId)
            .querySelectorAll(".app-hidden-info-reveal .jenkins-button")
            .forEach(function (elem) {
              elem.parentElement.classList.add("jenkins-hidden");
              elem.parentElement.nextSibling.classList.remove("jenkins-hidden");
            });
        });
      });

    document
      .querySelectorAll(".app-all-hidden-hide-all")
      .forEach(function (elem) {
        elem.addEventListener("click", function () {
          elem.classList.add("jenkins-hidden");
          elem.previousSibling.classList.remove("jenkins-hidden");
          let tableId = elem.getAttribute("data-table-id");
          document
            .getElementById(tableId)
            .querySelectorAll(".app-hidden-info-reveal .jenkins-button")
            .forEach(function (elem) {
              elem.parentElement.classList.remove("jenkins-hidden");
              elem.parentElement.nextSibling.classList.add("jenkins-hidden");
            });
        });
      });
  });
})();
