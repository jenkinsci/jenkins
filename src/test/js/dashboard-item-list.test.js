import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { initItemList } from "@/pages/dashboard/item-list";
import { initRecent } from "@/pages/dashboard/recent";

function page(names, nextStart) {
  const next = nextStart === undefined ? "" : ` data-next-start="${nextStart}"`;
  const rows = names
    .map(
      (name) =>
        `<div class="app-temporary-list__item" data-id="${name}"><a class="app-temporary-list__item__label">${name}</a></div>`,
    )
    .join("");
  return `<div class="app-dashboard-items__page"${next} data-count="${names.length}">${rows}</div>`;
}

function labels(root) {
  return Array.from(
    root.querySelectorAll(".app-temporary-list__item__label"),
  ).map((e) => e.textContent);
}

function response(html) {
  return Promise.resolve({ ok: true, text: () => Promise.resolve(html) });
}

describe("dashboard item list", () => {
  let root;
  let observers;

  beforeEach(() => {
    observers = [];
    window.IntersectionObserver = class {
      constructor(callback) {
        this.callback = callback;
        this.observe = vi.fn();
        this.unobserve = vi.fn();
        observers.push(this);
      }
      intersect() {
        this.callback([{ isIntersecting: true }]);
      }
    };
    document.body.innerHTML = `
      <div class="app-dashboard-items" data-items-url="/jenkins/all/items">
        <div class="jenkins-search"><input class="jenkins-search__input" /></div>
        <div id="dashboard-items-no-results" class="jenkins-hidden"></div>
        <div class="app-temporary-list app-dashboard-items__list">${page(["a", "b"], 2)}</div>
        <div class="app-dashboard-items__sentinel"></div>
      </div>`;
    root = document.querySelector(".app-dashboard-items");
  });

  afterEach(() => {
    delete window.IntersectionObserver;
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("loads the next page when the end of the list scrolls into view", async () => {
    const fetch = vi.fn(() => response(page(["c", "d"], 4)));
    const applySubtree = vi.fn();
    const list = initItemList(root, { fetch, applySubtree });

    observers[0].intersect();
    await vi.waitFor(() => expect(labels(root)).toEqual(["a", "b", "c", "d"]));

    expect(fetch).toHaveBeenCalledWith(
      "/jenkins/all/items?start=2&after=b",
      expect.anything(),
    );
    expect(applySubtree).toHaveBeenCalledTimes(1);
    // Re-observed, so another page loads if the list still fits on screen
    expect(observers[0].observe).toHaveBeenCalledTimes(2);

    await list.loadMore();
    expect(fetch).toHaveBeenLastCalledWith(
      "/jenkins/all/items?start=4&after=d",
      expect.anything(),
    );
  });

  it("initialises an element only once", () => {
    const fetch = vi.fn();
    const first = initItemList(root, { fetch });
    const second = initItemList(root, { fetch });

    expect(second).toBe(first);
    expect(observers).toHaveLength(1);
  });

  it("stops after the last page", async () => {
    const fetch = vi.fn(() => response(page(["c"])));
    const list = initItemList(root, { fetch });

    await list.loadMore();
    await list.loadMore();

    expect(fetch).toHaveBeenCalledTimes(1);
    expect(labels(root)).toEqual(["a", "b", "c"]);
  });

  it("replaces the list with search results", async () => {
    const fetch = vi.fn(() => response(page(["team » deploy"], 1)));
    const list = initItemList(root, { fetch });

    await list.search(" deploy ");

    expect(fetch).toHaveBeenCalledWith(
      "/jenkins/all/items?start=0&search=deploy",
      expect.anything(),
    );
    expect(labels(root)).toEqual(["team » deploy"]);

    // Further pages keep the query
    await list.loadMore();
    expect(fetch).toHaveBeenLastCalledWith(
      "/jenkins/all/items?start=1&after=team+%C2%BB+deploy&search=deploy",
      expect.anything(),
    );
  });

  it("shows a notice when nothing matches", async () => {
    const fetch = vi.fn(() => response(page([])));
    const list = initItemList(root, { fetch });

    await list.search("nothing");

    expect(
      root
        .querySelector("#dashboard-items-no-results")
        .classList.contains("jenkins-hidden"),
    ).toBe(false);
    expect(
      root
        .querySelector(".app-dashboard-items__list")
        .classList.contains("jenkins-hidden"),
    ).toBe(true);
  });

  it("waits for the list end to scroll into view again after a failure", async () => {
    const fetch = vi
      .fn()
      .mockImplementationOnce(() => Promise.resolve({ ok: false, status: 500 }))
      .mockImplementation(() => response(page(["c"])));
    vi.spyOn(console, "error").mockImplementation(() => {});
    initItemList(root, { fetch });

    observers[0].intersect();
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    await new Promise((resolve) => setTimeout(resolve));
    // Not re-observed, which would retry straight away
    expect(observers[0].observe).toHaveBeenCalledTimes(1);

    observers[0].intersect();
    await vi.waitFor(() => expect(labels(root)).toEqual(["a", "b", "c"]));
  });

  it("skips items already in the list", async () => {
    const fetch = vi.fn(() => response(page(["b", "c"])));
    const list = initItemList(root, { fetch });

    await list.loadMore();

    expect(labels(root)).toEqual(["a", "b", "c"]);
  });

  it("keeps the previous query when a search fails", async () => {
    const fetch = vi
      .fn()
      .mockImplementationOnce(() => Promise.resolve({ ok: false, status: 500 }))
      .mockImplementation(() => response(page(["c"])));
    vi.spyOn(console, "error").mockImplementation(() => {});
    const list = initItemList(root, { fetch });

    await list.search("deploy");
    await list.loadMore();

    expect(labels(root)).toEqual(["a", "b", "c"]);
    expect(fetch).toHaveBeenLastCalledWith(
      "/jenkins/all/items?start=2&after=b",
      expect.anything(),
    );
  });

  it("aborts the requests of a superseded search", async () => {
    const signals = [];
    const fetch = vi.fn((url, { signal }) => {
      signals.push(signal);
      return response(page(["x"]));
    });
    const list = initItemList(root, { fetch });

    const first = list.search("o");
    await list.search("ne");
    await first;

    expect(signals[0].aborted).toBe(true);
    expect(signals[1].aborted).toBe(false);
  });

  it("drops results of a search that was superseded", async () => {
    let resolveFirst;
    const fetch = vi
      .fn()
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirst = () =>
              resolve({ ok: true, text: () => Promise.resolve(page(["old"])) });
          }),
      )
      .mockImplementationOnce(() => response(page(["new"])));
    const list = initItemList(root, { fetch });

    const first = list.search("o");
    await list.search("ne");
    resolveFirst();
    await first;

    expect(labels(root)).toEqual(["new"]);
  });
});

