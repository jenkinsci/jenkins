Behaviour.specify(
  "#URICheckEncodingMonitor-message",
  "URICheckEncodingMonitor",
  0,
  function (element) {
    var url = element.getAttribute("data-url");
    var params = new URLSearchParams({ value: "\u57f7\u4e8b" });
    fetch(url + "?" + params).then((rsp) => {
      rsp.text().then((responseText) => {
        var message = document.getElementById(
          "URICheckEncodingMonitor-message",
        );
        message.innerHTML = responseText;
      });
    });
  },
);
