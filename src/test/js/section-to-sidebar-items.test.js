import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * The page body scrolls in a nested container rather than the document, so these
 * tests assert which element is scrolled rather than how far the page moved.
 * jsdom has no layout, so element rectangles are stubbed.
 */
const CONTAINER_TOP = 100;

function render() {
  document.body.innerHTML = `
    <div id="tasks"></div>
    <div id="page-body">
      <div class="contents">
        <div class="config-table">
          <div class="jenkins-app-bar"><h2>General</h2></div>
          <section class="jenkins-section">
            <div class="jenkins-section__title">Triggers</div>
          </section>
          <section class="jenkins-section">
            <div class="jenkins-section__title">Advanced</div>
          </section>
        </div>
      </div>
    </div>
  `;

  return document.querySelector(".contents");
}

function stubTop(element, top) {
  element.getBoundingClientRect = () => ({
    top,
    bottom: top,
    left: 0,
    right: 0,
    width: 0,
    height: 0,
    x: 0,
    y: top,
  });
}

function heading(text) {
  return Array.from(
    document.querySelectorAll(".jenkins-section__title, .jenkins-app-bar h2"),
  ).find((element) => element.textContent.trim() === text);
}

function click(text) {
  Array.from(document.querySelectorAll(".task-link"))
    .find((button) => button.textContent.trim() === text)
    .click();
}

function activeItem() {
  const active = document.querySelector(".task-link--active");

  return active ? active.textContent.trim() : null;
}

/**
 * Loads the module, capturing the listeners it registers instead of letting them
 * accumulate on the shared window and document between tests.
 */
async function load() {
  const captured = {};
  const onWindow = vi
    .spyOn(window, "addEventListener")
    .mockImplementation((type, handler) => {
      if (type === "load") {
        captured.load = handler;
      }
    });
  const onDocument = vi
    .spyOn(document, "addEventListener")
    .mockImplementation((type, handler, options) => {
      if (type === "scroll") {
        captured.scroll = handler;
        captured.scrollOptions = options;
      }
    });

  vi.resetModules();
  await import("../../main/js/section-to-sidebar-items.js");
  captured.load();

  onWindow.mockRestore();
  onDocument.mockRestore();

  return captured;
}

describe("section-to-sidebar-items", () => {
  let contents;

  beforeEach(() => {
    contents = render();
    contents.style.overflowY = "auto";
    stubTop(contents, CONTAINER_TOP);
    contents.scrollTo = vi.fn();
    window.scrollTo = vi.fn();
  });

  it("builds a sidebar item for every section heading", async () => {
    await load();

    expect(
      Array.from(document.querySelectorAll(".task-link-text")).map((item) =>
        item.textContent.trim(),
      ),
    ).toEqual(["General", "Triggers", "Advanced"]);
  });

  it("scrolls the container the sections live in, never the window", async () => {
    stubTop(heading("Triggers"), 400);
    contents.scrollTop = 50;
    await load();

    click("Triggers");

    // 50 already scrolled + (400 - 100) to the heading
    expect(contents.scrollTo).toHaveBeenCalledWith({
      top: 350,
      behavior: "smooth",
    });
    expect(window.scrollTo).not.toHaveBeenCalled();
  });

  it("subtracts the container's scroll-padding-top", async () => {
    contents.style.scrollPaddingTop = "20px";
    stubTop(heading("Triggers"), 400);
    contents.scrollTop = 50;
    await load();

    click("Triggers");

    expect(contents.scrollTo).toHaveBeenCalledWith({
      top: 330,
      behavior: "smooth",
    });
  });

  it("returns to the top for the first item", async () => {
    contents.scrollTop = 900;
    await load();

    click("General");

    expect(contents.scrollTo).toHaveBeenCalledWith({
      top: 0,
      behavior: "smooth",
    });
  });

  it("falls back to the document when no ancestor scrolls", async () => {
    contents.style.overflowY = "visible";
    const scroller = document.scrollingElement || document.documentElement;
    scroller.scrollTo = vi.fn();
    scroller.scrollTop = 40;
    stubTop(heading("Triggers"), 400);
    await load();

    click("Triggers");

    expect(scroller.scrollTo).toHaveBeenCalledWith({
      top: 440,
      behavior: "smooth",
    });
    expect(contents.scrollTo).not.toHaveBeenCalled();
  });

  it("listens for scroll in the capture phase, as it does not bubble", async () => {
    const captured = await load();

    expect(captured.scrollOptions).toBe(true);
  });

  it("moves the selected item as the container scrolls", async () => {
    stubTop(heading("Triggers").parentNode, 500);
    stubTop(heading("Advanced").parentNode, 900);
    const captured = await load();
    expect(activeItem()).toBe("General");

    // the container has scrolled, so Triggers is now above the fold
    stubTop(heading("Triggers").parentNode, 50);
    captured.scroll({ target: contents });

    expect(activeItem()).toBe("Triggers");
  });

  it("ignores scrolling of elements the sections do not live in", async () => {
    stubTop(heading("Triggers").parentNode, 500);
    stubTop(heading("Advanced").parentNode, 900);
    const captured = await load();
    const unrelated = document.createElement("textarea");
    document.body.appendChild(unrelated);

    stubTop(heading("Triggers").parentNode, 50);
    captured.scroll({ target: unrelated });

    expect(activeItem()).toBe("General");
  });
});
