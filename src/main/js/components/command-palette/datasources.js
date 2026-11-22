import { LinkResult } from "./models";
import Search from "@/api/search";
import Templates from "@/components/dropdowns/templates";
import behaviorShim from "@/util/behavior-shim";
import Path from "@/util/path";

const NON_ACTION_TYPES = ["HEADER", "SEPARATOR", "DISABLED", "CUSTOM"];

let pageActions = null;

function isAction(item) {
  if (!item.displayName || NON_ACTION_TYPES.includes(item.type)) {
    return false;
  }
  return !item.subMenu || item.event?.event != null;
}

function loadPageActions() {
  if (pageActions) {
    return pageActions;
  }

  const overflowButton = document.querySelector(
    "[data-type='auto-overflow']:not([data-compact='true'])",
  );
  if (!overflowButton) {
    pageActions = Promise.resolve([]);
    return pageActions;
  }

  const template = JSON.parse(overflowButton.nextSibling.content.textContent);
  const href = overflowButton.dataset.href;
  const appBarActions = template.items.map((item) => ({
    item,
    baseUrl: template.url,
  }));

  pageActions = fetch(Path.combinePath(href, "contextMenu") + "?menu-only=true")
    .then((response) => response.json())
    .then((json) => json.items.map((item) => ({ item, baseUrl: href })))
    .catch(() => [])
    .then((menuActions) => {
      const seen = new Set();
      return [...appBarActions, ...menuActions].filter(({ item }) => {
        if (!isAction(item) || seen.has(item.displayName)) {
          return false;
        }
        seen.add(item.displayName);
        return true;
      });
    });
  return pageActions;
}

function linkUrl(item, baseUrl) {
  const event = item.event?.event ?? item.event;
  if (event?.type !== "GET") {
    return null;
  }
  if (event.url.startsWith("/") || event.url.startsWith("http")) {
    return event.url;
  }
  return (item.baseUrl || baseUrl) + event.url;
}

function runAction(item, baseUrl) {
  let runner = document.getElementById("command-palette-action-runner");
  if (!runner) {
    runner = document.createElement("div");
    runner.id = "command-palette-action-runner";
    runner.hidden = true;
    document.body.append(runner);
  }

  const element = Templates.menuItem(item, "jenkins-dropdown__item", baseUrl);
  runner.replaceChildren(element);
  behaviorShim.applySubtree(runner, true);

  const target = element.matches("a, button")
    ? element
    : element.querySelector("a, button");
  target?.click();
}

export const PageActionsSource = {
  execute(query) {
    const group = document.getElementById("command-palette-i18n").dataset
      .actions;
    const normalizedQuery = query.toLowerCase();

    return loadPageActions().then((actions) =>
      actions
        .filter(({ item }) =>
          item.displayName.toLowerCase().includes(normalizedQuery),
        )
        .map(({ item, baseUrl }) => {
          const url = linkUrl(item, baseUrl);
          return LinkResult({
            icon: item.iconXml || "",
            type: "symbol",
            label: item.displayName,
            url,
            group,
            semantic: item.semantic,
            onSelect: url ? undefined : () => runAction(item, baseUrl),
          });
        }),
    );
  },
};

export const PageTabsSource = {
  execute(query) {
    const group = document.getElementById("command-palette-i18n").dataset.pages;
    const normalizedQuery = query.toLowerCase();

    const tabs = Array.from(document.querySelectorAll(".app-build-tabs > a"))
      .map((tab) => {
        const label = tab.cloneNode(true);
        label.querySelectorAll(".jenkins-badge").forEach((e) => e.remove());
        return {
          label: label.textContent.trim(),
          url: tab.getAttribute("href"),
          icon: tab.querySelector("svg")?.outerHTML ?? "",
        };
      })
      .filter((tab) => tab.label.toLowerCase().includes(normalizedQuery))
      .map((tab) =>
        LinkResult({
          icon: tab.icon,
          type: "symbol",
          label: tab.label,
          url: tab.url,
          group,
        }),
      );

    return Promise.resolve(tabs);
  },
};

export const JenkinsSearchSource = {
  execute(query) {
    const rootUrl = document.head.dataset.rooturl;

    function correctAddress(url) {
      if (url.startsWith("/")) {
        url = url.substring(1);
      }

      return rootUrl + "/" + url;
    }

    return Search.search(query).then((rsp) =>
      rsp.json().then((data) => {
        return data["suggestions"].slice().map((e) =>
          LinkResult({
            icon: e.icon,
            type: e.type,
            label: e.name,
            description: e.description,
            url: correctAddress(e.url),
            group: e.group,
          }),
        );
      }),
    );
  },
};
