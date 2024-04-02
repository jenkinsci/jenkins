import debounce from "lodash/debounce";

const buildHistoryContainer = document.getElementById("buildHistory");
const pageSearchInputContainer = buildHistoryContainer.querySelector(
  ".build-search-row .jenkins-search",
);
const pageSearchInput = buildHistoryContainer.querySelector(
  ".build-search-row input",
);
const buildHistoryPage = document.getElementById("buildHistoryPage");
const properties = document.getElementById("properties");
const ajaxUrl = buildHistoryPage.getAttribute("page-ajax");
const nextBuild = properties.getAttribute("page-next-build");
const noBuildsBanner = document.getElementById("no-builds");

const sidePanel = document.getElementById("side-panel");
const buildHistoryPageNav = document.getElementById("buildHistoryPageNav");

const pageOne = buildHistoryPageNav.querySelector(".pageOne");
const pageUp = buildHistoryPageNav.querySelector(".pageUp");
const pageDown = buildHistoryPageNav.querySelector(".pageDown");

const leftRightPadding = 8; // the left + right padding of a build-row-cell
const multiLinePadding = 20; // the left padding of the second/third line
const tabletBreakpoint = 900; // the breakpoint between tablet view and normal view,
// keep in sync with _breakpoints.scss
const updateBuildsRefreshInterval = 5000;

let lastClientWidth = 0;

function updateBuilds(params) {
  if (isPageVisible()) {
    fetch(ajaxUrl + toQueryString(params), {
      headers: {
        n: buildHistoryContainer.headers[1],
      },
    }).then((rsp) => {
      if (rsp.ok) {
        rsp.text().then((responseText) => {
          var dataTable = getDataTable(buildHistoryContainer);
          var rows = dataTable.rows;

          // Check there are no existing rows (except the search bar) before showing the no builds banner
          if (
            rows.length <= 1 &&
            responseText === '<table class="pane"></table>'
          ) {
            noBuildsBanner.style.display = "block";
            if (
              typeof params === "object" &&
              "search" in params &&
              params.search !== ""
            ) {
              pageSearchInputContainer.classList.remove("jenkins-hidden");
            } else {
              pageSearchInputContainer.classList.add("jenkins-hidden");
            }
          } else {
            noBuildsBanner.style.display = "none";
            pageSearchInputContainer.classList.remove("jenkins-hidden");
          }

          //delete rows with transitive data
          var firstBuildRow = 0;
          if (rows[firstBuildRow].classList.contains("build-search-row")) {
            firstBuildRow++;
          }
          while (
            rows.length > 1 &&
            rows[firstBuildRow].classList.contains("transitive")
          ) {
            rows[firstBuildRow].remove();
          }

          // insert new rows
          var div = document.createElement("div");
          div.innerHTML = responseText;
          Behaviour.applySubtree(div);

          var pivot = rows[firstBuildRow];
          var newDataTable = getDataTable(div);
          var newRows = newDataTable.rows;
          while (newRows.length > 0) {
            if (pivot !== undefined) {
              // The data table has rows.  Insert before a "pivot" row (first row).
              pivot.parentNode.insertBefore(newRows[0], pivot);
            } else {
              // The data table has no rows.  In this case, we just add all new rows directly to the
              // table, one after the other i.e. we don't insert before a "pivot" row (first row).
              dataTable
                .getElementsByTagName("tbody")[0]
                .appendChild(newRows[0]);
            }
          }

          if (newDataTable.classList.contains("hasPageData")) {
            buildHistoryPage.setAttribute(
              "page-entry-newest",
              newDataTable.getAttribute("page-entry-newest"),
            );
          }

          // next update
          buildHistoryContainer.headers = ["n", rsp.headers.get("n")];
          checkAllRowCellOverflows();
          createRefreshTimeout(params);
        });
      }
    });
  } else {
    createRefreshTimeout(params);
  }
}

var buildRefreshTimeout;
function createRefreshTimeout(params) {
  cancelRefreshTimeout();
  buildRefreshTimeout = window.setTimeout(
    () => updateBuilds(params),
    updateBuildsRefreshInterval,
  );
}

function cancelRefreshTimeout() {
  if (buildRefreshTimeout) {
    window.clearTimeout(buildRefreshTimeout);
    buildRefreshTimeout = undefined;
  }
}

function hasPageUp() {
  return buildHistoryPage.getAttribute("page-has-up") === "true";
}
function hasPageDown() {
  return buildHistoryPage.getAttribute("page-has-down") === "true";
}
function getNewestEntryId() {
  return buildHistoryPage.getAttribute("page-entry-newest");
}
function getOldestEntryId() {
  return buildHistoryPage.getAttribute("page-entry-oldest");
}

