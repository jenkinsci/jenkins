Behaviour.specify("A.post", "link.post", 0, function (element) {
  element.onclick = function () {
    const url =
      element.getAttribute("data-post-href") || element.getAttribute("href");
    if (!url) {
      return false;
    }
    const form = document.createElement("form");
    form.setAttribute("method", "POST");
    form.setAttribute("action", url);
    crumb.appendToForm(form);
    document.body.appendChild(form);
    form.submit();
    return false;
  };
});
