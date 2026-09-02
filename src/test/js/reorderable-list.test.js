import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { compile } from "sass";
import { beforeAll, describe, expect, it } from "vitest";

const here = dirname(fileURLToPath(import.meta.url));
const scssPath = resolve(here, "../../main/scss/form/_reorderable-list.scss");

beforeAll(() => {
  const stylesheet = document.createElement("style");
  stylesheet.textContent = compile(scssPath).css;
  document.head.appendChild(stylesheet);
});

function visibility(id, condition = "not-only") {
  const style = getComputedStyle(document.getElementById(id));
  const value = style.visibility;
  // jsdom inherits custom properties but does not resolve var() in visibility.
  if (value.startsWith("var(")) {
    return style.getPropertyValue(`--repeatable-show-if-${condition}`);
  }
  return value;
}

describe("nested repeatable element visibility", () => {
  it("uses the position of the nearest repeated chunk", () => {
    document.body.innerHTML = `
      <div class="repeated-container">
        <div class="repeated-chunk first last only">
          <div class="jenkins-repeated-chunk__content">
            <button id="outer-only" class="show-if-not-only"></button>
            <button id="outer-last" class="show-if-last"></button>
            <button id="outer-not-last" class="show-if-not-last"></button>
            <div class="repeated-container">
              <div class="repeated-chunk first">
                <div class="jenkins-repeated-chunk__content">
                  <button id="inner-first" class="show-if-not-only"></button>
                  <button id="inner-first-last" class="show-if-last"></button>
                  <button id="inner-first-not-last" class="show-if-not-last"></button>
                  <div class="repeated-container">
                    <div class="repeated-chunk first last only">
                      <div class="jenkins-repeated-chunk__content">
                        <button id="deep-only" class="show-if-not-only"></button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div class="repeated-chunk last">
                <div class="jenkins-repeated-chunk__content">
                  <button id="inner-last" class="show-if-not-only"></button>
                  <button id="inner-last-last" class="show-if-last"></button>
                  <button id="inner-last-not-last" class="show-if-not-last"></button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>`;

    expect(visibility("outer-only")).toBe("hidden");
    expect(visibility("outer-last", "last")).toBe("visible");
    expect(visibility("outer-not-last", "not-last")).toBe("hidden");
    expect(visibility("inner-first")).toBe("visible");
    expect(visibility("inner-first-last", "last")).toBe("hidden");
    expect(visibility("inner-first-not-last", "not-last")).toBe("visible");
    expect(visibility("inner-last")).toBe("visible");
    expect(visibility("inner-last-last", "last")).toBe("visible");
    expect(visibility("inner-last-not-last", "not-last")).toBe("hidden");
    expect(visibility("deep-only")).toBe("hidden");
  });
});
