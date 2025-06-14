import { toId } from "@/util/dom";

/*
 * Generates a jump list for the active breadcrumb to jump to
 * sections on the page (if using <f:breadcrumb-config-outline />)
 */
function init() {
  const inpageNavigationBreadcrumb = document.querySelector("#inpage-nav span");

  if (inpageNavigationBreadcrumb) {
    inpageNavigationBreadcrumb.items = Array.from(
      document.querySelectorAll(
        "form > div > .jenkins-section > .jenkins-section__title",
      ),
    ).map((section) => {
      section.id = toId(section.textContent);
      return { label: section.textContent, url: "#" + section.id };
    });
  }
}

export default { init };
