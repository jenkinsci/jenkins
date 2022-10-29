import { createElementFromHtml } from "../../util/dom";

const init = () => {
  const searchBarInputs = document.querySelectorAll(".jenkins-search__input");

  [...searchBarInputs]
    .filter((searchBar) => searchBar.suggestions)
    .forEach((searchBar) => {
      const searchWrapper = searchBar.parentElement.parentElement;
      const searchResultsContainer = createElementFromHtml(
        `<div class="jenkins-search__results-container"></div>`
      );
      searchWrapper.append(searchResultsContainer);
      const searchResults = createElementFromHtml(
        `<div class="jenkins-search__results"></div>`
      );
      searchResultsContainer.append(searchResults);

      searchBar.addEventListener("input", () => {
        const query = searchBar.value.toLowerCase();

        // Hide the suggestions if the search query is empty
        if (query.length === 0) {
          hideResultsContainer();
          return;
        }

        showResultsContainer();

        function appendResults(container, results) {
          results.forEach((item) => {
            container.append(
              createElementFromHtml(
                `<a href="${item.url}"><div>${item.icon}</div>${item.label}</a>`
              )
            );
            const children = createElementFromHtml(`<div></div>`);
            appendResults(children, item["children"]);
            container.append(children);
          });

          if (results.length === 0 && container === searchResults) {
            container.append(
              createElementFromHtml(
                `<p class="jenkins-search__results__no-results-label">No results</p>`
              )
            );
          }
        }

        // Filter results
        const results = searchBar
          .suggestions()
          .filter(
            (item) =>
              item.label.toLowerCase().includes(query) ||
              item.children.some((child) =>
                child.label.toLowerCase().includes(query)
              )
          )
          .map((item) => {
            item.children = item.children.filter((child) =>
              child.label.toLowerCase().includes(query)
            );
            return item;
          })
          .slice(0, 5);

        searchResults.innerHTML = "";
        appendResults(searchResults, results);
        searchResultsContainer.style.height = searchResults.offsetHeight + "px";
      });

      function showResultsContainer() {
        searchResultsContainer.classList.add(
          "jenkins-search__results-container--visible"
        );
      }

      function hideResultsContainer() {
        searchResultsContainer.classList.remove(
          "jenkins-search__results-container--visible"
        );
        searchResultsContainer.style.height = "1px";
      }

      searchBar.addEventListener("keydown", (e) => {
        if (e.keyCode === 13) {
          e.preventDefault();

          const topResult = searchResults.querySelector("a");
          topResult?.click();
        }

        if (e.keyCode === 38 || e.keyCode === 40) {
          console.log('im being hit')
          e.preventDefault();
        }
      })

      searchBar.addEventListener("focusin", () => {
        if (searchBar.value.length !== 0) {
          searchResultsContainer.style.height =
            searchResults.offsetHeight + "px";
          showResultsContainer();
        }
      });

      document.addEventListener("click", (event) => {
        if (searchWrapper.contains(event.target)) {
          return;
        }

        hideResultsContainer();
      });
    });
};

export default { init };
