function showhideCategories(hdr,on) {
  var table = hdr.parentNode.parentNode.parentNode,
      newDisplay = on ? '' : 'none',
      nameList = new Array(), name;
  for (var i = 1; i < table.rows.length; i++) {
    if (on || table.rows[i].cells.length == 1)
      table.rows[i].style.display = newDisplay;
     else {
      // Hide duplicate rows for a plugin when not viewing by-category
      name = table.rows[i].cells[1].getAttribute('data');
      if (nameList[name] == 1) table.rows[i].style.display = 'none';
      nameList[name] = 1;
    }
  }
}
function showhideCategory(col) {
  var row = col.parentNode.nextSibling;
  var newDisplay = row && row.style.display == 'none' ? '' : 'none';
  for (; row && row.cells.length > 1; row = row.nextSibling)
    row.style.display = newDisplay;
}

Behaviour.specify("#filter-box", '_table', 0, function(e) {
      function applyFilter() {
          var filter = e.value.toLowerCase();
          ["TR.plugin","TR.plugin-category"].each(function(clz) {
            var encountered = {};
            var items = document.getElementsBySelector(clz);
            for (var i=0; i<items.length; i++) {
                var visible = (filter=="" || items[i].innerHTML.toLowerCase().indexOf(filter)>=0);
                var name = items[i].getAttribute("name");
                if (visible && name != null) {
                    if (encountered[name]) {
                        visible = false;
                    }
                    encountered[name] = true;
                }
                items[i].style.display = (visible ? "" : "none");
            }
          });

          layoutUpdateCallback.call();
      }

      e.onkeyup = applyFilter;
});
