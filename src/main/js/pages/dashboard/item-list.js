import debounce from "lodash/debounce";

const PAGE_SELECTOR = ".app-dashboard-items__page";
// Rows of the list
const OWN_ROWS = `:scope > ${PAGE_SELECTOR} > [data-id]`;
const ERROR_CLASS = "app-dashboard-items__error";
const LABEL_SELECTOR = ".app-temporary-list__item__label";
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
 * A request that fails shows a warning with a button to try again: above the
 * search bar for a search, which also hides the results of the previous one,
 * and at the end of the list for the next page. Starting
 * a new search aborts the requests of the previous one, and drops their
 * responses. The `role="status"` element says how many results a search found.
 *
 * Relies on these elements from the server: `.app-dashboard-items__list`,
 * `.app-dashboard-items__sentinel`, `.app-dashboard-items__status`, the
 * `jenkins-search` input and `#dashboard-items-no-results`.
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
  const status = root.querySelector(".app-dashboard-items__status");
  const searchInput = root.querySelector(".jenkins-search__input");
  const search = searchInput && searchInput.closest(".jenkins-search");
  // The search bar's outer wrapper, which the warning for a failed search goes before
  const searchBar =
    search && (search.closest(".jenkins-search-container") || search);
  const noResults = root.querySelector("#dashboard-items-no-results");

  let query = "";
  let loading = false;
  // Whether the last search failed, which stops paging until a search succeeds
  let searchFailed = false;
  // Whether loading the next page failed, which stops paging until its button tries again
  let pageFailed = false;
  // Aborts the requests of the current search when a new one starts
  let controller = new AbortController();

  function lastShown(container) {
    const rows = container.querySelectorAll(OWN_ROWS);
    return rows.length ? rows[rows.length - 1].dataset.id : undefined;
  }

  function nextStart() {
    const pages = list.querySelectorAll(`:scope > ${PAGE_SELECTOR}`);
    const last = pages[pages.length - 1];
    return last ? last.dataset.nextStart : undefined;
  }

  /**
   * Fetches a page of rows.
   */
  function fetchPage({ start, after, q }, signal) {
    const params = new URLSearchParams({ start: String(start) });
    if (after) {
      // Continue after the last row shown, so removing an earlier item doesn't skip one
      params.set("after", after);
    }
    if (q) {
      params.set("search", q);
    }
    return fetchImpl(`${url}?${params}`, { signal })
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

  function isAbort(e) {
    return e.name === "AbortError";
  }

  /**
   * Says that something failed to load, with a button that removes the
   * warning and tries again. If the button had the focus, the focus moves to
   * the warning of another failure, or to `focusAfter`, unless the user moved
   * it elsewhere meanwhile.
   *
   * @param {function(): Promise<void>} retry
   * @param {function(Element): void} place adds the warning to the page
   * @param {function(): (HTMLElement|null|undefined)} focusAfter
   */
  function showError(retry, place, focusAfter) {
    const error = document.createElement("div");
    error.className = `jenkins-alert jenkins-alert-warning ${ERROR_CLASS}`;
    error.setAttribute("role", "alert");
    const message = document.createElement("span");
    message.textContent = root.dataset.loadFailed;
    error.append(message);
    const button = document.createElement("button");
    button.type = "button";
    button.className = "jenkins-button jenkins-button--tertiary";
    button.textContent = root.dataset.tryAgain;
    button.addEventListener("click", () => {
      const hadFocus = error.contains(document.activeElement);
      error.remove();
      retry().then(() => {
        const active = document.activeElement;
        if (!hadFocus || (active && active !== document.body)) {
          return;
        }
        const again = root.querySelector(`.${ERROR_CLASS} button`);
        (again || focusAfter())?.focus();
      });
    });
    error.append(button);
    place(error);
  }

  function clearListError() {
    root.querySelector(`:scope > .${ERROR_CLASS}`)?.remove();
  }

  function showListError(retry) {
    clearListError();
    // The first row the retry loads
    const shown = list.querySelectorAll(OWN_ROWS).length;
    showError(
      retry,
      (error) => sentinel.before(error),
      () =>
        list.querySelectorAll(OWN_ROWS)[shown]?.querySelector(LABEL_SELECTOR),
    );
  }

  function showSearchError(retry) {
    clearListError();
    // The results on the page are for the previous query
    list.classList.add("jenkins-hidden");
    if (noResults) {
      noResults.classList.add("jenkins-hidden");
    }
    if (status) {
      status.textContent = "";
    }
    showError(
      retry,
      (error) => (searchBar || list).before(error),
      () => searchInput,
    );
  }

  function observe() {
    if (observer) {
      // Re-observing reports the sentinel's current state, so another page
      // loads straight away if the list still doesn't fill the screen
      observer.unobserve(sentinel);
      observer.observe(sentinel);
    }
  }

  /**
   * Removes rows that are already in the list, which happens when items are
   * added between loading two pages.
   */
  function dropDuplicates(page) {
    const shown = new Set(
      Array.from(list.querySelectorAll(OWN_ROWS), (row) => row.dataset.id),
    );
    page.querySelectorAll("[data-id]").forEach((row) => {
      if (shown.has(row.dataset.id)) {
        row.remove();
      }
    });
  }

  function loadMore() {
    const start = nextStart();
    if (
      loading ||
      searchFailed ||
      pageFailed ||
      start === undefined ||
      start === ""
    ) {
      return Promise.resolve();
    }
    loading = true;
    const { signal } = controller;
    let failed = false;
    list.setAttribute("aria-busy", "true");
    return fetchPage({ start, q: query, after: lastShown(list) }, signal)
      .then((page) => {
        if (signal.aborted || !page) {
          return;
        }
        clearListError();
        dropDuplicates(page);
        list.append(page);
        applySubtree(page);
      })
      .catch((e) => {
        // A request a new search superseded must not change the page
        if (!isAbort(e) && !signal.aborted) {
          failed = true;
          pageFailed = true;
          console.error(e);
          showListError(() => {
            pageFailed = false;
            return loadMore();
          });
        }
      })
      .then(() => {
        if (!signal.aborted) {
          loading = false;
          list.removeAttribute("aria-busy");
          // After a failure, the button tries again
          if (!failed) {
            observe();
          }
        }
      });
  }

  /**
   * Says how many results a search found.
   */
  function announce(q, page) {
    if (!status) {
      return;
    }
    if (!q) {
      status.textContent = "";
      return;
    }
    const count = Number(page.dataset.count);
    const more = page.dataset.nextStart;
    if (count === 0) {
      status.textContent = root.dataset.noResults;
    } else {
      const template = more ? root.dataset.moreResults : root.dataset.results;
      status.textContent = template.replace("{0}", String(count));
    }
  }

  function runSearch(value) {
    const q = value.trim();
    controller.abort();
    controller = new AbortController();
    const { signal } = controller;
    loading = true;
    searchFailed = false;
    pageFailed = false;
    let failed = false;
    clearListError();
    // A page that was loading is aborted, so it won't clear this itself
    list.removeAttribute("aria-busy");
    return fetchPage({ start: 0, q }, signal)
      .then((page) => {
        if (signal.aborted || !page) {
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
        announce(q, page);
      })
      .catch((e) => {
        if (!isAbort(e) && !signal.aborted) {
          failed = true;
          searchFailed = true;
          console.error(e);
          showSearchError(() => runSearch(value));
        }
      })
      .then(() => {
        if (!signal.aborted) {
          loading = false;
          if (search) {
            search.classList.remove("jenkins-search--loading");
          }
          // After a failure, the list is hidden until the button tries again
          if (!failed) {
            observe();
          }
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
