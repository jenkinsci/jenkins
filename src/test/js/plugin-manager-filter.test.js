import tableSource from "../../../core/src/main/resources/hudson/PluginManager/_table.js?raw";
import { beforeEach, describe, expect, it } from "vitest";

/**
 * _table.js is a classic script (a Stapler adjunct) that registers the filter
 * through Behaviour.specify, so evaluate it in the global scope and capture
 * the registered callback.
 */
function loadTable() {
  let filterBehaviour;
  globalThis.Behaviour = {
    specify: (selector, id, priority, callback) => {
      filterBehaviour = callback;
    },
  };
  globalThis.layoutUpdateCallback = { call: () => {} };
  // indirect eval, so that the script runs in the global scope
  (0, eval)(tableSource);
  return filterBehaviour;
}

function pluginRow({
  id,
  name,
  description = "",
  dependencies = [],
  categories = [],
}) {
  const dependencySpans = dependencies
    .map((dependency) => `<span data-plugin-id="x">${dependency}</span>`)
    .join("");
  // same markup as updates.jelly renders for the plugin categories
  const categoryBadges = categories.length
    ? `<div class="app-plugin-manager__categories">${categories
        .map((category) => `<a href="#" class="jenkins-badge">${category}</a>`)
        .join("")}</div>`
    : "";
  return `
    <tr class="plugin" data-plugin-id="${id}">
      <td class="details">
        <a href="#">
          <span class="app-plugin-manager__name">${name}</span>
          <span class="jenkins-label">
            <span class="jenkins-visually-hidden">Version</span>
            1.0
          </span>
        </a>
        <div class="dependency-list">${dependencySpans}</div>
        ${categoryBadges}
        <div class="app-plugin-manager__description">${description}</div>
      </td>
    </tr>`;
}

describe("plugin manager table filter", () => {
  let filterBox;

  beforeEach(() => {
    document.body.innerHTML = `
      <input id="filter-box" value="">
      <table id="plugins"><tbody>
        ${pluginRow({
          id: "antisamy-markup-formatter",
          name: "OWASP Markup Formatter",
          description: "Sanitizes HTML input.",
        })}
        ${pluginRow({
          id: "matrix-auth",
          name: "Matrix Authorization Strategy",
          description: "Offers matrix-based security.",
          dependencies: ["OWASP Markup Formatter"],
        })}
        ${pluginRow({
          id: "git",
          name: "Git",
          description: "Integrates Jenkins with GIT SCM.",
          categories: ["Build Tools"],
        })}
      </tbody></table>`;
    filterBox = document.getElementById("filter-box");
    loadTable()(filterBox);
  });

  function search(query) {
    filterBox.value = query;
    filterBox.dispatchEvent(new Event("input"));
  }

  function visibleIds() {
    return Array.from(document.querySelectorAll("tr.plugin"))
      .filter((row) => !row.classList.contains("jenkins-hidden"))
      .map((row) => row.getAttribute("data-plugin-id"));
  }

  it("shows all plugins when the filter is empty", () => {
    expect(visibleIds()).toEqual([
      "antisamy-markup-formatter",
      "matrix-auth",
      "git",
    ]);
  });

  it("matches the plugin name", () => {
    search("owasp");
    expect(visibleIds()).toEqual(["antisamy-markup-formatter"]);
  });

  it("matches the plugin id", () => {
    search("matrix-auth");
    expect(visibleIds()).toEqual(["matrix-auth"]);
  });

  it("matches the description", () => {
    search("scm");
    expect(visibleIds()).toEqual(["git"]);
  });

  it("matches a category", () => {
    search("build tools");
    expect(visibleIds()).toEqual(["git"]);
  });

  it("requires every word to match", () => {
    search("markup sanitizes");
    expect(visibleIds()).toEqual(["antisamy-markup-formatter"]);
    search("markup scm");
    expect(visibleIds()).toEqual([]);
  });

  it("does not match hidden text, even once a row has been hidden", () => {
    // hide every row first, then search for text that only appears in the
    // hidden dependency list of matrix-auth or in visually hidden labels.
    // In a browser, innerText of a row hidden with display:none falls back to
    // textContent, which is how the old filter picked this text up. jsdom has
    // no layout and no innerText, so this asserts the new text source directly.
    search("zzz");
    expect(visibleIds()).toEqual([]);
    search("owasp");
    expect(visibleIds()).toEqual(["antisamy-markup-formatter"]);
    search("version");
    expect(visibleIds()).toEqual([]);
  });
});
