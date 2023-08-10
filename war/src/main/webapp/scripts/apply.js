window.applyCompletionHandler = function (w) {
  let scriptTagData = document.getElementById("form-apply-callback").dataset;

  w.notificationBar.show(
    scriptTagData.message,
    w.notificationBar[scriptTagData.notificationType],
  );
};
