import debounce from "lodash/debounce";
import behaviorShim from "@/util/behavior-shim";

// Card/item controls
const buildHistoryPage = document.getElementById("buildHistoryPage");
const pageSearch = buildHistoryPage.querySelector(".jenkins-search");
const pageSearchInput = buildHistoryPage.querySelector("input");
const ajaxUrl = buildHistoryPage.getAttribute("page-ajax");
const card = document.querySelector("#jenkins-builds");
const contents = card.querySelector("#jenkins-build-history");
const container = card.querySelector(".app-builds-container");
const noBuilds = card.querySelector("#no-builds");

// Pagination controls
const paginationControls = document.querySelector("#controls");
const paginationPrevious = document.querySelector("#up");
const paginationNext = document.querySelector("#down");

// Refresh variables
let buildRefreshTimeout;
const updateBuildsRefreshInterval = 5000;

/**
 * Refresh the 'Builds' card
 * @param {QueryParameters}  options
 */
function load(options = {}) {
  /** @type {QueryParameters} */
  cancelRefreshTimeout();
  const params = Object.assign({}, options, { search: pageSearchInput.value });
  const paginationOrFirst =
    buildHistoryPage.dataset.pageHasUp === "false" ||
    "older-than" in params ||
    "newer-than" in params;

  // Avoid fetching if the page isn't visible
  if (document.hidden) {
    return;
  }

  createRefreshTimeout();

  // When we're not on the first page and this is not a load due to pagination
  // we need to set the correct value for older-than so we fetch the same set of runs
  if (!paginationOrFirst) {
    params["older-than"] = (
      BigInt(buildHistoryPage.dataset.pageEntryNewest) + 1n
    ).toString();
  }

  fetch(ajaxUrl + toQueryString(params)).then((rsp) => {
    if (rsp.ok) {
      rsp.text().then((responseText) => {
        container.classList.remove("app-builds-container--loading");
        pageSearch.classList.remove("jenkins-search--loading");

        // Show the 'No builds' text if there are no builds
        if (responseText.trim() === "") {
          contents.innerHTML = "";
          noBuilds.style.display = "block";
          updateCardControls({
            pageHasUp: false,
            pageHasDown: false,
            pageEntryNewest: false,
            pageEntryOldest: false,
          });
          return;
        }

        // Show the refreshed builds list
        contents.innerHTML = responseText;
        noBuilds.style.display = "none";
        behaviorShim.applySubtree(contents);

        // Show the card controls
        const div = document.createElement("div");
        div.innerHTML = responseText;
        const innerChild = div.children[0];
        updateCardControls({
          pageHasUp: innerChild.dataset.pageHasUp === "true",
          pageHasDown: innerChild.dataset.pageHasDown === "true",
          pageEntryNewest: innerChild.dataset.pageEntryNewest,
          pageEntryOldest: innerChild.dataset.pageEntryOldest,
        });
      });
    } else {
      console.error("Failed to load 'Builds' card, response from API is:", rsp);
    }
  });
}

/**
 * Shows/hides the card's pagination controls depending on the passed parameter
 * @param {CardControlsOptions}  parameters
 */
function updateCardControls(parameters) {
  paginationControls.classList.toggle(
    "jenkins-hidden",
    !parameters.pageHasUp && !parameters.pageHasDown,
  );
  paginationPrevious.classList.toggle(
    "app-builds-container__button--disabled",
    !parameters.pageHasUp,
  );
  paginationNext.classList.toggle(
    "app-builds-container__button--disabled",
    !parameters.pageHasDown,
  );

  buildHistoryPage.dataset.pageEntryNewest = parameters.pageEntryNewest;
  buildHistoryPage.dataset.pageEntryOldest = parameters.pageEntryOldest;
  buildHistoryPage.dataset.pageHasUp = parameters.pageHasUp;
}

paginationPrevious.addEventListener("click", () => {
  load({ "newer-than": buildHistoryPage.dataset.pageEntryNewest });
});

paginationNext.addEventListener("click", () => {
  load({ "older-than": buildHistoryPage.dataset.pageEntryOldest });
});

function createRefreshTimeout() {
  cancelRefreshTimeout();
  buildRefreshTimeout = window.setTimeout(
    () => load(),
    updateBuildsRefreshInterval,
  );
}

function cancelRefreshTimeout() {
  if (buildRefreshTimeout) {
    window.clearTimeout(buildRefreshTimeout);
    buildRefreshTimeout = undefined;
  }
}

const debouncedLoad = debounce(() => {
  load();
}, 150);

document.addEventListener("DOMContentLoaded", function () {
  pageSearchInput.addEventListener("input", function () {
    container.classList.add("app-builds-container--loading");
    pageSearch.classList.add("jenkins-search--loading");
    debouncedLoad();
  });

  load();

  window.addEventListener("focus", function () {
    load();
  });
});
