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

document.addEventListener("DOMContentLoaded", function () {
  const messagesContainer = document.querySelector(".manage-messages");
  if (!messagesContainer) {
    return;
  }

  const updateLastVisibleMessageMargin = () => {
    const messageDivs = Array.from(messagesContainer.children).filter(
      (el) => el.tagName === "DIV",
    );

    messageDivs.forEach((el) => {
      el.style.marginBottom = "";
    });

    const visibleDivs = messageDivs.filter((el) => {
      const style = window.getComputedStyle(el);
      return (
        style.display !== "none" &&
        style.visibility !== "hidden" &&
        !el.hasAttribute("hidden") &&
        el.getClientRects().length > 0
      );
    });

    const lastVisible = visibleDivs[visibleDivs.length - 1];
    if (lastVisible) {
      lastVisible.style.marginBottom = "var(--section-padding)";
    }
  };

  let rafId = null;
  const scheduleUpdate = () => {
    if (rafId !== null) {
      return;
    }
    rafId = requestAnimationFrame(() => {
      rafId = null;
      updateLastVisibleMessageMargin();
    });
  };

  updateLastVisibleMessageMargin();

  const observer = new MutationObserver(scheduleUpdate);
  observer.observe(messagesContainer, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ["style", "class", "hidden", "aria-hidden"],
  });
});
