document
  .getElementById("button-started-at")
  .addEventListener("click", function () {
    const flipper = document.querySelector(".app-build__started-at");
    const front = document.querySelector(".app-build__started-at__front");
    const back = document.querySelector(".app-build__started-at__back");

    if (flipper.classList.contains("app-build__started-at--flipped")) {
      // Currently showing back, flip to front
      flipper.classList.remove("app-build__started-at--flipped");
      front.setAttribute("aria-hidden", "false");
      back.setAttribute("aria-hidden", "true");
    } else {
      // Currently showing front, flip to back
      flipper.classList.add("app-build__started-at--flipped");
      front.setAttribute("aria-hidden", "true");
      back.setAttribute("aria-hidden", "false");
    }
  });
