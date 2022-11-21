(function () {
  const selfScript = document.querySelector("#screenResolution-script");
  const secureCookie = selfScript.getAttribute("data-use-secure-cookie");
  let cookie =
    "screenResolution=" +
    screen.width +
    "x" +
    screen.height +
    "; path=/" +
    "; SameSite=Lax";
  if (secureCookie) {
    cookie += "; secure";
  }
  document.cookie = cookie;
})();
