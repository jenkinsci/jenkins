import resourcesSource from "../../../core/src/main/resources/jenkins/security/apitoken/ApiTokenLifetimeAdministrativeMonitor/resources.js?raw";
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

const REVOKE_URL = "/jenkins/administrativeMonitor/apiTokenLifetime/revoke";
const SHORTEN_URL = "/jenkins/administrativeMonitor/apiTokenLifetime/shorten";
const CRUMB = { "Jenkins-Crumb": "the-crumb" };

/** What a name that a user chose could do to code that interprets it. */
const HOSTILE_NAME = "$& $` %x% $1 </td><script>alert(1)</script>";

/** The behaviours that resources.js registered with Behaviour.specify, by selector. */
let behaviours;

/** The errors that listeners threw: jsdom reports them to the window instead of failing the test. */
let uncaughtErrors;

/** The stand-in for window.location.reload. */
let reload;

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
  globalThis.isSecureContext = false;
  // not implemented by jsdom: innerText, which the dialogs use to write text
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
  // the real dialogs, so that the texts and the buttons are where they are in Jenkins
  (await import("@/components/dialogs")).default.init();
  // let the translations in
  await new Promise((resolve) => setTimeout(resolve, 0));
});

beforeEach(() => {
  behaviours = {};
  uncaughtErrors = [];
  reload = vi.fn();
  window.addEventListener("error", onWindowError);
  vi.stubGlobal("location", { ...window.location, reload });
  globalThis.Behaviour = {
    register: () => {},
    specify: (selector, id, priority, fn) => {
      behaviours[selector] = fn;
    },
    applySubtree: () => {},
  };
  globalThis.crumb = { wrap: (headers) => ({ ...headers, ...CRUMB }) };
  // indirect eval: the script is a classic one, which runs in the global scope
  (0, eval)(resourcesSource);
});

afterEach(() => {
  window.removeEventListener("error", onWindowError);
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  document.body.innerHTML = "";
});

/** Sets data attributes the way the page does: as values, never parsed as markup. */
function withData(element, data) {
  for (const [name, value] of Object.entries(data)) {
    if (value !== undefined) {
      element.setAttribute(`data-${name}`, value);
    }
  }
  return element;
}

/**
 * Builds a row the way manage.jelly renders it, from the data that the server
 * puts in the attributes. The strings are static texts of the properties.
 */
function buildButton(action, overrides = {}) {
  const url = action === "revoke" ? REVOKE_URL : SHORTEN_URL;
  const defaults = {
    "token-action": action,
    url,
    "confirm-title": action === "revoke" ? "Revoke API token" : "Shorten it",
    "confirm-message":
      action === "revoke"
        ? "Revoke this API token? Whatever uses it will stop working."
        : "Set the expiration date to today plus the maximum?",
    "confirm-message-shared":
      action === "revoke"
        ? "This token shares its secret. Revoking it also revokes these tokens:\n%names%\nThey stop working."
        : undefined,
    "ok-text": action === "revoke" ? "Revoke" : "Shorten to maximum",
    "cancel-text": "Cancel",
    "error-title": "The action was not done",
    "error-message": "The API token could not be changed.",
  };
  const button = document.createElement("button");
  button.setAttribute("type", "button");
  button.className = "jenkins-button token-lifetime-action";
  button.textContent = action;
  return withData(button, { ...defaults, ...overrides });
}

function buildRow({
  userId = "alice",
  uuid = "uuid-1",
  sharedWith = [],
  actions = ["revoke", "shorten"],
  sharedWithAttribute,
  sharesSecret = sharedWith.length > 0,
  rowData = {},
} = {}) {
  const row = document.createElement("tr");
  withData(row, {
    "user-id": userId,
    uuid,
    "shares-secret": String(sharesSecret),
    "shared-with":
      sharedWithAttribute !== undefined
        ? sharedWithAttribute
        : JSON.stringify(sharedWith),
    ...rowData,
  });
  const cell = document.createElement("td");
  cell.className = "token-actions";
  const buttonsRow = document.createElement("div");
  buttonsRow.className = "jenkins-buttons-row";
  for (const action of actions) {
    buttonsRow.appendChild(buildButton(action));
  }
  cell.appendChild(buttonsRow);
  row.appendChild(cell);
  return row;
}

