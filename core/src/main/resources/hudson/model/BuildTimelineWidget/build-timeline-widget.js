/* global vis */

var targetDiv = document.querySelector("#build-timeline-div");
// var tz = targetDiv.getAttribute("data-hour-local-timezone");
// var tl = null;

// var interval = 24 * 60 * 60 * 1000;

function getData(timeline, current, min, max) {
  // if (!eventSource1.loaded[current]) {
  //   eventSource1.loaded[current] = true;
  fetch("timeline/data", {
    method: "POST",
    headers: crumb.wrap({
      "Content-Type": "application/x-www-form-urlencoded",
    }),
    body: new URLSearchParams({
      min: min,
      max: max,
      utcOffset: new Date().getTimezoneOffset(),
    }),
  }).then((t) => {
    if (t.ok) {
      t.json()
        .then((json) => {
          timeline.setData({ items: json.events });
        })
        .catch((err) => {
          alert(err);
        });
    }
  });
  // }
}

// Create a DataSet (allows two way data-binding)
let items = new vis.DataSet([]);

// Configuration for the Timeline
let options = {
  horizontalScroll: true,
  zoomable: true,
  zoomMax: 1000 * 3600 * 24 * 7,
  height: "300px",
  max: Date.now() + 6 * 3600 * 1000,
};

// Create a Timeline
let timeline = new vis.Timeline(targetDiv, items, options);
getData(
  timeline,
  0,
  Date.parse(timeline.getItemRange().min),
  Date.parse(timeline.getItemRange().max),
);

timeline.on("rangechanged", function (event) {
  console.log("NEw start: " + event.start + ", new end: " + event.end);
  getData(timeline, 0, Date.parse(event.start), Date.parse(event.end));
});