function getDataTable(buildHistoryDiv) {
  return buildHistoryDiv.querySelector("table.pane");
}

function updatePageParams(dataTable) {
  buildHistoryPage.setAttribute(
    "page-has-up",
    dataTable.getAttribute("page-has-up"),
  );
  buildHistoryPage.setAttribute(
    "page-has-down",
    dataTable.getAttribute("page-has-down"),
  );
  buildHistoryPage.setAttribute(
    "page-entry-newest",
    dataTable.getAttribute("page-entry-newest"),
  );
  buildHistoryPage.setAttribute(
    "page-entry-oldest",
    dataTable.getAttribute("page-entry-oldest"),
  );
}
function togglePageUpDown() {
  buildHistoryPageNav.classList.remove("hasUpPage");
  buildHistoryPageNav.classList.remove("hasDownPage");
  if (hasPageUp()) {
    buildHistoryPageNav.classList.add("hasUpPage");
  }
  if (hasPageDown()) {
    buildHistoryPageNav.classList.add("hasDownPage");
  }
}

/*
 * Arranges name, details (timestamp) and the badges for a build
 * so that it makes best use of the limited available space.
 * There are 6 possibilities how the parts can be arranged
 * 1. put everything in one row, with the name having a fixed width so that details are aligned.
 * 2. put name and badges in first row, details in first row
 * 3. put name in first row, details and badges in second row
 * 4. put name and details in first row, badges in second row
 * 5. put everything in separate rows
 * 6. there are no badges and name and details don't fit in one row
 */
