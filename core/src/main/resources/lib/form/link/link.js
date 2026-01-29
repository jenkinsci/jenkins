Behaviour.specify("A.post", "link.post", 0, function (element) {
  const pendingHref = element.getAttribute("data-post-href");
  if (pendingHref) {
    element.setAttribute("href", pendingHref);
    element.removeAttribute("data-post-href");
  }

  // Use addEventListener instead of onclick to prevent race conditions
  // Check if we already attached a listener (avoid duplicates)
  if (element._postLinkHandlerAttached) {
    return;
  }

  // Remove any existing onclick to avoid conflicts
  if (element.onclick) {
    element.onclick = null;
  }

  // Create handler function
  var postHandler = function (ev) {
    if (ev) {
      ev.preventDefault();
      ev.stopPropagation();
    }
    var form = document.createElement("form");
    form.setAttribute("method", "POST");
    form.setAttribute("action", element.getAttribute("href"));
    crumb.appendToForm(form);
    document.body.appendChild(form);
    form.submit();
    return false;
  };

  element.addEventListener("click", postHandler, true); // Use capture phase to ensure we handle it early
  element._postLinkHandlerAttached = true; // Mark as attached
});
