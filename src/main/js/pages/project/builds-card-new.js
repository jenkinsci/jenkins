import debounce from "lodash/debounce";
import tippy from "tippy.js";
import BehaviorShim from "@/util/behavior-shim";
import Templates from "@/components/dropdowns/templates";

const SELECTED_STATUS_ITEM_CLASS = "jenkins-dropdown__item--selected";
const MUTED_STATUS_ITEM_CLASS =
  "app-temporary-list__filter-panel__items__item--muted";

BehaviorShim.specify(
  "#buildHistoryPage",
  "build-history-page",
  1000,
  (buildHistoryPage) => {
    // Card/item controls
    const pageSearchInput = document.querySelector("#build-history-search");
    const pageSearch = pageSearchInput.closest(".jenkins-search");
    const pageSearchContainer = pageSearchInput.closest(
      ".jenkins-search-container",
    );
    const statusFilterButton = document.querySelector(
      "#build-status-filter-button",
    );
    const statusFilterTemplate = document.querySelector(
      "#build-status-filter-template",
    );
    const ajaxUrl = buildHistoryPage.getAttribute("page-ajax");
    const container = document.querySelector("#jenkins-builds");
    const contents = container.querySelector("#jenkins-build-history");
    const loadingBuilds = container.querySelector("#loading-builds");
    const noBuilds = buildHistoryPage.querySelector("#no-builds");
    const noBuildsYet = buildHistoryPage.querySelector("#no-builds-yet");

    // Pagination controls
    const paginationControls = document.querySelector("#controls");
    const paginationPrevious = document.querySelector("#up");
    const paginationNext = document.querySelector("#down");

    // Refresh variables
    let buildRefreshTimeout;
    const updateBuildsRefreshInterval = 5000;

    // Status filter state. Empty means "show everything".
    let selectedStatuses = new Set();

    /**
     * There's nothing to search/filter until the job has had a first build, so
     * hide the controls entirely until then.
     * @param {boolean}  visible
     */
    function setSearchControlsVisible(visible) {
      pageSearchContainer.classList.toggle("jenkins-hidden", !visible);
      statusFilterButton.classList.toggle("jenkins-hidden", !visible);
    }

    /**
     * Refresh the 'Builds' card
     * @param {QueryParameters}  options
     */
    function load(options = {}) {
      /** @type {QueryParameters} */
      cancelRefreshTimeout();
      const params = Object.assign({}, options, {
        search: pageSearchInput.value,
        status: Array.from(selectedStatuses).join(","),
      });
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
            container.classList.remove("app-temporary-list--loading");
            debouncedSpinner.cancel();
            pageSearch.classList.remove("jenkins-search--loading");

            // A search term or an active status filter narrows the results, so an
            // empty response then means "no results", not "no builds ever"
            const isFiltered = Boolean(params.search) || Boolean(params.status);

            // Show the 'No results found' notice if there are no builds
            if (responseText.trim() === "") {
              contents.innerHTML = "";
              container.classList.add("jenkins-hidden");
              if (isFiltered) {
                noBuilds.classList.remove("jenkins-hidden");
              } else {
                noBuildsYet.classList.remove("jenkins-hidden");
                setSearchControlsVisible(false);
              }
              loadingBuilds.style.display = "none";
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
            container.classList.remove("jenkins-hidden");
            noBuilds.classList.add("jenkins-hidden");
            if (!isFiltered) {
              noBuildsYet.classList.add("jenkins-hidden");
              setSearchControlsVisible(true);
            }
            loadingBuilds.style.display = "none";
            BehaviorShim.applySubtree(contents);

            // Show the card controls
            const dataset = contents.firstElementChild.dataset;
            updateCardControls({
              pageHasUp: dataset.pageHasUp === "true",
              pageHasDown: dataset.pageHasDown === "true",
              pageEntryNewest: dataset.pageEntryNewest,
              pageEntryOldest: dataset.pageEntryOldest,
            });
          });
        } else {
          console.error(
            "Failed to load 'Builds' card, response from API is:",
            rsp,
          );
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
      paginationPrevious.disabled = !parameters.pageHasUp;
      paginationNext.disabled = !parameters.pageHasDown;

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

    // Only show the search bar's spinner if a load takes a while - avoids a
    // flash of the spinner for fast responses, same as the command palette.
    const debouncedSpinner = debounce(() => {
      pageSearch.classList.add("jenkins-search--loading");
    }, 150);

    const debouncedLoad = debounce(() => {
      load();
    }, 150);

    pageSearchInput.addEventListener("input", function () {
      container.classList.add("app-temporary-list--loading");
      debouncedSpinner();
      debouncedLoad();
    });

    // Build the filter panel once and keep it around for the lifetime of the
    // page - tippy just shows/hides it, so listeners are only attached once.
    const statusFilterPanel =
      statusFilterTemplate.content.cloneNode(true).firstElementChild;

    /**
     * Applies the current selection to the filter panel's rows, the Reset link,
     * and the funnel trigger button.
     */
    function renderStatusSelection() {
      const hasSelection = selectedStatuses.size > 0;

      statusFilterPanel.querySelectorAll("[data-status]").forEach((item) => {
        const isSelected = selectedStatuses.has(item.dataset.status);
        item.classList.toggle(SELECTED_STATUS_ITEM_CLASS, isSelected);
        item.classList.toggle(
          MUTED_STATUS_ITEM_CLASS,
          hasSelection && !isSelected,
        );
      });

      statusFilterPanel
        .querySelector(".app-temporary-list__filter-panel__reset")
        .classList.toggle("jenkins-hidden", !hasSelection);

      statusFilterButton.classList.toggle(
        "jenkins-button--tertiary",
        !hasSelection,
      );
      statusFilterButton.classList.toggle(
        "jenkins-!-accent-color",
        hasSelection,
      );
    }

    statusFilterPanel
      .querySelector(".app-temporary-list__filter-panel__items")
      .addEventListener("click", (event) => {
        const item = event.target.closest("[data-status]");
        if (!item) {
          return;
        }

        const status = item.dataset.status;
        if (selectedStatuses.size === 0) {
          // Nothing selected yet (i.e. everything shown) - isolate to just this one
          selectedStatuses = new Set([status]);
        } else if (selectedStatuses.has(status)) {
          selectedStatuses.delete(status);
        } else {
          selectedStatuses.add(status);
        }

        renderStatusSelection();
        container.classList.add("app-temporary-list--loading");
        debouncedSpinner();
        load();
      });

    statusFilterPanel
      .querySelector(".app-temporary-list__filter-panel__reset")
      .addEventListener("click", () => {
        selectedStatuses = new Set();
        renderStatusSelection();
        container.classList.add("app-temporary-list--loading");
        debouncedSpinner();
        load();
      });

    tippy(
      statusFilterButton,
      Object.assign({}, Templates.dropdown(), {
        content: statusFilterPanel,
      }),
    );

    container.classList.add("app-temporary-list--loading");
    load();

    window.addEventListener("focus", function () {
      load();
    });
  },
);
