const graphHost = document.querySelector("#graph-host");
const timespanSelect = document.querySelector("#timespan-select");
const graphAlt = graphHost.dataset.graphAlt;

// On select change load a new graph
timespanSelect.addEventListener("change", () => {
  const rootURL = document.head.dataset.rooturl;
  const type = timespanSelect.value;
  // The SVG is inlined into the DOM (rather than referenced via an <img>) so that it resolves the
  // page's theme CSS variables and scales with its container via its viewBox.
  fetch(
    `${rootURL}/jenkins.diagnosis.MemoryUsageMonitorAction/heap/graphSvg?type=${type}`,
  )
    .then((response) => response.text())
    .then((svg) => {
      graphHost.innerHTML = svg;
      // The injected <svg> already carries role="img"; give it an accessible name.
      const svgElement = graphHost.querySelector("svg");
      if (svgElement) {
        svgElement.setAttribute("aria-label", graphAlt);
      }
    });
});

// Dispatch a change event to insert a graph on page load
timespanSelect.dispatchEvent(new Event("change"));