/** Puts the rows in a table in the page and applies the behaviours, as Behaviour does when the page is loaded. */
function renderPage(...rows) {
  const table = document.createElement("table");
  const body = document.createElement("tbody");
  rows.forEach((row) => body.appendChild(row));
  table.appendChild(body);
  document.body.appendChild(table);
  applyBehaviours();
  return table;
}

function applyBehaviours() {
  for (const [selector, behaviour] of Object.entries(behaviours)) {
    document
      .querySelectorAll(selector)
      .forEach((element) => behaviour(element));
  }
}

function buttonOf(row, action) {
  return row.querySelector(`button[data-token-action="${action}"]`);
}

/** Settles the promises of the dialog and of fetch. */
function settle() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

/** The dialog that asks for the confirmation, with the parts that the tests read. */
function confirmationDialog() {
  const dialogs = [...document.querySelectorAll("dialog")].filter((dialog) =>
    dialog.querySelector('button[data-id="ok"]'),
  );
  expect(dialogs).toHaveLength(1);
  const dialogElement = dialogs[0];
  return {
    dialogElement,
    title: dialogElement.querySelector(".jenkins-dialog__title span")
      .textContent,
    message: dialogElement.querySelector(".jenkins-form-item")?.textContent,
    ok: dialogElement.querySelector('button[data-id="ok"]'),
    cancel: dialogElement.querySelector('button[data-id="cancel"]'),
  };
}

function dialogTitles() {
  return [
    ...document.querySelectorAll("dialog .jenkins-dialog__title span"),
  ].map((title) => title.textContent);
}

