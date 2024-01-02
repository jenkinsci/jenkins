(function () {
  function updateBuildCaptionIcon() {
    const buildCaption = document.querySelector(".jenkins-build-caption");
    const url = buildCaption.dataset.statusUrl;
    fetch(url).then((rsp) => {
      if (rsp.ok) {
        let isBuilding = rsp.headers.get("X-Building");
        if (isBuilding === "true") {
          setTimeout(updateBuildCaptionIcon, 5000);
          let progress = rsp.headers.get("X-Progress");
          let runtime = rsp.headers.get("X-Executor-Runtime");
          let remaining = rsp.headers.get("X-Executor-Remaining");
          let progressBar = document.querySelector(".app-progress-bar");
          let progressBarDone = document.querySelector(
            ".app-progress-bar span",
          );
          if (progressBar) {
            let tooltip = progressBar.dataset.tooltipTemplate;
            tooltip = tooltip.replace("%0", runtime).replace("%1", remaining);
            progressBar.setAttribute("tooltip", tooltip);
            progressBar.setAttribute("title", tooltip);
            Behaviour.applySubtree(progressBar, true);
          }
          if (progressBarDone) {
            progressBarDone.style.width = `${progress}%`;
          }
        } else {
          let progressBar = document.querySelector(
            ".build-caption-progress-container",
          );
          if (progressBar) {
            progressBar.style.display = "none";
          }
        }
        rsp.text().then((responseText) => {
          document.querySelector(".jenkins-build-caption svg").outerHTML =
            responseText;
        });
      }
    });
  }

  setTimeout(updateBuildCaptionIcon, 5000);
})();
