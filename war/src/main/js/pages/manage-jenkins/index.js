const searchBarInput = document.querySelector("#settings-search-bar");

searchBarInput.suggestions = function () {
  return Array.from(document.querySelectorAll(".task-link"))
    .map((item) => ({
      url: item.href,
      icon: item.querySelector(".task-icon-link svg, .task-icon-link img")
        .outerHTML,
      label: item.querySelector(".task-link-text")?.textContent,
    }))
    .filter((item) => !item.url.endsWith("#"));
};
