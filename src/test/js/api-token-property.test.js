import resourcesSource from "../../../core/src/main/resources/jenkins/security/ApiTokenProperty/resources.js?raw";
import {
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";

// The dialogs fetch their translations from the server, and use them once they
// are in. Answer later than the import, as the server does, because the dialogs
// publish themselves as window.dialog only when init() is called.
vi.mock("@/util/jenkins", () => ({
  default: {
    loadTranslations: (bundleName, handler) =>
      setTimeout(() =>
        handler({ ok: "OK", cancel: "Cancel", yes: "Yes", submit: "Submit" }),
      ),
  },
}));

/** The dates that the server computes, in an order that no clock could produce. */
const MIN_DATE = "2031-02-03";
const MAX_DATE = "2031-03-04";
const PRESET_DATE = "2031-02-20";

/** The behaviours that resources.js registered with Behaviour.specify, by selector. */
let behaviours;

/** The errors that listeners threw: jsdom reports them to the window instead of failing the test. */
let uncaughtErrors;

function onWindowError(event) {
  event.preventDefault();
  uncaughtErrors.push(event.error);
}

beforeAll(async () => {
  globalThis.Behaviour = {
    register: () => {},
    specify: () => {},
    applySubtree: () => {},
  };
  globalThis.crumb = { wrap: (headers) => headers };
  globalThis.isSecureContext = false;
  // not implemented by jsdom: innerText, which the scripts use to write text
  Object.defineProperty(HTMLElement.prototype, "innerText", {
    get() {
      return this.textContent;
    },
    set(value) {
      this.textContent = value;
    },
    configurable: true,
  });
  globalThis.CSS = { supports: () => true };
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute("open", "");
  };
  // the real dialog, so that the buttons are where they are in Jenkins
  (await import("@/components/dialogs")).default.init();
  // let the translations in
  await new Promise((resolve) => setTimeout(resolve, 0));
});

beforeEach(() => {
  behaviours = {};
  uncaughtErrors = [];
  window.addEventListener("error", onWindowError);
  globalThis.Behaviour = {
    register: () => {},
    specify: (selector, id, priority, fn) => {
      behaviours[selector] = fn;
    },
    applySubtree: () => {},
  };
  // indirect eval, so that the functions of the script become globals
  (0, eval)(resourcesSource);
});

afterEach(() => {
  window.removeEventListener("error", onWindowError);
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  document.body.innerHTML = "";
});

/**
 * What ApiTokenProperty/config.jelly renders for the dialog and the templates
 * of a token, with the options of the maximum that does not apply to the owner.
 */
function renderPage({ withWarning = true } = {}) {
  document.body.innerHTML = `
    <button class="api-token-property-add" type="button"
            data-target-url="generateNewToken"
            data-prompt-message="Create new API token"
            data-generate="Generate"
            data-cancel="Cancel"
            data-button-done="Done"
            data-not-added="Token could not be added"></button>
    <div id="api-token-add-template" class="jenkins-hidden">
      <div>
        <input type="text" name="tokenName">
        <div class="token-expiration">
          <select name="expirationDuration">
            <option value="7">7 Days</option>
            <option value="30" selected>30 Days</option>
            <option value="custom">Custom</option>
            <option value="never">No Expiration</option>
          </select>
          <input type="date" name="tokenExpiration" class="jenkins-hidden"
                 data-min-date="${MIN_DATE}" data-max-date="${MAX_DATE}"
                 data-preset-date="${PRESET_DATE}">
          ${
            withWarning
              ? '<div class="jenkins-hidden warning token-warning">Not recommended</div>'
              : ""
          }
        </div>
      </div>
    </div>
    <div id="api-token-row-template" class="jenkins-hidden">
      <div class="token-card">
        <span class="token-name"></span>
        <span class="api-token-new-expiration" data-never="This token does not expire"
              data-expires-on="Expires on __DATA__"></span>
        <button type="button" class="api-token-property-token-show jenkins-hidden"
                data-button-done="Done"></button>
      </div>
    </div>
    <div id="api-token-template">
      <div>
        <span class="api-token-new-value"></span>
        <span class="api-token-new-expiration" data-never="This token does not expire"
              data-expires-on="Expires on __DATA__"></span>
      </div>
    </div>
    <div class="token-list">
      <div class="token-list-empty-item"></div>
      <div id="api-tokens"><div id="api-token-list"></div></div>
    </div>`;
}

