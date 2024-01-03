import { LinkResult } from "@/components/command-palette/models";
import { JenkinsSearchSource } from "./datasources";
import debounce from "lodash/debounce";
import * as Symbols from "./symbols";
import makeKeyboardNavigable from "@/util/keyboard";

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
  headerCommandPaletteButton.addEventListener("click", async function () {
    if (commandPalette.hasAttribute("open")) {
      hideCommandPalette();
    } else {
      await showCommandPalette();
    }
  });

  commandPaletteWrapper.addEventListener("click", function (e) {
    if (e.target !== e.currentTarget) {
      return;
    }

    hideCommandPalette();
  });

  async function renderResults() {
    const query = commandPaletteInput.value;
    let results;

    if (query.length === 0) {
      results = [
        new LinkResult(
          Symbols.HELP,
          i18n.dataset.getHelp,
          "https://www.jenkins.io/redirect/search-box",
          true,
          document.getElementById("page-header").dataset.searchHelpUrl,
          true,
        ),
      ];
    } else {
      await Promise.all(datasources.map((ds) => ds.execute(query))).then(
        (response) => {
          results = response.flat();
        },
      );
    }

    // Clear current search results
    searchResults.innerHTML = "";

    if (query.length === 0 || Object.keys(results).length > 0) {
      results.forEach(function (obj) {
        const renderedObject = obj.render();

        let link = document.createElement("DIV");
        link.innerHTML = renderedObject;
        link = link.firstChild;
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
        commandPaletteInput.value.escapeHTML();
      searchResults.append(label);
    }

    searchResultsContainer.style.height = searchResults.offsetHeight + "px";
    commandPaletteSearchBarContainer.classList.remove(
      "jenkins-search--loading",
    );
  }

  commandPaletteInput.addEventListener("input", () => {
    commandPaletteSearchBarContainer.classList.add("jenkins-search--loading");
    debounce(renderResults, 300)();
  });

  // Helper methods for visibility of command palette
  async function showCommandPalette() {
    commandPalette.showModal();
    commandPaletteInput.focus();
    commandPaletteInput.setSelectionRange(
      commandPaletteInput.value.length,
      commandPaletteInput.value.length,
    );

    await renderResults();
  }

  function hideCommandPalette() {
    commandPalette.close();
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
      const element = [...searchResults.getElementsByTagName("a")][index];
      element.classList.add(hoverClass);

      if (scrollIntoView) {
        element.scrollIntoView();
      }
    }
  }
}

document.addEventListener("DOMContentLoaded", () => {
  init();
});
