import { LinkResult } from "@/components/command-palette/models";
import { JenkinsSearchSource } from "./datasources";
import debounce from "lodash/debounce";
import * as Symbols from "./symbols";
import makeKeyboardNavigable from "@/util/keyboard";
import { xmlEscape } from "@/util/security";
import { createElementFromHtml } from "@/util/dom";
import { groupResultsByCategory } from "@/components/command-palette/utils";

const datasources = [JenkinsSearchSource];
const MAX_RESULTS_PER_GROUP = 5;

function startsWithQuery(result, query) {
  return result.label.toLowerCase().startsWith(query.toLowerCase());
}

function withTrailingSlash(path) {
  return path.endsWith("/") ? path : path + "/";
}

function init() {
  const i18n = document.getElementById("command-palette-i18n");
  const headerCommandPaletteButton = document.getElementById(
    "root-action-SearchAction",
  );
  if (headerCommandPaletteButton === null) {
    return; // no JenkinsHeader, no h:searchbox
  }
  const commandPalette = document.getElementById("command-palette");
  const commandPaletteWrapper = commandPalette.querySelector(
    ".jenkins-command-palette__wrapper",
  );
  const commandPaletteInput = document.getElementById("command-bar");
  const commandPaletteSearchBarContainer = commandPalette.querySelector(
    ".jenkins-command-palette__search",
  );
  const searchResults = document.getElementById("search-results");
  const searchResultsContainer = document.getElementById(
    "search-results-container",
  );

  const hoverClass = "jenkins-command-palette__results__item--hover";
  const resultSelector = ".jenkins-command-palette__results__item";

  const pageScopes = JSON.parse(commandPalette.dataset.scopes || "[]");
  const defaultPlaceholder = commandPaletteInput.placeholder;
  let scopes = [];
  const renderedResults = new WeakMap();
  let latestRequest = 0;
  let highlightOnRender = null;

  const scopesContainer = document.createElement("div");
  scopesContainer.className = "jenkins-command-palette__scopes";
  commandPaletteInput.before(scopesContainer);

  makeKeyboardNavigable(
    searchResultsContainer,
    () => searchResults.querySelectorAll(resultSelector),
    hoverClass,
    (selectedItem, key, e) => {
      if (key !== "Tab" || e.shiftKey) {
        return;
      }

      const result = selectedItem && renderedResults.get(selectedItem);
      if (!result) {
        return;
      }

      e.preventDefault();

      if (!result.searchScope) {
        fillQuery(result.label);
        return;
      }

      drillInto(result);
    },
    () =>
      commandPalette.open && !scopesContainer.contains(document.activeElement),
    updatePlaceholder,
  );

  const selectedScopesClass = "jenkins-command-palette__scopes--selected";

  function setScopesSelected(selected) {
    scopesContainer.classList.toggle(selectedScopesClass, selected);
  }

  commandPaletteInput.addEventListener("keydown", (e) => {
    if (e.key.toLowerCase() === "a" && (e.metaKey || e.ctrlKey)) {
      setScopesSelected(scopes.length > 0);
      return;
    }

    if (
      scopesContainer.classList.contains(selectedScopesClass) &&
      (e.key === "Backspace" || e.key === "Delete")
    ) {
      e.preventDefault();
      commandPaletteInput.value = "";
      scopes = [];
      updateScopes();
      return;
    }

    if (!["Shift", "Meta", "Control", "Alt"].includes(e.key)) {
      setScopesSelected(false);
    }

    if (
      e.key !== "Backspace" ||
      commandPaletteInput.value !== "" ||
      scopes.length === 0
    ) {
      return;
    }

    e.preventDefault();
    popScope();
  });

  commandPaletteInput.addEventListener("mousedown", () =>
    setScopesSelected(false),
  );

  commandPalette.addEventListener("cancel", (e) => {
    if (scopes.length === 0) {
      return;
    }

    e.preventDefault();
    popScope();
  });

  // Events
  headerCommandPaletteButton.addEventListener("click", function () {
    if (commandPalette.hasAttribute("open")) {
      hideCommandPalette();
    } else {
      showCommandPalette();
    }
  });

  commandPaletteWrapper.addEventListener("click", function (e) {
    if (e.target !== e.currentTarget) {
      return;
    }

    hideCommandPalette();
  });

  function drillInto(result) {
    scopes.push({
      label: result.label,
      url: withTrailingSlash(result.url),
      icon: result.type === "image" ? null : result.icon,
      parentQuery: commandPaletteInput.value,
    });
    commandPaletteInput.value = "";
    updateScopes();
  }

  function popScope() {
    goToScope(scopes.length - 2);
  }

  function goToScope(index) {
    const left = scopes[index + 1];
    if (!left) {
      commandPaletteInput.focus();
      return;
    }

    scopes.length = index + 1;
    commandPaletteInput.value = left.parentQuery ?? "";
    highlightOnRender = left.url;
    updateScopes();
  }

  function fillQuery(text) {
    commandPaletteInput.value = text;
    commandPaletteInput.focus();
    commandPaletteInput.setSelectionRange(text.length, text.length);
    renderNow();
  }

  function updateScopes() {
    updateScopeChips();
    updateViewportHeight();
    commandPaletteInput.focus();
    renderNow();
  }

  function renderNow() {
    debouncedLoad.cancel();
    renderResults();
  }

  function updateScopeChips() {
    setScopesSelected(false);
    scopesContainer.innerHTML = "";
    scopes.forEach((scope, index) => {
      const chip = document.createElement("button");
      chip.type = "button";
      chip.className = "jenkins-command-palette__scope";
      chip.addEventListener("click", () => goToScope(index));

      if (scope.icon) {
        const icon = document.createElement("span");
        icon.className = "jenkins-command-palette__scope__icon";
        icon.innerHTML = scope.icon;
        chip.append(icon);
      }

      const label = document.createElement("span");
      label.className = "jenkins-command-palette__scope__label";
      label.textContent = scope.label;
      chip.append(label);

      scopesContainer.append(chip);
    });
  }

  function renderResults() {
    const query = commandPaletteInput.value;
    const scope = scopes.at(-1);
    const request = ++latestRequest;
    const showHelp = !scope && query.length === 0;
    let results;

    if (showHelp) {
      results = Promise.all([
        LinkResult({
          icon: Symbols.HELP,
          type: "symbol",
          label: i18n.dataset.getHelp,
          url: document.querySelector("body").dataset.searchHelpUrl,
          isExternal: true,
          group: null,
        }),
      ]);
    } else {
      results = Promise.all(
        datasources.map((ds) => ds.execute(query, scope)),
      ).then((e) => e.flat());
    }

    results.then((results) => {
      if (request !== latestRequest) {
        return;
      }

      results = groupResultsByCategory(results);

      // Clear current search results
      searchResults.innerHTML = "";
      updatePlaceholder(null);

      if (showHelp || Object.keys(results).length > 0) {
        for (const [group, items] of Object.entries(results)) {
          if (group !== "null") {
            const heading = document.createElement("p");
            heading.className = "jenkins-command-palette__results__heading";
            heading.innerText = group;
            searchResults.append(heading);
          }

          const prioritisedItems = [...items].sort(
            (a, b) =>
              Number(startsWithQuery(b, query)) -
              Number(startsWithQuery(a, query)),
          );

          prioritisedItems
            .slice(0, scope ? undefined : MAX_RESULTS_PER_GROUP)
            .forEach(function (obj) {
              const link = createElementFromHtml(obj.render());
              renderedResults.set(link, obj);
              link.addEventListener("mouseenter", () => setHovered(link));
              link
                .querySelector(".jenkins-command-palette__results__item__hint")
                ?.addEventListener("click", (e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  drillInto(obj);
                });
              searchResults.append(link);
            });
        }

        const rendered = searchResults.querySelectorAll(resultSelector);
        const restored = highlightOnRender
          ? Array.from(rendered).findIndex(
              (element) =>
                withTrailingSlash(renderedResults.get(element).url) ===
                highlightOnRender,
            )
          : -1;
        const index = Math.max(restored, 0);
        if (rendered[index]) {
          setHovered(rendered[index]);
          if (restored > 0) {
            rendered[index].scrollIntoView({ block: "nearest" });
          }
        }
      } else {
        const label = document.createElement("p");
        label.className = "jenkins-command-palette__info";
        label.innerHTML =
          "<span>" +
          i18n.dataset.noResultsFor +
          "</span> " +
          xmlEscape(commandPaletteInput.value);
        searchResults.append(label);
      }

      highlightOnRender = null;

      syncResultsHeight();
      debouncedSpinner.cancel();
      commandPaletteSearchBarContainer.classList.remove(
        "jenkins-search--loading",
      );
    });
  }

  const debouncedSpinner = debounce(() => {
    commandPaletteSearchBarContainer.classList.add("jenkins-search--loading");
  }, 150);

  const debouncedLoad = debounce(() => {
    renderResults();
  }, 150);

  commandPaletteInput.addEventListener("input", () => {
    debouncedSpinner();
    debouncedLoad();
  });

  // Helper methods for visibility of command palette
  function syncResultsHeight() {
    searchResultsContainer.style.height = searchResults.offsetHeight + "px";
  }

  function updateViewportHeight() {
    if (!commandPalette.open) {
      return;
    }

    const height = window.visualViewport?.height ?? window.innerHeight;
    commandPalette.style.setProperty(
      "--command-palette-viewport-height",
      `${height}px`,
    );
    commandPalette.style.setProperty(
      "--command-palette-search-height",
      `${commandPaletteSearchBarContainer.offsetHeight}px`,
    );
    syncResultsHeight();
  }

  window.addEventListener("resize", updateViewportHeight);
  window.visualViewport?.addEventListener("resize", updateViewportHeight);

  function showCommandPalette() {
    scopes = pageScopes.slice();
    updateScopeChips();
    commandPalette.showModal();
    updateViewportHeight();
    commandPaletteInput.focus();
    commandPaletteInput.setSelectionRange(0, commandPaletteInput.value.length);

    renderResults();
  }

  function hideCommandPalette() {
    commandPalette.setAttribute("closing", "");

    commandPalette.addEventListener(
      "animationend",
      () => {
        commandPalette.removeAttribute("closing");
        commandPalette.close();
      },
      { once: true },
    );
  }

  function setHovered(element) {
    searchResults.querySelector("." + hoverClass)?.classList.remove(hoverClass);
    element.classList.add(hoverClass);
    updatePlaceholder(element);
  }

  function updatePlaceholder(element) {
    const result = element && renderedResults.get(element);
    commandPaletteInput.placeholder = result?.label ?? defaultPlaceholder;
  }
}

export default { init };
