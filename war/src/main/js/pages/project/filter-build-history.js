import debounce from "lodash/debounce";
import behaviorShim from "@/util/behavior-shim";

const buildHistoryPage = document.getElementById("buildHistoryPage");
const pageSearch = buildHistoryPage.querySelector(".jenkins-search");
const pageSearchInput = buildHistoryPage.querySelector("input");
const ajaxUrl = buildHistoryPage.getAttribute("page-ajax");
const card = document.querySelector("#jenkins-builds");
const contents = card.querySelector("#jenkins-build-history");
const container = card.querySelector(".app-builds-container");
const noBuilds = card.querySelector("#no-builds");

const controls = document.querySelector("#controls");
const up = document.querySelector("#up");
const down = document.querySelector("#down");

// const updateBuildsRefreshInterval = 5000;

/**
 * Refresh the 'Builds' card
 * @param {QueryParameters}  options
 */
function load(options = {}) {
  /** @type {QueryParameters} */
  const params = Object.assign({}, options, { search: pageSearchInput.value });

  fetch(ajaxUrl + toQueryString(params)).then((rsp) => {
    if (rsp.ok) {
      rsp.text().then((responseText) => {
        container.classList.remove("app-builds-container--loading");
        pageSearch.classList.remove("jenkins-search--loading");

        // TODO
        if (responseText.trim() === "") {
          contents.innerHTML = "";
          noBuilds.style.display = "block";
          return;
        }

        // TODO
        contents.innerHTML = responseText;
        noBuilds.style.display = "none";
        behaviorShim.applySubtree(contents);

        // TODO
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
    }
  });
}

/**
 * Shows/hides the card's pagination controls depending on the passed parameter
 * @param {CardControlsOptions}  parameters
 */
function updateCardControls(parameters) {
  controls.classList.toggle(
    "jenkins-!-display-none",
    !parameters.pageHasUp && !parameters.pageHasDown,
  );
  up.classList.toggle(
    "app-builds-container__button--disabled",
    !parameters.pageHasUp,
  );
  down.classList.toggle(
    "app-builds-container__button--disabled",
    !parameters.pageHasDown,
  );

  buildHistoryPage.dataset.pageEntryNewest = parameters.pageEntryNewest;
  buildHistoryPage.dataset.pageEntryOldest = parameters.pageEntryOldest;
}

up.addEventListener("click", () => {
  load({ "newer-than": buildHistoryPage.dataset.pageEntryNewest });
});

down.addEventListener("click", () => {
  // cancelRefreshTimeout();
  load({ "older-than": buildHistoryPage.dataset.pageEntryOldest });
});

// setInterval(() => {
//   loadPage({})
// }, updateBuildsRefreshInterval)

document.addEventListener("DOMContentLoaded", function () {
  pageSearchInput.addEventListener("input", function () {
    container.classList.add("app-builds-container--loading");
    pageSearch.classList.add("jenkins-search--loading");
    debounce(() => {
      load();
    }, 300);
  });

  load();
});