function checkRowCellOverflows(row, recalculate = false) {
  if (!row) {
    return;
  }

  if (row.classList.contains("overflow-checked") && !recalculate) {
    // already done.
    return;
  }

  function markSingleline() {
    row.classList.add("single-line");
    row.classList.remove("multi-line");
  }
  function markMultiline() {
    row.classList.remove("single-line");
    row.classList.add("multi-line");
  }
  function indentMultiline(element) {
    element.classList.add("indent-multiline");
  }

  function blockWrap(el1, el2) {
    var div = document.createElement("div");

    div.classList.add("block");

    el1.parentNode.insertBefore(div, el1);
    el1.parentNode.removeChild(el1);
    el2.parentNode.removeChild(el2);
    div.appendChild(el1);
    div.appendChild(el2);

    return div;
  }

  var cell = row.querySelector(".build-row-cell");
  var buildName = row.querySelector(".build-name");
  var buildDetails = row.querySelector(".build-details");
  var leftBar = row.querySelector(".left-bar");
  if (!buildName || !buildDetails) {
    return;
  }

  var buildBadges = row.querySelector(".build-badges");
  if (buildBadges.childElementCount === 0) {
    buildBadges.remove();
    buildBadges = null;
  }

  var desc = row.querySelector(".desc");

  function resetCellOverflows() {
    markSingleline();

    cell.insertBefore(buildName, leftBar);
    cell.insertBefore(buildDetails, leftBar);
    if (buildBadges) {
      cell.insertBefore(buildBadges, leftBar);
    }
    buildName.classList.remove("block");
    buildName.classList.remove("block");
    buildName.classList.remove("indent-multiline");
    buildName.removeAttribute("style");
    buildDetails.classList.remove("block");
    buildDetails.removeAttribute("style");
    buildDetails.classList.remove("indent-multiline");
    if (buildBadges) {
      buildBadges.classList.remove("block");
      buildBadges.removeAttribute("style");
      buildBadges.classList.remove("indent-multiline");
    }
    const nameBadges = cell.querySelector(".build-name-badges");
    if (nameBadges) {
      nameBadges.remove();
    }

    const detailsBadges = cell.querySelector(".build-details-badges");
    if (detailsBadges) {
      detailsBadges.remove();
    }
  }

  // Undo everything from the previous poll.
  resetCellOverflows();

  // Mark the text as multiline, if it has more than one line
  if (desc) {
    markMultiline();
  }

  //
  function getElementOverflowData(element, width) {
    // First we force it to wrap so we can get those dimension.
    // Then we force it to "nowrap", so we can get those dimension.
    // We can then compare the two sets, which will indicate if
    // wrapping is potentially happening, or not.
    // The scrollWidth is calculated based on the content and not the actual
    // width of the element

    // Force it to wrap.
    const oldWidth = element.style.width;
    element.style.width = width + "px";
    element.classList.add("force-wrap");
    var wrappedClientHeight = element.clientHeight;
    element.classList.remove("force-wrap");

    // Force it to nowrap. Return the comparisons.
    element.classList.add("force-nowrap");
    element.style.width = "fit-content";
    var nowrapClientHeight = element.clientHeight;
    try {
      var overflowParams = {
        element: element,
        scrollWidth: element.scrollWidth + 5, // 1 for rounding + 4 for left/right padding
        isOverflowed: wrappedClientHeight > nowrapClientHeight,
      };
      return overflowParams;
    } finally {
      element.classList.remove("force-nowrap");
      element.style.width = oldWidth;
    }
  }

  // eslint-disable-next-line no-inner-declarations
  function expandLeftWithRight(
    leftCellOverFlowParams,
    rightCellOverflowParams,
  ) {
    // Float them left and right...
    leftCellOverFlowParams.element.style.float = "left";
    rightCellOverflowParams.element.style.float = "right";

    leftCellOverFlowParams.element.style.width =
      leftCellOverFlowParams.scrollWidth + "px";
    rightCellOverflowParams.element.style.width =
      rightCellOverflowParams.scrollWidth + "px";
  }

  var rowWidth = buildHistoryContainer.clientWidth;
  var usableRowWidth = rowWidth - leftRightPadding * 2;

  let nameWidth = usableRowWidth * 0.32;
  let detailsWidth = usableRowWidth * 0.5;
  let badgesWidth = usableRowWidth * 0.18;

  var nameOverflowParams = getElementOverflowData(buildName, nameWidth);
  var detailsOverflowParams = getElementOverflowData(
    buildDetails,
    detailsWidth,
  );
  var badgesOverflowParams;
  if (buildBadges) {
    badgesOverflowParams = getElementOverflowData(buildBadges, badgesWidth);
  } else {
    badgesOverflowParams = {
      element: null,
      scrollWidth: 0,
      isOverflowed: false,
    };
  }

  function setBuildBadgesWidths() {
    buildBadges.style.width = "100%";
  }

  if (
    !nameOverflowParams.isOverflowed &&
    nameWidth +
      detailsOverflowParams.scrollWidth +
      badgesOverflowParams.scrollWidth <
      usableRowWidth
  ) {
    // Everything fits in one row
    buildDetails.style.width = "fit-content";
    buildBadges.style.float = "right";
    buildBadges.style.width = "fit-content";
  } else {
    markMultiline();
    if (buildBadges) {
      // We have build badges. Lets see can we find a combination that allows the build badges
      // to sit beside either the build name or the build details.

      if (
        nameOverflowParams.scrollWidth + badgesOverflowParams.scrollWidth <=
        usableRowWidth
      ) {
        // Build name and badges can go on one row (first row). Need to move build details down
        // to a row of its own (second row) by making it a block element, forcing it to wrap. If there
        // are badges, we move them up to position them after the build name by inserting before the
        // build details.
        buildDetails.classList.add("block");
        buildBadges.parentNode.removeChild(buildBadges);
        buildDetails.parentNode.insertBefore(buildBadges, buildDetails);
        var wrap = blockWrap(buildName, buildBadges);
        wrap.classList.add("build-name-badges");
        indentMultiline(buildDetails);
        expandLeftWithRight(nameOverflowParams, badgesOverflowParams);
      } else if (
        detailsOverflowParams.scrollWidth +
          badgesOverflowParams.scrollWidth +
          multiLinePadding <=
        usableRowWidth
      ) {
        // Build details and badges can go on one row. Need to make the
        // build name (first field) a block element, forcing the details and badges to wrap
        // onto the next row (creating a second row).
        buildName.classList.add("block");
        wrap = blockWrap(buildDetails, buildBadges);
        indentMultiline(wrap);
        wrap.classList.add("build-details-badges");
        expandLeftWithRight(detailsOverflowParams, badgesOverflowParams);
      } else if (
        !nameOverflowParams.isOverflowed &&
        nameWidth + detailsOverflowParams.scrollWidth < usableRowWidth
      ) {
        // Build name and details can go on one row. Make badges take full row
        // it goes on separate row
        indentMultiline(buildBadges);
        setBuildBadgesWidths();
      } else {
        // No suitable combo fits on a row. All need to go on rows of their own.
        buildName.classList.add("block");
        buildDetails.classList.add("block");
        buildBadges.classList.add("block");
        indentMultiline(buildDetails);
        indentMultiline(buildBadges);
        setBuildBadgesWidths();
      }
    } else {
      // name and details don't fit in one row
      indentMultiline(buildDetails);
      buildName.classList.add("block");
    }
  }

  row.classList.add("overflow-checked");
}

function checkAllRowCellOverflows(recalculate = false) {
  if (isRunAsTest) {
    return;
  }
  var dataTable = getDataTable(buildHistoryContainer);
  var rows = dataTable.rows;

  for (var i = 0; i < rows.length; i++) {
    var row = rows[i];
    checkRowCellOverflows(row, recalculate);
  }
}

