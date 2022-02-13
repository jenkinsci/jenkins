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
     * Current mouse cursor position in the page coordinate.
     *
     * @type {YAHOO.util.Point}
     */
    var mouse;

    var logger = function() {};
    // logger = function() { console.log.apply(console,arguments) };  // uncomment this line to enable logging

    function makeMenuHtml(icon, iconXml, displayName) {
        var displaynameSpan = '<span>' + displayName + '</span>';

        if (iconXml != null) {
          return iconXml + displaynameSpan;
        }

        if (icon === null) return "<span style='margin: 2px 4px 2px 2px;' />" + displaynameSpan;

        // TODO: move this to the API response in a clean way
        var isSvgSprite = icon.toLowerCase().indexOf('svg#') !== -1;
        return isSvgSprite
            ? "<svg class='svg-icon' width='24' height='24' style='margin: 2px 4px 2px 2px;' aria-label='' focusable='false'>" +
                "<use href='" + icon + "' />" +
                "</svg>" + displaynameSpan
            : "<img src='"+icon+"' width=24 height=24 style='margin: 2px 4px 2px 2px;' alt=''>" + displaynameSpan;
    }

    Event.observe(window,"load",function(){
      menu = new YAHOO.widget.Menu("breadcrumb-menu", {position:"dynamic", hidedelay:1000, zIndex:2001, scrollincrement: 2});
    });


    Event.observe(document,"mousemove",function (ev){
        mouse = new YAHOO.util.Point(ev.pageX,ev.pageY);
    });

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
        if (confirm(cfg.displayName + ': are you sure?')) { // TODO I18N
            var form = document.createElement('form');
            form.setAttribute('method', cfg.post ? 'POST' : 'GET');
            form.setAttribute('action', cfg.url);
            if (cfg.post) {
                crumb.appendToForm(form);
            }
            document.body.appendChild(form);
            form.submit();
        }
    }

    /**
     * Wraps a delayed action and its cancellation.
     */
    function Delayed(action, timeout) {
        this.schedule = function () {
            this.cancel();
            this.token = window.setTimeout(function () {
                this.token = null;
                action();
            }.bind(this), timeout);
            logger("Scheduled %s",this.token)
        };
        this.cancel = function () {
            if (this.token != null) {
                logger("Cancelling %s",this.token);
                window.clearTimeout(this.token);
            }
            this.token = null;
        };
    }

    /**
     * Called when the user clicks a mouse to show a context menu.
     *
     * If the mouse stays there for a while, a context menu gets displayed.
     *
     * @param {HTMLElement} e
     *      anchor tag
     * @param {String} contextMenuUrl
     *      The URL that renders JSON for context menu. Optional.
     */
    function invokeContextMenu(e, contextMenuUrl) {
      contextMenuUrl = contextMenuUrl || "contextMenu";

        function showMenu(items) {
            menu.hide();
            var pos = [e, "tl", "bl"];
            if ($(e).hasClassName("tl-tr"))  pos = [e,"tl","tr"];
            menu.cfg.setProperty("context", pos);
            menu.clearContent();
            menu.addItems(items);
            menu.render("breadcrumb-menu-target");
            menu.show();
        }

        if (xhr)
            xhr.options.onComplete = function () {
            };   // ignore the currently pending call

        if (e.items) {// use what's already loaded
            showMenu(e.items());
        } else {
          // fetch menu on demand
            xhr = new Ajax.Request(combinePath(e.getAttribute("href"), contextMenuUrl), {
                onComplete:function (x) {
                  var items = x.responseText.evalJSON().items;
                    function fillMenuItem(e) {
                        if (e.type === "HEADER") {
                            e.text = makeMenuHtml(e.icon, e.iconXml, "<span class='header'>" + e.displayName + "</span>");
                            e.disabled = true;
                        } else if (e.type === "SEPARATOR") {
                            e.text = "<span class='separator'>--</span>";
                            e.disabled = true;
                        } else {
                          e.text = makeMenuHtml(e.icon, e.iconXml, e.displayName);
                        }
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
                    items.each(fillMenuItem);
                    e.items = function() { return items };
                    showMenu(items);
                }
            });
        }

        return false;
    }

    Behaviour.specify("A.model-link", 'breadcrumbs', 0, function (link) {
        const isFirefox = (navigator.userAgent.indexOf("Firefox") !== -1)
        // Firefox adds unwanted lines when copying buttons in text, so use a span instead
        const dropdownChevron = document.createElement(isFirefox ? "span" : "button")
        dropdownChevron.className = "jenkins-menu-dropdown-chevron"
        dropdownChevron.addEventListener("click", function(e) {
            e.preventDefault();
            invokeContextMenu(link);
        })
        link.appendChild(dropdownChevron)
    });

    Behaviour.specify("#breadcrumbs LI.children", 'breadcrumbs', 0, function (a) {
        a.observe("click", function() {
            invokeContextMenu(this, "childrenContextMenu");
        })
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
            this.items.push({ url:url, text:makeMenuHtml(icon, null, displayName) });
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
            $(li).items = (typeof menu=="function") ? menu : function() { return menu.items };
            $(li).addEventListener("click", function() {
              invokeContextMenu($(li));
            })
        },

        "ContextMenu" : ContextMenu
    };
})();
