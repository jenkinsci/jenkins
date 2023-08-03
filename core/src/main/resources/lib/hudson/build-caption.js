(function () {
  function updateBuildCaptionIcon() {
    fetch("statusIcon").then((rsp) => {
      var isBuilding = rsp.headers.get("X-Building");
      if (isBuilding === "true") {
        setTimeout(updateBuildCaptionIcon, 5000);
      } else {
        var progressBar = document.querySelector(
          ".build-caption-progress-container",
        );
        if (progressBar) {
          progressBar.style.display = "none";
        }
      }
      rsp.text().then((responseText) => {
        document.querySelector(".jenkins-build-caption .icon-xlg").outerHTML =
          responseText;
      });
    });
  }

  window.addEventListener("load", function () {
    window.addEventListener("jenkins:consoleFinished", updateBuildCaptionIcon);
  });
})();
