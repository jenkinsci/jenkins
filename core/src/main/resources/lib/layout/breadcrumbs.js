var breadcrumbs = (function() {
    /** @type {YAHOO.widget.Menu} */
    var menu;

    /**
     * Used for fetching the content of the menu asynchronously from the server
     */
    var xhr;

    function makeMenuHtml(icon,displayName) {
        return "<img src='"+icon+"' width=24 height=24 style='margin: 2px;' alt=''> "+displayName;
    }

    window.addEventListener("load",function(){
      menu = new YAHOO.widget.Menu("breadcrumb-menu", {position:"dynamic", hidedelay:1000});
    });

    jenkinsRules["#breadcrumbs LI"] = function (e) {
        // when the moust hovers over LI, activate the menu
        e.addEventListener("mouseover", function () {
            function showMenu(items) {
                menu.hide();
                menu.cfg.setProperty("context", [e, "tl", "bl"]);
                menu.clearContent();
                menu.addItems(items);
                menu.render("breadcrumb-menu-target");
                menu.show();
            }

            if (xhr)
                xhr.options.onComplete = function () {
                };   // ignore the currently pending call

            if (e.items) {// use what's already loaded
                showMenu(e.items);
            } else {// fetch menu on demand
                xhr = new Ajax.Request(e.firstChild.getAttribute("href") + "contextMenu", {
                    onComplete:function (x) {
                        var a = x.responseText.evalJSON().items;
                        a.each(function (e) {
                            e.text = makeMenuHtml(e.icon, e.displayName);
                        });
                        e.items = a;
                        showMenu(a);
                    }
                });
            }

            return false;
        });
    };

    /**
     * @namespace breadcrumbs
     * @class ContextMenu
     * @constructor
     */
    var ContextMenu = function () {
        this.items = [];
    };
    ContextMenu.prototype = {
        /**
         * Creates a menu item.
         *
         * @return {breadcrumbs.MenuItem}
         */
        "add" : function (url,icon,displayName) {
            this.items.push({ url:url, text:makeMenuHtml(icon,displayName) });
            return this;
        }
    };

    return {
        /**
         * Activates the context menu for the specified breadcrumb element.
         *
         * @param {String|HTMLElement} li
         *      The LI tag to which you associate the menu (or its ID)
         * @param {breadcrumbs.ContextMenu}
         */
        "attachMenu" : function (li,menu) {
            $(li).items = menu.items;
        },

        "ContextMenu" : ContextMenu
    };
})();
