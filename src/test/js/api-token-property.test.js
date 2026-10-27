import apiTokenSource from "../../../core/src/main/resources/jenkins/security/ApiTokenProperty/resources.js?raw";
import { beforeEach, describe, expect, it } from "vitest";

describe("API token expiration form", () => {
  let expiration;
  let date;
  let submit;
  let warning;

  beforeEach(() => {
    globalThis.Behaviour = { specify: () => {} };
    (0, eval)(apiTokenSource);
    document.body.innerHTML = `
      <dialog open>
        <form>
          <select name="expirationDuration">
            <option value="7">7 Days</option>
            <option value="custom">Custom</option>
            <option value="never">No Expiration</option>
          </select>
          <input type="date" name="tokenExpiration" class="jenkins-hidden"
            min="2026-01-01" max="2026-01-08" value="2026-01-08">
          <div class="token-warning jenkins-hidden"></div>
        </form>
        <button data-id="ok">Generate</button>
      </dialog>`;
    const form = document.querySelector("form");
    globalThis.addFormChangesHandling(form);
    expiration = form.querySelector("select");
    date = form.querySelector("input");
    submit = document.querySelector("button");
    warning = form.querySelector(".token-warning");
  });

  function select(value) {
    expiration.value = value;
    expiration.dispatchEvent(new Event("change"));
  }

  function enterDate(value) {
    date.value = value;
    date.dispatchEvent(new Event("input"));
  }

  it("validates custom dates using the dialog's sibling submit button", () => {
    select("custom");
    expect(date.classList.contains("jenkins-hidden")).toBe(false);
    expect(submit.disabled).toBe(false);
    for (const value of ["", "2026-01-09", "2025-12-31"]) {
      enterDate(value);
      expect(submit.disabled).toBe(true);
    }
    enterDate("2026-01-08");
    expect(submit.disabled).toBe(false);
  });

  it("reenables preset durations after an invalid custom date", () => {
    select("custom");
    enterDate("");
    select("7");
    expect(submit.disabled).toBe(false);
    expect(date.classList.contains("jenkins-hidden")).toBe(true);
  });

  it("shows the warning only for tokens with no expiration", () => {
    select("never");
    expect(warning.classList.contains("jenkins-hidden")).toBe(false);
    select("custom");
    expect(warning.classList.contains("jenkins-hidden")).toBe(true);
  });
});
