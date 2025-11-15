const searchBarInput = document.querySelector("#settings-search-bar");

searchBarInput.suggestions = function () {
  return Array.from(
    document.querySelectorAll(
      ".jenkins-section__item, #tasks .task-link-wrapper",
    ),
  )
    .map((item) => ({
      url: item.querySelector("a").href,
      icon: item.querySelector(
        ".jenkins-section__item__icon svg, .jenkins-section__item__icon img, .task-icon-link svg, .task-icon-link img",
      ).outerHTML,
      label: (
        item.querySelector("dt") ||
        item.querySelector(".task-link-text") ||
        item.querySelector(".task-link")
      ).textContent,
    }))
    .filter((item) => !item.url.endsWith("#"));
};
