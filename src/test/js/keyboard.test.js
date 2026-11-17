import { afterEach, beforeAll, describe, expect, it } from "vitest";
import makeKeyboardNavigable from "@/util/keyboard";

const SELECTED_CLASS = "selected";

beforeAll(() => {
  // jsdom does not implement scrollIntoView
  Element.prototype.scrollIntoView = () => {};
});

afterEach(() => {
  document.body.innerHTML = "";
});

function createMenu() {
  const container = document.createElement("div");
  container.innerHTML = `
    <a href="#one" class="item">One</a>
    <a href="#two" class="item">Two</a>
  `;
  document.body.appendChild(container);

  makeKeyboardNavigable(
    container,
    () => container.querySelectorAll(".item"),
    SELECTED_CLASS,
    () => {},
    (c) => c.isConnected,
  );

  return container;
}

function pressArrowDown() {
  window.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowDown" }));
}

describe("makeKeyboardNavigable", () => {
  it("moves focus to the selected item when opened from a button", () => {
    const trigger = document.createElement("button");
    document.body.appendChild(trigger);
    const menu = createMenu();
    trigger.focus();

    pressArrowDown();

    const first = menu.querySelector(".item");
    expect(first.classList.contains(SELECTED_CLASS)).toBe(true);
    expect(document.activeElement).toBe(first);
  });

  it("keeps focus in a text field so typing still works", () => {
    const input = document.createElement("input");
    document.body.appendChild(input);
    const menu = createMenu();
    input.focus();

    pressArrowDown();

    expect(menu.querySelector(".item").classList.contains(SELECTED_CLASS)).toBe(
      true,
    );
    expect(document.activeElement).toBe(input);
  });
});
