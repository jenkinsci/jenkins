import Utils from "@/components/dropdowns/utils";

function init() {
  window.addEventListener("scroll", () => {
    const navigation = document.querySelector("#page-header");
    const scrollY = Math.max(0, window.scrollY);
    navigation.style.setProperty(
      "--background-opacity",
      Math.min(70, scrollY) + "%",
    );
    navigation.style.setProperty(
      "--background-blur",
      Math.min(40, scrollY) + "px",
    );
    if (
      !document.querySelector(".jenkins-search--app-bar") &&
      !document.querySelector(".app-page-body__sidebar--sticky")
    ) {
      navigation.style.setProperty(
        "--border-opacity",
        Math.min(10, scrollY) + "%",
      );
    }
  });

  window.addEventListener("resize", computeBreadcrumbs);
  computeBreadcrumbs();
}

function computeBreadcrumbs() {
  const breadcrumbsOverflow = document.querySelector("#button-breadcrumbs-overflow");
  const breadcrumbs = [...document.querySelectorAll(".jenkins-header__breadcrumbs__list-item")].slice(2)

  breadcrumbsOverflow.parentNode.classList.remove("jenkins-hidden");
  breadcrumbs.forEach(b => {
    b.classList.remove("jenkins-hidden");
  })

  if (!breadcrumbsBarOverflows()) {
    breadcrumbsOverflow.parentNode.classList.add("jenkins-hidden");
  }

  const items = [];
  while (breadcrumbsBarOverflows()) {
    const item = breadcrumbs.shift();
    items.push(item);
    item.classList.add("jenkins-hidden");
  }

  Utils.generateDropdown(breadcrumbsOverflow, (instance) => {
    const mappedItems = items.map(e => (
      {
        type: "link",
        label: e.textContent,
        url: e.querySelector("a")?.href,
      }
    ));

    instance.setContent(Utils.generateDropdownItems(mappedItems));
  });
}

function breadcrumbsBarOverflows() {
  const breadcrumbsBar = document.querySelector("#breadcrumbBar");
  return breadcrumbsBar.scrollWidth > breadcrumbsBar.offsetWidth;
}

init();
