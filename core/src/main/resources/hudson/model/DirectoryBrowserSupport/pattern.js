document.addEventListener("DOMContentLoaded", function () {
  document
    .getElementById("pattern-submit")
    .addEventListener("click", function (ev) {
      ev.preventDefault();

      let input = document.getElementById("pattern");
      let pattern = input.value;
      let back = input.dataset.backpath;

      let baseurl = back;
      if (!baseurl.endsWith("/")) {
        baseurl = baseurl + "/";
      }
      baseurl = baseurl + pattern;
      document.location.href = baseurl;
    });
});
