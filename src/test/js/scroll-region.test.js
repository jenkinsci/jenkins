import { afterEach, describe, expect, it, vi } from "vitest";

function render({ contentsOverflow = "auto", pageBodyOverflow = "auto" } = {}) {
  document.body.innerHTML = `
    <main
      class="app-page-body__contents"
      data-scroll-region-label="Main content"
      style="overflow-y: ${contentsOverflow}"
    ></main>
    <div id="page-body" style="overflow-y: ${pageBodyOverflow}"></div>
  `;

  return {
    contents: document.querySelector(".app-page-body__contents"),
    pageBody: document.querySelector("#page-body"),
  };
}

async function load() {
  const listeners = {};
  const documentListener = vi
    .spyOn(document, "addEventListener")
    .mockImplementation((type, handler) => {
      if (type === "keydown") {
        listeners.keydown = handler;
      }
    });
  const windowListener = vi
    .spyOn(window, "addEventListener")
    .mockImplementation((type, handler) => {
      if (type === "resize") {
        listeners.resize = handler;
      }
    });

  vi.resetModules();
  const { default: scrollRegion } =
    await import("../../main/js/components/scroll-region/index.js");
  scrollRegion.init();

  documentListener.mockRestore();
  windowListener.mockRestore();
  return listeners;
}

afterEach(() => {
  vi.restoreAllMocks();
  document.body.innerHTML = "";
});

describe("scroll-region", () => {
  it("makes the main content region focusable before the legacy page body", async () => {
    const { contents, pageBody } = render();

    await load();

    expect(contents.getAttribute("tabindex")).toBe("0");
    expect(contents.getAttribute("role")).toBe("region");
    expect(contents.getAttribute("aria-label")).toBe("Main content");
    expect(pageBody.hasAttribute("tabindex")).toBe(false);
    expect(pageBody.hasAttribute("role")).toBe(false);
  });

  it("falls back to the legacy page body when the contents do not scroll", async () => {
    const { contents, pageBody } = render({ contentsOverflow: "visible" });

    await load();

    expect(contents.hasAttribute("tabindex")).toBe(false);
    expect(pageBody.getAttribute("tabindex")).toBe("0");
    expect(pageBody.getAttribute("role")).toBe("region");
  });

  it("updates the active region when a responsive width change moves scrolling", async () => {
    const originalWidth = window.innerWidth;
    const { contents, pageBody } = render();
    const { resize } = await load();

    contents.style.overflowY = "visible";
    Object.defineProperty(window, "innerWidth", {
      configurable: true,
      value: originalWidth + 1,
    });

    try {
      resize();

      expect(contents.hasAttribute("tabindex")).toBe(false);
      expect(pageBody.getAttribute("tabindex")).toBe("0");
      expect(pageBody.getAttribute("role")).toBe("region");
    } finally {
      Object.defineProperty(window, "innerWidth", {
        configurable: true,
        value: originalWidth,
      });
    }
  });

  it("moves focus when a responsive width change selects a new region", async () => {
    const originalWidth = window.innerWidth;
    const { contents, pageBody } = render();
    const { resize } = await load();

    contents.focus();
    contents.style.overflowY = "visible";
    Object.defineProperty(window, "innerWidth", {
      configurable: true,
      value: originalWidth + 1,
    });

    try {
      resize();

      expect(document.activeElement).toBe(pageBody);
    } finally {
      Object.defineProperty(window, "innerWidth", {
        configurable: true,
        value: originalWidth,
      });
    }
  });

  it("focuses the scroll region for every supported scroll key", async () => {
    const { contents } = render();
    const focus = vi.spyOn(contents, "focus");
    const { keydown } = await load();

    ["End", "Home", "PageUp", "PageDown", "ArrowUp", "ArrowDown", " "].forEach(
      (key) => keydown({ target: document.body, key }),
    );

    expect(focus).toHaveBeenCalledTimes(7);
    expect(focus).toHaveBeenLastCalledWith({ preventScroll: true });
  });

  it("does not steal focus for unrelated keys or from an interactive control", async () => {
    const { contents } = render();
    const input = document.createElement("input");
    document.body.appendChild(input);
    const focus = vi.spyOn(contents, "focus");
    const { keydown } = await load();

    keydown({ target: document.body, key: "Enter" });
    keydown({ target: input, key: "PageDown" });

    expect(focus).not.toHaveBeenCalled();
  });
});
