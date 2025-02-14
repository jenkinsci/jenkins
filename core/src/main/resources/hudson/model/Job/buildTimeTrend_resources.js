/**
 * Public method to be called by progressiveRendering's callback
 */
window.buildTimeTrend_displayBuilds = function (data) {
  const p = document.getElementById("trend");
  p.classList.remove("jenkins-hidden");

  const showAgent = "true" === p.dataset.showAgent;
  const rootURL = document.head.getAttribute("data-rooturl");

  for (let x = 0; data.length > x; x++) {
    const e = data[x];
    let tr = document.createElement("tr");

    let td = document.createElement("td");
    td.setAttribute("data", e.iconColorOrdinal);
    td.classList.add("jenkins-table__cell--tight", "jenkins-table__icon");
    let div = document.createElement("div");
    div.classList.add("jenkins-table__cell__button-wrapper");
    let svg = generateSVGIcon(e.iconName);
    svg.setAttribute("tooltip", e.iconColorDescription);
    div.appendChild(svg);
    td.appendChild(div);
    tr.appendChild(td);

    td = document.createElement("td");
    td.setAttribute("data", e.number);

    let link = document.createElement("a");
    link.href = e.number + "/";
    link.classList.add("model-link", "inside");
    link.innerText = escapeHTML(e.displayName);

    td.appendChild(link);
    tr.appendChild(td);

    td = document.createElement("td");
    td.setAttribute("data", e.timestampString2);
    td.textContent = e.timestampString;
    tr.appendChild(td);

    td = document.createElement("td");
    td.setAttribute("data", e.duration);

    td.innerText = escapeHTML(e.durationString);

    tr.appendChild(td);
    if (showAgent) {
      let buildInfo = null;
      let buildInfoStr = escapeHTML(e.builtOnStr || "");
      if (e.builtOn) {
        buildInfo = document.createElement("a");
        buildInfo.href = rootURL + "/computer/" + e.builtOn + "/";
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

    let tdConsole = document.createElement("td");
    tdConsole.classList.add("jenkins-table__cell--tight");
    let div2 = document.createElement("div");
    div2.classList.add("jenkins-table__cell__button-wrapper");
    link = document.createElement("a");
    link.classList.add("jenkins-button", "jenkins-button--tertiary");
    link.href = e.consoleUrl;
    link.appendChild(generateSVGIcon("console"));
    div2.appendChild(link);
    tdConsole.appendChild(div2);
    tr.appendChild(tdConsole);

    p.appendChild(tr);
    Behaviour.applySubtree(tr);
  }
  ts_refresh(p);
};

/**
 * Generate SVG Icon
 */
function generateSVGIcon(iconName) {
  const icons = document.querySelector("#jenkins-build-status-icons");

  return icons.content.querySelector(`#${iconName}`).cloneNode(true);
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
    var svg = generateSVGIcon(e.iconName);
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
    td3.textContent = e.timestampString;
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
    a3.classList.add("jenkins-button", "jenkins-button--tertiary");
    a3.href = e.consoleUrl;
    a3.innerHTML = p.dataset.consoleOutputIcon;
    div2.appendChild(a3);
    td5.appendChild(div2);
    tr.appendChild(td5);

    p.appendChild(tr);
    Behaviour.applySubtree(tr);
  }
  ts_refresh(p);
};
