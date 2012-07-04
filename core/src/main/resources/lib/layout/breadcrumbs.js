var breadcrumbs = (function() {
    var Dom = YAHOO.util.Dom;

    /**
     * This component actually renders the menu.
     *
     * @type {YAHOO.widget.Menu}
     */
    var menu;

    /**
     * Used for fetching the content of the menu asynchronously from the server
     */
    var xhr;

    /**
     * When mouse hovers over the anchor that has context menu, we capture its region here.
     * This is used to to avoid showing menu when the mouse slides over to other elements after a delay.
     *
     * @type {YAHOO.util.Region}
     */
    var hitTest;

    /**
     * Current mouse cursor position in the page coordinate.
     *
     * @type {YAHOO.util.Point}
     */
    var mouse;

    /**
     * Timer ID for lazy menu display.
     */
    var menuDelay;

    function makeMenuHtml(icon,displayName) {
        return (icon!=null ? "<img src='"+icon+"' width=24 height=24 style='margin: 2px;' alt=''> " : "")+displayName;
    }

    Event.observe(window,"load",function(){
      menu = new YAHOO.widget.Menu("breadcrumb-menu", {position:"dynamic", hidedelay:1000});
    });


    Event.observe(document,"mousemove",function (ev){
        mouse = new YAHOO.util.Point(ev.pageX,ev.pageY);
    });

    function cancelMenu() {
        if (menuDelay) {
            window.clearTimeout(menuDelay);
            menuDelay = null;
        }
    }

    function combinePath(a,b) {
        var qs;
        var i = a.indexOf('?');
        if (i>=0)  { qs=a.substring(i); a=a.substring(0,i); }
        else        qs="";

        i=a.indexOf('#');
        if (i>=0)   a=a.substring(0,i);

        if (a.endsWith('/'))    return a+b+qs;
        return a+'/'+b+qs;
    }

    /**
     * @param {HTMLElement} e
     *      anchor tag
     * @param {Number} delay
     *      Number of milliseconds to wait before the menu is displayed.
     *      The mouse needs to be on the same anchor tag after this delay.
     */
    function handleHover(e,delay) {
        function showMenu(items) {
            cancelMenu();
            hitTest = Dom.getRegion(e);
            menuDelay = window.setTimeout(function() {
                if (hitTest.contains(mouse)) {
                    menu.hide();
                    var pos = [e, "tl", "bl"];
                    if ($(e).hasClassName("tl-tr"))  pos = [e,"tl","tr"]
                    menu.cfg.setProperty("context", pos);
                    menu.clearContent();
                    menu.addItems(items);
                    menu.render("breadcrumb-menu-target");
                    menu.show();
                }
                menuDelay = null;
            },delay);
        }

        if (xhr)
            xhr.options.onComplete = function () {
            };   // ignore the currently pending call

        if (e.items) {// use what's already loaded
            showMenu(e.items());
        } else {// fetch menu on demand
            xhr = new Ajax.Request(combinePath(e.getAttribute("href"),"contextMenu"), {
                onComplete:function (x) {
                    var a = x.responseText.evalJSON().items;
                    a.each(function (e) {
                        e.text = makeMenuHtml(e.icon, e.displayName);
                    });
                    e.items = function() { return a };
                    showMenu(a);
                }
            });
        }

        return false;
    }

    jenkinsRules["#breadcrumbs LI"] = function (e) {
        // when the mouse hovers over LI, activate the menu
        e = $(e);
        if (e.hasClassName("no-context-menu"))  return;
        e.observe("mouseover", function () { handleHover(e.firstChild,0) });
    };

    jenkinsRules["A.model-link"] = function (a) {
        // ditto for model-link, but give it a larger delay to avoid unintended menus to be displayed
        $(a).observe("mouseover", function () { handleHover(a,500); });
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
         * @param {Function|breadcrumbs.ContextMenu} menu
         *      Pass in the configured menu object. If a function is given, this function
         *      is called each time a menu needs to be displayed. This is convenient for dynamically
         *      populating the content.
         */
        "attachMenu" : function (li,menu) {
            $(li).firstChild.items =  (typeof menu=="function") ? menu : function() { return menu.items };
        },

        "ContextMenu" : ContextMenu
    };
})();
