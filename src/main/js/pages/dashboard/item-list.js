import debounce from "lodash/debounce";

const PAGE_SELECTOR = ".app-dashboard-items__page";
// Rows of a list or group, leaving out those inside the folders expanded in it
const OWN_ROWS = `:scope > ${PAGE_SELECTOR} > [data-id]`;
const ROW_SELECTOR = ".app-temporary-list__item";
const LABEL_SELECTOR = ".app-temporary-list__item__label";
const NAME_SELECTOR = ".app-dashboard-items__name";
const TOGGLE_SELECTOR = ".app-dashboard-items__toggle";
const CHILDREN_CLASS = "app-dashboard-items__children";
const MORE_SELECTOR = ".app-dashboard-items__more";
const ERROR_CLASS = "app-dashboard-items__error";
const DEPTH_PROPERTY = "--dashboard-items-depth";
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
 * Clicking a folder's row expands it: the first page of the items inside it
 * loads from the same endpoint, naming the folder, into a group under the
 * row. A page of a folder that has more ends with a "Show more" button, which
 * loads the next. Clicking the row again collapses the group, which keeps
 * what it loaded.
 *
 * A request that fails shows a warning with a button to try again: above the
 * search bar for a search, which also hides the results of the previous one,
 * and at the end of the list for the next page. A folder that is gone, or that
 * the user can no longer see, says so without the button. Starting
 * a new search aborts the requests of the previous one, and drops their
 * responses. The `role="status"` element says how many results a search found.
 *
 * Relies on these elements from the server: `.app-dashboard-items__list`,
 * `.app-dashboard-items__sentinel`, `.app-dashboard-items__status`, the
 * `jenkins-search` input, `#dashboard-items-no-results`, and for each folder
 * row, a `.app-dashboard-items__toggle` button holding the folder's
 * `.app-dashboard-items__name`.
 *
 * @param {HTMLElement} root the `.app-dashboard-items` element
 * @param {{applySubtree?: function(Element): void, fetch?: typeof fetch}} [options]
 * @return {{loadMore: function(): Promise<void>, search: function(string): Promise<void>, toggle: function(HTMLElement): Promise<void>}}
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
  let groups = 0;

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
   * Fetches a page of rows: of the list, or of the folder `folder` names.
   */
  function fetchPage({ start, after, q, folder }, signal) {
    const params = new URLSearchParams({ start: String(start) });
    if (folder) {
      params.set("folder", folder);
    }
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
          const error = new Error(`Failed to load items: ${rsp.status}`);
          error.status = rsp.status;
          throw error;
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
   * the warning of another failure in the same place, or to `focusAfter`,
   * unless the user moved it elsewhere meanwhile.
   *
   * @param {(function(): Promise<void>)|null} retry, or `null` for a folder
   *   that is gone, which trying again cannot load
   * @param {function(Element): void} place adds the warning to the page
   * @param {function(): (HTMLElement|null|undefined)} [focusAfter]
   */
  function showError(retry, place, focusAfter) {
    const error = document.createElement("div");
    error.className = `jenkins-alert jenkins-alert-warning ${ERROR_CLASS}`;
    error.setAttribute("role", "alert");
    const message = document.createElement("span");
    message.textContent = retry
      ? root.dataset.loadFailed
      : root.dataset.folderGone;
    error.append(message);
    if (!retry) {
      place(error);
      return;
    }
    const button = document.createElement("button");
    button.type = "button";
    button.className = "jenkins-button jenkins-button--tertiary";
    button.textContent = root.dataset.tryAgain;
    button.addEventListener("click", () => {
      const hadFocus = error.contains(document.activeElement);
      // Where a warning of another failure would go
      const before = error.previousElementSibling;
      const parent = error.parentElement;
      error.remove();
      retry().then(() => {
        const active = document.activeElement;
        if (!hadFocus || (active && active !== document.body)) {
          return;
        }
        const spot = before
          ? before.nextElementSibling
          : parent?.firstElementChild;
        const again = spot?.classList.contains(ERROR_CLASS)
          ? spot.querySelector("button")
          : null;
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
      () => {
        const rows = list.querySelectorAll(OWN_ROWS);
        // The last row there is when the retry loads none
        const row = rows[shown] || rows[rows.length - 1];
        return row?.querySelector(LABEL_SELECTOR) || searchInput;
      },
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
   * Removes rows that are already in the list or folder, which happens when
   * items are added between loading two pages.
   *
   * @param {Element} page
   * @param {Element} container the list, or the group of a folder's items
   */
  function dropDuplicates(page, container) {
    const shown = new Set(
      Array.from(container.querySelectorAll(OWN_ROWS), (row) => row.dataset.id),
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
        dropDuplicates(page, list);
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

  /**
   * Adds a page of a folder's items to its group. The server ends the page
   * with a button that loads the next one, if there is one.
   */
  function appendChildren(group, page) {
    group
      .querySelector(`:scope > ${PAGE_SELECTOR} > ${MORE_SELECTOR}`)
      ?.remove();
    group.append(page);
    applySubtree(page);
  }

  /**
   * Whether a request failed as the folder it named is gone, or the user can
   * no longer see it.
   */
  function isGone(e) {
    return e.status === 404;
  }

  /**
   * Expands or collapses the folder of a toggle. The first expansion loads the
   * first page of its items.
   */
  function toggle(button) {
    const row = button.closest(ROW_SELECTOR);
    const existing = button.getAttribute("aria-controls");
    const group = existing && document.getElementById(existing);
    if (group) {
      const expand = group.hidden;
      group.hidden = !expand;
      button.setAttribute("aria-expanded", String(expand));
      return Promise.resolve();
    }
    if (button.getAttribute("aria-busy") === "true") {
      return Promise.resolve();
    }
    // A message from an earlier failure is replaced if this one fails too
    const next = row.nextElementSibling;
    if (next?.classList.contains(ERROR_CLASS)) {
      next.remove();
    }
    button.setAttribute("aria-busy", "true");
    const { signal } = controller;
    return fetchPage({ start: 0, folder: row.dataset.id }, signal)
      .then((page) => {
        if (signal.aborted || !page) {
          return;
        }
        const created = document.createElement("div");
        created.className = CHILDREN_CLASS;
        created.id = `dashboard-items-children-${++groups}`;
        created.setAttribute("role", "group");
        const name = row.querySelector(NAME_SELECTOR);
        if (name) {
          created.setAttribute("aria-label", name.textContent.trim());
        }
        // Each level is indented further than the one containing it
        const parent = row.parentElement.closest(`.${CHILDREN_CLASS}`);
        const depth =
          Number(parent?.style.getPropertyValue(DEPTH_PROPERTY) || 0) + 1;
        created.style.setProperty(DEPTH_PROPERTY, String(depth));
        appendChildren(created, page);
        row.after(created);
        button.setAttribute("aria-controls", created.id);
        button.setAttribute("aria-expanded", "true");
      })
      .catch((e) => {
        if (!isAbort(e)) {
          console.error(e);
          showError(
            isGone(e) ? null : () => toggle(button),
            (error) => row.after(error),
            () => button,
          );
        }
      })
      .then(() => button.removeAttribute("aria-busy"));
  }

  /**
   * Loads the next page of a folder's items into its group.
   */
  function showMore(button) {
    const group = button.closest(`.${CHILDREN_CLASS}`);
    const row = group.previousElementSibling;
    const shown = group.querySelectorAll(OWN_ROWS).length;
    // The first row a page loaded, else the last row, if none was left to
    // load, else the folder's toggle
    const firstLoaded = () => {
      const rows = group.querySelectorAll(OWN_ROWS);
      const loaded = rows[shown] || rows[rows.length - 1];
      return (
        loaded?.querySelector(LABEL_SELECTOR) ||
        row.querySelector(TOGGLE_SELECTOR)
      );
    };
    // The warning of an earlier failure is replaced if this one fails too
    const previous = button.previousElementSibling;
    if (previous?.classList.contains(ERROR_CLASS)) {
      previous.remove();
    }
    // Disabling the button takes the focus from it
    const focused = document.activeElement === button;
    // Unless the user moved it elsewhere while the page loaded
    const stillFocused = () => {
      const active = document.activeElement;
      return (
        focused && (!active || active === document.body || active === button)
      );
    };
    button.disabled = true;
    const { signal } = controller;
    return fetchPage(
      {
        start: button.dataset.nextStart,
        after: lastShown(group),
        folder: row.dataset.id,
      },
      signal,
    )
      .then((page) => {
        if (signal.aborted || !page) {
          return;
        }
        dropDuplicates(page, group);
        appendChildren(group, page);
        if (stillFocused()) {
          // The button is gone, so the first row it loaded takes the focus
          firstLoaded()?.focus();
        }
      })
      .catch((e) => {
        if (isAbort(e)) {
          button.disabled = false;
          return;
        }
        console.error(e);
        const refocus = stillFocused();
        if (isGone(e)) {
          // Trying again cannot load more of a folder that is gone
          showError(null, (error) => button.replaceWith(error));
          if (refocus) {
            firstLoaded()?.focus();
          }
          return;
        }
        button.disabled = false;
        if (refocus) {
          button.focus();
        }
        showError(
          () => showMore(button),
          (error) => button.before(error),
          firstLoaded,
        );
      });
  }

  list.addEventListener("click", (event) => {
    const more = event.target.closest(MORE_SELECTOR);
    if (more && list.contains(more)) {
      showMore(more);
      return;
    }
    const button = event.target.closest(TOGGLE_SELECTOR);
    if (button && list.contains(button)) {
      toggle(button);
    }
  });

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

  root.dashboardItemList = { loadMore, search: runSearch, toggle };
  return root.dashboardItemList;
}
