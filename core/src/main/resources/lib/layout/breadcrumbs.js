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

    function postRequest(action, event, url) {
        new Ajax.Request(url);
        if (event.length == 1 && event[0].target != null) {
            hoverNotification('Done.', event[0].target);
        }
    }

    function requireConfirmation(action, event, cfg) {
        if (confirm(cfg.displayName + ': are you sure?')) { // XXX I18N
            var form = document.createElement('form');
            form.setAttribute('method', cfg.post ? 'POST' : 'GET');
            form.setAttribute('action', cfg.url);
            document.body.appendChild(form);
            form.submit();
        }
    }

    /**
     * Called when the mouse cursor comes into the context menu hot spot.
     *
     * If the mouse stays there for a while, a context menu gets displayed.
     *
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
                    $(menu.getItem(0).element).addClassName("yui-menuitem-tooltip")
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
                    function fillMenuItem(e) {
                        e.text = makeMenuHtml(e.icon, e.displayName);
                        if (e.subMenu!=null)
                            e.subMenu = {id:"submenu"+(iota++), itemdata:e.subMenu.items.each(fillMenuItem)};
                        if (e.requiresConfirmation) {
                            e.onclick = {fn: requireConfirmation, obj: {url: e.url, displayName: e.displayName, post: e.post}};
                            delete e.url;
                        } else if (e.post) {
                            e.onclick = {fn: postRequest, obj: e.url};
                            delete e.url;
                        }
                    }
                    a.each(fillMenuItem);

                    var tooltip = e.getAttribute('tooltip');
                    if (tooltip) {
                        // join the tooltip into the context menu. color #000 to cancel out the text effect on disabled menu items
                        a.unshift({text:"<div class='yui-menu-tooltip'>"+tooltip+"</div>", disabled:true})
                    }

                    e.items = function() { return a };
                    showMenu(a);
                }
            });
        }

        return false;
    }

    Behaviour.specify("#breadcrumbs LI", 'breadcrumbs', 0, function (e) {
        // when the mouse hovers over LI, activate the menu
        e = $(e);
        if (e.hasClassName("no-context-menu"))  return;
        e.observe("mouseover", function () { handleHover(e.firstChild,0) });
    });

    Behaviour.specify("A.model-link", 'breadcrumbs', 0, function (a) {
        // ditto for model-link, but give it a larger delay to avoid unintended menus to be displayed
        $(a).observe("mouseover", function () { handleHover(a,500); });
    });

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
