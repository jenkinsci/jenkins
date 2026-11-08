/**
 * Appends rows for a page of runs; used as the data-runs-renderer of the build time trend table
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
      /* eslint-disable-next-line no-useless-assignment */
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
 * Appends rows for a page of runs; used as the data-runs-renderer of the build list table
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
    a2.classList.add("jenkins-badge", "model-link");
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

/**
 * Loads pages of runs from the endpoint in data-runs-url and renders them with the
 * function named by data-runs-renderer. Further pages are loaded on request.
 */
Behaviour.specify(
  ".jenkins-runs[data-runs-url]",
  "jenkins-runs",
  0,
  function (container) {
    const url = container.dataset.runsUrl;
    const render = window[container.dataset.runsRenderer];
    const loading = container.querySelector(".jenkins-runs__loading");
    const more = container.querySelector(".jenkins-runs__more");
    let next = null;
    // rows of the current page loaded so far, kept if loading the rest fails so that a retry asks only for the rest
    let loaded = 0;
    let pageSize = null;

    function fetchPage() {
      const params = new URLSearchParams();
      if (next !== null) {
        params.set("olderThan", next);
      }
      if (loaded > 0) {
        params.set("limit", pageSize - loaded);
      }
      const query = params.toString();
      return fetch(query ? url + "?" + query : url)
        .then(function (rsp) {
          if (!rsp.ok) {
            throw new Error(rsp.status + " " + rsp.statusText);
          }
          return rsp.json();
        })
        .then(function (json) {
          const page = json.data;
          render(page.builds);
          next = page.next;
          loaded += page.builds.length;
          pageSize = page.pageSize;
          // the server stopped early to bound its scan time, so the page is not full yet
          if (page.partial && next !== null && loaded < pageSize) {
            return fetchPage();
          }
          loaded = 0;
        });
    }

    function load() {
      more.disabled = true;
      loading.classList.remove("jenkins-hidden");
      fetchPage()
        .then(function () {
          more.classList.toggle("jenkins-hidden", next === null);
        })
        .catch(function (e) {
          console.error("Failed to load builds from " + url, e);
          // offer to retry, loading the rest of the page
          more.classList.remove("jenkins-hidden");
          notificationBar.show(
            container.dataset.loadError,
            notificationBar.ERROR,
          );
        })
        .finally(function () {
          loading.classList.add("jenkins-hidden");
          more.disabled = false;
        });
    }

    more.addEventListener("click", load);
    load();
  },
);