function row(id, text, extra = false) {
  return `<div class="app-temporary-list__item${extra ? " app-dashboard-section__extra" : ""}" page-entry-id="${id}"><div class="app-build-item"><div class="details">${text}</div><div class="actions"><button class="menu">…</button></div></div></div>`;
}

function recent(sections) {
  return `<div class="app-dashboard-recent">${sections
    .map(
      ({ id, rows, toggle }) =>
        `<section class="app-dashboard-section${toggle ? " app-dashboard-section--collapsible" : ""}" data-section="${id}"><div class="app-dashboard-section__header"><h2>${id} ${rows.length}</h2>${
          toggle
            ? `<button class="app-dashboard-section__toggle" data-show-text="Show all ${rows.length}" data-hide-text="Show fewer">Show all ${rows.length}</button>`
            : ""
        }</div><div class="app-dashboard-section__body"><div class="app-temporary-list">${rows.join("")}</div></div></section>`,
    )
    .join("")}</div>`;
}

describe("dashboard recent tab", () => {
  let root;
  let recentTab;

  // The first fetch, on load, returns the page's own content; later ones `next`
  async function setUp(html, next) {
    document.body.innerHTML = `<div id="dashboard-recent" data-rows-url="/jenkins/defaultTab/rows">${html}</div>`;
    root = document.querySelector("#dashboard-recent");
    const applySubtree = vi.fn();
    const fetch = vi
      .fn()
      .mockImplementationOnce(() => response(html))
      .mockImplementation(() => response(next));
    recentTab = initRecent(root, { fetch, applySubtree, interval: 60000 });
    recentTab.fetch = fetch;
    await recentTab.ready;
    applySubtree.mockClear();
    return applySubtree;
  }

  afterEach(() => recentTab && recentTab.stop());

  it("only replaces the rows that changed", async () => {
    const before = recent([
      { id: "running", rows: [row("a#1", "started 1 sec ago")] },
      { id: "finished", rows: [row("b#1", "took 2 sec")] },
    ]);
    const after = recent([
      { id: "running", rows: [row("a#1", "started 6 sec ago")] },
      {
        id: "finished",
        rows: [row("c#1", "took 1 sec"), row("b#1", "took 2 sec")],
      },
    ]);
    const applySubtree = await setUp(before, after);
    const finishedRow = root.querySelector('[page-entry-id="b#1"]');
    const runningMenu = root.querySelector('[page-entry-id="a#1"] .actions');

    await recentTab.refresh();

    // The unchanged row is the same node, so anything open on it stays open
    expect(root.querySelector('[page-entry-id="b#1"]')).toBe(finishedRow);
    // Only the changed cell of the running row was swapped
    expect(root.querySelector('[page-entry-id="a#1"] .actions')).toBe(
      runningMenu,
    );
    expect(
      root.querySelector('[page-entry-id="a#1"] .details').textContent,
    ).toBe("started 6 sec ago");
    expect(
      Array.from(root.querySelectorAll("[page-entry-id]")).map((e) =>
        e.getAttribute("page-entry-id"),
      ),
    ).toEqual(["a#1", "c#1", "b#1"]);
    expect(applySubtree).toHaveBeenCalled();
  });

  it("replaces everything when sections come or go", async () => {
    const before = recent([{ id: "finished", rows: [row("b#1", "x")] }]);
    const after = recent([
      { id: "running", rows: [row("a#1", "y")] },
      { id: "finished", rows: [row("b#1", "x")] },
    ]);
    await setUp(before, after);

    await recentTab.refresh();

    expect(sectionsOf(root)).toEqual(["running", "finished"]);
  });

  it("leaves unchanged rows alone after replacing everything", async () => {
    const before = recent([{ id: "finished", rows: [row("b#1", "x")] }]);
    const after = recent([
      { id: "running", rows: [row("a#1", "y")] },
      { id: "finished", rows: [row("b#1", "x")] },
    ]);
    const later = recent([
      { id: "running", rows: [row("a#1", "z")] },
      { id: "finished", rows: [row("b#1", "x")] },
    ]);
    await setUp(before, after);
    await recentTab.refresh();
    const finishedRow = root.querySelector('[page-entry-id="b#1"]');
    const runningMenu = root.querySelector('[page-entry-id="a#1"] .actions');

    recentTab.fetch.mockImplementation(() => response(later));
    await recentTab.refresh();

    expect(root.querySelector('[page-entry-id="b#1"]')).toBe(finishedRow);
    expect(root.querySelector('[page-entry-id="a#1"] .actions')).toBe(
      runningMenu,
    );
  });

  it("keeps an expanded section expanded across refreshes", async () => {
    const queued = (n) =>
      recent([
        {
          id: "queued",
          toggle: true,
          rows: Array.from({ length: n }, (_, i) =>
            row(`queue-${i}`, `waiting ${i}`, i >= 3),
          ),
        },
      ]);
    await setUp(queued(5), queued(6));
    const section = () => root.querySelector('[data-section="queued"]');
    const toggle = () => root.querySelector(".app-dashboard-section__toggle");

    expect(
      section().classList.contains("app-dashboard-section--expanded"),
    ).toBe(false);
    toggle().click();
    expect(
      section().classList.contains("app-dashboard-section--expanded"),
    ).toBe(true);
    expect(toggle().textContent).toBe("Show fewer");

    await recentTab.refresh();

    // The header changed (6 queued), but the section stays expanded
    expect(
      section().classList.contains("app-dashboard-section--expanded"),
    ).toBe(true);
    expect(toggle().textContent).toBe("Show fewer");
    expect(toggle().dataset.showText).toBe("Show all 6");

    toggle().click();
    expect(
      section().classList.contains("app-dashboard-section--expanded"),
    ).toBe(false);
    expect(toggle().textContent).toBe("Show all 6");
  });

  it("starts only one refresh loop per element", async () => {
    const html = recent([{ id: "finished", rows: [row("b#1", "x")] }]);
    await setUp(html, html);
    const fetch = vi.fn(() => response(html));

    expect(initRecent(root, { fetch })).toBe(recentTab);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("updates the tab's badge in the tab bar", async () => {
    const withBadge = (count) =>
      recent([{ id: "running", rows: [row("a#1", "x")] }]).replace(
        '<div class="app-dashboard-recent">',
        `<div class="app-dashboard-recent"><template class="app-dashboard-recent__badge">${
          count ? `<span class="jenkins-badge">${count}</span>` : ""
        }</template>`,
      );
    await setUp(withBadge(1), withBadge(2));
    document.body.insertAdjacentHTML(
      "afterbegin",
      '<div data-testid="dashboard-tabs"><a aria-current="page">Recent</a></div>',
    );
    const badge = () =>
      document.querySelector("[data-testid=dashboard-tabs] .jenkins-badge");

    await recentTab.refresh();
    expect(badge().textContent).toBe("2");

    recentTab.fetch.mockImplementation(() => response(withBadge(0)));
    await recentTab.refresh();
    expect(badge()).toBeNull();
  });

  it("keeps keyboard focus on a row's link when the row changes", async () => {
    const withLink = (description) =>
      recent([
        {
          id: "running",
          rows: [
            `<div class="app-temporary-list__item" page-entry-id="a#1"><div class="app-build-item"><div class="details"><a class="label" href="/job/a/1/">a #1</a> ${description}</div><div class="actions"><button class="menu">…</button></div></div></div>`,
          ],
        },
      ]);
    await setUp(withLink("started 1 sec ago"), withLink("started 6 sec ago"));
    const link = root.querySelector("a.label");
    link.focus();

    await recentTab.refresh();

    // The details cell was replaced, and the new link has focus
    expect(root.querySelector("a.label")).not.toBe(link);
    expect(document.activeElement).toBe(root.querySelector("a.label"));
  });

  it("keeps keyboard focus on a section's toggle when its header changes", async () => {
    const queued = (n) =>
      recent([
        {
          id: "queued",
          toggle: true,
          rows: Array.from({ length: n }, (_, i) =>
            row(`queue-${i}`, `waiting ${i}`, i >= 3),
          ),
        },
      ]);
    await setUp(queued(5), queued(6));
    const toggle = root.querySelector(".app-dashboard-section__toggle");
    toggle.focus();

    await recentTab.refresh();

    expect(root.querySelector(".app-dashboard-section__toggle")).not.toBe(
      toggle,
    );
    expect(document.activeElement).toBe(
      root.querySelector(".app-dashboard-section__toggle"),
    );
  });

  it("expands a section before focusing a control in it again", async () => {
    const queued = (extraSection) =>
      recent([
        ...(extraSection ? [{ id: "running", rows: [row("a#1", "x")] }] : []),
        {
          id: "queued",
          toggle: true,
          rows: Array.from({ length: 5 }, (_, i) =>
            row(`queue-${i}`, `waiting ${i}`, i >= 3),
          ),
        },
      ]);
    // A section appearing replaces everything, with the queue collapsed as the server renders it
    await setUp(queued(false), queued(true));
    root.querySelector(".app-dashboard-section__toggle").click();
    const menu = root.querySelector('[page-entry-id="queue-4"] .menu');
    menu.focus();
    let expandedWhenFocused;
    const focus = vi
      .spyOn(HTMLElement.prototype, "focus")
      .mockImplementation(function () {
        expandedWhenFocused = this.closest("[data-section]").classList.contains(
          "app-dashboard-section--expanded",
        );
      });

    await recentTab.refresh();

    expect(sectionsOf(root)).toEqual(["running", "queued"]);
    expect(focus).toHaveBeenCalled();
    expect(expandedWhenFocused).toBe(true);
    focus.mockRestore();
  });

  it("keeps keyboard focus on a control in a row that moves", async () => {
    const order = (ids) =>
      recent([{ id: "running", rows: ids.map((id) => row(id, id)) }]);
    await setUp(order(["a#1", "b#1"]), order(["b#1", "a#1"]));
    const menu = root.querySelector('[page-entry-id="b#1"] .menu');
    menu.focus();

    await recentTab.refresh();

    expect(
      Array.from(root.querySelectorAll("[page-entry-id]"), (e) =>
        e.getAttribute("page-entry-id"),
      ),
    ).toEqual(["b#1", "a#1"]);
    expect(document.activeElement).toBe(menu);
  });

  it("expands a section when the focused row moves into its hidden rows", async () => {
    const queued = (ids) =>
      recent([
        {
          id: "queued",
          toggle: true,
          rows: ids.map((id, i) => row(id, id, i >= 2)),
        },
      ]);
    await setUp(queued(["a", "b", "c"]), queued(["new", "a", "b", "c"]));
    const menu = root.querySelector('[page-entry-id="b"] .menu');
    menu.focus();

    await recentTab.refresh();

    const section = root.querySelector('[data-section="queued"]');
    expect(section.classList.contains("app-dashboard-section--expanded")).toBe(
      true,
    );
    expect(
      root
        .querySelector('[page-entry-id="b"]')
        .classList.contains("app-dashboard-section__extra"),
    ).toBe(true);
    expect(document.activeElement).toBe(menu);
  });

  it("does nothing when the content is unchanged", async () => {
    const html = recent([{ id: "finished", rows: [row("b#1", "x")] }]);
    const applySubtree = await setUp(html, html);

    await recentTab.refresh();

    expect(applySubtree).not.toHaveBeenCalled();
  });
});

function sectionsOf(root) {
  return Array.from(root.querySelectorAll("[data-section]")).map(
    (s) => s.dataset.section,
  );
}
