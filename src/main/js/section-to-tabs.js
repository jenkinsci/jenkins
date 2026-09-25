// Converts a page's section headings into clickable tabs, see 'About Jenkins' page for example
const tabPanes = document.querySelectorAll(".jenkins-tab-pane");

// Hide tab panes
tabPanes.forEach((tabPane) => {
  tabPane.style.display = "none";
});

// Show the first
tabPanes[0].style.display = "block";

const tabBar = document.createElement("div");
tabBar.className = "tabBar";

// Add tabs for each tab pane
tabPanes.forEach((tabPane, index) => {
  const tabPaneTitle = tabPane.querySelector(".jenkins-tab-pane__title");
  tabPaneTitle.style.display = "none";

  const tab = document.createElement("div");
  tab.className = "tab";

  if (index === 0) {
    tab.classList.add("active");
  }

  tab.addEventListener("click", function (e) {
    e.preventDefault();
    document.querySelectorAll(".tab").forEach((tab) => {
      tab.classList.remove("active");
    });
    tab.classList.add("active");

    tabPanes.forEach((tabPane) => {
      tabPane.style.display = "none";
    });
    tabPanes[index].style.display = "block";
  });

  const tabLink = document.createElement("a");
  tabLink.setAttribute("href", "#");
  tabLink.innerText = tabPaneTitle.textContent;

  tab.append(tabLink);

  tabBar.append(tab);
});

tabPanes[0].parentElement.insertBefore(tabBar, tabPanes[0]);