/** Replaces fetch, restored after each test. */
function stubFetch(response = { ok: true }) {
  const fetchMock = vi.fn(() => Promise.resolve(response));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("the actions of the page", () => {
  it("registers one behaviour for the action buttons", () => {
    expect(Object.keys(behaviours)).toEqual([".token-lifetime-action"]);
  });

  it("confirms a Revoke on a row that shares its secret by listing the other tokens as text, whatever their names hold", async () => {
    const row = buildRow({
      sharedWith: ["first", HOSTILE_NAME, "%names%"],
    });
    renderPage(row);

    buttonOf(row, "revoke").click();
    await settle();

    const { message, dialogElement } = confirmationDialog();
    // the placeholder is filled once, with the names as they are: nothing in them is a pattern of a replacement
    expect(message).toBe(
      `This token shares its secret. Revoking it also revokes these tokens:\n- first\n- ${HOSTILE_NAME}\n- %names%\nThey stop working.`,
    );
    // the names are text, not markup
    expect(dialogElement.querySelectorAll("script, img")).toHaveLength(0);
    expect(document.querySelectorAll("script, img")).toHaveLength(0);
    expect(
      dialogElement.querySelector(".jenkins-form-item").children,
    ).toHaveLength(0);
    expect(uncaughtErrors).toEqual([]);
  });

  it("shows the title and the labels of the buttons that the server rendered, and nothing else", async () => {
    const row = buildRow({ sharedWith: ["other"] });
    renderPage(row);

    buttonOf(row, "revoke").click();
    await settle();

    const { title, ok, cancel } = confirmationDialog();
    expect(title).toBe("Revoke API token");
    expect(ok.textContent).toBe("Revoke");
    expect(cancel.textContent).toBe("Cancel");
    expect(ok.classList.contains("jenkins-!-destructive-color")).toBe(true);
  });

  it("confirms with the static text when the row does not share its secret, even if the names are there", async () => {
    const row = buildRow({
      sharedWith: ["not listed"],
      sharesSecret: false,
    });
    renderPage(row);

    buttonOf(row, "revoke").click();
    await settle();

    expect(confirmationDialog().message).toBe(
      "Revoke this API token? Whatever uses it will stop working.",
    );
  });

  it("confirms a Shorten with its own text on a row that shares its secret, since it changes that token only", async () => {
    const row = buildRow({ sharedWith: ["other"] });
    renderPage(row);

    buttonOf(row, "shorten").click();
    await settle();

    const { title, message, ok } = confirmationDialog();
    expect(title).toBe("Shorten it");
    expect(message).toBe("Set the expiration date to today plus the maximum?");
    expect(ok.textContent).toBe("Shorten to maximum");
  });

  it("falls back to the static text of the button when the row has no names to list, whatever the attribute holds", async () => {
    for (const sharedWithAttribute of [
      undefined,
      "not json",
      "{}",
      "null",
      '"a string"',
      "[]",
    ]) {
      document.body.innerHTML = "";
      behaviours = {};
      (0, eval)(resourcesSource);
      const row = buildRow({ sharesSecret: true, sharedWithAttribute });
      if (sharedWithAttribute === undefined) {
        row.removeAttribute("data-shared-with");
      }
      renderPage(row);

      buttonOf(row, "revoke").click();
      await settle();

      // the shared text is used, with nobody listed
      expect(confirmationDialog().message).toBe(
        "This token shares its secret. Revoking it also revokes these tokens:\n\nThey stop working.",
      );
      expect(uncaughtErrors).toEqual([]);
    }
  });

  it("lists only the entries of the attribute that are strings", async () => {
    const row = buildRow({
      sharedWithAttribute: JSON.stringify(["one", 2, null, { a: 1 }, "three"]),
      sharesSecret: true,
    });
    renderPage(row);

    buttonOf(row, "revoke").click();
    await settle();

    expect(confirmationDialog().message).toContain(":\n- one\n- three\n");
    expect(uncaughtErrors).toEqual([]);
  });

  it("does nothing until the confirmation is given, and nothing at all when it is cancelled", async () => {
    const fetchMock = stubFetch();
    const row = buildRow();
    renderPage(row);

    buttonOf(row, "revoke").click();
    await settle();
    expect(fetchMock).not.toHaveBeenCalled();

    confirmationDialog().cancel.click();
    await settle();

    expect(fetchMock).not.toHaveBeenCalled();
    expect(reload).not.toHaveBeenCalled();
    expect(dialogTitles()).toEqual(["Revoke API token"]);
    expect(uncaughtErrors).toEqual([]);
  });

  it("posts the ID of the user and the UUID of the token of the row as JSON, with the crumb, when the confirmation is given", async () => {
    const fetchMock = stubFetch();
    const row = buildRow({ userId: `ev"il'<b> ${HOSTILE_NAME}`, uuid: "u-9" });
    renderPage(row);

    buttonOf(row, "revoke").click();
    await settle();
    confirmationDialog().ok.click();
    await settle();

    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(REVOKE_URL);
    expect(init.method).toBe("post");
    expect(init.headers).toEqual({
      "Content-Type": "application/json",
      ...CRUMB,
    });
    expect(JSON.parse(init.body)).toEqual({
      userId: `ev"il'<b> ${HOSTILE_NAME}`,
      uuid: "u-9",
    });
    expect(uncaughtErrors).toEqual([]);
  });

  it("posts a Shorten to its own URL", async () => {
    const fetchMock = stubFetch();
    const row = buildRow({ userId: "bob", uuid: "u-3" });
    renderPage(row);

    buttonOf(row, "shorten").click();
    await settle();
    confirmationDialog().ok.click();
    await settle();

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock.mock.calls[0][0]).toBe(SHORTEN_URL);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      userId: "bob",
      uuid: "u-3",
    });
  });

  it("reloads the page when the server did the action, and says nothing", async () => {
    stubFetch({ ok: true });
    const row = buildRow();
    renderPage(row);

    buttonOf(row, "revoke").click();
    await settle();
    confirmationDialog().ok.click();
    await settle();

    expect(reload).toHaveBeenCalledOnce();
    expect(dialogTitles()).toEqual(["Revoke API token"]);
    expect(uncaughtErrors).toEqual([]);
  });

  it("shows the static error and does not reload when the server refuses, and lets the button be used again", async () => {
    stubFetch({ ok: false, status: 409 });
    const row = buildRow({ sharedWith: ["other"] });
    renderPage(row);
    const button = buttonOf(row, "revoke");

    button.click();
    await settle();
    confirmationDialog().ok.click();
    await settle();

    expect(reload).not.toHaveBeenCalled();
    expect(dialogTitles()).toEqual([
      "Revoke API token",
      "The action was not done",
    ]);
    const alertDialog = [...document.querySelectorAll("dialog")].at(-1);
    expect(alertDialog.querySelector(".jenkins-form-item").textContent).toBe(
      "The API token could not be changed.",
    );
    expect(button.disabled).toBe(false);
    expect(uncaughtErrors).toEqual([]);
  });

  it("shows the same error and does not reload when the request fails", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.reject(new TypeError("Failed to fetch"))),
    );
    const row = buildRow();
    renderPage(row);

    buttonOf(row, "shorten").click();
    await settle();
    confirmationDialog().ok.click();
    await settle();

    expect(reload).not.toHaveBeenCalled();
    expect(dialogTitles()).toEqual(["Shorten it", "The action was not done"]);
    expect(uncaughtErrors).toEqual([]);
  });

  it("keeps the button disabled while the request is pending, so that the action is not sent twice", async () => {
    let answer;
    const fetchMock = vi.fn(
      () =>
        new Promise((resolve) => {
          answer = resolve;
        }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const row = buildRow();
    renderPage(row);
    const button = buttonOf(row, "revoke");

    button.click();
    await settle();
    confirmationDialog().ok.click();
    await settle();
    expect(button.disabled).toBe(true);

    answer({ ok: true });
    await settle();
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(reload).toHaveBeenCalledOnce();
  });

  it("finds the row from the button, whatever is between them, and acts on that row and no other", async () => {
    const fetchMock = stubFetch();
    const first = buildRow({ userId: "alice", uuid: "u-1" });
    const second = buildRow({ userId: "bob", uuid: "u-2" });
    // the button is deeper than the cell: the row is still the closest tr
    const wrapper = document.createElement("span");
    const secondButton = buttonOf(second, "revoke");
    secondButton.replaceWith(wrapper);
    wrapper.appendChild(secondButton);
    renderPage(first, second);

    secondButton.click();
    await settle();
    confirmationDialog().ok.click();
    await settle();

    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      userId: "bob",
      uuid: "u-2",
    });
  });

  it("does nothing, and does not throw, when what the action needs is missing", async () => {
    const fetchMock = stubFetch();
    // no row around the button
    const orphan = buildButton("revoke");
    document.body.appendChild(orphan);
    // no URL
    const withoutUrl = buildRow({ uuid: "u-1", actions: ["revoke"] });
    buttonOf(withoutUrl, "revoke").removeAttribute("data-url");
    // no UUID, no user ID
    const withoutUuid = buildRow({ uuid: "u-2", actions: ["revoke"] });
    withoutUuid.removeAttribute("data-uuid");
    const withoutUser = buildRow({ uuid: "u-3", actions: ["revoke"] });
    withoutUser.removeAttribute("data-user-id");
    renderPage(withoutUrl, withoutUuid, withoutUser);

    for (const button of document.querySelectorAll("button")) {
      button.click();
    }
    await settle();

    expect(document.querySelectorAll("dialog")).toHaveLength(0);
    expect(fetchMock).not.toHaveBeenCalled();
    expect(reload).not.toHaveBeenCalled();
    expect(uncaughtErrors).toEqual([]);
  });

  it("opens the dialog with the defaults of the dialog when the labels are missing, never with the word undefined", async () => {
    const row = buildRow();
    for (const name of [
      "ok-text",
      "cancel-text",
      "confirm-title",
      "confirm-message",
    ]) {
      buttonOf(row, "revoke").removeAttribute(`data-${name}`);
    }
    renderPage(row);

    buttonOf(row, "revoke").click();
    await settle();

    const { title, ok, cancel, dialogElement } = confirmationDialog();
    expect(title).toBe("");
    expect(ok.textContent).toBe("Yes");
    expect(cancel.textContent).toBe("Cancel");
    expect(dialogElement.textContent).not.toContain("undefined");
    expect(uncaughtErrors).toEqual([]);
  });

  it("opens an error with no text rather than undefined when the error attributes are missing", async () => {
    stubFetch({ ok: false });
    const row = buildRow();
    buttonOf(row, "revoke").removeAttribute("data-error-title");
    buttonOf(row, "revoke").removeAttribute("data-error-message");
    renderPage(row);

    buttonOf(row, "revoke").click();
    await settle();
    confirmationDialog().ok.click();
    await settle();

    expect(reload).not.toHaveBeenCalled();
    expect(dialogTitles()).toEqual(["Revoke API token", ""]);
    expect(document.body.textContent).not.toContain("undefined");
    expect(uncaughtErrors).toEqual([]);
  });

  it("never writes what users chose as HTML, which the source of the script shows too", () => {
    const code = resourcesSource
      .replace(/\/\*[\s\S]*?\*\//g, "")
      .replace(/\/\/.*$/gm, "");

    expect(code).not.toMatch(
      /innerHTML|outerHTML|insertAdjacentHTML|createElementFromHtml|document\.write/,
    );
    expect(code).not.toContain("`");
    expect(code).not.toMatch(/\.replace\(\s*["']/);
    expect(code).not.toMatch(/\son[a-z]+\s*=/);
  });
});
