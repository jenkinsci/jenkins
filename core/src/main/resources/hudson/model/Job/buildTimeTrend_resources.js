/**
 * Public method to be called by progressiveRendering's callback
 */
window.buildTimeTrend_displayBuilds = function (data) {
  var p = document.getElementById("trend");
  var isDistributedBuildsEnabled =
    "true" === p.getAttribute("data-is-distributed-build-enabled");
  var rootURL = document.head.getAttribute("data-rooturl");

  for (var x = 0; data.length > x; x++) {
    var e = data[x];
    var tr = document.createElement("tr");

    let td = document.createElement("td");
    td.setAttribute("data", e.iconColorOrdinal);

    let link = document.createElement("a");
    link.classList.add("build-status-link");
    link.href = e.number + "/console";
    td.appendChild(link);
    let svg = generateSVGIcon(e.iconName);
    link.appendChild(svg);
    tr.appendChild(td);

    td = document.createElement("td");
    td.setAttribute("data", e.number);

    link = document.createElement("a");
    link.href = e.number + "/";
    link.classList.add("model-link", "inside");
    link.innerText = escapeHTML(e.displayName);

    td.appendChild(link);
    tr.appendChild(td);

    td = document.createElement("td");
    td.setAttribute("data", e.duration);

    td.innerText = escapeHTML(e.durationString);

    tr.appendChild(td);
    if (isDistributedBuildsEnabled) {
      var buildInfo = null;
      var buildInfoStr = escapeHTML(e.builtOnStr || "");
      if (e.builtOn) {
        buildInfo = document.createElement("a");
        buildInfo.href = rootURL + "/computer/" + e.builtOn;
        buildInfo.classList.add("model-link", "inside");
        buildInfo.innerText = buildInfoStr;
      } else {
        buildInfo = buildInfoStr;
      }
      td = document.createElement("td");
      if (buildInfo instanceof Node) {
        td.appendChild(buildInfo);
      } else {
        td.innerText = buildInfo;
      }
      tr.appendChild(td);
    }
    p.appendChild(tr);
    Behaviour.applySubtree(tr);
  }
  ts_refresh(p);
};

/**
 * Generate SVG Icon
 */
function generateSVGIcon(iconName, iconSizeClass) {
  const imagesURL = document.head.getAttribute("data-imagesurl");

  const isInProgress = iconName.endsWith("anime");
  let buildStatus = "never-built";
  switch (iconName) {
    case "red":
    case "red-anime":
      buildStatus = "last-failed";
      break;
    case "yellow":
    case "yellow-anime":
      buildStatus = "last-unstable";
      break;
    case "blue":
    case "blue-anime":
      buildStatus = "last-successful";
      break;
    case "grey":
    case "grey-anime":
    case "disabled":
    case "disabled-anime":
      buildStatus = "last-disabled";
      break;
    case "aborted":
    case "aborted-anime":
      buildStatus = "last-aborted";
      break;
    case "nobuilt":
    case "nobuilt-anime":
      buildStatus = "never-built";
      break;
  }

  const svg1 = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg1.setAttribute("class", "svg-icon");
  svg1.setAttribute("viewBox", "0 0 24 24");
  const use1 = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use1.setAttribute(
    "href",
    imagesURL +
      "/build-status/build-status-sprite.svg#build-status-" +
      (isInProgress ? "in-progress" : "static"),
  );
  svg1.appendChild(use1);

  const svg2 = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg2.setAttribute(
    "class",
    "svg-icon icon-" + iconName + " " + (iconSizeClass || "icon-sm"),
  );
  svg2.setAttribute("viewBox", "0 0 24 24");
  const use2 = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use2.setAttribute(
    "href",
    imagesURL + "/build-status/build-status-sprite.svg#" + buildStatus,
  );
  svg2.appendChild(use2);

  const span = document.createElement("span");
  span.classList.add("build-status-icon__wrapper", "icon-" + iconName);

  let span2 = document.createElement("span");
  span2.classList.add("build-status-icon__outer");
  span2.appendChild(svg1);

  span.appendChild(span2);
  span.appendChild(svg2);

  return span;
}

/**
 * Public method to be called by progressiveRendering's callback
 */
window.displayBuilds = function (data) {
  var rootUrl = document.head.getAttribute("data-rooturl");
  var p = document.getElementById("projectStatus");
  p.style.display = "";
  for (var x = 0; data.length > x; x++) {
    var e = data[x];
    var tr = document.createElement("tr");

    var td1 = document.createElement("td");
    td1.setAttribute("data", e.iconColorOrdinal);
    td1.classList.add("jenkins-table__cell--tight", "jenkins-table__icon");
    var div1 = document.createElement("div");
    div1.classList.add("jenkins-table__cell__button-wrapper");
    var svg = generateSVGIcon(e.iconName, p.dataset.iconSizeClass);
    div1.appendChild(svg);
    td1.appendChild(div1);
    tr.appendChild(td1);

    var td2 = document.createElement("td");
    var a1 = document.createElement("a");
    a1.classList.add("jenkins-table__link", "model-link");
    a1.href = rootUrl + "/" + e.parentUrl;
    var span1 = document.createElement("span");
    // TODO port Functions#breakableString to JavaScript and use .textContent rather than .innerHTML
    span1.innerHTML = e.parentFullDisplayName;
    a1.appendChild(span1);
    td2.appendChild(a1);
    var a2 = document.createElement("a");
    a2.classList.add(
      "jenkins-table__link",
      "jenkins-table__badge",
      "model-link",
      "inside",
    );
    a2.href = rootUrl + "/" + e.url;
    a2.textContent = e.displayName;
    td2.appendChild(a2);
    tr.appendChild(td2);

    var td3 = document.createElement("td");
    td3.setAttribute("data", e.timestampString2);
    var button = document.createElement("button");
    button.classList.add("jenkins-table__link");
    button.setAttribute("tooltip", p.dataset.scrollTooltip);
    button.setAttribute(
      "onclick",
      'javascript:tl.getBand(0).scrollToCenter(Timeline.DateTime.parseGregorianDateTime("' +
        e.timestampString3 +
        '"))',
    );
    button.textContent = e.timestampString;
    td3.appendChild(button);
    tr.appendChild(td3);

    var td4 = document.createElement("td");
    if (e.buildStatusSummaryWorse) {
      td4.style.color = "var(--red)";
    }
    td4.textContent = e.buildStatusSummaryMessage;
    tr.appendChild(td4);

    var td5 = document.createElement("td");
    td5.classList.add("jenkins-table__cell--tight");
    var div2 = document.createElement("div");
    div2.classList.add("jenkins-table__cell__button-wrapper");
    var a3 = document.createElement("a");
    a3.classList.add("jenkins-table__button");
    a3.href = rootUrl + "/" + e.url + "console";
    a3.innerHTML = p.dataset.consoleOutputIcon;
    div2.appendChild(a3);
    td5.appendChild(div2);
    tr.appendChild(td5);

    p.appendChild(tr);
    Behaviour.applySubtree(tr);
  }
  ts_refresh(p);
};
