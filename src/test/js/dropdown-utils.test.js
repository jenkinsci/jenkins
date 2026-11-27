import { beforeAll, describe, expect, it } from "vitest";

/**
 * The dropdown modules call into Behaviour (a global published by
 * hudson-behavior.js) when rendering, so stub it before they are loaded.
 */
let Utils;
let Templates;

beforeAll(async () => {
  globalThis.Behaviour = {
    register: () => {},
    specify: () => {},
    applySubtree: () => {},
  };
  Utils = (await import("@/components/dropdowns/utils")).default;
  Templates = (await import("@/components/dropdowns/templates")).default;
});

/**
 * Builds the children of a dropdown container, mirroring the templates that
 * lib/layout/dropdowns/item.jelly renders.
 */
function itemTemplates(badgeAttributes = "") {
  const container = document.createElement("div");
  container.innerHTML = `
    <template data-dropdown-type="ITEM"
              data-dropdown-text="Security"
              data-dropdown-href="/user/admin/security"
              ${badgeAttributes}></template>
  `;
  return container.children;
}

const BADGE_ATTRIBUTES = `
  data-dropdown-badge-text="1"
  data-dropdown-badge-tooltip="1 token about to expire"
  data-dropdown-badge-severity="warning"
`;

describe("Utils.convertHtmlToItems", () => {
  it("maps the badge attributes of an ITEM template", () => {
    const items = Utils.convertHtmlToItems(itemTemplates(BADGE_ATTRIBUTES));

    expect(items).toHaveLength(1);
    expect(items[0].displayName).toBe("Security");
    expect(items[0].event).toEqual({
      url: "/user/admin/security",
      type: "GET",
    });
    expect(items[0].badge).toEqual({
      text: "1",
      tooltip: "1 token about to expire",
      severity: "warning",
    });
  });

  it("leaves the badge undefined when the template has none", () => {
    const items = Utils.convertHtmlToItems(itemTemplates());

    expect(items).toHaveLength(1);
    expect(items[0].badge).toBeUndefined();
  });

  it("renders the mapped badge into the dropdown item", () => {
    const items = Utils.convertHtmlToItems(itemTemplates(BADGE_ATTRIBUTES));
    const dropdown = Utils.generateDropdownItems(items);

    const badge = dropdown.querySelector(
      ".jenkins-dropdown__item .jenkins-dropdown__item__badge",
    );
    expect(badge).not.toBeNull();
    expect(badge.textContent).toBe("1");
    expect(badge.classList.contains("jenkins-!-warning-color")).toBe(true);
    expect(badge.getAttribute("tooltip")).toBe("1 token about to expire");
  });
});

describe("dropdown accessibility", () => {
  it("renders the dropdown as a menu with menu items", () => {
    const items = Utils.convertHtmlToItems(itemTemplates());
    const dropdown = Utils.generateDropdownItems(items);

    expect(dropdown.getAttribute("role")).toBe("menu");
    expect(
      dropdown.querySelector(".jenkins-dropdown__item").getAttribute("role"),
    ).toBe("menuitem");
  });

  it("does not use the menu item role for inline buttons", () => {
    const button = Templates.menuItem(
      { displayName: "Build Now", event: { url: "build", type: "GET" } },
      "jenkins-button",
    );

    expect(button.hasAttribute("role")).toBe(false);
  });

  it("marks click-triggered dropdowns as menu buttons", () => {
    const button = document.createElement("button");
    document.body.appendChild(button);

    Utils.generateDropdown(button, () => {});

    expect(button.getAttribute("aria-haspopup")).toBe("menu");
    expect(button.getAttribute("aria-expanded")).toBe("false");
  });

  it("does not mark suggestion or hover dropdowns as menu buttons", () => {
    const input = document.createElement("input");
    const link = document.createElement("a");
    document.body.append(input, link);

    Utils.generateDropdown(input, () => {}, true);
    Utils.generateDropdown(link, () => {}, false, { trigger: "mouseenter" });

    expect(input.hasAttribute("aria-haspopup")).toBe(false);
    expect(link.hasAttribute("aria-haspopup")).toBe(false);
  });
});

/**
 * A split button item, shaped like the JSON SplitButtonEvent produces.
 */
function splitButtonItem() {
  const subItem = {
    type: "ITEM",
    displayName: "Edit and replay",
    event: { url: "replay", type: "GET" },
  };
  return {
    type: "ITEM",
    displayName: "Replay",
    semantic: "BUILD",
    event: {
      event: { url: "replay/rebuild", type: "GET" },
      actions: [subItem],
    },
    subMenu: { items: [subItem] },
  };
}

describe("split buttons", () => {
  it("renders as a split button in the app bar", () => {
    const element = Templates.menuItem(splitButtonItem(), "jenkins-button");

    expect(element.classList.contains("jenkins-split-button")).toBe(true);
    expect(element.children).toHaveLength(2);
    expect(element.firstElementChild.getAttribute("href")).toBe(
      "replay/rebuild",
    );
  });

  it("renders only the primary action in a dropdown", () => {
    const dropdown = Utils.generateDropdownItems([splitButtonItem()]);

    expect(dropdown.querySelector(".jenkins-split-button")).toBeNull();
    expect(dropdown.querySelector(".jenkins-button")).toBeNull();
    expect(dropdown.children).toHaveLength(1);

    const item = dropdown.firstElementChild;
    expect(item.classList.contains("jenkins-dropdown__item")).toBe(true);
    expect(item.classList.contains("jenkins-!-build-color")).toBe(true);
    expect(item.getAttribute("href")).toBe("replay/rebuild");
    expect(item.querySelector(".jenkins-dropdown__item__chevron")).toBeNull();
    expect(item._tippy).toBeUndefined();
  });
});
