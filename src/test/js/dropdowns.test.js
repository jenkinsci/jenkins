import { describe, expect, it } from "vitest";
import Utils from "../../main/js/components/dropdowns/utils";

describe("dropdown item conversion", () => {
  it("preserves badge attributes emitted by the Jelly item template", () => {
    const template = document.createElement("template");
    template.innerHTML = `
      <template
        data-dropdown-type="ITEM"
        data-dropdown-text="Security"
        data-dropdown-badge-text="1"
        data-dropdown-badge-tooltip="Token expires soon"
        data-dropdown-badge-severity="warning"
      ></template>
    `;

    expect(Utils.convertHtmlToItems(template.content.children)).toEqual([
      {
        type: "ITEM",
        displayName: "Security",
        badge: {
          text: "1",
          tooltip: "Token expires soon",
          severity: "warning",
        },
      },
    ]);
  });
});
