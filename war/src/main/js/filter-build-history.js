import debounce from "lodash/debounce";

const buildHistoryPage = document.getElementById("buildHistoryPage");
const pageSearch = buildHistoryPage.querySelector(
  ".jenkins-search",
);const pageSearchInput = buildHistoryPage.querySelector(
  "input",
);
const ajaxUrl = buildHistoryPage.getAttribute("page-ajax");
const card = document.querySelector("#jenkins-builds");
const contents = card.querySelector("#jenkins-build-history");
const container = card.querySelector(".app-builds-container");
const noBuilds = card.querySelector("#no-builds")

const up = document.querySelector("#up");
const down = document.querySelector("#down");

const updateBuildsRefreshInterval = 5000;

function updatePageParams(dataTable) {
  const pageHasUp = dataTable.getAttribute("page-has-up");
  const pageHasDown = dataTable.getAttribute("page-has-down");

  up.classList.toggle("app-builds-container__button--disabled", pageHasUp === "false");
  down.classList.toggle("app-builds-container__button--disabled", pageHasDown === "false");

  buildHistoryPage.setAttribute(
    "page-has-up",
    pageHasUp
  );
  buildHistoryPage.setAttribute(
    "page-has-down",
    pageHasDown
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

function getNewestEntryId() {
  return buildHistoryPage.getAttribute("page-entry-newest");
}
function getOldestEntryId() {
  return buildHistoryPage.getAttribute("page-entry-oldest");
}

up.addEventListener('click', () => {
  loadPage({ "newer-than": getNewestEntryId() });
})

down.addEventListener('click', () => {
  // cancelRefreshTimeout();
  loadPage({ "older-than": getOldestEntryId() });
})

function loadPage(options) {
  const params = {
    ...options,
    search: pageSearchInput.value
  };

  // console.log(getNewestEntryId())
  // console.log(getOldestEntryId())

  // newerThan
  // loadPage({ "newer-than": getNewestEntryId() });
  // loadPage({ "older-than": getOldestEntryId() });

  fetch(ajaxUrl + toQueryString(params)).then((rsp) => {
    if (rsp.ok) {
      rsp.text().then((responseText) => {
        container.classList.remove("app-builds-container--loading");
        pageSearch.classList.remove("jenkins-search--loading");

        if (responseText.trim() === "") {
          contents.innerHTML = "";
          noBuilds.style.display = "block";
          return;
        }

        if (!document.startViewTransition) {
          contents.innerHTML = responseText;
          noBuilds.style.display = "none";
          Behaviour.applySubtree(contents);
        } else {
          document.startViewTransition(() => {
            contents.innerHTML = responseText;
            noBuilds.style.display = "none";
            Behaviour.applySubtree(contents);
          });
        }

        var div = document.createElement("div");
        div.innerHTML = responseText;
        updatePageParams(div.children[0]);
      });
    }
  });
}

const handleFilter = function () {
  loadPage({}, true);
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

  loadPage({});
});
