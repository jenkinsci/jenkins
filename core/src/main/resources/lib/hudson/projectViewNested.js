hudsonRules["IMG.treeview-fold-control"] = function(e) {
  e.onexpanded = function() {
    var img = this;
    var tr = findAncestor(this, "TR");
    var tail = tr.nextSibling;

    img.oncollapsed = function() {
      while(tr.nextSibling!=tail)
        tr.nextSibling.remove();
    };

    new Ajax.Request(
            this.getAttribute("url"),
    {
      method : 'post',
      onComplete : function(x) {
        var cont = document.createElement("div");
        cont.innerHTML = x.responseText;
        var rows = $A(cont.firstChild.rows);
        rows.reverse().each(function(r) {
          YAHOO.util.Dom.insertAfter(r, tr);
          Behaviour.applySubtree(r);
        });
      }
    });
  };
  e = null;
};
