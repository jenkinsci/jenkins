import {
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";

let Templates;

beforeAll(async () => {
  globalThis.Behaviour = {
    register: () => {},
    specify: () => {},
    applySubtree: () => {},
  };
  Templates = (await import("@/components/dropdowns/templates")).default;
});

describe("POST dropdown actions", () => {
  let requests;
  let location;
  let notificationBar;

  beforeEach(() => {
    document.head.dataset.rooturl = "/jenkins";
    document.head.dataset.dropdownPostError = "Unable to complete the request.";
    location = { href: "/jenkins/job/example/" };
    vi.stubGlobal("window", { location });
    vi.stubGlobal("crumb", {
      fieldName: "Jenkins-Crumb",
      wrap: (headers) => ({ ...headers, "Jenkins-Crumb": "expired" }),
    });
    notificationBar = { show: vi.fn(), ERROR: { sticky: true } };
    vi.stubGlobal("notificationBar", notificationBar);
    requests = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(
        (url, options) =>
          new Promise((resolve, reject) => {
            requests.push({ url, options, resolve, reject });
          }),
      ),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete document.head.dataset.rooturl;
    delete document.head.dataset.dropdownPostError;
  });

  function menuItem(url = "1/retry", context = "/jenkins/job/example/") {
    return Templates.menuItem(
      { displayName: "Retry", event: { type: "POST", url } },
      "jenkins-dropdown__item",
      context,
    );
  }

  function settle() {
    return new Promise((resolve) => setTimeout(resolve, 0));
  }

  function respond(request, body = {}, status = 200) {
    request.resolve({
      ok: status >= 200 && status < 300,
      status,
      json: () => Promise.resolve(body),
    });
    return settle();
  }

  function freshCrumb() {
    return respond(requests[0], {
      crumbRequestField: "Custom-Crumb",
      crumb: "fresh",
    });
  }

  function expectFailure(button) {
    expect(location.href).toBe("/jenkins/job/example/");
    expect(notificationBar.show).toHaveBeenCalledWith(
      document.head.dataset.dropdownPostError,
      notificationBar.ERROR,
    );
    expect(button.disabled).toBe(false);
  }

  it("fetches a fresh crumb and waits for the action before reloading", async () => {
    const button = menuItem();
    button.click();

    expect(requests).toHaveLength(1);
    expect(requests[0].url).toBe("/jenkins/crumbIssuer/api/json");
    expect(requests[0].options.cache).toBe("no-store");
    expect(location.href).toBe("/jenkins/job/example/");
    expect(button.disabled).toBe(true);

    // Repeated clicks must not schedule duplicate builds.
    button.click();
    expect(requests).toHaveLength(1);

    await freshCrumb();
    expect(requests).toHaveLength(2);
    expect(requests[1].url).toBe("/jenkins/job/example/1/retry");
    expect(requests[1].options).toEqual({
      method: "post",
      headers: { "Custom-Crumb": "fresh" },
    });
    expect(location.href).toBe("/jenkins/job/example/");

    button.click();
    expect(requests).toHaveLength(2);
    await respond(requests[1], {}, 201);
    expect(location.href).toBe(".");
    expect(notificationBar.show).not.toHaveBeenCalled();
  });

  it("preserves root-relative action URLs", async () => {
    menuItem("/jenkins/job/other/1/retry").click();
    await freshCrumb();
    expect(requests[1].url).toBe("/jenkins/job/other/1/retry");
    await respond(requests[1]);
  });

  it("works when Jenkins has no context path", async () => {
    document.head.dataset.rooturl = "";
    menuItem().click();
    expect(requests[0].url).toBe("/crumbIssuer/api/json");
    await freshCrumb();
    await respond(requests[1]);
  });

  it("posts without fetching a crumb when CSRF protection is disabled", async () => {
    crumb.fieldName = null;
    menuItem().click();
    await settle();
    expect(requests).toHaveLength(1);
    expect(requests[0].url).toBe("/jenkins/job/example/1/retry");
    expect(requests[0].options.headers).toEqual({});
    expect(location.href).toBe("/jenkins/job/example/");
    await respond(requests[0], {}, 204);
    expect(location.href).toBe(".");
  });

  it("allows an explicit retry after a failed action", async () => {
    const button = menuItem();
    button.click();
    await freshCrumb();
    await respond(requests[1], {}, 403);
    expectFailure(button);
    expect(requests).toHaveLength(2);

    button.click();
    expect(requests).toHaveLength(3);
    expect(requests[2].url).toBe("/jenkins/crumbIssuer/api/json");
    await respond(requests[2], {
      crumbRequestField: "Custom-Crumb",
      crumb: "refreshed-again",
    });
    expect(requests).toHaveLength(4);
    expect(requests[3].options.headers).toEqual({
      "Custom-Crumb": "refreshed-again",
    });
    await respond(requests[3]);
    expect(location.href).toBe(".");
    expect(notificationBar.show).toHaveBeenCalledTimes(1);
  });

  it("leaves GET links alone", () => {
    const link = Templates.menuItem(
      { displayName: "Console", event: { type: "GET", url: "1/console" } },
      "jenkins-dropdown__item",
      "/jenkins/job/example/",
    );
    // Prevent jsdom from trying to navigate after the click.
    link.addEventListener("click", (event) => event.preventDefault());
    link.click();
    expect(link.tagName).toBe("A");
    expect(link.getAttribute("href")).toBe("/jenkins/job/example/1/console");
    expect(requests).toHaveLength(0);
    expect(notificationBar.show).not.toHaveBeenCalled();
  });

  it.each([401, 403, 404, 500])(
    "does not post when the crumb request returns HTTP %i",
    async (status) => {
      const button = menuItem();
      button.click();
      await respond(requests[0], {}, status);
      expect(requests).toHaveLength(1);
      expectFailure(button);
    },
  );

  it.each([{}, { crumb: "fresh" }, { crumbRequestField: "Custom-Crumb" }])(
    "does not post with an invalid crumb response: %j",
    async (body) => {
      const button = menuItem();
      button.click();
      await respond(requests[0], body);
      expect(requests).toHaveLength(1);
      expectFailure(button);
    },
  );

  it("shows an error if the crumb endpoint returns a login page", async () => {
    const button = menuItem();
    button.click();
    requests[0].resolve({
      ok: true,
      json: () => Promise.reject(new SyntaxError("Not JSON")),
    });
    await settle();
    expect(requests).toHaveLength(1);
    expectFailure(button);
  });

  it.each([403, 500])(
    "shows HTTP %i action failures without retrying the POST",
    async (status) => {
      const button = menuItem();
      button.click();
      await freshCrumb();
      await respond(requests[1], {}, status);
      expect(requests).toHaveLength(2);
      expectFailure(button);
    },
  );

  it.each(["crumb", "action"])(
    "shows %s network failures without retrying the POST",
    async (stage) => {
      const button = menuItem();
      button.click();
      if (stage === "action") {
        await freshCrumb();
      }
      requests.at(-1).reject(new TypeError("Failed to fetch"));
      await settle();
      expect(requests).toHaveLength(stage === "action" ? 2 : 1);
      expectFailure(button);
    },
  );
});
