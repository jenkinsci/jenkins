(function () {
  const buildCaption = document.querySelector("[data-status-url]");
  if (!buildCaption) {
    return;
  }

  const statusIconSelector = ".app-build-bar__content__headline svg, svg";
  const progressRingSelector = "[data-build-caption-progress-ring]";
  const svgNamespace = "http://www.w3.org/2000/svg";
  const progress =
    buildCaption.dataset.progress ||
    document.querySelector(".app-progress-bar span")?.style.width;
  const url = buildCaption.dataset.statusUrl;
  const actionsUrl = buildCaption.dataset.actionsUrl;
  const title = document.title;

  function updateBuildCaptionIcon() {
    fetch(url).then((rsp) => {
      if (rsp.ok) {
        let isBuilding = rsp.headers.get("X-Building");
        let progress = rsp.headers.get("X-Progress");
        if (isBuilding === "true") {
          setTimeout(updateBuildCaptionIcon, 5000);
          let runtime = rsp.headers.get("X-Executor-Runtime");
          let remaining = rsp.headers.get("X-Executor-Remaining");
          let stuck = rsp.headers.get("X-Executor-Stuck");
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
            if (stuck === "true") {
              progressBar.classList.add("app-progress-bar--error");
            } else {
              progressBar.classList.remove("app-progress-bar--error");
            }
          }
          if (progressBarDone) {
            progressBarDone.style.width = `${progress}%`;
          }
          setTitle(progress);
        } else {
          let progressBar = document.querySelector(
            ".build-caption-progress-container",
          );
          if (progressBar) {
            progressBar.style.display = "none";
          }
          document.title = title;

          // Once the build is complete, refresh the build's actions
          if (actionsUrl) {
            fetch(actionsUrl).then((rsp) => {
              if (rsp.ok) {
                rsp.text().then((responseText) => {
                  const controls = buildCaption.querySelector(
                    ".app-build-bar__controls",
                  );
                  document.startViewTransition(() => {
                    controls.innerHTML = responseText;
                    Behaviour.applySubtree(controls);
                  });
                });
              }
            });
          }
        }

        rsp.text().then((responseText) => {
          updateBuildIcon(
            responseText,
            isBuilding === "true" ? progress : undefined,
          );

          // Behaviour.applySubtree(buildCaption, false);
        });
      }
    });
  }

  function updateBuildIcon(responseText, progress) {
    const template = document.createElement("template");
    template.innerHTML = responseText.trim();

    const nextIcon = template.content.querySelector("svg");
    const currentIcon = buildCaption.querySelector(statusIconSelector);
    if (!nextIcon || !currentIcon) {
      return;
    }

    updateBuildIconProgress(nextIcon, progress);
    removeCurrentIconLabel(currentIcon);
    currentIcon.outerHTML = template.innerHTML;
  }

  function updateCurrentBuildIconProgress(progress) {
    updateBuildIconProgress(
      buildCaption.querySelector(statusIconSelector),
      progress,
    );
  }

  function updateBuildIconProgress(icon, progress) {
    if (!icon) {
      return;
    }

    icon.querySelector(progressRingSelector)?.remove();

    const percentage = parseProgressPercentage(progress);
    if (percentage === undefined) {
      return;
    }

    const strokeWidth = 50;
    const center = 256;
    const radius = getBuildIconOuterRadius(icon);
    const circumference = 2 * Math.PI * radius;
    const offset = circumference - (percentage / 100) * circumference;
    const ring = document.createElementNS(svgNamespace, "circle");

    icon.style.overflow = "visible";
    ring.setAttribute("data-build-caption-progress-ring", "true");
    ring.setAttribute("cx", center);
    ring.setAttribute("cy", center);
    ring.setAttribute("r", radius);
    ring.setAttribute("fill", "none");
    ring.setAttribute("stroke", getBuildIconColor(icon));
    ring.setAttribute("stroke-width", strokeWidth);
    ring.setAttribute("stroke-linecap", "round");
    ring.setAttribute("stroke-dasharray", circumference);
    ring.setAttribute("stroke-dashoffset", offset);
    ring.setAttribute(
      "style",
      "transform: rotate(-90deg); transform-origin: 50% 50%; transition: var(--standard-transition);",
    );

    const runningIndicator = icon.querySelector(".app-status-icon-running");
    if (runningIndicator) {
      icon.insertBefore(ring, runningIndicator);
    } else {
      icon.append(ring);
    }
  }

  function getBuildIconColor(icon) {
    return (
      icon.querySelector(".app-status-icon-running")?.getAttribute("fill") ||
      icon.querySelector("circle[stroke]")?.getAttribute("stroke") ||
      "currentColor"
    );
  }

  function getBuildIconOuterRadius(icon) {
    const borderRadius = Number.parseFloat(
      icon.querySelector("circle[stroke]")?.getAttribute("r"),
    );
    if (Number.isFinite(borderRadius)) {
      return borderRadius;
    }

    const radii = Array.from(icon.querySelectorAll("circle"))
      .map((circle) => Number.parseFloat(circle.getAttribute("r")))
      .filter(Number.isFinite);

    return Math.max(...radii, 250);
  }

  function parseProgressPercentage(progress) {
    const percentage = Number.parseFloat(progress);
    if (!Number.isFinite(percentage) || percentage < 0) {
      return undefined;
    }

    return Math.min(100, percentage);
  }

  function removeCurrentIconLabel(currentIcon) {
    const label = currentIcon.previousElementSibling;
    if (label?.classList.contains("jenkins-visually-hidden")) {
      label.remove();
    }
  }

  function setTitle(percentage) {
    const progressPercentage = parseProgressPercentage(percentage);
    if (progressPercentage === undefined) {
      return;
    }

    document.title = "(" + progressPercentage + "%) " + title;
  }

  setTimeout(updateBuildCaptionIcon, 5000);
  updateCurrentBuildIconProgress(progress);
  setTitle(progress);
})();
