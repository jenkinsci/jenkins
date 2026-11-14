import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@/pages/manage-jenkins";

const NativeMutationObserver = window.MutationObserver;

describe("Manage Jenkins warning layout", () => {
  let observers;
  let frames;
  let container;
  let first;
  let last;

  beforeEach(() => {
    observers = [];
    frames = [];
    vi.stubGlobal(
      "MutationObserver",
      class extends NativeMutationObserver {
        constructor(callback) {
          super(callback);
          observers.push(this);
        }
      },
    );
    vi.stubGlobal(
      "requestAnimationFrame",
      vi.fn((callback) => {
        frames.push(callback);
        return frames.length;
      }),
    );
    // jsdom has no layout, so give the warning elements a rendered box.
    vi.spyOn(HTMLElement.prototype, "getClientRects").mockReturnValue([{}]);
    document.body.innerHTML = `
      <style>.hidden-warning { display: none; }</style>
      <input id="settings-search-bar">
      <section class="manage-messages">
        <div id="first">First warning</div>
        <div id="last">Last warning</div>
      </section>
    `;
    container = document.querySelector(".manage-messages");
    first = document.querySelector("#first");
    last = document.querySelector("#last");
    document.dispatchEvent(new Event("DOMContentLoaded"));
  });

  afterEach(() => {
    observers.forEach((observer) => observer.disconnect());
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    document.body.innerHTML = "";
  });

  async function settleLayout() {
    // Deliver real MutationObserver notifications between animation frames.
    // Bound the drain so a self-sustaining observer loop fails the test.
    await Promise.resolve();
    for (let i = 0; i < 5 && frames.length; i++) {
      const pending = frames.splice(0);
      pending.forEach((callback) => callback(0));
      await Promise.resolve();
    }
    expect(frames).toHaveLength(0);
  }

  it("sets the initial spacing without scheduling updates", async () => {
    await settleLayout();
    expect(first.style.marginBottom).toBe("");
    expect(last.style.marginBottom).toBe("var(--section-padding)");
    expect(requestAnimationFrame).not.toHaveBeenCalled();
  });

  it("stops updating after dismissing an earlier warning", async () => {
    first.remove();
    await settleLayout();
    expect(last.style.marginBottom).toBe("var(--section-padding)");
    expect(requestAnimationFrame).toHaveBeenCalled();
  });

  it("moves the spacing after dismissing the last warning", async () => {
    last.remove();
    await settleLayout();
    expect(first.style.marginBottom).toBe("var(--section-padding)");
  });

  it.each([
    ["hidden", (element, hidden) => (element.hidden = hidden)],
    [
      "class",
      (element, hidden) => element.classList.toggle("hidden-warning", hidden),
    ],
    [
      "style",
      (element, hidden) =>
        (element.style.visibility = hidden ? "hidden" : "visible"),
    ],
  ])("updates spacing when %s changes visibility", async (_, setHidden) => {
    setHidden(last, true);
    await settleLayout();
    expect(first.style.marginBottom).toBe("var(--section-padding)");
    expect(last.style.marginBottom).toBe("");

    setHidden(last, false);
    await settleLayout();
    expect(first.style.marginBottom).toBe("");
    expect(last.style.marginBottom).toBe("var(--section-padding)");
  });

  it("moves spacing to a newly added warning and stops updating", async () => {
    const added = document.createElement("div");
    added.textContent = "New warning";
    container.append(added);
    await settleLayout();
    expect(last.style.marginBottom).toBe("");
    expect(added.style.marginBottom).toBe("var(--section-padding)");
  });

  it("clears spacing when all warnings are hidden", async () => {
    first.hidden = true;
    last.hidden = true;
    await settleLayout();
    expect(first.style.marginBottom).toBe("");
    expect(last.style.marginBottom).toBe("");
  });
});
