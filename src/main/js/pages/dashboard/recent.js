import { updateEntries } from "@/pages/project/build-entries";

const REFRESH_INTERVAL = 5000;
const SECTION = "[data-section]";
const HEADER = ".app-dashboard-section__header";
const BODY = ".app-dashboard-section__body";
const TOGGLE = ".app-dashboard-section__toggle";
const EXPANDED = "app-dashboard-section--expanded";
const EXTRA = "app-dashboard-section__extra";
const BADGE_TEMPLATE = "template.app-dashboard-recent__badge";
const CURRENT_TAB = "[data-testid=dashboard-tabs] a[aria-current=page]";

/**
 * Refreshes the Recent tab's content while the page is visible, and expands or
 * collapses its collapsible sections.
 *
 * A refresh only replaces the rows (and cells of rows) whose server-rendered
 * HTML changed, so menus and tooltips on the other rows stay open. Sections
 * the user expanded stay expanded.
 *
 * @param {HTMLElement} root the element whose `data-rows-url` serves its content
 * @param {{applySubtree?: function(Element): void, fetch?: typeof fetch, interval?: number}} [options]
 * @return {{refresh: function(): Promise<void>, ready: Promise<void>, stop: function(): void}}
 */
export function initRecent(root, options = {}) {
  // Behaviours can be applied to the same element again, which must not start another refresh loop
  if (root.dashboardRecent) {
    return root.dashboardRecent;
  }
  const fetchImpl = options.fetch || window.fetch.bind(window);
  const applySubtree = options.applySubtree || (() => {});
  const interval = options.interval || REFRESH_INTERVAL;
  const url = root.dataset.rowsUrl;
  const expanded = new Set();
  // The server-rendered HTML of each header, as the toggle's label changes client-side
  const headerHtml = new WeakMap();
  let last = root.innerHTML.trim();
  let timeout;

  function rememberHeaders(container) {
    container.querySelectorAll(HEADER).forEach((header) => {
      headerHtml.set(header, header.outerHTML);
    });
  }

  function applyToggles() {
    root.querySelectorAll(TOGGLE).forEach((toggle) => {
      const section = toggle.closest(SECTION);
      const isExpanded = expanded.has(section.dataset.section);
      section.classList.toggle(EXPANDED, isExpanded);
      toggle.textContent = isExpanded
        ? toggle.dataset.hideText
        : toggle.dataset.showText;
      toggle.setAttribute("aria-expanded", String(isExpanded));
    });
  }

  function sectionIds(container) {
    return Array.from(container.querySelectorAll(SECTION))
      .map((section) => section.dataset.section)
      .join(",");
  }

  function replaceAll(incoming) {
    // Inserting the rows through updateEntries records their HTML for the next refresh
    incoming.querySelectorAll(BODY).forEach((body) => {
      const html = body.innerHTML.trim();
      body.replaceChildren();
      if (html) {
        updateEntries(body, html);
      }
    });
    root.replaceChildren(incoming);
    rememberHeaders(incoming);
    applySubtree(incoming);
  }

  /**
   * Shows the badge from the refreshed content on this tab in the tab bar,
   * which is outside the refreshed element.
   */
  function updateBadge(incoming) {
    const template = incoming.querySelector(BADGE_TEMPLATE);
    const tab = document.querySelector(CURRENT_TAB);
    if (!template || !tab) {
      return;
    }
    const badge = template.content.firstElementChild;
    const existing = tab.querySelector(".jenkins-badge");
    if (existing && badge && existing.outerHTML === badge.outerHTML) {
      return;
    }
    existing?.remove();
    if (badge) {
      const added = badge.cloneNode(true);
      tab.append(added);
      applySubtree(added);
    }
  }

  /**
   * Describes the focused control in a row or section header, so that the
   * same control can be focused again if a refresh replaces it.
   */
  function focusedControl() {
    const focused = document.activeElement;
    if (!focused || focused === document.body || !root.contains(focused)) {
      return null;
    }
    const entry = focused.closest("[page-entry-id]");
    const section = focused.closest(SECTION);
    return {
      element: focused,
      entryId: entry ? entry.getAttribute("page-entry-id") : null,
      sectionId: section ? section.dataset.section : null,
      href: focused.getAttribute("href"),
      className: focused.className,
    };
  }

  function restoreFocus(control) {
    if (!control) {
      return;
    }
    if (control.element.isConnected) {
      // Moving a row to reorder it takes the focus from the control in it
      if (document.activeElement !== control.element) {
        control.element.focus({ preventScroll: true });
      }
      return;
    }
    const container = control.entryId
      ? Array.from(root.querySelectorAll("[page-entry-id]")).find(
          (e) => e.getAttribute("page-entry-id") === control.entryId,
        )
      : Array.from(root.querySelectorAll(SECTION)).find(
          (e) => e.dataset.section === control.sectionId,
        );
    const replacement =
      container &&
      Array.from(container.querySelectorAll("a, button")).find(
        (e) =>
          e.getAttribute("href") === control.href &&
          e.className === control.className,
      );
    replacement?.focus({ preventScroll: true });
  }

  /**
   * Expands the section of the focused control's row if the row is now one of
   * the rows a collapsed section hides, for example when a newer build is
   * ranked above it.
   */
  function revealRowOf(control) {
    if (!control || !control.entryId || !control.sectionId) {
      return;
    }
    const row = Array.from(root.querySelectorAll("[page-entry-id]")).find(
      (e) => e.getAttribute("page-entry-id") === control.entryId,
    );
    if (row && row.classList.contains(EXTRA)) {
      expanded.add(control.sectionId);
    }
  }

  function update(html) {
    const control = focusedControl();
    updateContent(html);
    revealRowOf(control);
    // Expand sections first, as a control in a collapsed section can't take focus
    applyToggles();
    restoreFocus(control);
  }

  function updateContent(html) {
    const template = document.createElement("template");
    template.innerHTML = html;
    const incoming = template.content.firstElementChild;
    const current = root.firstElementChild;
    if (!incoming) {
      return;
    }
    updateBadge(incoming);

    const ids = sectionIds(incoming);
    if (!current || !ids || ids !== sectionIds(current)) {
      // Sections appeared or went away, so there is little to keep
      replaceAll(incoming);
      return;
    }

    incoming.querySelectorAll(SECTION).forEach((section) => {
      const existing = current.querySelector(
        `[data-section="${section.dataset.section}"]`,
      );

      const header = section.querySelector(HEADER);
      const existingHeader = existing.querySelector(HEADER);
      if (headerHtml.get(existingHeader) !== header.outerHTML) {
        existingHeader.replaceWith(header);
        headerHtml.set(header, header.outerHTML);
      }

      const inserted = updateEntries(
        existing.querySelector(BODY),
        section.querySelector(BODY).innerHTML.trim(),
      );
      inserted.forEach((element) => applySubtree(element));
    });
  }

  /**
   * @param {boolean} [force] fetch even while the page is hidden, and update
   *   even if the content is unchanged
   */
  function refresh(force = false) {
    if (document.hidden && !force) {
      return Promise.resolve();
    }
    return fetchImpl(url)
      .then((rsp) => (rsp.ok ? rsp.text() : null))
      .then((text) => {
        if (text === null) {
          return;
        }
        const html = text.trim();
        if (force || html !== last) {
          last = html;
          update(html);
        }
      })
      .catch((e) => console.error(e));
  }

  // Whether a refresh is in progress, so that only one chain of refreshes runs
  let refreshing = false;
  let stopped = false;

  function tick(force = false) {
    refreshing = true;
    return refresh(force).then(() => {
      refreshing = false;
      if (!stopped) {
        timeout = window.setTimeout(tick, interval);
      }
    });
  }

  document.addEventListener("visibilitychange", () => {
    if (!document.hidden && !refreshing && !stopped) {
      window.clearTimeout(timeout);
      tick();
    }
  });

  root.addEventListener("click", (event) => {
    const toggle = event.target.closest(TOGGLE);
    if (!toggle) {
      return;
    }
    const id = toggle.closest(SECTION).dataset.section;
    if (expanded.has(id)) {
      expanded.delete(id);
    } else {
      expanded.add(id);
    }
    applyToggles();
  });

  rememberHeaders(root);
  applyToggles();
  // Rows are compared with the HTML the server last sent for them, which only a
  // refresh records, so load once straight away. Later refreshes then leave
  // unchanged rows, and anything open on them, alone.
  const ready = tick(true);
  root.dashboardRecent = {
    refresh: () => refresh(),
    ready,
    stop: () => {
      stopped = true;
      window.clearTimeout(timeout);
    },
  };
  return root.dashboardRecent;
}
