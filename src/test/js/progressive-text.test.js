import progressiveTextSource from "../../../core/src/main/resources/lib/hudson/progressive-text.js?raw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * progressive-text.js is a classic script that registers its behaviour with
 * Behaviour.specify, so evaluate it in the global scope and run the
 * registered callback against a holder element ourselves.
 */
function loadProgressiveText() {
  let apply;
  globalThis.Behaviour = {
    specify: (selector, id, priority, fn) => {
      apply = fn;
    },
    applySubtree: () => {},
  };
  globalThis.crumb = { wrap: (headers) => headers };
  globalThis.AutoScroller = class {
    isSticking() {
      return false;
    }
    scrollToBottom() {}
  };
  // indirect eval, so that the script runs in the global scope
  (0, eval)(progressiveTextSource);
  return apply;
}

describe("progressive-text", () => {
  /** The requests handed to fetch, each one settled by the test. */
  let requests;

  beforeEach(() => {
    vi.useFakeTimers();
    requests = [];
    globalThis.fetch = vi.fn(
      (url, options) =>
        new Promise((resolve) => {
          requests.push({ url, options, resolve });
        }),
    );

    document.body.innerHTML = `
      <pre id="out"></pre>
      <div class="progressiveText-holder" data-href="logText/progressiveHtml"
           data-idref="out" data-spinner="" data-start-offset=""
           data-on-finish-event="" data-error-message="failed"></div>`;
    loadProgressiveText()(document.querySelector(".progressiveText-holder"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  /**
   * Answers a request the way the streaming progressiveHtml endpoint does,
   * then lets the client schedule and send its next request.
   */
  async function respondWith(request, text, meta) {
    request.resolve({
      status: 200,
      headers: {
        get: (name) =>
          name === "Content-Type"
            ? "multipart/form-data;boundary=b;charset=utf-8"
            : null,
      },
      formData: () =>
        Promise.resolve({
          get: (name) => ({ text, meta: JSON.stringify(meta) })[name],
        }),
    });
    await vi.advanceTimersByTimeAsync(1000);
  }

  function sentAnnotator(request) {
    return request.options.headers["X-ConsoleAnnotator"];
  }

  function sentStart(request) {
    return request.options.body.get("start");
  }

  it("keeps sending the console annotator after a poll without new output", async () => {
    expect(sentAnnotator(requests[0])).toBeUndefined();
    await respondWith(requests[0], "line1\n", {
      completed: false,
      start: 0,
      end: 6,
      consoleAnnotator: "state-1",
    });
    expect(sentAnnotator(requests[1])).toBe("state-1");

    // No new output: the server sends no annotator state for an empty chunk.
    await respondWith(requests[1], "", { completed: false, start: 6, end: 6 });

    expect(sentStart(requests[2])).toBe("6");
    expect(sentAnnotator(requests[2])).toBe("state-1");
  });

  it("sends the new console annotator after more output", async () => {
    await respondWith(requests[0], "line1\n", {
      completed: false,
      start: 0,
      end: 6,
      consoleAnnotator: "state-1",
    });
    await respondWith(requests[1], "", { completed: false, start: 6, end: 6 });
    await respondWith(requests[2], "line2\n", {
      completed: false,
      start: 6,
      end: 12,
      consoleAnnotator: "state-2",
    });

    expect(sentStart(requests[3])).toBe("12");
    expect(sentAnnotator(requests[3])).toBe("state-2");
    expect(document.getElementById("out").textContent).toBe("line1\nline2\n");
  });
});
