import { beforeEach, describe, expect, it } from "vitest";
import computeBreadcrumbs from "@/components/header/breadcrumbs-overflow";

describe("computeBreadcrumbs", () => {
  beforeEach(() => {
    document.body.innerHTML = "";
  });

  it("does not throw when breadcrumbs bar is absent", () => {
    document.body.innerHTML = "<div></div>";
    expect(() => computeBreadcrumbs()).not.toThrow();
  });

  it("does not throw when breadcrumb item is absent even if bar exists", () => {
    document.body.innerHTML = '<div id="breadcrumbBar"></div>';
    expect(() => computeBreadcrumbs()).not.toThrow();
  });

  it("does not throw when breadcrumb item is absent during overflow", () => {
    document.body.innerHTML = '<div id="breadcrumbBar"></div>';
    const bar = document.querySelector("#breadcrumbBar");
    Object.defineProperty(bar, "scrollWidth", {
      configurable: true,
      get: () => 500,
    });
    Object.defineProperty(bar, "offsetWidth", {
      configurable: true,
      get: () => 300,
    });

    expect(() => computeBreadcrumbs()).not.toThrow();
  });

  it("removes overflow button when breadcrumbs bar does not overflow", () => {
    document.body.innerHTML = `
      <div id="breadcrumbBar">
        <ol class="jenkins-breadcrumbs">
          <li class="jenkins-breadcrumbs__list-item"><a href="/">Dashboard</a></li>
          <li class="jenkins-breadcrumbs__list-item"><button class="jenkins-button">...</button></li>
        </ol>
      </div>
    `;

    computeBreadcrumbs();

    const overflowBtn = document.querySelector(
      ".jenkins-breadcrumbs__list-item .jenkins-button",
    );
    expect(overflowBtn).toBeNull();
  });
});
