function init() {
  window.addEventListener("scroll", () => {
    const navigation = document.querySelector("#page-header");
    navigation.style.setProperty("--background-opacity", Math.min(70, window.scrollY) + "%");
    navigation.style.setProperty("--background-blur", Math.min(40, window.scrollY) + "px");
    if (!document.querySelector(".jenkins-search--app-bar")) {
      navigation.style.setProperty("--border-opacity", Math.min(10, window.scrollY) + "%");
    }
  });
}

export default { init };
