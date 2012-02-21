var breadcrumb = (function() {
    /** @type {YAHOO.widget.Menu} */
    var menu;

    /**
     * Used for fetching the content of the menu asynchronously from the server
     */
    var xhr;

    /**
     * Activates the context menu for the specified breadcrumb element.
     *
     * @param {HTMLElement} e
     *      The LI tag that the mouse has wondered into.
     */
    function activate(e) {
      function showMenu(items) {
        menu.hide();
        menu.cfg.setProperty("context",[e,"tl","bl"]);
        menu.clearContent();
        menu.addItems(items);
        menu.render("breadcrumb-menu-target");
        menu.show();
      }

      if (xhr)
        xhr.options.onComplete = function() {};   // ignore the currently pending call

      if (e.items) {// use what's already loaded
        showMenu(e.items);
      } else {// fetch menu on demand
        xhr = new Ajax.Request(e.firstChild.getAttribute("href")+"contextMenu", {
          onComplete : function (x) {
            var a = x.responseText.evalJSON().items;
            a.each(function(e) {
              e.text = "<img src='"+e.icon+"' width=24 height=24 style='margin: 2px;' alt=''> "+e.displayName;
            });
            e.items = a;
            showMenu(a);
          }
        });
      }

      return false;
    }

    window.addEventListener("load",function(){
      menu = new YAHOO.widget.Menu("breadcrumb-menu", {position:"dynamic", hidedelay:1000});
    });

    return { activate : activate };
})();
