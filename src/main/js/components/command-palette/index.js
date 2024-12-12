import { LinkResult } from "@/components/command-palette/models";
import { JenkinsSearchSource } from "./datasources";
import debounce from "lodash/debounce";
import * as Symbols from "./symbols";
import makeKeyboardNavigable from "@/util/keyboard";
import { xmlEscape } from "@/util/security";
import { createElementFromHtml } from "@/util/dom";

const datasources = [JenkinsSearchSource];

function init() {
  const i18n = document.getElementById("command-palette-i18n");
  const headerCommandPaletteButton = document.getElementById(
    "button-open-command-palette",
  );
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

  makeKeyboardNavigable(
    searchResultsContainer,
    () => searchResults.querySelectorAll("a"),
    hoverClass,
  );

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

  function renderResults() {
    const query = commandPaletteInput.value;
    let results;

    if (query.length === 0) {
      results = Promise.all([
        LinkResult({
          icon: Symbols.HELP,
          label: i18n.dataset.getHelp,
          url: headerCommandPaletteButton.dataset.searchHelpUrl,
          isExternal: true,
        }),
      ]);
    } else {
      results = Promise.all(datasources.map((ds) => ds.execute(query))).then(
        (e) => e.flat(),
      );
    }

    results.then((results) => {
      // Clear current search results
      searchResults.innerHTML = "";

      if (query.length === 0 || Object.keys(results).length > 0) {
        results.forEach(function (obj) {
          const link = createElementFromHtml(obj.render());
          link.addEventListener("mouseenter", (e) => itemMouseEnter(e));
          searchResults.append(link);
        });

        updateSelectedItem(0);
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

      searchResultsContainer.style.height = searchResults.offsetHeight + "px";
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
  function showCommandPalette() {
    commandPalette.showModal();
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

  function itemMouseEnter(item) {
    let hoveredItems = document.querySelector("." + hoverClass);
    if (hoveredItems) {
      hoveredItems.classList.remove(hoverClass);
    }

    item.target.classList.add(hoverClass);
  }

  function updateSelectedItem(index, scrollIntoView = false) {
    const maxLength = searchResults.getElementsByTagName("a").length;
    const hoveredItem = document.querySelector("." + hoverClass);

    if (hoveredItem) {
      hoveredItem.classList.remove(hoverClass);
    }

    if (index < maxLength) {
      const element = Array.from(searchResults.getElementsByTagName("a"))[
        index
      ];
      element.classList.add(hoverClass);

      if (scrollIntoView) {
        element.scrollIntoView();
      }
    }
  }
}

export default { init };
