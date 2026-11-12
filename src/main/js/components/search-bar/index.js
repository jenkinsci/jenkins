import { createElementFromHtml } from "@/util/dom";
import makeKeyboardNavigable from "@/util/keyboard";
import { xmlEscape } from "@/util/security";

const SELECTED_CLASS = "jenkins-dropdown__item--selected";

function init() {
  const searchBarInputs = document.querySelectorAll(".jenkins-search__input");

  Array.from(searchBarInputs).forEach((searchBar) => {
    let suggestions = searchBar.suggestions;
    let initialized = false;
    let searchWrapper;
    let searchResultsContainer;
    let searchResults;

    function showResultsContainer() {
      searchResultsContainer.classList.add(
        "jenkins-search__results-container--visible",
      );
    }

    function hideResultsContainer() {
      searchResultsContainer.classList.remove(
        "jenkins-search__results-container--visible",
      );
      searchResultsContainer.style.height = "1px";
    }

    function appendResults(container, results) {
      results.forEach((item, index) => {
        container.appendChild(
          createElementFromHtml(
            `<a class="jenkins-dropdown__item ${index === 0 ? SELECTED_CLASS : ""}" href="${
              item.url
            }"><div class="jenkins-dropdown__item__icon">${item.icon}</div>${xmlEscape(item.label)}</a>`,
          ),
        );
      });

      if (results.length === 0 && container === searchResults) {
        container.appendChild(
          createElementFromHtml(
            `<p class="jenkins-search__results__no-results-label">No results</p>`,
          ),
        );
      }
    }

    function refreshResults() {
      if (!initialized) {
        return;
      }

      const query = searchBar.value.toLowerCase();

      // Hide the suggestions if the search query is empty
      if (query.length === 0 || typeof searchBar.suggestions !== "function") {
        searchResults.innerHTML = "";
        hideResultsContainer();
        return;
      }

      showResultsContainer();

      // Filter results
      const results = searchBar
        .suggestions()
        .filter((item) => item.label.toLowerCase().includes(query))
        .slice(0, 5);

      searchResults.innerHTML = "";
      appendResults(searchResults, results);
      searchResultsContainer.style.height = searchResults.offsetHeight + "px";
    }

    function initializeSearchBar() {
      if (initialized || typeof searchBar.suggestions !== "function") {
        return;
      }

      initialized = true;
      searchWrapper = searchBar.parentElement.parentElement;
      searchResultsContainer = createElementFromHtml(
        `<div class="jenkins-search__results-container"></div>`,
      );
      searchWrapper.appendChild(searchResultsContainer);
      searchResults = createElementFromHtml(
        `<div class="jenkins-dropdown"></div>`,
      );
      searchResultsContainer.appendChild(searchResults);

      searchBar.addEventListener("input", refreshResults);

      searchBar.addEventListener("keydown", (e) => {
        if (e.key === "ArrowUp" || e.key === "ArrowDown") {
          e.preventDefault();
        }
      });

      makeKeyboardNavigable(
        searchResultsContainer,
        () => searchResults.querySelectorAll("a"),
        SELECTED_CLASS,
      );

      // Workaround: Firefox doesn't update the dropdown height correctly so
      // let's bind the container's height to it's child
      // Disabled in HtmlUnit
      if (!document.head.getAttribute("data-unit-test")) {
        new ResizeObserver(() => {
          searchResultsContainer.style.height =
            searchResults.offsetHeight + "px";
        }).observe(searchResults);
      }

      searchBar.addEventListener("focusin", () => {
        if (searchBar.value.length !== 0) {
          refreshResults();
        }
      });

      document.addEventListener("click", (event) => {
        if (searchWrapper.contains(event.target)) {
          return;
        }

        hideResultsContainer();
      });
    }

    Object.defineProperty(searchBar, "suggestions", {
      configurable: true,
      enumerable: true,
      get() {
        return suggestions;
      },
      set(value) {
        suggestions = value;
        initializeSearchBar();
        refreshResults();
      },
    });

    initializeSearchBar();
  });
}

export default { init };
