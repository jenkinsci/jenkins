const searchBarInput = document.querySelector("#settings-search-bar");

searchBarInput.suggestions = function () {
  return Array.from(document.querySelectorAll(".jenkins-section__item"))
    .map((item) => ({
      url: item.querySelector("a").href,
      icon: item.querySelector(
        ".jenkins-section__item__icon svg, .jenkins-section__item__icon img",
      ).outerHTML,
      label: item.querySelector("dt").textContent,
    }))
    .filter((item) => !item.url.endsWith("#"));
};
