import {
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";

let sidebar;
let width;
let mediaQuery;
let documentListeners;
let mediaListeners;

beforeAll(async () => {
  sidebar = (await import("@/components/side-panel")).default;
});

beforeEach(() => {
  const behaviors = new Map();
  const applied = new WeakMap();
  globalThis.Behaviour = {
    specify(selector, id, priority, callback) {
      behaviors.set(id, { selector, callback });
    },
    applySubtree(node, includeSelf) {
      for (const [id, { selector, callback }] of behaviors) {
        const elements = [...node.querySelectorAll(selector)];
        if (includeSelf && node.matches(selector)) {
          elements.unshift(node);
        }
        for (const element of elements) {
          const ids = applied.get(element) || new Set();
          if (ids.has(id)) {
            continue;
          }
          ids.add(id);
          applied.set(element, ids);
          callback(element);
        }
      }
    },
  };
  width = 739;
  mediaQuery = new EventTarget();
  Object.defineProperty(mediaQuery, "matches", { get: () => width < 740 });
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query) => {
      expect(query).toBe("(width < 740px)");
      return mediaQuery;
    }),
  );
  documentListeners = vi.spyOn(document, "addEventListener");
  mediaListeners = vi.spyOn(mediaQuery, "addEventListener");
});

afterEach(() => {
  for (const [type, listener, options] of documentListeners.mock.calls) {
    document.removeEventListener(type, listener, options);
  }
  for (const [type, listener, options] of mediaListeners.mock.calls) {
    mediaQuery.removeEventListener(type, listener, options);
  }
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  delete globalThis.Behaviour;
  document.body.innerHTML = "";
});

function initialize(initialWidth = 739, widget = "") {
  width = initialWidth;
  document.body.innerHTML = `
    <div class="app-page-body">
      <aside id="side-panel" class="app-page-body__sidebar">
        <div class="jenkins-app-bar__content"></div>
        <button id="inside">Sidebar action</button>${widget}
      </aside>
      <main id="main-panel">
        <div class="jenkins-app-bar__content"></div>
        <button id="outside">Main action</button>
      </main>
    </div>`;
  sidebar.init();
  globalThis.Behaviour.applySubtree(document.body, true);
}

function resize(nextWidth) {
  const wasMobile = width < 740;
  width = nextWidth;
  if (wasMobile !== mediaQuery.matches) {
    const event = new Event("change");
    Object.defineProperty(event, "matches", { value: mediaQuery.matches });
    mediaQuery.dispatchEvent(event);
  }
}

function mainToggle() {
  return document.querySelector("#main-panel .app-side-panel-toggle");
}

function expectExpanded(expanded) {
  expect(
    document
      .querySelector("#side-panel")
      .classList.contains("app-page-body__sidebar--expanded"),
  ).toBe(expanded);
  for (const toggle of document.querySelectorAll(".app-side-panel-toggle")) {
    expect(toggle.getAttribute("aria-expanded")).toBe(String(expanded));
    expect(toggle.getAttribute("aria-controls")).toBe("side-panel");
  }
}

describe("mobile sidebar", () => {
  it.each([740, 1100])(
    "allows the first main action after expanding at 739px and resizing to %ipx",
    (desktopWidth) => {
      initialize();
      mainToggle().click();
      resize(desktopWidth);
      const action = vi.fn();
      document.querySelector("#outside").addEventListener("click", action);
      document.querySelector("#outside").click();
      expect(action).toHaveBeenCalledOnce();
    },
  );

  it.each([740, 1100])(
    "clears expansion and both ARIA attributes when leaving mobile for %ipx",
    (desktopWidth) => {
      initialize();
      mainToggle().click();
      expectExpanded(true);
      resize(desktopWidth);
      expectExpanded(false);
    },
  );

  it("keeps the sidebar closed when returning from desktop to mobile", () => {
    initialize();
    mainToggle().click();
    resize(1100);
    resize(739);
    expectExpanded(false);
    mainToggle().click();
    expectExpanded(true);
  });

  it("does not consume a desktop click before the media change event arrives", () => {
    initialize();
    mainToggle().click();
    width = 740;
    const action = vi.fn();
    document.querySelector("#outside").addEventListener("click", action);
    document.querySelector("#outside").click();
    expect(action).toHaveBeenCalledOnce();
  });

  it("keeps the expanded sidebar active below the breakpoint", () => {
    initialize(700);
    mainToggle().click();
    resize(739);
    expectExpanded(true);
  });

  it("consumes an outside mobile click while dismissing the sidebar", () => {
    initialize();
    mainToggle().click();
    const action = vi.fn();
    const button = document.querySelector("#outside");
    button.addEventListener("click", action);
    button.click();
    expect(action).not.toHaveBeenCalled();
    expectExpanded(false);
    button.click();
    expect(action).toHaveBeenCalledOnce();
  });

  it("allows an inside click without dismissing the sidebar", () => {
    initialize();
    mainToggle().click();
    const action = vi.fn();
    const button = document.querySelector("#inside");
    button.addEventListener("click", action);
    button.click();
    expect(action).toHaveBeenCalledOnce();
    expectExpanded(true);
  });

  it("opens and closes from either toggle", () => {
    initialize();
    expectExpanded(false);
    mainToggle().click();
    expectExpanded(true);
    document.querySelector("#side-panel .app-side-panel-toggle").click();
    expectExpanded(false);
  });

  it.each([740, 1100])(
    "leaves fresh %ipx desktop actions alone",
    (initialWidth) => {
      initialize(initialWidth);
      const action = vi.fn();
      const button = document.querySelector("#outside");
      button.addEventListener("click", action);
      button.click();
      expect(action).toHaveBeenCalledOnce();
      expectExpanded(false);
    },
  );

  it("does not duplicate toggles or capture handlers when behaviors are reapplied", () => {
    initialize();
    globalThis.Behaviour.applySubtree(document.body, true);
    globalThis.Behaviour.applySubtree(document.body, true);
    expect(document.querySelectorAll(".app-side-panel-toggle")).toHaveLength(2);
    expect(
      documentListeners.mock.calls.filter(
        ([type, , capture]) => type === "click" && capture === true,
      ),
    ).toHaveLength(1);
    mainToggle().click();
    expectExpanded(true);
  });

  it.each(["pane", "jenkins-card"])(
    "preserves an excluded %s widget when the breakpoint changes",
    (className) => {
      initialize(1100, `<div class="${className}">Existing widget</div>`);
      const widget = document.querySelector(`#side-panel .${className}`);
      resize(739);
      resize(1100);
      expect(widget.isConnected).toBe(true);
      expect(widget.textContent).toBe("Existing widget");
      expectExpanded(false);
    },
  );

  it("does not fail on a page without a sidebar", () => {
    initialize();
    document.querySelector("#side-panel").remove();
    resize(1100);
    document.querySelector("#outside").click();
    expect(document.querySelector("#side-panel")).toBeNull();
  });
});