function loadPage(params, focusOnSearch) {
  var searchString = pageSearchInput.value;

  if (searchString !== "") {
    if (params === undefined) {
      params = {};
    }
    params.search = searchString;
  }

  fetch(ajaxUrl + toQueryString(params)).then((rsp) => {
    if (rsp.ok) {
      rsp.text().then((responseText) => {
        pageSearchInputContainer.classList.remove("jenkins-search--loading");
        buildHistoryContainer.classList.remove("jenkins-pane--loading");

        if (responseText === '<table class="pane"></table>') {
          noBuildsBanner.style.display = "block";
          if (
            typeof params === "object" &&
            "search" in params &&
            params.search !== ""
          ) {
            pageSearchInputContainer.classList.remove("jenkins-hidden");
          } else {
            pageSearchInputContainer.classList.add("jenkins-hidden");
          }
        } else {
          noBuildsBanner.style.display = "none";
          pageSearchInputContainer.classList.remove("jenkins-hidden");
        }

        var dataTable = getDataTable(buildHistoryContainer);
        var tbody = dataTable.getElementsByTagName("tbody")[0];
        var rows = tbody.getElementsByClassName("build-row");

        // Delete all build rows
        while (rows.length > 0) {
          rows[0].remove();
        }

        // insert new rows
        var div = document.createElement("div");
        div.innerHTML = responseText;
        Behaviour.applySubtree(div);

        var newDataTable = getDataTable(div);
        var newRows = newDataTable.rows;
        while (newRows.length > 0) {
          tbody.appendChild(newRows[0]);
        }

        checkAllRowCellOverflows();
        updatePageParams(newDataTable);
        togglePageUpDown();
        if (!hasPageUp()) {
          createRefreshTimeout(params);
        }

        if (focusOnSearch) {
          pageSearchInput.focus();
        }
      });
    }
  });
}

const handleResize = function () {
  checkAllRowCellOverflows(true);
};

const debouncedResizer = debounce(handleResize, 500);

addEventListener("resize", function () {
  const newClientWidth = document.body.clientWidth;
  // the sidepanel has 2 sizes depending on the clientWidth
  // > tabletBreakpoint: the sidepanel has fixed width
  // <= tabletBreakpoint: the sidepanel takes the complete width
  if (
    lastClientWidth > tabletBreakpoint &&
    newClientWidth > tabletBreakpoint &&
    lastClientWidth != newClientWidth
  ) {
    // we're in a range of the clientWidth were changes do not affect the layout
    // or the width hasn't changed.
    lastClientWidth = newClientWidth;
    return;
  }
  lastClientWidth = newClientWidth;
  debouncedResizer();
});

const handleFilter = function () {
  loadPage({}, true);
};

const debouncedFilter = debounce(handleFilter, 300);

document.addEventListener("DOMContentLoaded", function () {
  lastClientWidth = document.body.clientWidth;
  // Apply correct styling upon filter bar text change, call API after wait
  if (pageSearchInput !== null) {
    pageSearchInput.addEventListener("input", function () {
      pageSearchInputContainer.classList.add("jenkins-search--loading");
      buildHistoryContainer.classList.add("jenkins-pane--loading");
      noBuildsBanner.style.display = "none";

      debouncedFilter();
    });
  }

  if (isRunAsTest) {
    return;
  }

  // If the build history pane is collapsed, just return immediately and don't set up
  // the build history refresh.
  if (buildHistoryContainer.classList.contains("collapsed")) {
    return;
  }

  buildHistoryContainer.headers = ["n", nextBuild];

  createRefreshTimeout();
  checkAllRowCellOverflows();

  // Show/hide the nav as the mouse moves into the sidepanel and build history.
  sidePanel.addEventListener("mouseover", function () {
    buildHistoryPageNav.classList.add("mouseOverSidePanel");
  });
  sidePanel.addEventListener("mouseout", function () {
    buildHistoryPageNav.classList.remove("mouseOverSidePanel");
  });
  buildHistoryContainer.addEventListener("mouseover", function () {
    buildHistoryPageNav.classList.add("mouseOverSidePanelBuildHistory");
  });
  buildHistoryContainer.addEventListener("mouseout", function () {
    buildHistoryPageNav.classList.remove("mouseOverSidePanelBuildHistory");
  });

  pageOne.addEventListener("click", function () {
    loadPage();
  });
  pageUp.addEventListener("click", function () {
    loadPage({ "newer-than": getNewestEntryId() });
  });
  pageDown.addEventListener("click", function () {
    if (hasPageDown()) {
      cancelRefreshTimeout();
      loadPage({ "older-than": getOldestEntryId() });
    } else {
      // wrap back around to the top
      loadPage();
    }
  });

  togglePageUpDown();
});
