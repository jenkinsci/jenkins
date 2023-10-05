/* global Timeline */

var targetDiv = document.querySelector("#build-timeline-div");
var tz = targetDiv.getAttribute("data-hour-local-timezone");
var tl = null;
var interval = 24 * 60 * 60 * 1000;

function getData(eventSource1, current, min, max) {
  if (current < min) {
    return;
  }
  if (!eventSource1.loaded[current]) {
    eventSource1.loaded[current] = true;
    fetch("timeline/data", {
      method: "POST",
      headers: crumb.wrap({
        "Content-Type": "application/x-www-form-urlencoded",
      }),
      body: new URLSearchParams({
        min: current * interval,
        max: (current + 1) * interval,
      }),
    }).then((t) => {
      if (t.ok) {
        t.json()
          .then((json) => {
            eventSource1.loadJSON(json, ".");
            getData(eventSource1, current - 1, min, max);
          })
          .catch((err) => {
            alert(err);
          });
      }
    });
  }
}

function doLoad() {
  var tl_el = document.getElementById("tl");
  var eventSource1 = new Timeline.DefaultEventSource();
  eventSource1.loaded = {};
  eventSource1.ensureVisible = function (band) {
    // make sure all data are loaded for the portion visible in the band
    // $('status').innerHTML = "min="+band.getMinDate()+" max="+band.getMaxDate();
    var min = Math.floor(band.getMinVisibleDate().getTime() / interval);
    var max = Math.ceil(band.getMaxVisibleDate().getTime() / interval);
    getData(eventSource1, max, min, max);
  };

  var theme1 = Timeline.ClassicTheme.create();
  //theme1.autoWidth = true; // Set the Timeline's "width" automatically.
  // Set autoWidth on the Timeline's first band's theme,
  // will affect all bands.

  var bandInfos = [
    // the bar that shows outline
    Timeline.createBandInfo({
      width: "20%",
      intervalUnit: Timeline.DateTime.DAY,
      intervalPixels: 200,
      eventSource: eventSource1,
      timeZone: tz,
      theme: theme1,
      layout: "overview", // original, overview, detailed
    }),
    // the main area
    Timeline.createBandInfo({
      width: "80%",
      eventSource: eventSource1,
      timeZone: tz,
      theme: theme1,
      intervalUnit: Timeline.DateTime.HOUR,
      intervalPixels: 200,
    }),
  ];
  bandInfos[0].highlight = true;
  bandInfos[0].syncWith = 1;

  // create the Timeline
  tl = Timeline.create(tl_el, bandInfos, Timeline.HORIZONTAL);

  tl.getBand(0).addOnScrollListener(function (band) {
    eventSource1.ensureVisible(band);
  });

  tl.layout(); // display the Timeline

  // if resized, redo layout
  var resizeTimerID = null;
  function doResize() {
    if (resizeTimerID == null) {
      resizeTimerID = window.setTimeout(function () {
        resizeTimerID = null;
        tl.layout();
      }, 500);
    }
  }

  if (window.addEventListener) {
    window.addEventListener("resize", doResize, false);
  } else if (window.attachEvent) {
    window.attachEvent("onresize", doResize);
  } else if (window.onResize) {
    window.onresize = doResize;
  }
}

if (window.addEventListener) {
  window.addEventListener("load", doLoad, false);
} else if (window.attachEvent) {
  window.attachEvent("onload", doLoad);
} else if (window.onLoad) {
  window.onload = doLoad;
}

//add resize handle
(function () {
  var resize = new YAHOO.util.Resize("resizeContainer", {
    handles: "b",
    minHeight: 300, // this should be the same as the height of the container div,
    // to fix an issue when it's resized to be smaller than the original height
  });

  //update timeline after resizing
  resize.on(
    "endResize",
    function () {
      tl.layout();
    },
    null,
    true,
  );
})();
