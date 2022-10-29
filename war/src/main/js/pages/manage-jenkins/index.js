const searchBarInput = document.querySelector("#settings-search-bar");

searchBarInput.suggestions = () => {
  return [...document.querySelectorAll(".jenkins-section__item")]
    .map((item) => ({
      url: item.querySelector("a").href,
      icon: item.querySelector(
        ".jenkins-section__item__icon svg, .jenkins-section__item__icon img"
      ).outerHTML,
      label: item.querySelector("dt").textContent,
      children: [],
    }))
    .filter((item) => !item.url.endsWith("#"));
};
