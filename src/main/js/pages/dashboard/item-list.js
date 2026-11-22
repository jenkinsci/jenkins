import debounce from "lodash/debounce";

const PAGE_SELECTOR = ".app-dashboard-items__page";
// Start loading the next page this far before the end of the list scrolls into view
const LOOKAHEAD = "600px";
// Each search walks every folder on the server, so wait for typing to pause
const SEARCH_DELAY = 300;

/**
 * Loads more items into a dashboard item list as the end of the list scrolls
 * into view, and replaces the list when the search query changes.
 *
 * The server renders the first page with the page. Each page is a
 * `.app-dashboard-items__page` element whose `data-next-start` says where the
 * next page starts; it is absent on the last page. Each request also names
 * the last item shown, so the server continues after it.
 *
 * After a page fails to load, the next one is requested only when the end of
 * the list scrolls into view again.
 *
 * @param {HTMLElement} root the `.app-dashboard-items` element
 * @param {{applySubtree?: function(Element): void, fetch?: typeof fetch}} [options]
 * @return {{loadMore: function(): Promise<void>, search: function(string): Promise<void>}}
 */
export function initItemList(root, options = {}) {
  // Behaviours can be applied to the same element again, which must not add more listeners
  if (root.dashboardItemList) {
    return root.dashboardItemList;
  }
  const fetchImpl = options.fetch || window.fetch.bind(window);
  const applySubtree = options.applySubtree || (() => {});
  const url = root.dataset.itemsUrl;
  const list = root.querySelector(".app-dashboard-items__list");
  const sentinel = root.querySelector(".app-dashboard-items__sentinel");
  const searchInput = root.querySelector(".jenkins-search__input");
  const search = searchInput && searchInput.closest(".jenkins-search");
  const noResults = root.querySelector("#dashboard-items-no-results");

  let query = "";
  let loading = false;
  // Incremented on every new search, so responses to older ones are dropped
  let generation = 0;
  // Aborts the requests of the current search when a new one starts
  let controller = new AbortController();

  function nextStart() {
    const pages = list.querySelectorAll(PAGE_SELECTOR);
    const last = pages[pages.length - 1];
    return last ? last.dataset.nextStart : undefined;
  }

  function lastShown() {
    const rows = list.querySelectorAll("[data-id]");
    return rows.length ? rows[rows.length - 1].dataset.id : undefined;
  }

  function fetchPage(start, q, after) {
    const params = new URLSearchParams({ start: String(start) });
    if (after) {
      // Continue after the last row shown, so removing an earlier item doesn't skip one
      params.set("after", after);
    }
    if (q) {
      params.set("search", q);
    }
    return fetchImpl(`${url}?${params}`, { signal: controller.signal })
      .then((rsp) => {
        if (!rsp.ok) {
          throw new Error(`Failed to load items: ${rsp.status}`);
        }
        return rsp.text();
      })
      .then((html) => {
        const template = document.createElement("template");
        template.innerHTML = html.trim();
        return template.content.firstElementChild;
      });
  }

  function observe() {
    if (observer) {
      // Re-observing reports the sentinel's current state, so another page
      // loads straight away if the list still doesn't fill the screen
      observer.unobserve(sentinel);
      observer.observe(sentinel);
    }
  }

  function logError(e) {
    if (e.name !== "AbortError") {
      console.error(e);
    }
  }

  /**
   * Removes rows that are already in the list, which happens when items are
   * added between loading two pages.
   */
  function dropDuplicates(page) {
    const shown = new Set(
      Array.from(list.querySelectorAll("[data-id]"), (row) => row.dataset.id),
    );
    page.querySelectorAll("[data-id]").forEach((row) => {
      if (shown.has(row.dataset.id)) {
        row.remove();
      }
    });
  }

  function loadMore() {
    const start = nextStart();
    if (loading || start === undefined || start === "") {
      return Promise.resolve();
    }
    loading = true;
    const current = generation;
    let failed = false;
    return fetchPage(start, query, lastShown())
      .then((page) => {
        if (current !== generation || !page) {
          return;
        }
        dropDuplicates(page);
        list.append(page);
        applySubtree(page);
      })
      .catch((e) => {
        failed = true;
        logError(e);
      })
      .then(() => {
        if (current === generation) {
          loading = false;
          if (!failed) {
            observe();
          }
        }
      });
  }

  function runSearch(value) {
    const q = value.trim();
    const current = ++generation;
    controller.abort();
    controller = new AbortController();
    loading = true;
    return fetchPage(0, q)
      .then((page) => {
        if (current !== generation || !page) {
          return;
        }
        query = q;
        list.replaceChildren(page);
        applySubtree(page);
        const empty = page.dataset.count === "0";
        list.classList.toggle("jenkins-hidden", empty);
        if (noResults) {
          noResults.classList.toggle("jenkins-hidden", !empty);
        }
      })
      .catch(logError)
      .then(() => {
        if (current === generation) {
          loading = false;
          if (search) {
            search.classList.remove("jenkins-search--loading");
          }
          observe();
        }
      });
  }

  const observer =
    sentinel && "IntersectionObserver" in window
      ? new IntersectionObserver(
          (entries) => {
            if (entries.some((entry) => entry.isIntersecting)) {
              loadMore();
            }
          },
          { rootMargin: `0px 0px ${LOOKAHEAD} 0px` },
        )
      : null;
  if (observer) {
    observer.observe(sentinel);
  }

  if (searchInput) {
    const debouncedSearch = debounce(
      () => runSearch(searchInput.value),
      SEARCH_DELAY,
    );
    searchInput.addEventListener("input", () => {
      if (search) {
        search.classList.add("jenkins-search--loading");
      }
      debouncedSearch();
    });
  }

  root.dashboardItemList = { loadMore, search: runSearch };
  return root.dashboardItemList;
}
