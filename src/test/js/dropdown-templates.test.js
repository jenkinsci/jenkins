import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

let Templates;

beforeAll(async () => {
  globalThis.Behaviour = {
    register: () => {},
    specify: () => {},
    applySubtree: () => {},
  };
  Templates = (await import("@/components/dropdowns/templates")).default;
});

describe("Templates.menuItem POST actions", () => {
  beforeEach(() => {
    vi.stubGlobal("crumb", {
      wrap: (headers) => ({ ...headers, "Jenkins-Crumb": "test-crumb" }),
    });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true }));
  });

  it("keeps POST requests alive while navigating away", () => {
    const item = Templates.menuItem({
      displayName: "Build Now",
      event: { url: "/job/example/build?delay=0sec", type: "POST" },
    });

    item.click();

    expect(fetch).toHaveBeenCalledWith(
      "/job/example/build?delay=0sec",
      expect.objectContaining({
        method: "post",
        headers: { "Jenkins-Crumb": "test-crumb" },
        keepalive: true,
      }),
    );
  });
});
