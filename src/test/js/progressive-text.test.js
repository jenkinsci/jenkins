import progressiveTextSource from "../../../core/src/main/resources/lib/hudson/progressive-text.js?raw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

describe("progressive-text", () => {
  let behaviorCallback;
  let isStickingValue;
  let scrollToBottomMock;

  beforeEach(() => {
    vi.useFakeTimers();
    isStickingValue = true;
    scrollToBottomMock = vi.fn();

    globalThis.Behaviour = {
      specify: vi.fn((selector, id, priority, callback) => {
        if (selector === ".progressiveText-holder") {
          behaviorCallback = callback;
        }
      }),
      applySubtree: vi.fn(),
    };

    globalThis.AutoScroller = vi.fn().mockImplementation(function () {
      return {
        isSticking: () => isStickingValue,
        scrollToBottom: scrollToBottomMock,
      };
    });

    globalThis.crumb = {
      wrap: (headers) => headers,
    };

    document.body.innerHTML = "";
    (0, eval)(progressiveTextSource);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function createFixture({
    maxChunks = "3",
    startOffset = "0",
    href = "logText/progressiveHtml",
  } = {}) {
    const container = document.createElement("div");
    container.className = "progressive-text-container";

    const pre = document.createElement("pre");
    pre.id = "out";
    container.appendChild(pre);

    const holder = document.createElement("div");
    holder.className = "progressiveText-holder";
    holder.setAttribute("data-href", href);
    holder.setAttribute("data-idref", "out");
    holder.setAttribute("data-spinner", "");
    holder.setAttribute("data-start-offset", startOffset);
    holder.setAttribute("data-max-chunks", maxChunks);
    holder.setAttribute(
      "data-hidden-chunks-message",
      "Earlier output hidden ({0} chunks).",
    );
    holder.setAttribute("data-show-earlier-text", "Show all");
    holder.setAttribute("data-error-message", "Error loading log");
    container.appendChild(holder);

    document.body.appendChild(container);
    return { container, pre, holder };
  }

  function mockTextResponse({
    text,
    end = "100",
    completed = false,
    annotator = "token",
  }) {
    return Promise.resolve({
      status: 200,
      headers: {
        get: (h) => {
          if (h === "X-Text-Size") {
            return end;
          }
          if (h === "X-ConsoleAnnotator") {
            return annotator;
          }
          if (h === "X-More-Data") {
            return completed ? "false" : "true";
          }
          return null;
        },
      },
      text: () => Promise.resolve(text),
    });
  }

  it("registers Behaviour for .progressiveText-holder", () => {
    expect(globalThis.Behaviour.specify).toHaveBeenCalledWith(
      ".progressiveText-holder",
      "progressive-text",
      0,
      expect.any(Function),
    );
  });

  it("bounds DOM nodes and displays expansion banner when exceeding maxChunks", async () => {
    const { pre, holder, container } = createFixture({ maxChunks: "2" });

    const chunks = ["First chunk", "Second chunk", "Third chunk"];
    let callIdx = 0;

    globalThis.fetch = vi.fn(() => {
      const text = chunks[callIdx] || "";
      callIdx++;
      const completed = callIdx >= chunks.length;
      return mockTextResponse({
        text,
        end: String(callIdx * 100),
        completed,
      });
    });

    behaviorCallback(holder);

    // Initial fetch (Chunk 1)
    await vi.advanceTimersByTimeAsync(0);
    expect(pre.children.length).toBe(1);
    expect(pre.children[0].innerHTML).toBe("First chunk");

    // Advance timer for Chunk 2
    await vi.advanceTimersByTimeAsync(1000);
    expect(pre.children.length).toBe(2);
    expect(pre.children[0].innerHTML).toBe("First chunk");
    expect(pre.children[1].innerHTML).toBe("Second chunk");

    // Advance timer for Chunk 3 (exceeds maxChunks=2, so Chunk 1 pruned)
    await vi.advanceTimersByTimeAsync(1000);
    expect(pre.children.length).toBe(2);
    expect(pre.children[0].innerHTML).toBe("Second chunk");
    expect(pre.children[1].innerHTML).toBe("Third chunk");

    // Banner should be visible before pre
    const banner = container.querySelector(".progressive-text-expand-button");
    expect(banner).not.toBeNull();
    expect(banner.style.display).toBe("");
    expect(banner.textContent).toContain("Earlier output hidden (1 chunks)");

    // Clicking banner restores the first chunk
    banner.click();

    expect(pre.children.length).toBe(3);
    expect(pre.children[0].innerHTML).toBe("First chunk");
    expect(pre.children[1].innerHTML).toBe("Second chunk");
    expect(pre.children[2].innerHTML).toBe("Third chunk");
    expect(banner.style.display).toBe("none");
  });

  it("does not prune chunks when user is scrolled up (not sticking to bottom)", async () => {
    isStickingValue = false; // user is scrolled up reading
    const { pre, holder, container } = createFixture({ maxChunks: "2" });

    const chunks = ["Line A", "Line B", "Line C"];
    let callIdx = 0;

    globalThis.fetch = vi.fn(() => {
      const text = chunks[callIdx] || "";
      callIdx++;
      const completed = callIdx >= chunks.length;
      return mockTextResponse({
        text,
        end: String(callIdx * 100),
        completed,
      });
    });

    behaviorCallback(holder);

    // Initial fetch
    await vi.advanceTimersByTimeAsync(0);
    expect(pre.children.length).toBe(1);

    // Chunk 2
    await vi.advanceTimersByTimeAsync(1000);
    expect(pre.children.length).toBe(2);

    // Chunk 3 (because isSticking is false, no chunks pruned)
    await vi.advanceTimersByTimeAsync(1000);
    expect(pre.children.length).toBe(3);
    expect(pre.children[0].innerHTML).toBe("Line A");
    expect(pre.children[1].innerHTML).toBe("Line B");
    expect(pre.children[2].innerHTML).toBe("Line C");

    const banner = container.querySelector(".progressive-text-expand-button");
    expect(banner).toBeNull();
  });

  it("disables pruning when maxChunks is 0", async () => {
    const { pre, holder } = createFixture({ maxChunks: "0" });

    const chunks = ["Alpha", "Beta", "Gamma"];
    let callIdx = 0;

    globalThis.fetch = vi.fn(() => {
      const text = chunks[callIdx] || "";
      callIdx++;
      const completed = callIdx >= chunks.length;
      return mockTextResponse({
        text,
        end: String(callIdx * 100),
        completed,
      });
    });

    behaviorCallback(holder);

    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(1000);
    await vi.advanceTimersByTimeAsync(1000);

    expect(pre.children.length).toBe(3);
    expect(pre.children[0].innerHTML).toBe("Alpha");
    expect(pre.children[1].innerHTML).toBe("Beta");
    expect(pre.children[2].innerHTML).toBe("Gamma");
  });

  it("handles HTTP error response by displaying errorMessage and hiding spinner", async () => {
    const { pre, holder, container } = createFixture({ maxChunks: "2" });
    const spinner = document.createElement("div");
    spinner.id = "spinner";
    spinner.style.display = "block";
    container.appendChild(spinner);
    holder.setAttribute("data-spinner", "spinner");

    globalThis.fetch = vi.fn(() =>
      Promise.resolve({
        status: 404,
        headers: { get: () => null },
        text: () => Promise.resolve("Not Found"),
      }),
    );

    behaviorCallback(holder);

    await vi.advanceTimersByTimeAsync(0);

    expect(pre.children.length).toBe(1);
    expect(pre.children[0].innerHTML).toContain("Error loading log");
    expect(spinner.style.display).toBe("none");
  });

  it("preserves consoleAnnotator state when a poll returns no annotator", async () => {
    const { pre, holder } = createFixture({ maxChunks: "2" });
    let callIdx = 0;

    globalThis.fetch = vi.fn(() => {
      callIdx++;
      if (callIdx === 1) {
        return mockTextResponse({
          text: "First line",
          end: "100",
          completed: false,
          annotator: "token-abc",
        });
      }
      return mockTextResponse({
        text: "",
        end: "100",
        completed: true,
        annotator: null,
      });
    });

    behaviorCallback(holder);

    await vi.advanceTimersByTimeAsync(0);
    expect(pre.consoleAnnotator).toBe("token-abc");

    await vi.advanceTimersByTimeAsync(1000);
    expect(pre.consoleAnnotator).toBe("token-abc");
  });
});
