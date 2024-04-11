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
          "page-has-up": innerChild.getAttribute("page-has-up") === "true",
          "page-has-down": innerChild.getAttribute("page-has-down") === "true",
          "page-entry-newest": innerChild.getAttribute("page-entry-newest"),
          "page-entry-oldest": innerChild.getAttribute("page-entry-oldest"),
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
  const pageHasUp = parameters["page-has-up"];
  const pageHasDown = parameters["page-has-down"];

  controls.classList.toggle(
    "jenkins-!-display-none",
    !pageHasUp && !pageHasDown,
  );

  up.classList.toggle("app-builds-container__button--disabled", !pageHasUp);
  down.classList.toggle("app-builds-container__button--disabled", !pageHasDown);

  buildHistoryPage.setAttribute("page-has-up", pageHasUp.toString());
  buildHistoryPage.setAttribute("page-has-down", pageHasDown.toString());
  buildHistoryPage.setAttribute(
    "page-entry-newest",
    parameters["page-entry-newest"],
  );
  buildHistoryPage.setAttribute(
    "page-entry-oldest",
    parameters["page-entry-oldest"],
  );
}

up.addEventListener("click", () => {
  load({ "newer-than": buildHistoryPage.getAttribute("page-entry-newest") });
});

down.addEventListener("click", () => {
  // cancelRefreshTimeout();
  load({ "older-than": buildHistoryPage.getAttribute("page-entry-oldest") });
});

const handleFilter = function () {
  load();
};

const debouncedFilter = debounce(handleFilter, 300);

// setInterval(() => {
//   loadPage({})
// }, updateBuildsRefreshInterval)

document.addEventListener("DOMContentLoaded", function () {
  pageSearchInput.addEventListener("input", function () {
    container.classList.add("app-builds-container--loading");
    pageSearch.classList.add("jenkins-search--loading");
    debouncedFilter();
  });

  load();
});
