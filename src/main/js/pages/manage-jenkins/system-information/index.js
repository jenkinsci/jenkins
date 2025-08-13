const imageWidth = document.getElementById("main-panel").offsetWidth - 30;
const imageHeight = 500;
const graphHost = document.querySelector("#graph-host");
const timespanSelect = document.querySelector("#timespan-select");

// Set the aspect ratio of the graph host so it doesn't resize when new graphs load
graphHost.style.aspectRatio = `${imageWidth} / ${imageHeight}`;

// On select change load a new graph
timespanSelect.addEventListener("change", () => {
  const rootURL = document.head.dataset.rooturl;
  const type = timespanSelect.value;
  graphHost.innerHTML = `<img src="${rootURL}/jenkins.diagnosis.MemoryUsageMonitorAction/heap/graph?type=${type}&width=${imageWidth}&height=${imageHeight}" srcset="${rootURL}/jenkins.diagnosis.MemoryUsageMonitorAction/heap/graph?type=${type}&width=${imageWidth}&height=${imageHeight}&scale=2 2x" loading="lazy" style="width: 100%" alt="Memory usage graph" class="jenkins-graph-card" />`;
});

// Dispatch a change event to insert a graph on page load
timespanSelect.dispatchEvent(new Event("change"));
