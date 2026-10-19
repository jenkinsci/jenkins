import hudsonBehaviorSource from "../../../war/src/main/webapp/scripts/hudson-behavior.js?raw";
import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * hudson-behavior.js is a classic script that publishes its API (FormChecker,
 * updateValidationArea, …) as globals, so evaluate it in the global scope
 * rather than importing it as a module.
 */
function loadHudsonBehavior() {
  globalThis.Behaviour = {
    register: () => {},
    specify: () => {},
    applySubtree: () => {},
  };
  // indirect eval, so that the top level `var`s of the script become globals
  (0, eval)(hudsonBehaviorSource);
}

describe("FormChecker.delayedCheck", () => {
  /** The requests handed to fetch, each one settled by the test. */
  let requests;

  beforeEach(() => {
    loadHudsonBehavior();
    // http2 raises this, but the tests rely on checks being serialized
    FormChecker.maxParallel = 1;

    requests = [];
    globalThis.fetch = vi.fn(
      (url, options) =>
        new Promise((resolve, reject) => {
          requests.push({ url, options, resolve, reject });
        }),
    );
  });

  /** Lets the pending promise callbacks of FormChecker run. */
  function settle() {
    return new Promise((resolve) => setTimeout(resolve, 0));
  }

  function respondWith(request, body, status = 200) {
    request.resolve({
      ok: status >= 200 && status < 300,
      status,
      text: () => Promise.resolve(body),
    });
    return settle();
  }

  function abort(request) {
    const error = new Error("The operation was aborted.");
    error.name = "AbortError";
    request.reject(error);
    return settle();
  }

  function validationArea() {
    return document.createElement("div");
  }

  function requestedUrls() {
    return requests.map((request) => request.url);
  }

  it("runs the queued checks in order and fills in the validation areas", async () => {
    const first = validationArea();
    const second = validationArea();

    FormChecker.delayedCheck("/check-first", "post", first);
    FormChecker.delayedCheck("/check-second", "post", second);

    expect(requestedUrls()).toEqual(["/check-first"]);

    await respondWith(requests[0], "<div>first</div>");

    expect(first.innerHTML).toBe("<div>first</div>");
    expect(requestedUrls()).toEqual(["/check-first", "/check-second"]);

    await respondWith(requests[1], "<div>second</div>");

    expect(second.innerHTML).toBe("<div>second</div>");
    expect(FormChecker.queue).toHaveLength(0);
    expect(FormChecker.inProgress).toBe(0);
  });

  it("shows a concise message instead of the error page when a check fails", async () => {
    const target = validationArea();

    FormChecker.delayedCheck("/check", "post", target);

    await respondWith(
      requests[0],
      "<html><body><h1>Oops!</h1></body></html>",
      500,
    );

    expect(target.textContent).toContain(
      "An internal error occurred during form field validation (HTTP 500)",
    );
    expect(target.textContent).not.toContain("Oops!");
    expect(FormChecker.inProgress).toBe(0);
  });

  it("never sends a queued check that was aborted while waiting", async () => {
    const inFlight = validationArea();
    const outdated = validationArea();
    const controller = new AbortController();

    FormChecker.delayedCheck("/in-flight", "post", inFlight);
    FormChecker.delayedCheck("/outdated", "post", outdated, controller);
    controller.abort();

    await respondWith(requests[0], "<div>in flight</div>");

    expect(requestedUrls()).toEqual(["/in-flight"]);
    expect(inFlight.innerHTML).toBe("<div>in flight</div>");
    expect(outdated.innerHTML).toBe("");
    expect(FormChecker.queue).toHaveLength(0);
    expect(FormChecker.inProgress).toBe(0);
  });

  it("drops an in flight check that was aborted and carries on with the next one", async () => {
    const outdated = validationArea();
    const current = validationArea();
    const controller = new AbortController();

    FormChecker.delayedCheck("/outdated", "post", outdated, controller);
    FormChecker.delayedCheck("/current", "post", current);

    expect(requests[0].options.signal).toBe(controller.signal);

    controller.abort();
    await abort(requests[0]);

    expect(outdated.innerHTML).toBe("");

    await respondWith(requests[1], "<div>current</div>");

    expect(current.innerHTML).toBe("<div>current</div>");
    expect(FormChecker.inProgress).toBe(0);
  });

  it("discards the response of a check aborted after the server replied", async () => {
    const target = validationArea();
    const controller = new AbortController();

    // a bare AbortSignal is accepted as well as an AbortController
    FormChecker.delayedCheck("/check", "post", target, controller.signal);

    expect(requests[0].options.signal).toBe(controller.signal);

    controller.abort();
    await respondWith(requests[0], "<div>too late</div>");

    expect(target.innerHTML).toBe("");
    expect(FormChecker.inProgress).toBe(0);
  });

  it("ignores an abort argument that is neither a controller nor a signal", async () => {
    const target = validationArea();

    FormChecker.delayedCheck("/check", "post", target, {});

    expect(requests[0].options.signal).toBeUndefined();

    await respondWith(requests[0], "<div>ok</div>");

    expect(target.innerHTML).toBe("<div>ok</div>");
    expect(FormChecker.inProgress).toBe(0);
  });
});
