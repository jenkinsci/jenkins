import { beforeAll, describe, expect, it } from "vitest";

/**
 * The dropdown modules call into Behaviour (a global published by
 * hudson-behavior.js) when rendering, so stub it before they are loaded.
 */
let Utils;

beforeAll(async () => {
  globalThis.Behaviour = {
    register: () => {},
    specify: () => {},
    applySubtree: () => {},
  };
  Utils = (await import("@/components/dropdowns/utils")).default;
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

  it("resolves root-relative URLs with document.head.dataset.rooturl", () => {
    document.head.dataset.rooturl = "/jenkins";
    try {
      const items = [
        {
          type: "ITEM",
          displayName: "Absolute Link",
          event: { url: "/manage", type: "GET" },
        },
        {
          type: "ITEM",
          displayName: "Already Prefixed Link",
          event: { url: "/jenkins/manage", type: "GET" },
        },
        {
          type: "ITEM",
          displayName: "Relative Link",
          event: { url: "configure", type: "GET" },
        },
        {
          type: "ITEM",
          displayName: "External Link",
          event: { url: "https://example.com", type: "GET" },
        },
        {
          type: "ITEM",
          displayName: "No URL",
          event: { url: null, type: "GET" },
        },
      ];

      const dropdown = Utils.generateDropdownItems(
        items,
        false,
        "/jenkins/job/built/",
      );
      const links = dropdown.querySelectorAll("a.jenkins-dropdown__item");
      expect(links[0].getAttribute("href")).toBe("/jenkins/manage");
      expect(links[1].getAttribute("href")).toBe("/jenkins/manage");
      expect(links[2].getAttribute("href")).toBe(
        "/jenkins/job/built/configure",
      );
      expect(links[3].getAttribute("href")).toBe("https://example.com");

      const buttons = dropdown.querySelectorAll(
        "button.jenkins-dropdown__item",
      );
      expect(buttons).toHaveLength(1);
      expect(buttons[0].getAttribute("href")).toBeNull();
    } finally {
      delete document.head.dataset.rooturl;
    }
  });
});
