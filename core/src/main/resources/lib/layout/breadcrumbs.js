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

    function makeMenuHtml(icon,displayName) {
        return (icon!=null ? "<img src='"+icon+"' width=24 height=24 style='margin: 2px;' alt=''> " : "")+displayName;
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
     * '>' control used to launch context menu.
     */
    var menuSelector = (function() {
        var menuSelector = $(document.createElement("div"));
        var menuSelectorTarget;
        var parentToUpdate;

        document.body.appendChild(menuSelector);
        menuSelector.id = 'menuSelector';

        /**
         * @param target
         *      DOM node to attach this selector to.
         */
        menuSelector.show = function(target) {
            var xy = Dom.getXY(target);

            if ($(target).hasClassName("inside"))
                xy[0] -= this.offsetWidth;  // show the menu selector inside the text

            if ($(target).hasClassName("inverse")) {
                menuSelector.addClassName("inverse");
            } else {
                menuSelector.removeClassName("inverse");
            }

            xy[0] += target.offsetWidth;
            xy[1] += target.offsetHeight/2 - this.offsetHeight/2;
            Dom.setXY(this, xy);
            this.target = target;

            this.style.visibility = "visible";

            menuSelectorTarget = target;
            var updateParentSelector = menuSelectorTarget.getAttribute('update-parent-class');
            if (updateParentSelector) {
                parentToUpdate = $(menuSelectorTarget).up(updateParentSelector);
            }
        };
        menuSelector.hide = function() {
            this.style.visibility = "hidden";
            menuSelectorTarget = undefined;
            parentToUpdate = undefined;
        };
        menuSelector.observe("click",function () {
            invokeContextMenu(this.target);
        });

        // if the mouse leaves the selector, hide it
        canceller = new Delayed(function () {
            logger("hiding 'v'");
            menuSelector.hide();
        }.bind(menuSelector), 750);

        menuSelector.observe("mouseover",function () {
            if (menuSelectorTarget) {
                if (parentToUpdate) {
                    parentToUpdate.addClassName('model-link-active');
                }
                menuSelectorTarget.addClassName('mouseIsOverMenuSelector');
            }
            canceller.cancel();
        });
        menuSelector.observe("mouseout",function () {
            canceller.schedule();
            if (menuSelectorTarget) {
                if (parentToUpdate) {
                    parentToUpdate.removeClassName('model-link-active');
                }
                menuSelectorTarget.removeClassName('mouseIsOverMenuSelector');
            }
        });
        menuSelector.canceller = canceller;

        return menuSelector;
    })();

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
    function invokeContextMenu(e,contextMenuUrl) {
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
        } else {// fetch menu on demand
            xhr = new Ajax.Request(combinePath(e.getAttribute("href"),contextMenuUrl), {
                onComplete:function (x) {
                    var a = x.responseText.evalJSON().items;
                    function fillMenuItem(e) {
                        if (e.header) {
                            e.text = makeMenuHtml(e.icon, "<span class='header'>" + e.displayName + "</span>");
                        } else {
                            e.text = makeMenuHtml(e.icon, e.displayName);
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
                    a.each(fillMenuItem);

                    e.items = function() { return a };
                    showMenu(a);
                }
            });
        }

        return false;
    }

//    Behaviour.specify("#breadcrumbs LI", 'breadcrumbs', 0, function (e) {
//        // when the mouse hovers over LI, activate the menu
//        if (e.hasClassName("no-context-menu"))  return;
//        e.observe("mouseover", function () { handleHover(e.firstChild) });
//    });

    Behaviour.specify("A.model-link", 'breadcrumbs', 0, function (a) {
        // ditto for model-link, but give it a larger delay to avoid unintended menus to be displayed
        // $(a).observe("mouseover", function () { handleHover(a,500); });

        a.observe("mouseover",function () {
            logger("mouse entered model-link %s",this.href);
            menuSelector.canceller.cancel();
            menuSelector.show(this);
        });
        a.observe("mouseout",function () {
            logger("mouse left model-link %s",this.href);
            menuSelector.canceller.schedule();
        });
    });

    Behaviour.specify("#breadcrumbs LI.children", 'breadcrumbs', 0, function (a) {
        a.observe("mouseover",function() {
            menuSelector.hide();
        });
        a.observe("click",function() {
            invokeContextMenu(this,"childrenContextMenu");
        })
    });

    Behaviour.specify("#breadcrumbs A", 'breadcrumbs', 0, function (a) {
        $(a).addClassName('breadcrumbBarAnchor');
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
