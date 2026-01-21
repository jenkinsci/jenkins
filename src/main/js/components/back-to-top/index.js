function init() {
  const backToTopButton = document.querySelector(".jenkins-back-to-top");

  if (!backToTopButton) {
    return;
  }

  const threshold = 300;
  let ticking = false;

  const updateVisibility = () => {
    if (window.scrollY > threshold) {
      backToTopButton.classList.add("jenkins-back-to-top--visible");
    } else {
      backToTopButton.classList.remove("jenkins-back-to-top--visible");
    }
    ticking = false;
  };

  window.addEventListener(
    "scroll",
    () => {
      if (!ticking) {
        window.requestAnimationFrame(updateVisibility);
        ticking = true;
      }
    },
    { passive: true },
  );

  backToTopButton.addEventListener("click", () => {
    window.scrollTo({
      top: 0,
      behavior: "smooth",
    });
  });
}

export default { init };