/** Clicks "Add new token" and returns the parts of the dialog that the tests drive. */
function openAddTokenDialog() {
  const addButton = document.querySelector(".api-token-property-add");
  globalThis.addToken(addButton);
  const dialogElement = document.querySelector("dialog");
  return {
    dialogElement,
    form: dialogElement.querySelector("form"),
    select: dialogElement.querySelector('select[name="expirationDuration"]'),
    dateInput: dialogElement.querySelector('input[name="tokenExpiration"]'),
    ok: dialogElement.querySelector('button[data-id="ok"]'),
    warning: dialogElement.querySelector(".token-warning"),
  };
}

function choose(select, value) {
  select.value = value;
  select.dispatchEvent(new Event("change"));
}

/** Sets the date as a browser does when the user types it: input as the value changes, and change once it is committed. */
function pickDate(dateInput, value) {
  dateInput.value = value;
  dateInput.dispatchEvent(new Event("input"));
  dateInput.dispatchEvent(new Event("change"));
}

/** Settles the promises of the dialog and of fetch. */
function settle() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

/** Replaces fetch, restored after each test, with one that answers that the request failed. */
function stubFetch() {
  const fetchMock = vi.fn(() => Promise.resolve({ ok: false }));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function pressEnter(target) {
  const event = new KeyboardEvent("keydown", {
    key: "Enter",
    bubbles: true,
    cancelable: true,
  });
  target.dispatchEvent(event);
  return event;
}

describe("the add token dialog", () => {
  it("has the OK button outside the form, so that the handlers must look for it in the dialog", () => {
    renderPage();
    const { form, ok, dialogElement } = openAddTokenDialog();

    expect(ok).not.toBeNull();
    expect(form.contains(ok)).toBe(false);
    expect(dialogElement.contains(ok)).toBe(true);
  });

  it("takes the minimum, the maximum and the preset of the date from the server, whatever the clock says", () => {
    // 2001 is not near any of the dates of the server: a date computed here would show it
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2001-01-01T12:00:00Z"));
    renderPage();

    const { dateInput } = openAddTokenDialog();

    expect(dateInput.min).toBe(MIN_DATE);
    expect(dateInput.max).toBe(MAX_DATE);
    expect(dateInput.value).toBe(PRESET_DATE);
  });

  it("shows the date and enables the OK button for the preset date of the custom choice, without throwing", () => {
    renderPage();
    const { select, dateInput, ok } = openAddTokenDialog();
    expect(dateInput.classList.contains("jenkins-hidden")).toBe(true);

    choose(select, "custom");

    expect(uncaughtErrors).toEqual([]);
    expect(dateInput.classList.contains("jenkins-hidden")).toBe(false);
    expect(ok.disabled).toBe(false);
  });

  it("enables the OK button for the custom choice only while the date is within the limits of the server", () => {
    renderPage();
    const { select, dateInput, ok } = openAddTokenDialog();
    choose(select, "custom");
    expect(ok.disabled).toBe(false);

    pickDate(dateInput, "");
    expect(ok.disabled).toBe(true);

    pickDate(dateInput, MIN_DATE);
    expect(ok.disabled).toBe(false);

    pickDate(dateInput, "2031-02-02");
    expect(ok.disabled).toBe(true);

    pickDate(dateInput, MAX_DATE);
    expect(ok.disabled).toBe(false);

    pickDate(dateInput, "2031-03-05");
    expect(ok.disabled).toBe(true);

    pickDate(dateInput, PRESET_DATE);
    expect(ok.disabled).toBe(false);
    expect(uncaughtErrors).toEqual([]);
  });

  it("enables the OK button once a valid date is typed, when the browser fires input and not change", () => {
    renderPage();
    const { select, dateInput, ok } = openAddTokenDialog();
    choose(select, "custom");
    pickDate(dateInput, "");
    expect(ok.disabled).toBe(true);

    // Chrome fires change when a whole valid date is typed, Firefox and Safari may wait for the blur
    dateInput.value = PRESET_DATE;
    dateInput.dispatchEvent(new Event("input"));
    expect(ok.disabled).toBe(false);

    dateInput.value = "2031-03-05";
    dateInput.dispatchEvent(new Event("input"));
    expect(ok.disabled).toBe(true);
    expect(uncaughtErrors).toEqual([]);
  });

  it("enables the OK button once a valid date is committed, when the browser fires change and not input", () => {
    renderPage();
    const { select, dateInput, ok } = openAddTokenDialog();
    choose(select, "custom");
    pickDate(dateInput, "");
    expect(ok.disabled).toBe(true);

    dateInput.value = PRESET_DATE;
    dateInput.dispatchEvent(new Event("change"));
    expect(ok.disabled).toBe(false);

    dateInput.value = "2031-03-05";
    dateInput.dispatchEvent(new Event("change"));
    expect(ok.disabled).toBe(true);
    expect(uncaughtErrors).toEqual([]);
  });

  it("disables the OK button when the custom choice is selected with no usable date, and enables it for any other choice", () => {
    renderPage();
    const { select, dateInput, ok } = openAddTokenDialog();
    pickDate(dateInput, "2031-03-05");

    choose(select, "custom");
    expect(ok.disabled).toBe(true);

    choose(select, "7");
    expect(ok.disabled).toBe(false);
    expect(dateInput.classList.contains("jenkins-hidden")).toBe(true);

    choose(select, "custom");
    expect(ok.disabled).toBe(true);

    choose(select, "never");
    expect(ok.disabled).toBe(false);
    expect(dateInput.classList.contains("jenkins-hidden")).toBe(true);
    expect(uncaughtErrors).toEqual([]);
  });

  it("shows the warning for the choice that never expires and hides it for the others", () => {
    renderPage();
    const { select, warning } = openAddTokenDialog();
    expect(warning.classList.contains("jenkins-hidden")).toBe(true);

    choose(select, "never");
    expect(warning.classList.contains("jenkins-hidden")).toBe(false);

    choose(select, "7");
    expect(warning.classList.contains("jenkins-hidden")).toBe(true);
    expect(uncaughtErrors).toEqual([]);
  });

  it("works when the warning is not rendered, as when the maximum applies", () => {
    renderPage({ withWarning: false });
    const { select, dateInput, ok, warning } = openAddTokenDialog();
    expect(warning).toBeNull();

    choose(select, "never");
    choose(select, "custom");
    pickDate(dateInput, "");
    expect(ok.disabled).toBe(true);
    choose(select, "30");
    expect(ok.disabled).toBe(false);

    expect(uncaughtErrors).toEqual([]);
  });

  it("does not throw if there is no dialog button to enable", () => {
    renderPage();
    const { select, ok } = openAddTokenDialog();
    ok.remove();

    choose(select, "custom");

    expect(uncaughtErrors).toEqual([]);
  });
});

describe("submitting the add token dialog", () => {
  it("sends the custom choice and the maximum date of the server when OK is clicked", async () => {
    renderPage();
    const fetchMock = stubFetch();
    const { form, select, dateInput, ok } = openAddTokenDialog();
    form.querySelector('input[name="tokenName"]').value = "my token";

    choose(select, "custom");
    pickDate(dateInput, MAX_DATE);
    expect(ok.disabled).toBe(false);
    ok.click();
    await settle();

    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("generateNewToken");
    expect(init.method).toBe("post");
    const body = init.body.toString();
    expect(body).toContain("expirationDuration=custom");
    expect(body).toContain(`tokenExpiration=${MAX_DATE}`);
    expect(body).toContain("newTokenName=my+token");
    expect(uncaughtErrors).toEqual([]);
  });

  it("sends the preset date of the server for the custom choice when the date is left alone", async () => {
    renderPage();
    const fetchMock = stubFetch();
    const { select, ok } = openAddTokenDialog();

    choose(select, "custom");
    ok.click();
    await settle();

    expect(fetchMock).toHaveBeenCalledOnce();
    const body = fetchMock.mock.calls[0][1].body.toString();
    expect(body).toContain("expirationDuration=custom");
    expect(body).toContain(`tokenExpiration=${PRESET_DATE}`);
  });

  it("does nothing when Enter is pressed while the OK button is disabled", async () => {
    renderPage();
    const fetchMock = stubFetch();
    const { select, dateInput, ok, dialogElement } = openAddTokenDialog();
    choose(select, "custom");
    pickDate(dateInput, "2031-03-05");
    expect(ok.disabled).toBe(true);

    const event = pressEnter(dateInput);
    await settle();

    // the dialog handles the key itself, so that the browser does not submit the form
    expect(event.defaultPrevented).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
    expect(dialogElement.isConnected).toBe(true);
    expect(dialogElement.hasAttribute("closing")).toBe(false);
    expect(uncaughtErrors).toEqual([]);
  });

  it("submits when Enter is pressed while the OK button is enabled", async () => {
    renderPage();
    const fetchMock = stubFetch();
    const { select, dateInput, ok } = openAddTokenDialog();
    choose(select, "custom");
    pickDate(dateInput, MAX_DATE);
    expect(ok.disabled).toBe(false);

    pressEnter(dateInput);
    await settle();

    expect(fetchMock).toHaveBeenCalledOnce();
    const body = fetchMock.mock.calls[0][1].body.toString();
    expect(body).toContain("expirationDuration=custom");
    expect(body).toContain(`tokenExpiration=${MAX_DATE}`);
    expect(uncaughtErrors).toEqual([]);
  });
});

describe("showing a token", () => {
  function newTokenResponse(data) {
    return Promise.resolve({
      ok: true,
      json: () => Promise.resolve({ status: "ok", data }),
    });
  }

  function alertDialogs() {
    return Array.from(document.querySelectorAll("dialog")).filter(
      (element) => element.querySelector(".api-token-new-value") != null,
    );
  }

  it("uses the done text of the button and the real about to expire flag when a token is shown again", () => {
    renderPage();
    const showButton = document.querySelector(
      "#api-token-row-template .api-token-property-token-show",
    );
    behaviours[".api-token-property-token-show"](showButton);
    showButton.dataset.title = "my token";
    showButton.dataset.tokenValue = "secret";
    showButton.dataset.expirationDate = "11/30/26";
    showButton.dataset.aboutToExpire = "false";
    const showToken = vi.spyOn(globalThis, "showToken");

    showButton.onclick();

    expect(showToken).toHaveBeenCalledExactlyOnceWith(
      "my token",
      "secret",
      "11/30/26",
      false,
      "Done",
    );
    const [shown] = alertDialogs();
    expect(shown.querySelector('button[data-id="ok"]').textContent).toBe(
      "Done",
    );
    const expiration = shown.querySelector(".api-token-new-expiration");
    expect(expiration.textContent).toBe("Expires on 11/30/26");
    expect(expiration.classList.contains("warning")).toBe(false);
  });

  it("flags a token that is about to expire when it is shown again", () => {
    renderPage();
    const showButton = document.querySelector(
      "#api-token-row-template .api-token-property-token-show",
    );
    behaviours[".api-token-property-token-show"](showButton);
    showButton.dataset.title = "my token";
    showButton.dataset.tokenValue = "secret";
    showButton.dataset.expirationDate = "11/30/26";
    showButton.dataset.aboutToExpire = "true";

    showButton.onclick();

    const [shown] = alertDialogs();
    expect(
      shown
        .querySelector(".api-token-new-expiration")
        .classList.contains("warning"),
    ).toBe(true);
  });

  it("shows a token that was just created with the done text, and remembers whether it is about to expire", async () => {
    renderPage();
    behaviours[".api-token-property-add"](
      document.querySelector(".api-token-property-add"),
    );
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        newTokenResponse({
          tokenName: "$& <b>name</b>",
          tokenValue: "11abc",
          tokenUuid: "uuid-1",
          expirationDate: "11/30/26",
          expirationDateIso: "2026-11-30",
          aboutToExpire: true,
        }),
      ),
    );
    const showToken = vi.spyOn(globalThis, "showToken");

    document.querySelector(".api-token-property-add").onclick();
    document.querySelector('dialog button[data-id="ok"]').click();
    await settle();

    expect(showToken).toHaveBeenCalledExactlyOnceWith(
      "$& <b>name</b>",
      "11abc",
      "11/30/26",
      true,
      "Done",
    );
    const [shown] = alertDialogs();
    expect(shown.querySelector('button[data-id="ok"]').textContent).toBe(
      "Done",
    );
    // the name of the token is text, wherever it is shown
    expect(shown.querySelector(".jenkins-dialog__title span").textContent).toBe(
      "$& <b>name</b>",
    );
    expect(shown.querySelector("b")).toBeNull();

    const row = document.getElementById("uuid-1");
    expect(row.querySelector(".token-name").textContent).toBe("$& <b>name</b>");
    const rowShowButton = row.querySelector(".api-token-property-token-show");
    expect(rowShowButton.dataset.aboutToExpire).toBe("true");
    expect(uncaughtErrors).toEqual([]);
  });
});
