import { beforeEach, describe, expect, it } from "vitest";
import Templates from "@/components/dropdowns/templates";

describe("menuItem dialog events", () => {
  beforeEach(() => {
    document.body.innerHTML = "";
  });

  it("keeps dialog attributes and does not load a script when javascriptUrl is blank", () => {
    const item = Templates.menuItem(
      {
        displayName: "Build with Parameters",
        event: {
          attributes: {
            type: "dialog-opener",
            "dialog-url": "parametersDefinitionProperty/dialog",
          },
          javascriptUrl: "",
        },
      },
      "jenkins-button",
      "/jenkins/job/param/",
    );

    expect(item.dataset.type).toBe("dialog-opener");
    expect(item.dataset.dialogUrl).toBe(
      "/jenkins/job/param/parametersDefinitionProperty/dialog",
    );
    expect(document.querySelector("script")).toBeNull();
  });

  it("still loads a script when javascriptUrl is set", () => {
    Templates.menuItem(
      {
        displayName: "Build Now",
        event: {
          attributes: { type: "build-now" },
          javascriptUrl: "/jenkins/static/abc/jsbundles/pages/project/build.js",
        },
      },
      "jenkins-button",
      "/jenkins/job/plain/",
    );

    expect(document.querySelector("script")?.getAttribute("src")).toBe(
      "/jenkins/static/abc/jsbundles/pages/project/build.js",
    );
  });
});
