import Utils from "@/components/dropdowns/utils";
import { createElementFromHtml } from "@/util/dom";

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
  document
    .querySelectorAll(".jenkins-header__breadcrumbs__list-item.jenkins-hidden")
    .forEach((b) => {
      b.classList.remove("jenkins-hidden");
    });

  if (!breadcrumbsBarOverflows()) {
    removeOverflowButton();
    return;
  }

  const items = [];
  const breadcrumbs = [
    ...document.querySelectorAll(`[data-type="breadcrumb-item"]`),
  ];
  while (breadcrumbsBarOverflows()) {
    const item = breadcrumbs.shift();
    items.push(item);
    item.classList.add("jenkins-hidden");
  }

  const breadcrumbsOverflow = generateOverflowButton().querySelector("button");
  if (breadcrumbsOverflow) {
    Utils.generateDropdown(breadcrumbsOverflow, (instance) => {
      const mappedItems = items.map((e) => ({
        type: "link",
        label: e.textContent,
        url: e.querySelector("a")?.href,
      }));

      instance.setContent(Utils.generateDropdownItems(mappedItems));
    });
  }
}

function generateOverflowButton() {
  // If an overflow menu already exists let's use that
  const overflowMenu = document.querySelector(
    ".jenkins-header__breadcrumbs__list-item .jenkins-button",
  )?.parentNode;
  if (overflowMenu) {
    return overflowMenu;
  }

  // Generate an overflow menu to store breadcrumbs
  const logo = document.querySelector(
    ".jenkins-header__breadcrumbs__list-item",
  );
  const element =
    createElementFromHtml(`<li class="jenkins-header__breadcrumbs__list-item"><button class="jenkins-button jenkins-button--tertiary"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
    <circle cx="256" cy="256" r="45" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"/>
    <circle cx="441" cy="256" r="45" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"/>
    <circle cx="71" cy="256" r="45" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"/>
</svg>
</button></li>`);
  logo.after(element);
  return element;
}

function removeOverflowButton() {
  const breadcrumbsOverflow = document.querySelector(
    ".jenkins-header__breadcrumbs__list-item .jenkins-button",
  )?.parentNode;
  breadcrumbsOverflow?.remove();
}

function breadcrumbsBarOverflows() {
  const breadcrumbsBar = document.querySelector("#breadcrumbBar");
  return breadcrumbsBar.scrollWidth > breadcrumbsBar.offsetWidth;
}

init();
