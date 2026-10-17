import hudsonBehaviorSource from "../../../war/src/main/webapp/scripts/hudson-behavior.js?raw";
import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * hudson-behavior.js is a classic script that publishes its API (validateButton,
 * formatValidationResponse, …) as globals, so evaluate it in the global scope
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

describe("validateButton", () => {
  /** The requests handed to fetch, each one settled by the test. */
  let requests;

  beforeEach(() => {
    loadHudsonBehavior();

    requests = [];
    globalThis.fetch = vi.fn(
      (url, options) =>
        new Promise((resolve) => {
          requests.push({ url, options, resolve });
        }),
    );
  });

  /** Lets the pending promise callbacks of validateButton run. */
  function settle() {
    return new Promise((resolve) => setTimeout(resolve, 0));
  }

  function respondWith(request, body, status) {
    request.resolve({
      ok: status >= 200 && status < 300,
      status,
      text: () => Promise.resolve(body),
      headers: { get: () => null },
    });
    return settle();
  }

  /**
   * Builds the markup validateButton expects inside the button's parent:
   * a spinner, a resize span, the validation target and the button itself.
   */
  function createContainer() {
    const container = document.createElement("div");
    container.innerHTML =
      '<span class="spinner"></span>' +
      '<span class="resize"></span>' +
      '<div class="form-validate"></div>' +
      '<button type="button"></button>';
    document.body.appendChild(container);
    return container;
  }

  function validationArea(container) {
    return container.querySelector(".form-validate").children[0];
  }

  it("posts to the check URL", async () => {
    const container = createContainer();
    const button = container.querySelector("button");

    validateButton("/check-url", "", button);
    await respondWith(requests[0], "<div>ok</div>", 200);

    expect(requests[0].url).toBe("/check-url");
    expect(requests[0].options.method).toBe("post");
  });

  it("renders a successful response unchanged", async () => {
    const container = createContainer();
    const button = container.querySelector("button");

    validateButton("/check-url", "", button);
    await respondWith(requests[0], "<div>all good</div>", 200);

    expect(validationArea(container).innerHTML).toBe("<div>all good</div>");
  });

  it("replaces the raw body with a concise message on HTTP errors", async () => {
    const container = createContainer();
    const button = container.querySelector("button");

    validateButton("/check-url", "", button);
    await respondWith(
      requests[0],
      "<div>java.lang.NullPointerException stack trace</div>",
      500,
    );

    const area = validationArea(container);
    expect(area.innerHTML).toContain(
      "An internal error occurred during form field validation (HTTP 500)",
    );
    expect(area.innerHTML).not.toContain("NullPointerException");
  });

  it("hides the spinner once the response is rendered", async () => {
    const container = createContainer();
    const button = container.querySelector("button");

    validateButton("/check-url", "", button);
    expect(container.querySelector(".spinner").style.display).toBe("block");

    await respondWith(requests[0], "<div>ok</div>", 200);
    expect(container.querySelector(".spinner").style.display).toBe("none");
  });
});
