Behaviour.specify("INPUT.combobox2", "combobox", 100, function (e) {
  var items = [];

  new ComboBox(
    e,
    function (value) {
      var candidates = [];
      for (var i = 0; i < items.length; i++) {
        if (items[i].indexOf(value) == 0) {
          candidates.push(items[i]);
          if (candidates.length > 20) {
            break;
          }
        }
      }
      return candidates;
    },
    {},
  );

  refillOnChange(e, function (params) {
    fetch(e.getAttribute("fillUrl"), {
      headers: crumb.wrap({
        "Content-Type": "application/x-www-form-urlencoded",
      }),
      method: "post",
      body: new URLSearchParams(params),
    }).then((rsp) => {
      if (rsp.ok) {
        rsp.json().then((json) => {
          items = json;
        });
      }
    });
  });
});
