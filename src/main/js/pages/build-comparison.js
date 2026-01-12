/**
 * Handles build comparison page interactions
 */

document.addEventListener("DOMContentLoaded", function () {
  const build2Select = document.getElementById("build2-select");
  if (!build2Select) {
    return;
  }

  // Handle URL parameters for pre-selected builds
  const urlParams = new URLSearchParams(window.location.search);
  const build2Param = urlParams.get("build2");
  if (build2Param && build2Select) {
    build2Select.value = build2Param;
  }

  // Highlight differences when both builds are displayed
  const comparisonPanels = document.querySelectorAll(".build-comparison-panel");
  if (comparisonPanels.length === 2) {
    highlightDifferences();
  }
});

/**
 * Highlights differences between the two build panels
 */
function highlightDifferences() {
  const panels = document.querySelectorAll(".build-comparison-panel");
  if (panels.length !== 2) {
    return;
  }

  const leftPanel = panels[0];
  const rightPanel = panels[1];

  // Compare status
  const leftStatus = leftPanel.querySelector(".build-comparison-table tr:first-child td");
  const rightStatus = rightPanel.querySelector(".build-comparison-table tr:first-child td");
  if (leftStatus && rightStatus && leftStatus.textContent.trim() !== rightStatus.textContent.trim()) {
    leftStatus.classList.add("build-comparison-diff");
    rightStatus.classList.add("build-comparison-diff");
  }

  // Compare duration
  const leftDuration = leftPanel.querySelector(".build-comparison-table tr:nth-child(2) td");
  const rightDuration = rightPanel.querySelector(".build-comparison-table tr:nth-child(2) td");
  if (leftDuration && rightDuration && leftDuration.textContent.trim() !== rightDuration.textContent.trim()) {
    leftDuration.classList.add("build-comparison-diff");
    rightDuration.classList.add("build-comparison-diff");
  }
}
