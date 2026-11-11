import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  layoutBuildColumns,
  observeBuildColumns,
} from "@/pages/project/build-columns";

const PADDING = 44;
const MAX_WIDTH = 500;
const SUMMARY_WIDTH = 140;

function summary() {
  return `<div class="summary" style="min-width: 96px"></div>`;
}

function entry(id, content = "") {
  return `
    <div page-entry-id="${id}">
      <div class="app-build-item--columns">
        <div class="app-build-item__column">#${id}</div>
        <div class="app-build-item__column" style="max-width: ${MAX_WIDTH}px; padding-left: 22px; padding-right: 22px; border-left-width: 0px; border-right-width: 0px">${content}</div>
      </div>
    </div>`;
}

function columnBasis(container, id) {
  return container
    .querySelector(`[page-entry-id="${id}"]`)
    .querySelectorAll(".app-build-item__column")[1].style.flexBasis;
}

describe("build columns", () => {
  let container;
  let summaryWidth;

  beforeEach(() => {
    container = document.createElement("div");
    document.body.append(container);
    summaryWidth = SUMMARY_WIDTH;
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(
      function () {
        const width = this.querySelector(".summary")
          ? summaryWidth
          : this.childElementCount === 0 && this.textContent === ""
            ? PADDING
            : 100;
        return { width };
      },
    );
    vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(2000);
  });

  afterEach(() => {
    container.remove();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("offers new shrinkable content all the room, then fits it", () => {
    container.innerHTML = entry(1, summary());

    expect(layoutBuildColumns(container)).toBe(true);
    expect(columnBasis(container, 1)).toBe(`${MAX_WIDTH}px`);

    expect(layoutBuildColumns(container)).toBe(false);
    expect(columnBasis(container, 1)).toBe(`${SUMMARY_WIDTH}px`);
  });

  it("doesn't size a column by the padding of its empty cells", () => {
    container.innerHTML = entry(1) + entry(2);
    expect(layoutBuildColumns(container)).toBe(false);
    expect(layoutBuildColumns(container)).toBe(false);

    container.insertAdjacentHTML("afterbegin", entry(3, summary()));

    expect(layoutBuildColumns(container)).toBe(true);
    expect(columnBasis(container, 3)).toBe(`${MAX_WIDTH}px`);

    expect(layoutBuildColumns(container)).toBe(false);
    expect(columnBasis(container, 3)).toBe(`${SUMMARY_WIDTH}px`);
    expect(columnBasis(container, 1)).toBe(`${SUMMARY_WIDTH}px`);
  });

  it("offers all the room to a cell that was empty when its entry was first seen", () => {
    container.innerHTML = entry(1) + entry(2);
    expect(layoutBuildColumns(container)).toBe(false);

    container
      .querySelector(`[page-entry-id="1"]`)
      .querySelectorAll(".app-build-item__column")[1].innerHTML = summary();

    expect(layoutBuildColumns(container)).toBe(true);
    expect(columnBasis(container, 1)).toBe(`${MAX_WIDTH}px`);

    expect(layoutBuildColumns(container)).toBe(false);
    expect(columnBasis(container, 1)).toBe(`${SUMMARY_WIDTH}px`);
  });

  it("only asks to remeasure content that hasn't rendered once", () => {
    summaryWidth = PADDING;
    container.innerHTML = entry(1, summary());

    expect(layoutBuildColumns(container)).toBe(true);
    expect(layoutBuildColumns(container)).toBe(false);
    expect(columnBasis(container, 1)).toBe(`${MAX_WIDTH}px`);
  });

  it("settles without any further changes to the contents", async () => {
    const frames = [];
    vi.stubGlobal(
      "ResizeObserver",
      class {
        observe() {}
      },
    );
    vi.stubGlobal("requestAnimationFrame", (callback) => frames.push(callback));

    observeBuildColumns(container);
    container.innerHTML = entry(1, summary());
    await Promise.resolve();
    expect(columnBasis(container, 1)).toBe(`${MAX_WIDTH}px`);

    while (frames.length > 0) {
      frames.shift()();
    }

    expect(columnBasis(container, 1)).toBe(`${SUMMARY_WIDTH}px`);
    expect(frames).toHaveLength(0);
  });
});
