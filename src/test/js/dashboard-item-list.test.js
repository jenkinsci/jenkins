import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { initItemList } from "@/pages/dashboard/item-list";

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
