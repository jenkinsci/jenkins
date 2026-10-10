import {
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";

/**
 * autocomplete.js registers its behaviour through Behaviour (a global
 * published by hudson-behavior.js), so capture the callback and apply it to
 * an input manually.
 */
let applyBehaviour;
let pending;

beforeAll(async () => {
  globalThis.Behaviour = {
    register: () => {},
    specify: (selector, id, priority, behavior) => {
      if (selector === "INPUT.auto-complete") {
        applyBehaviour = behavior;
      }
    },
    applySubtree: () => {},
  };
  globalThis.crumb = { wrap: (headers) => headers };
  globalThis.TryEach = (fn) => fn;
  const autocomplete = (await import("@/components/dropdowns/autocomplete"))
    .default;
  autocomplete.init();
});

beforeEach(() => {
  vi.useFakeTimers();
  pending = [];
  globalThis.qs = (e) => ({
    addThis() {
      return this;
    },
    nearBy() {},
    toString: () => "?value=" + encodeURIComponent(e.value),
  });
  globalThis.fetch = () =>
    new Promise((resolve) => {
      pending.push((suggestions) =>
        resolve({ ok: true, json: () => ({ suggestions }) }),
      );
    });
});

afterEach(() => {
  vi.useRealTimers();
  document.body.innerHTML = "";
});

function createInput() {
  const input = document.createElement("input");
  input.className = "auto-complete";
  input.setAttribute("autoCompleteUrl", "/autoComplete");
  document.body.appendChild(input);
  applyBehaviour(input);
  input.focus();
  return input;
}

async function type(input, value) {
  input.value = value;
  input.dispatchEvent(new Event("input"));
  await vi.advanceTimersByTimeAsync(300);
}

async function respond(index, names) {
  pending[index](names.map((name) => ({ name })));
  await vi.advanceTimersByTimeAsync(0);
}

function isVisible(input) {
  return Boolean(input.dropdown && input.dropdown.state.isVisible);
}

describe("autocomplete", () => {
  it("shows the suggestions of the response", async () => {
    const input = createInput();
    await type(input, "hud");
    await respond(0, ["hudson"]);

    expect(isVisible(input)).toBe(true);
    expect(input.dropdown.popper.textContent).toContain("hudson");
  });

  it("ignores a response that arrives after a newer one", async () => {
    const input = createInput();
    await type(input, "h");
    await type(input, "jen");
    expect(pending).toHaveLength(2);

    await respond(1, ["jenkins"]);
    await respond(0, ["hudson"]);

    expect(isVisible(input)).toBe(true);
    expect(input.dropdown.popper.textContent).toContain("jenkins");
    expect(input.dropdown.popper.textContent).not.toContain("hudson");
  });

  it("does not open the dropdown when the field was cleared meanwhile", async () => {
    const input = createInput();
    await type(input, "hud");
    await type(input, "");
    await respond(0, ["hudson"]);

    expect(isVisible(input)).toBe(false);
  });

  it("does not open the dropdown when the field lost focus meanwhile", async () => {
    const input = createInput();
    await type(input, "hud");
    input.blur();
    await vi.advanceTimersByTimeAsync(200);
    await respond(0, ["hudson"]);

    expect(isVisible(input)).toBe(false);
  });
});
