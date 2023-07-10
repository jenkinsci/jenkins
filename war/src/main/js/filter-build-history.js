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

const leftRightPadding = 4;
const updateBuildsRefreshInterval = 5000;

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

function checkRowCellOverflows(row) {
  if (!row) {
    return;
  }

  if (row.classList.contains("overflow-checked")) {
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
    div.classList.add("wrap");
    el1.classList.add("wrapped");
    el2.classList.add("wrapped");

    el1.parentNode.insertBefore(div, el1);
    el1.parentNode.removeChild(el1);
    el2.parentNode.removeChild(el2);
    div.appendChild(el1);
    div.appendChild(el2);

    return div;
  }
  function blockUnwrap(element) {
    element.querySelectorAll(".wrapped").forEach(function (wrappedEl) {
      wrappedEl.parentNode.removeChild(wrappedEl);
      element.parentNode.insertBefore(wrappedEl, element);
      wrappedEl.classList.remove("wrapped");
    });
    element.parentNode.removeChild(element);
  }

  var buildName = row.querySelector(".build-name");
  var buildDetails = row.querySelector(".build-details");

  if (!buildName || !buildDetails) {
    return;
  }

  var buildControls = row.querySelector(".build-controls");
  var desc = row.querySelector(".desc");

  function resetCellOverflows() {
    markSingleline();

    // undo block wraps
    row.querySelectorAll(".block.wrap").forEach(function (blockWrap) {
      blockUnwrap(blockWrap);
    });

    buildName.classList.remove("block");
    buildName.removeAttribute("style");
    buildDetails.classList.remove("block");
    buildDetails.removeAttribute("style");
    if (buildControls) {
      buildControls.classList.remove("block");
      buildDetails.removeAttribute("style");
    }
  }

  // Undo everything from the previous poll.
  resetCellOverflows();

  // Mark the text as multiline, if it has more than one line
  if (desc) {
    markMultiline();
  }

  var rowWidth = buildHistoryContainer.clientWidth;
  var usableRowWidth = rowWidth - leftRightPadding * 2;
  var nameOverflowParams = getElementOverflowParams(buildName);
  var detailsOverflowParams = getElementOverflowParams(buildDetails);

  var controlsOverflowParams;
  if (buildControls) {
    controlsOverflowParams = getElementOverflowParams(buildControls);
  }

  function fitToControlsHeight(element) {
    if (buildControls) {
      if (element.clientHeight < buildControls.clientHeight) {
        element.style.height = buildControls.clientHeight.toString() + "px";
      }
    }
  }

  function setBuildControlWidths() {
    if (buildControls) {
      var buildBadge = buildControls.querySelector(".build-badge");

      if (buildBadge) {
        var buildControlsWidth = buildControls.clientWidth;
        var buildBadgeWidth;

        var buildStop = buildControls.querySelector(".build-stop");
        if (buildStop) {
          buildStop.style.width = "24px";
          // Minus 24 for the buildStop width,
          // minus 4 for left+right padding in the controls container
          buildBadgeWidth = buildControlsWidth - 24 - leftRightPadding;
          if (buildControls.classList.contains("indent-multiline")) {
            buildBadgeWidth = buildBadgeWidth - 20;
          }
          buildBadge.style.width = buildBadgeWidth + "px";
        } else {
          buildBadge.style.width = "100%";
        }
      }
      controlsOverflowParams = getElementOverflowParams(buildControls);
    }
  }
  setBuildControlWidths();

  var controlsRepositioned = false;

  if (nameOverflowParams.isOverflowed || detailsOverflowParams.isOverflowed) {
    // At least one of the cells (name or details) needs to move to a row of its own.

    markMultiline();

    if (buildControls) {
      // We have build controls. Lets see can we find a combination that allows the build controls
      // to sit beside either the build name or the build details.

      var badgesOverflowing = false;
      var nameLessThanHalf = true;
      var detailsLessThanHalf = true;
      var buildBadge = buildControls.querySelector(".build-badge");
      if (buildBadge) {
        var badgeOverflowParams = getElementOverflowParams(buildBadge);

        if (badgeOverflowParams.isOverflowed) {
          // The badges are also overflowing. In this case, we will only attempt to
          // put the controls on the same line as the name or details (see below)
          // if the name or details is using less than half the width of the build history
          // widget.
          badgesOverflowing = true;
          nameLessThanHalf =
            nameOverflowParams.scrollWidth < usableRowWidth / 2;
          detailsLessThanHalf =
            detailsOverflowParams.scrollWidth < usableRowWidth / 2;
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

        if (
          !leftCellOverFlowParams.isOverflowed &&
          !rightCellOverflowParams.isOverflowed
        ) {
          // If neither left nor right are overflowed, just leave as is and let them float left and right.
          return;
        }
        if (
          leftCellOverFlowParams.isOverflowed &&
          !rightCellOverflowParams.isOverflowed
        ) {
          leftCellOverFlowParams.element.style.width =
            leftCellOverFlowParams.scrollWidth + "px";
          return;
        }
        if (
          !leftCellOverFlowParams.isOverflowed &&
          rightCellOverflowParams.isOverflowed
        ) {
          rightCellOverflowParams.element.style.width =
            rightCellOverflowParams.scrollWidth + "px";
          return;
        }
      }

      if (
        (!badgesOverflowing || nameLessThanHalf) &&
        nameOverflowParams.scrollWidth + controlsOverflowParams.scrollWidth <=
          usableRowWidth
      ) {
        // Build name and controls can go on one row (first row). Need to move build details down
        // to a row of its own (second row) by making it a block element, forcing it to wrap. If there
        // are controls, we move them up to position them after the build name by inserting before the
        // build details.
        buildDetails.classList.add("block");
        buildControls.parentNode.removeChild(buildControls);
        buildDetails.parentNode.insertBefore(buildControls, buildDetails);
        var wrap = blockWrap(buildName, buildControls);
        wrap.classList.add("build-name-controls");
        indentMultiline(buildDetails);
        nameOverflowParams = getElementOverflowParams(buildName); // recalculate
        expandLeftWithRight(nameOverflowParams, controlsOverflowParams);
        setBuildControlWidths();
        fitToControlsHeight(buildName);
      } else if (
        (!badgesOverflowing || detailsLessThanHalf) &&
        detailsOverflowParams.scrollWidth +
          controlsOverflowParams.scrollWidth <=
          usableRowWidth
      ) {
        // Build details and controls can go on one row. Need to make the
        // build name (first field) a block element, forcing the details and controls to wrap
        // onto the next row (creating a second row).
        buildName.classList.add("block");
        wrap = blockWrap(buildDetails, buildControls);
        indentMultiline(wrap);
        wrap.classList.add("build-details-controls");
        detailsOverflowParams = getElementOverflowParams(buildDetails); // recalculate
        expandLeftWithRight(detailsOverflowParams, controlsOverflowParams);
        setBuildControlWidths();
        fitToControlsHeight(buildDetails);
      } else {
        // No suitable combo fits on a row. All need to go on rows of their own.
        buildName.classList.add("block");
        buildDetails.classList.add("block");
        buildControls.classList.add("block");
        indentMultiline(buildDetails);
        indentMultiline(buildControls);
        nameOverflowParams = getElementOverflowParams(buildName); // recalculate
        detailsOverflowParams = getElementOverflowParams(buildDetails); // recalculate
        setBuildControlWidths();
      }
      controlsRepositioned = true;
    } else {
      buildName.classList.add("block");
      buildDetails.classList.add("block");
      indentMultiline(buildDetails);
    }
  }

  if (buildControls && !controlsRepositioned) {
    buildBadge = buildControls.querySelector(".build-badge");
    if (buildBadge) {
      badgeOverflowParams = getElementOverflowParams(buildBadge);

      if (badgeOverflowParams.isOverflowed) {
        markMultiline();
        indentMultiline(buildControls);
        buildControls.classList.add("block");
        controlsRepositioned = true;
        setBuildControlWidths();
      }
    }
  }

  if (
    !nameOverflowParams.isOverflowed &&
    !detailsOverflowParams.isOverflowed &&
    !controlsRepositioned
  ) {
    fitToControlsHeight(buildName);
    fitToControlsHeight(buildDetails);
  }

  row.classList.add("overflow-checked");
}

function checkAllRowCellOverflows() {
  if (isRunAsTest) {
    return;
  }

  var dataTable = getDataTable(buildHistoryContainer);
  var rows = dataTable.rows;

  for (var i = 0; i < rows.length; i++) {
    var row = rows[i];
    checkRowCellOverflows(row);
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

const handleFilter = function () {
  loadPage({}, true);
};

const debouncedFilter = debounce(handleFilter, 300);

document.addEventListener("DOMContentLoaded", function () {
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
