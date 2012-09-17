/*
Copyright (c) 2011, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.com/yui/license.html
version: 2.9.0
*/


/**
* @module menu
* @description <p>The Menu family of components features a collection of 
* controls that make it easy to add menus to your website or web application.  
* With the Menu Controls you can create website fly-out menus, customized 
* context menus, or application-style menu bars with just a small amount of 
* scripting.</p><p>The Menu family of controls features:</p>
* <ul>
*    <li>Keyboard and mouse navigation.</li>
*    <li>A rich event model that provides access to all of a menu's 
*    interesting moments.</li>
*    <li>Support for 
*    <a href="http://en.wikipedia.org/wiki/Progressive_Enhancement">Progressive
*    Enhancement</a>; Menus can be created from simple, 
*    semantic markup on the page or purely through JavaScript.</li>
* </ul>
* @title Menu
* @namespace YAHOO.widget
* @requires Event, Dom, Container
*/
(function () {

    var UA = YAHOO.env.ua,
        Dom = YAHOO.util.Dom,
        Event = YAHOO.util.Event,
        Lang = YAHOO.lang,

        _DIV = "DIV",
        _HD = "hd",
        _BD = "bd",
        _FT = "ft",
        _LI = "LI",
        _DISABLED = "disabled",
        _MOUSEOVER = "mouseover",
        _MOUSEOUT = "mouseout",
        _MOUSEDOWN = "mousedown",
        _MOUSEUP = "mouseup",
        _CLICK = "click",
        _KEYDOWN = "keydown",
        _KEYUP = "keyup",
        _KEYPRESS = "keypress",
        _CLICK_TO_HIDE = "clicktohide",
        _POSITION = "position", 
        _DYNAMIC = "dynamic",
        _SHOW_DELAY = "showdelay",
        _SELECTED = "selected",
        _VISIBLE = "visible",
        _UL = "UL",
        _MENUMANAGER = "MenuManager";


    /**
    * Singleton that manages a collection of all menus and menu items.  Listens 
    * for DOM events at the document level and dispatches the events to the 
    * corresponding menu or menu item.
    *
    * @namespace YAHOO.widget
    * @class MenuManager
    * @static
    */
    YAHOO.widget.MenuManager = function () {
    
        // Private member variables
    
    
        // Flag indicating if the DOM event handlers have been attached
    
        var m_bInitializedEventHandlers = false,
    
    
        // Collection of menus

        m_oMenus = {},


        // Collection of visible menus
    
        m_oVisibleMenus = {},
    
    
        //  Collection of menu items 

        m_oItems = {},


        // Map of DOM event types to their equivalent CustomEvent types
        
        m_oEventTypes = {
            "click": "clickEvent",
            "mousedown": "mouseDownEvent",
            "mouseup": "mouseUpEvent",
            "mouseover": "mouseOverEvent",
            "mouseout": "mouseOutEvent",
            "keydown": "keyDownEvent",
            "keyup": "keyUpEvent",
            "keypress": "keyPressEvent",
            "focus": "focusEvent",
            "focusin": "focusEvent",
            "blur": "blurEvent",
            "focusout": "blurEvent"
        },
    
    
        m_oFocusedMenuItem = null;
    
    
    
        // Private methods
    
    
        /**
        * @method getMenuRootElement
        * @description Finds the root DIV node of a menu or the root LI node of 
        * a menu item.
        * @private
        * @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
        * level-one-html.html#ID-58190037">HTMLElement</a>} p_oElement Object 
        * specifying an HTML element.
        */
        function getMenuRootElement(p_oElement) {
        
            var oParentNode,
                returnVal;
    
            if (p_oElement && p_oElement.tagName) {
            
                switch (p_oElement.tagName.toUpperCase()) {
                        
                case _DIV:
    
                    oParentNode = p_oElement.parentNode;
    
                    // Check if the DIV is the inner "body" node of a menu

                    if ((
                            Dom.hasClass(p_oElement, _HD) ||
                            Dom.hasClass(p_oElement, _BD) ||
                            Dom.hasClass(p_oElement, _FT)
                        ) && 
                        oParentNode && 
                        oParentNode.tagName && 
                        oParentNode.tagName.toUpperCase() == _DIV) {
                    
                        returnVal = oParentNode;
                    
                    }
                    else {
                    
                        returnVal = p_oElement;
                    
                    }
                
                    break;

                case _LI:
    
                    returnVal = p_oElement;
                    
                    break;

                default:
    
                    oParentNode = p_oElement.parentNode;
    
                    if (oParentNode) {
                    
                        returnVal = getMenuRootElement(oParentNode);
                    
                    }
                
                    break;
                
                }
    
            }
            
            return returnVal;
            
        }
    
    
    
        // Private event handlers
    
    
        /**
        * @method onDOMEvent
        * @description Generic, global event handler for all of a menu's 
        * DOM-based events.  This listens for events against the document 
        * object.  If the target of a given event is a member of a menu or 
        * menu item's DOM, the instance's corresponding Custom Event is fired.
        * @private
        * @param {Event} p_oEvent Object representing the DOM event object  
        * passed back by the event utility (YAHOO.util.Event).
        */
        function onDOMEvent(p_oEvent) {
    
            // Get the target node of the DOM event
        
            var oTarget = Event.getTarget(p_oEvent),
                
            // See if the target of the event was a menu, or a menu item
    
            oElement = getMenuRootElement(oTarget),
            bFireEvent = true,
            sEventType = p_oEvent.type,
            sCustomEventType,
            sTagName,
            sId,
            oMenuItem,
            oMenu; 
    
    
            if (oElement) {
    
                sTagName = oElement.tagName.toUpperCase();
        
                if (sTagName == _LI) {
            
                    sId = oElement.id;
            
                    if (sId && m_oItems[sId]) {
            
                        oMenuItem = m_oItems[sId];
                        oMenu = oMenuItem.parent;
            
                    }
                
                }
                else if (sTagName == _DIV) {
                
                    if (oElement.id) {
                    
                        oMenu = m_oMenus[oElement.id];
                    
                    }
                
                }
    
            }
    
    
            if (oMenu) {
    
                sCustomEventType = m_oEventTypes[sEventType];

                /*
                    There is an inconsistency between Firefox for Mac OS X and 
                    Firefox Windows & Linux regarding the triggering of the 
                    display of the browser's context menu and the subsequent 
                    firing of the "click" event. In Firefox for Windows & Linux, 
                    when the user triggers the display of the browser's context 
                    menu the "click" event also fires for the document object, 
                    even though the "click" event did not fire for the element 
                    that was the original target of the "contextmenu" event. 
                    This is unique to Firefox on Windows & Linux.  For all 
                    other A-Grade browsers, including Firefox for Mac OS X, the 
                    "click" event doesn't fire for the document object. 

                    This bug in Firefox for Windows affects Menu, as Menu 
                    instances listen for events at the document level and 
                    dispatches Custom Events of the same name.  Therefore users
                    of Menu will get an unwanted firing of the "click" 
                    custom event.  The following line fixes this bug.
                */
                


                if (sEventType == "click" && 
                    (UA.gecko && oMenu.platform != "mac") && 
                    p_oEvent.button > 0) {

                    bFireEvent = false;

                }
    
                // Fire the Custom Event that corresponds the current DOM event    
        
                if (bFireEvent && oMenuItem && !oMenuItem.cfg.getProperty(_DISABLED)) {
                    oMenuItem[sCustomEventType].fire(p_oEvent);                   
                }
        
                if (bFireEvent) {
                    oMenu[sCustomEventType].fire(p_oEvent, oMenuItem);
                }
            
            }
            else if (sEventType == _MOUSEDOWN) {
    
                /*
                    If the target of the event wasn't a menu, hide all 
                    dynamically positioned menus
                */
                
                for (var i in m_oVisibleMenus) {
        
                    if (Lang.hasOwnProperty(m_oVisibleMenus, i)) {
        
                        oMenu = m_oVisibleMenus[i];

                        if (oMenu.cfg.getProperty(_CLICK_TO_HIDE) && 
                            !(oMenu instanceof YAHOO.widget.MenuBar) && 
                            oMenu.cfg.getProperty(_POSITION) == _DYNAMIC) {

                            oMenu.hide();

                            //	In IE when the user mouses down on a focusable 
                            //	element that element will be focused and become 
                            //	the "activeElement".
                            //	(http://msdn.microsoft.com/en-us/library/ms533065(VS.85).aspx)
                            //	However, there is a bug in IE where if there is 
                            //	a positioned element with a focused descendant 
                            //	that is hidden in response to the mousedown 
                            //	event, the target of the mousedown event will 
                            //	appear to have focus, but will not be set as 
                            //	the activeElement.  This will result in the 
                            //	element not firing key events, even though it
                            //	appears to have focus.  The following call to 
                            //	"setActive" fixes this bug.

                            if (UA.ie && oTarget.focus && (UA.ie < 9)) {
                                oTarget.setActive();
                            }
        
                        }
                        else {
                            
                            if (oMenu.cfg.getProperty(_SHOW_DELAY) > 0) {
                            
                                oMenu._cancelShowDelay();
                            
                            }


                            if (oMenu.activeItem) {
                        
                                oMenu.activeItem.blur();
                                oMenu.activeItem.cfg.setProperty(_SELECTED, false);
                        
                                oMenu.activeItem = null;            
                        
                            }
        
                        }
        
                    }
        
                } 
    
            }
            
        }
    
    
        /**
        * @method onMenuDestroy
        * @description "destroy" event handler for a menu.
        * @private
        * @param {String} p_sType String representing the name of the event 
        * that was fired.
        * @param {Array} p_aArgs Array of arguments sent when the event 
        * was fired.
        * @param {YAHOO.widget.Menu} p_oMenu The menu that fired the event.
        */
        function onMenuDestroy(p_sType, p_aArgs, p_oMenu) {
    
            if (m_oMenus[p_oMenu.id]) {
    
                this.removeMenu(p_oMenu);
    
            }
    
        }
    
    
        /**
        * @method onMenuFocus
        * @description "focus" event handler for a MenuItem instance.
        * @private
        * @param {String} p_sType String representing the name of the event 
        * that was fired.
        * @param {Array} p_aArgs Array of arguments sent when the event 
        * was fired.
        */
        function onMenuFocus(p_sType, p_aArgs) {
    
            var oItem = p_aArgs[1];
    
            if (oItem) {
    
                m_oFocusedMenuItem = oItem;
            
            }
    
        }
    
    
        /**
        * @method onMenuBlur
        * @description "blur" event handler for a MenuItem instance.
        * @private
        * @param {String} p_sType String representing the name of the event  
        * that was fired.
        * @param {Array} p_aArgs Array of arguments sent when the event 
        * was fired.
        */
        function onMenuBlur(p_sType, p_aArgs) {
    
            m_oFocusedMenuItem = null;
    
        }

    
        /**
        * @method onMenuVisibleConfigChange
        * @description Event handler for when the "visible" configuration  
        * property of a Menu instance changes.
        * @private
        * @param {String} p_sType String representing the name of the event  
        * that was fired.
        * @param {Array} p_aArgs Array of arguments sent when the event 
        * was fired.
        */
        function onMenuVisibleConfigChange(p_sType, p_aArgs) {
    
            var bVisible = p_aArgs[0],
                sId = this.id;
            
            if (bVisible) {
    
                m_oVisibleMenus[sId] = this;
                
                YAHOO.log(this + " added to the collection of visible menus.", 
                    "info", _MENUMANAGER);
            
            }
            else if (m_oVisibleMenus[sId]) {
            
                delete m_oVisibleMenus[sId];
                
                YAHOO.log(this + " removed from the collection of visible menus.", 
                    "info", _MENUMANAGER);
            
            }
        
        }
    
    
        /**
        * @method onItemDestroy
        * @description "destroy" event handler for a MenuItem instance.
        * @private
        * @param {String} p_sType String representing the name of the event  
        * that was fired.
        * @param {Array} p_aArgs Array of arguments sent when the event 
        * was fired.
        */
        function onItemDestroy(p_sType, p_aArgs) {
    
            removeItem(this);
    
        }


        /**
        * @method removeItem
        * @description Removes a MenuItem instance from the MenuManager's collection of MenuItems.
        * @private
        * @param {MenuItem} p_oMenuItem The MenuItem instance to be removed.
        */    
        function removeItem(p_oMenuItem) {

            var sId = p_oMenuItem.id;
    
            if (sId && m_oItems[sId]) {
    
                if (m_oFocusedMenuItem == p_oMenuItem) {
    
                    m_oFocusedMenuItem = null;
    
                }
    
                delete m_oItems[sId];
                
                p_oMenuItem.destroyEvent.unsubscribe(onItemDestroy);
    
                YAHOO.log(p_oMenuItem + " successfully unregistered.", "info", _MENUMANAGER);
    
            }

        }
    
    
        /**
        * @method onItemAdded
        * @description "itemadded" event handler for a Menu instance.
        * @private
        * @param {String} p_sType String representing the name of the event  
        * that was fired.
        * @param {Array} p_aArgs Array of arguments sent when the event 
        * was fired.
        */
        function onItemAdded(p_sType, p_aArgs) {
    
            var oItem = p_aArgs[0],
                sId;
    
            if (oItem instanceof YAHOO.widget.MenuItem) { 
    
                sId = oItem.id;
        
                if (!m_oItems[sId]) {
            
                    m_oItems[sId] = oItem;
        
                    oItem.destroyEvent.subscribe(onItemDestroy);
        
                    YAHOO.log(oItem + " successfully registered.", "info", _MENUMANAGER);
        
                }
    
            }
        
        }
    
    
        return {
    
            // Privileged methods
    
    
            /**
            * @method addMenu
            * @description Adds a menu to the collection of known menus.
            * @param {YAHOO.widget.Menu} p_oMenu Object specifying the Menu  
            * instance to be added.
            */
            addMenu: function (p_oMenu) {
    
                var oDoc;
    
                if (p_oMenu instanceof YAHOO.widget.Menu && p_oMenu.id && 
                    !m_oMenus[p_oMenu.id]) {
        
                    m_oMenus[p_oMenu.id] = p_oMenu;
                
            
                    if (!m_bInitializedEventHandlers) {
            
                        oDoc = document;
                
                        Event.on(oDoc, _MOUSEOVER, onDOMEvent, this, true);
                        Event.on(oDoc, _MOUSEOUT, onDOMEvent, this, true);
                        Event.on(oDoc, _MOUSEDOWN, onDOMEvent, this, true);
                        Event.on(oDoc, _MOUSEUP, onDOMEvent, this, true);
                        Event.on(oDoc, _CLICK, onDOMEvent, this, true);
                        Event.on(oDoc, _KEYDOWN, onDOMEvent, this, true);
                        Event.on(oDoc, _KEYUP, onDOMEvent, this, true);
                        Event.on(oDoc, _KEYPRESS, onDOMEvent, this, true);
    
                        Event.onFocus(oDoc, onDOMEvent, this, true);
                        Event.onBlur(oDoc, onDOMEvent, this, true);						
    
                        m_bInitializedEventHandlers = true;
                        
                        YAHOO.log("DOM event handlers initialized.", "info", _MENUMANAGER);
            
                    }
            
                    p_oMenu.cfg.subscribeToConfigEvent(_VISIBLE, onMenuVisibleConfigChange);
                    p_oMenu.destroyEvent.subscribe(onMenuDestroy, p_oMenu, this);
                    p_oMenu.itemAddedEvent.subscribe(onItemAdded);
                    p_oMenu.focusEvent.subscribe(onMenuFocus);
                    p_oMenu.blurEvent.subscribe(onMenuBlur);
        
                    YAHOO.log(p_oMenu + " successfully registered.", "info", _MENUMANAGER);
        
                }
        
            },
    
        
            /**
            * @method removeMenu
            * @description Removes a menu from the collection of known menus.
            * @param {YAHOO.widget.Menu} p_oMenu Object specifying the Menu  
            * instance to be removed.
            */
            removeMenu: function (p_oMenu) {
    
                var sId,
                    aItems,
                    i;
        
                if (p_oMenu) {
    
                    sId = p_oMenu.id;
        
                    if ((sId in m_oMenus) && (m_oMenus[sId] == p_oMenu)) {

                        // Unregister each menu item

                        aItems = p_oMenu.getItems();

                        if (aItems && aItems.length > 0) {

                            i = aItems.length - 1;

                            do {

                                removeItem(aItems[i]);

                            }
                            while (i--);

                        }


                        // Unregister the menu

                        delete m_oMenus[sId];
            
                        YAHOO.log(p_oMenu + " successfully unregistered.", "info", _MENUMANAGER);
        

                        /*
                             Unregister the menu from the collection of 
                             visible menus
                        */

                        if ((sId in m_oVisibleMenus) && (m_oVisibleMenus[sId] == p_oMenu)) {
            
                            delete m_oVisibleMenus[sId];
                            
                            YAHOO.log(p_oMenu + " unregistered from the" + 
                                        " collection of visible menus.", "info", _MENUMANAGER);
       
                        }


                        // Unsubscribe event listeners

                        if (p_oMenu.cfg) {

                            p_oMenu.cfg.unsubscribeFromConfigEvent(_VISIBLE, 
                                onMenuVisibleConfigChange);
                            
                        }

                        p_oMenu.destroyEvent.unsubscribe(onMenuDestroy, 
                            p_oMenu);
                
                        p_oMenu.itemAddedEvent.unsubscribe(onItemAdded);
                        p_oMenu.focusEvent.unsubscribe(onMenuFocus);
                        p_oMenu.blurEvent.unsubscribe(onMenuBlur);

                    }
                
                }
    
            },
        
        
            /**
            * @method hideVisible
            * @description Hides all visible, dynamically positioned menus 
            * (excluding instances of YAHOO.widget.MenuBar).
            */
            hideVisible: function () {
        
                var oMenu;
        
                for (var i in m_oVisibleMenus) {
        
                    if (Lang.hasOwnProperty(m_oVisibleMenus, i)) {
        
                        oMenu = m_oVisibleMenus[i];
        
                        if (!(oMenu instanceof YAHOO.widget.MenuBar) && 
                            oMenu.cfg.getProperty(_POSITION) == _DYNAMIC) {
        
                            oMenu.hide();
        
                        }
        
                    }
        
                }        
    
            },


            /**
            * @method getVisible
            * @description Returns a collection of all visible menus registered
            * with the menu manger.
            * @return {Object}
            */
            getVisible: function () {
            
                return m_oVisibleMenus;
            
            },

    
            /**
            * @method getMenus
            * @description Returns a collection of all menus registered with the 
            * menu manger.
            * @return {Object}
            */
            getMenus: function () {
    
                return m_oMenus;
            
            },
    
    
            /**
            * @method getMenu
            * @description Returns a menu with the specified id.
            * @param {String} p_sId String specifying the id of the 
            * <code>&#60;div&#62;</code> element representing the menu to
            * be retrieved.
            * @return {YAHOO.widget.Menu}
            */
            getMenu: function (p_sId) {
                
                var returnVal;
                
                if (p_sId in m_oMenus) {
                
                    returnVal = m_oMenus[p_sId];
                
                }
            
                return returnVal;
            
            },
    
    
            /**
            * @method getMenuItem
            * @description Returns a menu item with the specified id.
            * @param {String} p_sId String specifying the id of the 
            * <code>&#60;li&#62;</code> element representing the menu item to
            * be retrieved.
            * @return {YAHOO.widget.MenuItem}
            */
            getMenuItem: function (p_sId) {
    
                var returnVal;
    
                if (p_sId in m_oItems) {
    
                    returnVal = m_oItems[p_sId];
                
                }
                
                return returnVal;
            
            },


            /**
            * @method getMenuItemGroup
            * @description Returns an array of menu item instances whose 
            * corresponding <code>&#60;li&#62;</code> elements are child 
            * nodes of the <code>&#60;ul&#62;</code> element with the 
            * specified id.
            * @param {String} p_sId String specifying the id of the 
            * <code>&#60;ul&#62;</code> element representing the group of 
            * menu items to be retrieved.
            * @return {Array}
            */
            getMenuItemGroup: function (p_sId) {

                var oUL = Dom.get(p_sId),
                    aItems,
                    oNode,
                    oItem,
                    sId,
                    returnVal;
    

                if (oUL && oUL.tagName && oUL.tagName.toUpperCase() == _UL) {

                    oNode = oUL.firstChild;

                    if (oNode) {

                        aItems = [];
                        
                        do {

                            sId = oNode.id;

                            if (sId) {
                            
                                oItem = this.getMenuItem(sId);
                                
                                if (oItem) {
                                
                                    aItems[aItems.length] = oItem;
                                
                                }
                            
                            }
                        
                        }
                        while ((oNode = oNode.nextSibling));


                        if (aItems.length > 0) {

                            returnVal = aItems;
                        
                        }

                    }
                
                }

                return returnVal;
            
            },

    
            /**
            * @method getFocusedMenuItem
            * @description Returns a reference to the menu item that currently 
            * has focus.
            * @return {YAHOO.widget.MenuItem}
            */
            getFocusedMenuItem: function () {
    
                return m_oFocusedMenuItem;
    
            },
    
    
            /**
            * @method getFocusedMenu
            * @description Returns a reference to the menu that currently 
            * has focus.
            * @return {YAHOO.widget.Menu}
            */
            getFocusedMenu: function () {

                var returnVal;
    
                if (m_oFocusedMenuItem) {
    
                    returnVal = m_oFocusedMenuItem.parent.getRoot();
                
                }
    
                return returnVal;
    
            },
    
        
            /**
            * @method toString
            * @description Returns a string representing the menu manager.
            * @return {String}
            */
            toString: function () {
            
                return _MENUMANAGER;
            
            }
    
        };
    
    }();

})();



(function () {

    var Lang = YAHOO.lang,

    // String constants
    
        _MENU = "Menu",
        _DIV_UPPERCASE = "DIV",
        _DIV_LOWERCASE = "div",
        _ID = "id",
        _SELECT = "SELECT",
        _XY = "xy",
        _Y = "y",
        _UL_UPPERCASE = "UL",
        _UL_LOWERCASE = "ul",
        _FIRST_OF_TYPE = "first-of-type",
        _LI = "LI",
        _OPTGROUP = "OPTGROUP",
        _OPTION = "OPTION",
        _DISABLED = "disabled",
        _NONE = "none",
        _SELECTED = "selected",
        _GROUP_INDEX = "groupindex",
        _INDEX = "index",
        _SUBMENU = "submenu",
        _VISIBLE = "visible",
        _HIDE_DELAY = "hidedelay",
        _POSITION = "position",
        _DYNAMIC = "dynamic",
        _STATIC = "static",
        _DYNAMIC_STATIC = _DYNAMIC + "," + _STATIC,
        _URL = "url",
        _HASH = "#",
        _TARGET = "target",
        _MAX_HEIGHT = "maxheight",
        _TOP_SCROLLBAR = "topscrollbar",
        _BOTTOM_SCROLLBAR = "bottomscrollbar",
        _UNDERSCORE = "_",
        _TOP_SCROLLBAR_DISABLED = _TOP_SCROLLBAR + _UNDERSCORE + _DISABLED,
        _BOTTOM_SCROLLBAR_DISABLED = _BOTTOM_SCROLLBAR + _UNDERSCORE + _DISABLED,
        _MOUSEMOVE = "mousemove",
        _SHOW_DELAY = "showdelay",
        _SUBMENU_HIDE_DELAY = "submenuhidedelay",
        _IFRAME = "iframe",
        _CONSTRAIN_TO_VIEWPORT = "constraintoviewport",
        _PREVENT_CONTEXT_OVERLAP = "preventcontextoverlap",
        _SUBMENU_ALIGNMENT = "submenualignment",
        _AUTO_SUBMENU_DISPLAY = "autosubmenudisplay",
        _CLICK_TO_HIDE = "clicktohide",
        _CONTAINER = "container",
        _SCROLL_INCREMENT = "scrollincrement",
        _MIN_SCROLL_HEIGHT = "minscrollheight",
        _CLASSNAME = "classname",
        _SHADOW = "shadow",
        _KEEP_OPEN = "keepopen",
        _HD = "hd",
        _HAS_TITLE = "hastitle",
        _CONTEXT = "context",
        _EMPTY_STRING = "",
        _MOUSEDOWN = "mousedown",
        _KEYDOWN = "keydown",
        _HEIGHT = "height",
        _WIDTH = "width",
        _PX = "px",
        _EFFECT = "effect",
        _MONITOR_RESIZE = "monitorresize",
        _DISPLAY = "display",
        _BLOCK = "block",
        _VISIBILITY = "visibility",
        _ABSOLUTE = "absolute",
        _ZINDEX = "zindex",
        _YUI_MENU_BODY_SCROLLED = "yui-menu-body-scrolled",
        _NON_BREAKING_SPACE = "&#32;",
        _SPACE = " ",
        _MOUSEOVER = "mouseover",
        _MOUSEOUT = "mouseout",
        _ITEM_ADDED = "itemAdded",
        _ITEM_REMOVED = "itemRemoved",
        _HIDDEN = "hidden",
        _YUI_MENU_SHADOW = "yui-menu-shadow",
        _YUI_MENU_SHADOW_VISIBLE = _YUI_MENU_SHADOW + "-visible",
        _YUI_MENU_SHADOW_YUI_MENU_SHADOW_VISIBLE = _YUI_MENU_SHADOW + _SPACE + _YUI_MENU_SHADOW_VISIBLE;


/**
* The Menu class creates a container that holds a vertical list representing 
* a set of options or commands.  Menu is the base class for all 
* menu containers. 
* @param {String} p_oElement String specifying the id attribute of the 
* <code>&#60;div&#62;</code> element of the menu.
* @param {String} p_oElement String specifying the id attribute of the 
* <code>&#60;select&#62;</code> element to be used as the data source 
* for the menu.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
* level-one-html.html#ID-22445964">HTMLDivElement</a>} p_oElement Object 
* specifying the <code>&#60;div&#62;</code> element of the menu.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
* level-one-html.html#ID-94282980">HTMLSelectElement</a>} p_oElement 
* Object specifying the <code>&#60;select&#62;</code> element to be used as 
* the data source for the menu.
* @param {Object} p_oConfig Optional. Object literal specifying the 
* configuration for the menu. See configuration class documentation for 
* more details.
* @namespace YAHOO.widget
* @class Menu
* @constructor
* @extends YAHOO.widget.Overlay
*/
YAHOO.widget.Menu = function (p_oElement, p_oConfig) {

    if (p_oConfig) {
        this.parent = p_oConfig.parent;
        this.lazyLoad = p_oConfig.lazyLoad || p_oConfig.lazyload;
        this.itemData = p_oConfig.itemData || p_oConfig.itemdata;
    }

    YAHOO.widget.Menu.superclass.constructor.call(this, p_oElement, p_oConfig);
};



/**
* @method checkPosition
* @description Checks to make sure that the value of the "position" property 
* is one of the supported strings. Returns true if the position is supported.
* @private
* @param {Object} p_sPosition String specifying the position of the menu.
* @return {Boolean}
*/
function checkPosition(p_sPosition) {

    var returnVal = false;

    if (Lang.isString(p_sPosition)) {

        returnVal = (_DYNAMIC_STATIC.indexOf((p_sPosition.toLowerCase())) != -1);

    }

    return returnVal;

}


var Dom = YAHOO.util.Dom,
    Event = YAHOO.util.Event,
    Module = YAHOO.widget.Module,
    Overlay = YAHOO.widget.Overlay,
    Menu = YAHOO.widget.Menu,
    MenuManager = YAHOO.widget.MenuManager,
    CustomEvent = YAHOO.util.CustomEvent,
    UA = YAHOO.env.ua,
    
    m_oShadowTemplate,

    bFocusListenerInitialized = false,

    oFocusedElement,

    EVENT_TYPES = [
    
        ["mouseOverEvent", _MOUSEOVER],
        ["mouseOutEvent", _MOUSEOUT],
        ["mouseDownEvent", _MOUSEDOWN],
        ["mouseUpEvent", "mouseup"],
        ["clickEvent", "click"],
        ["keyPressEvent", "keypress"],
        ["keyDownEvent", _KEYDOWN],
        ["keyUpEvent", "keyup"],
        ["focusEvent", "focus"],
        ["blurEvent", "blur"],
        ["itemAddedEvent", _ITEM_ADDED],
        ["itemRemovedEvent", _ITEM_REMOVED]

    ],

    VISIBLE_CONFIG =  { 
        key: _VISIBLE, 
        value: false, 
        validator: Lang.isBoolean
    }, 

    CONSTRAIN_TO_VIEWPORT_CONFIG =  {
        key: _CONSTRAIN_TO_VIEWPORT, 
        value: true, 
        validator: Lang.isBoolean, 
        supercedes: [_IFRAME,"x",_Y,_XY]
    }, 

    PREVENT_CONTEXT_OVERLAP_CONFIG =  {
        key: _PREVENT_CONTEXT_OVERLAP,
        value: true,
        validator: Lang.isBoolean,  
        supercedes: [_CONSTRAIN_TO_VIEWPORT]
    },

    POSITION_CONFIG =  { 
        key: _POSITION, 
        value: _DYNAMIC, 
        validator: checkPosition, 
        supercedes: [_VISIBLE, _IFRAME]
    }, 

    SUBMENU_ALIGNMENT_CONFIG =  { 
        key: _SUBMENU_ALIGNMENT, 
        value: ["tl","tr"]
    },

    AUTO_SUBMENU_DISPLAY_CONFIG =  { 
        key: _AUTO_SUBMENU_DISPLAY, 
        value: true, 
        validator: Lang.isBoolean,
        suppressEvent: true
    }, 

    SHOW_DELAY_CONFIG =  { 
        key: _SHOW_DELAY, 
        value: 250, 
        validator: Lang.isNumber, 
        suppressEvent: true
    }, 

    HIDE_DELAY_CONFIG =  { 
        key: _HIDE_DELAY, 
        value: 0, 
        validator: Lang.isNumber, 
        suppressEvent: true
    }, 

    SUBMENU_HIDE_DELAY_CONFIG =  { 
        key: _SUBMENU_HIDE_DELAY, 
        value: 250, 
        validator: Lang.isNumber,
        suppressEvent: true
    }, 

    CLICK_TO_HIDE_CONFIG =  { 
        key: _CLICK_TO_HIDE, 
        value: true, 
        validator: Lang.isBoolean,
        suppressEvent: true
    },

    CONTAINER_CONFIG =  { 
        key: _CONTAINER,
        suppressEvent: true
    }, 

    SCROLL_INCREMENT_CONFIG =  { 
        key: _SCROLL_INCREMENT, 
        value: 1, 
        validator: Lang.isNumber,
        supercedes: [_MAX_HEIGHT],
        suppressEvent: true
    },

    MIN_SCROLL_HEIGHT_CONFIG =  { 
        key: _MIN_SCROLL_HEIGHT, 
        value: 90, 
        validator: Lang.isNumber,
        supercedes: [_MAX_HEIGHT],
        suppressEvent: true
    },    

    MAX_HEIGHT_CONFIG =  { 
        key: _MAX_HEIGHT, 
        value: 0, 
        validator: Lang.isNumber,
        supercedes: [_IFRAME],
        suppressEvent: true
    }, 

    CLASS_NAME_CONFIG =  { 
        key: _CLASSNAME, 
        value: null, 
        validator: Lang.isString,
        suppressEvent: true
    }, 

    DISABLED_CONFIG =  { 
        key: _DISABLED, 
        value: false, 
        validator: Lang.isBoolean,
        suppressEvent: true
    },
    
    SHADOW_CONFIG =  { 
        key: _SHADOW, 
        value: true, 
        validator: Lang.isBoolean,
        suppressEvent: true,
        supercedes: [_VISIBLE]
    },
    
    KEEP_OPEN_CONFIG = {
        key: _KEEP_OPEN, 
        value: false, 
        validator: Lang.isBoolean
    };


function onDocFocus(event) {

    oFocusedElement = Event.getTarget(event);

}



YAHOO.lang.extend(Menu, Overlay, {


// Constants


/**
* @property CSS_CLASS_NAME
* @description String representing the CSS class(es) to be applied to the 
* menu's <code>&#60;div&#62;</code> element.
* @default "yuimenu"
* @final
* @type String
*/
CSS_CLASS_NAME: "yuimenu",


/**
* @property ITEM_TYPE
* @description Object representing the type of menu item to instantiate and 
* add when parsing the child nodes (either <code>&#60;li&#62;</code> element, 
* <code>&#60;optgroup&#62;</code> element or <code>&#60;option&#62;</code>) 
* of the menu's source HTML element.
* @default YAHOO.widget.MenuItem
* @final
* @type YAHOO.widget.MenuItem
*/
ITEM_TYPE: null,


/**
* @property GROUP_TITLE_TAG_NAME
* @description String representing the tagname of the HTML element used to 
* title the menu's item groups.
* @default H6
* @final
* @type String
*/
GROUP_TITLE_TAG_NAME: "h6",


/**
* @property OFF_SCREEN_POSITION
* @description Array representing the default x and y position that a menu 
* should have when it is positioned outside the viewport by the 
* "poistionOffScreen" method.
* @default "-999em"
* @final
* @type String
*/
OFF_SCREEN_POSITION: "-999em",


// Private properties


/** 
* @property _useHideDelay
* @description Boolean indicating if the "mouseover" and "mouseout" event 
* handlers used for hiding the menu via a call to "YAHOO.lang.later" have 
* already been assigned.
* @default false
* @private
* @type Boolean
*/
_useHideDelay: false,


/**
* @property _bHandledMouseOverEvent
* @description Boolean indicating the current state of the menu's 
* "mouseover" event.
* @default false
* @private
* @type Boolean
*/
_bHandledMouseOverEvent: false,


/**
* @property _bHandledMouseOutEvent
* @description Boolean indicating the current state of the menu's
* "mouseout" event.
* @default false
* @private
* @type Boolean
*/
_bHandledMouseOutEvent: false,


/**
* @property _aGroupTitleElements
* @description Array of HTML element used to title groups of menu items.
* @default []
* @private
* @type Array
*/
_aGroupTitleElements: null,


/**
* @property _aItemGroups
* @description Multi-dimensional Array representing the menu items as they
* are grouped in the menu.
* @default []
* @private
* @type Array
*/
_aItemGroups: null,


/**
* @property _aListElements
* @description Array of <code>&#60;ul&#62;</code> elements, each of which is 
* the parent node for each item's <code>&#60;li&#62;</code> element.
* @default []
* @private
* @type Array
*/
_aListElements: null,


/**
* @property _nCurrentMouseX
* @description The current x coordinate of the mouse inside the area of 
* the menu.
* @default 0
* @private
* @type Number
*/
_nCurrentMouseX: 0,


/**
* @property _bStopMouseEventHandlers
* @description Stops "mouseover," "mouseout," and "mousemove" event handlers 
* from executing.
* @default false
* @private
* @type Boolean
*/
_bStopMouseEventHandlers: false,


/**
* @property _sClassName
* @description The current value of the "classname" configuration attribute.
* @default null
* @private
* @type String
*/
_sClassName: null,



// Public properties


/**
* @property lazyLoad
* @description Boolean indicating if the menu's "lazy load" feature is 
* enabled.  If set to "true," initialization and rendering of the menu's 
* items will be deferred until the first time it is made visible.  This 
* property should be set via the constructor using the configuration 
* object literal.
* @default false
* @type Boolean
*/
lazyLoad: false,


/**
* @property itemData
* @description Array of items to be added to the menu.  The array can contain 
* strings representing the text for each item to be created, object literals 
* representing the menu item configuration properties, or MenuItem instances.  
* This property should be set via the constructor using the configuration 
* object literal.
* @default null
* @type Array
*/
itemData: null,


/**
* @property activeItem
* @description Object reference to the item in the menu that has is selected.
* @default null
* @type YAHOO.widget.MenuItem
*/
activeItem: null,


/**
* @property parent
* @description Object reference to the menu's parent menu or menu item.  
* This property can be set via the constructor using the configuration 
* object literal.
* @default null
* @type YAHOO.widget.MenuItem
*/
parent: null,


/**
* @property srcElement
* @description Object reference to the HTML element (either 
* <code>&#60;select&#62;</code> or <code>&#60;div&#62;</code>) used to 
* create the menu.
* @default null
* @type <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
* level-one-html.html#ID-94282980">HTMLSelectElement</a>|<a 
* href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-one-html.
* html#ID-22445964">HTMLDivElement</a>
*/
srcElement: null,



// Events


/**
* @event mouseOverEvent
* @description Fires when the mouse has entered the menu.  Passes back 
* the DOM Event object as an argument.
*/


/**
* @event mouseOutEvent
* @description Fires when the mouse has left the menu.  Passes back the DOM 
* Event object as an argument.
* @type YAHOO.util.CustomEvent
*/


/**
* @event mouseDownEvent
* @description Fires when the user mouses down on the menu.  Passes back the 
* DOM Event object as an argument.
* @type YAHOO.util.CustomEvent
*/


/**
* @event mouseUpEvent
* @description Fires when the user releases a mouse button while the mouse is 
* over the menu.  Passes back the DOM Event object as an argument.
* @type YAHOO.util.CustomEvent
*/


/**
* @event clickEvent
* @description Fires when the user clicks the on the menu.  Passes back the 
* DOM Event object as an argument.
* @type YAHOO.util.CustomEvent
*/


/**
* @event keyPressEvent
* @description Fires when the user presses an alphanumeric key when one of the
* menu's items has focus.  Passes back the DOM Event object as an argument.
* @type YAHOO.util.CustomEvent
*/


/**
* @event keyDownEvent
* @description Fires when the user presses a key when one of the menu's items 
* has focus.  Passes back the DOM Event object as an argument.
* @type YAHOO.util.CustomEvent
*/


/**
* @event keyUpEvent
* @description Fires when the user releases a key when one of the menu's items 
* has focus.  Passes back the DOM Event object as an argument.
* @type YAHOO.util.CustomEvent
*/


/**
* @event itemAddedEvent
* @description Fires when an item is added to the menu.
* @type YAHOO.util.CustomEvent
*/


/**
* @event itemRemovedEvent
* @description Fires when an item is removed to the menu.
* @type YAHOO.util.CustomEvent
*/


/**
* @method init
* @description The Menu class's initialization method. This method is 
* automatically called by the constructor, and sets up all DOM references 
* for pre-existing markup, and creates required markup if it is not 
* already present.
* @param {String} p_oElement String specifying the id attribute of the 
* <code>&#60;div&#62;</code> element of the menu.
* @param {String} p_oElement String specifying the id attribute of the 
* <code>&#60;select&#62;</code> element to be used as the data source 
* for the menu.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
* level-one-html.html#ID-22445964">HTMLDivElement</a>} p_oElement Object 
* specifying the <code>&#60;div&#62;</code> element of the menu.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
* level-one-html.html#ID-94282980">HTMLSelectElement</a>} p_oElement 
* Object specifying the <code>&#60;select&#62;</code> element to be used as 
* the data source for the menu.
* @param {Object} p_oConfig Optional. Object literal specifying the 
* configuration for the menu. See configuration class documentation for 
* more details.
*/
init: function (p_oElement, p_oConfig) {

    this._aItemGroups = [];
    this._aListElements = [];
    this._aGroupTitleElements = [];

    if (!this.ITEM_TYPE) {

        this.ITEM_TYPE = YAHOO.widget.MenuItem;

    }


    var oElement;

    if (Lang.isString(p_oElement)) {

        oElement = Dom.get(p_oElement);

    }
    else if (p_oElement.tagName) {

        oElement = p_oElement;

    }


    if (oElement && oElement.tagName) {

        switch(oElement.tagName.toUpperCase()) {
    
            case _DIV_UPPERCASE:

                this.srcElement = oElement;

                if (!oElement.id) {

                    oElement.setAttribute(_ID, Dom.generateId());

                }


                /* 
                    Note: we don't pass the user config in here yet 
                    because we only want it executed once, at the lowest 
                    subclass level.
                */ 
            
                Menu.superclass.init.call(this, oElement);

                this.beforeInitEvent.fire(Menu);

                YAHOO.log("Source element: " + this.srcElement.tagName, "info", this.toString());
    
            break;
    
            case _SELECT:
    
                this.srcElement = oElement;

    
                /*
                    The source element is not something that we can use 
                    outright, so we need to create a new Overlay

                    Note: we don't pass the user config in here yet 
                    because we only want it executed once, at the lowest 
                    subclass level.
                */ 

                Menu.superclass.init.call(this, Dom.generateId());

                this.beforeInitEvent.fire(Menu);

                YAHOO.log("Source element: " + this.srcElement.tagName, "info", this.toString());

            break;

        }

    }
    else {

        /* 
            Note: we don't pass the user config in here yet 
            because we only want it executed once, at the lowest 
            subclass level.
        */ 
    
        Menu.superclass.init.call(this, p_oElement);

        this.beforeInitEvent.fire(Menu);

        YAHOO.log("No source element found.  Created element with id: " + this.id, "info", this.toString());

    }


    if (this.element) {
        Dom.addClass(this.element, this.CSS_CLASS_NAME);

        // Subscribe to Custom Events
        this.initEvent.subscribe(this._onInit);
        this.beforeRenderEvent.subscribe(this._onBeforeRender);
        this.renderEvent.subscribe(this._onRender);
        this.beforeShowEvent.subscribe(this._onBeforeShow);
        this.hideEvent.subscribe(this._onHide);
        this.showEvent.subscribe(this._onShow);
        this.beforeHideEvent.subscribe(this._onBeforeHide);
        this.mouseOverEvent.subscribe(this._onMouseOver);
        this.mouseOutEvent.subscribe(this._onMouseOut);
        this.clickEvent.subscribe(this._onClick);
        this.keyDownEvent.subscribe(this._onKeyDown);
        this.keyPressEvent.subscribe(this._onKeyPress);
        this.blurEvent.subscribe(this._onBlur);

        if (!bFocusListenerInitialized) {
            Event.onFocus(document, onDocFocus);
            bFocusListenerInitialized = true;
        }

        //	Fixes an issue in Firefox 2 and Webkit where Dom's "getX" and "getY" 
        //	methods return values that don't take scrollTop into consideration 

        if ((UA.gecko && UA.gecko < 1.9) || (UA.webkit && UA.webkit < 523)) {
            this.cfg.subscribeToConfigEvent(_Y, this._onYChange);
        }


        if (p_oConfig) {
            this.cfg.applyConfig(p_oConfig, true);
        }

        // Register the Menu instance with the MenuManager
        MenuManager.addMenu(this);

        this.initEvent.fire(Menu);
    }
},



// Private methods


/**
* @method _initSubTree
* @description Iterates the childNodes of the source element to find nodes 
* used to instantiate menu and menu items.
* @private
*/
_initSubTree: function () {

    var oSrcElement = this.srcElement,
        sSrcElementTagName,
        nGroup,
        sGroupTitleTagName,
        oNode,
        aListElements,
        nListElements,
        i;


    if (oSrcElement) {
    
        sSrcElementTagName = 
            (oSrcElement.tagName && oSrcElement.tagName.toUpperCase());


        if (sSrcElementTagName == _DIV_UPPERCASE) {
    
            //  Populate the collection of item groups and item group titles
    
            oNode = this.body.firstChild;
    

            if (oNode) {
    
                nGroup = 0;
                sGroupTitleTagName = this.GROUP_TITLE_TAG_NAME.toUpperCase();
        
                do {
        

                    if (oNode && oNode.tagName) {
        
                        switch (oNode.tagName.toUpperCase()) {
        
                            case sGroupTitleTagName:
                            
                                this._aGroupTitleElements[nGroup] = oNode;
        
                            break;
        
                            case _UL_UPPERCASE:
        
                                this._aListElements[nGroup] = oNode;
                                this._aItemGroups[nGroup] = [];
                                nGroup++;
        
                            break;
        
                        }
                    
                    }
        
                }
                while ((oNode = oNode.nextSibling));
        
        
                /*
                    Apply the "first-of-type" class to the first UL to mimic 
                    the ":first-of-type" CSS3 psuedo class.
                */
        
                if (this._aListElements[0]) {
        
                    Dom.addClass(this._aListElements[0], _FIRST_OF_TYPE);
        
                }
            
            }
    
        }
    
    
        oNode = null;
    
        YAHOO.log("Searching DOM for items to initialize.", "info", this.toString());
    

        if (sSrcElementTagName) {
    
            switch (sSrcElementTagName) {
        
                case _DIV_UPPERCASE:

                    aListElements = this._aListElements;
                    nListElements = aListElements.length;
        
                    if (nListElements > 0) {
        
                        YAHOO.log("Found " + nListElements + " item groups to initialize.", 
                                    "info", this.toString());
        
                        i = nListElements - 1;
        
                        do {
        
                            oNode = aListElements[i].firstChild;
            
                            if (oNode) {

                                YAHOO.log("Scanning " + 
                                    aListElements[i].childNodes.length + 
                                    " child nodes for items to initialize.", "info", this.toString());
            
                                do {
                
                                    if (oNode && oNode.tagName && 
                                        oNode.tagName.toUpperCase() == _LI) {
                
                                        YAHOO.log("Initializing " + 
                                            oNode.tagName + " node.", "info", this.toString());
        
                                        this.addItem(new this.ITEM_TYPE(oNode, 
                                                    { parent: this }), i);
            
                                    }
                        
                                }
                                while ((oNode = oNode.nextSibling));
                            
                            }
                    
                        }
                        while (i--);
        
                    }
        
                break;
        
                case _SELECT:
        
                    YAHOO.log("Scanning " +  
                        oSrcElement.childNodes.length + 
                        " child nodes for items to initialize.", "info", this.toString());
        
                    oNode = oSrcElement.firstChild;
        
                    do {
        
                        if (oNode && oNode.tagName) {
                        
                            switch (oNode.tagName.toUpperCase()) {
            
                                case _OPTGROUP:
                                case _OPTION:
            
                                    YAHOO.log("Initializing " +  
                                        oNode.tagName + " node.", "info", this.toString());
            
                                    this.addItem(
                                            new this.ITEM_TYPE(
                                                    oNode, 
                                                    { parent: this }
                                                )
                                            );
            
                                break;
            
                            }
    
                        }
        
                    }
                    while ((oNode = oNode.nextSibling));
        
                break;
        
            }
    
        }    
    
    }

},


/**
* @method _getFirstEnabledItem
* @description Returns the first enabled item in the menu.
* @return {YAHOO.widget.MenuItem}
* @private
*/
_getFirstEnabledItem: function () {

    var aItems = this.getItems(),
        nItems = aItems.length,
        oItem,
        returnVal;
    

    for(var i=0; i<nItems; i++) {

        oItem = aItems[i];

        if (oItem && !oItem.cfg.getProperty(_DISABLED) && oItem.element.style.display != _NONE) {

            returnVal = oItem;
            break;

        }
    
    }
    
    return returnVal;
    
},


/**
* @method _addItemToGroup
* @description Adds a menu item to a group.
* @private
* @param {Number} p_nGroupIndex Number indicating the group to which the 
* item belongs.
* @param {YAHOO.widget.MenuItem} p_oItem Object reference for the MenuItem 
* instance to be added to the menu.
* @param {HTML} p_oItem String or markup specifying the content of the item to be added 
* to the menu. The item is inserted into the DOM as HTML, and should be escaped by the implementor if coming from an external source.
* @param {Object} p_oItem Object literal containing a set of menu item 
* configuration properties.
* @param {Number} p_nItemIndex Optional. Number indicating the index at 
* which the menu item should be added.
* @return {YAHOO.widget.MenuItem}
*/
_addItemToGroup: function (p_nGroupIndex, p_oItem, p_nItemIndex) {

    var oItem,
        nGroupIndex,
        aGroup,
        oGroupItem,
        bAppend,
        oNextItemSibling,
        nItemIndex,
        returnVal;


    function getNextItemSibling(p_aArray, p_nStartIndex) {

        return (p_aArray[p_nStartIndex] || getNextItemSibling(p_aArray, (p_nStartIndex+1)));

    }


    if (p_oItem instanceof this.ITEM_TYPE) {

        oItem = p_oItem;
        oItem.parent = this;

    }
    else if (Lang.isString(p_oItem)) {

        oItem = new this.ITEM_TYPE(p_oItem, { parent: this });
    
    }
    else if (Lang.isObject(p_oItem)) {

        p_oItem.parent = this;

        oItem = new this.ITEM_TYPE(p_oItem.text, p_oItem);

    }


    if (oItem) {

        if (oItem.cfg.getProperty(_SELECTED)) {

            this.activeItem = oItem;
        
        }


        nGroupIndex = Lang.isNumber(p_nGroupIndex) ? p_nGroupIndex : 0;
        aGroup = this._getItemGroup(nGroupIndex);



        if (!aGroup) {

            aGroup = this._createItemGroup(nGroupIndex);

        }


        if (Lang.isNumber(p_nItemIndex)) {

            bAppend = (p_nItemIndex >= aGroup.length);            


            if (aGroup[p_nItemIndex]) {
    
                aGroup.splice(p_nItemIndex, 0, oItem);
    
            }
            else {
    
                aGroup[p_nItemIndex] = oItem;
    
            }


            oGroupItem = aGroup[p_nItemIndex];

            if (oGroupItem) {

                if (bAppend && (!oGroupItem.element.parentNode || 
                        oGroupItem.element.parentNode.nodeType == 11)) {
        
                    this._aListElements[nGroupIndex].appendChild(oGroupItem.element);
    
                }
                else {
    
                    oNextItemSibling = getNextItemSibling(aGroup, (p_nItemIndex+1));
    
                    if (oNextItemSibling && (!oGroupItem.element.parentNode || 
                            oGroupItem.element.parentNode.nodeType == 11)) {
            
                        this._aListElements[nGroupIndex].insertBefore(
                                oGroupItem.element, oNextItemSibling.element);
        
                    }
    
                }
    

                oGroupItem.parent = this;
        
                this._subscribeToItemEvents(oGroupItem);
    
                this._configureSubmenu(oGroupItem);
                
                this._updateItemProperties(nGroupIndex);
        
                YAHOO.log("Item inserted." + 
                    " Text: " + oGroupItem.cfg.getProperty("text") + ", " + 
                    " Index: " + oGroupItem.index + ", " + 
                    " Group Index: " + oGroupItem.groupIndex, "info", this.toString());

                this.itemAddedEvent.fire(oGroupItem);
                this.changeContentEvent.fire();

                returnVal = oGroupItem;
    
            }

        }
        else {
    
            nItemIndex = aGroup.length;
    
            aGroup[nItemIndex] = oItem;

            oGroupItem = aGroup[nItemIndex];
    

            if (oGroupItem) {
    
                if (!Dom.isAncestor(this._aListElements[nGroupIndex], oGroupItem.element)) {
    
                    this._aListElements[nGroupIndex].appendChild(oGroupItem.element);
    
                }
    
                oGroupItem.element.setAttribute(_GROUP_INDEX, nGroupIndex);
                oGroupItem.element.setAttribute(_INDEX, nItemIndex);
        
                oGroupItem.parent = this;
    
                oGroupItem.index = nItemIndex;
                oGroupItem.groupIndex = nGroupIndex;
        
                this._subscribeToItemEvents(oGroupItem);
    
                this._configureSubmenu(oGroupItem);
    
                if (nItemIndex === 0) {
        
                    Dom.addClass(oGroupItem.element, _FIRST_OF_TYPE);
        
                }

                YAHOO.log("Item added." + 
                    " Text: " + oGroupItem.cfg.getProperty("text") + ", " + 
                    " Index: " + oGroupItem.index + ", " + 
                    " Group Index: " + oGroupItem.groupIndex, "info", this.toString());
        

                this.itemAddedEvent.fire(oGroupItem);
                this.changeContentEvent.fire();

                returnVal = oGroupItem;
    
            }
    
        }

    }
    
    return returnVal;
    
},


/**
* @method _removeItemFromGroupByIndex
* @description Removes a menu item from a group by index.  Returns the menu 
* item that was removed.
* @private
* @param {Number} p_nGroupIndex Number indicating the group to which the menu 
* item belongs.
* @param {Number} p_nItemIndex Number indicating the index of the menu item 
* to be removed.
* @return {YAHOO.widget.MenuItem}
*/
_removeItemFromGroupByIndex: function (p_nGroupIndex, p_nItemIndex) {

    var nGroupIndex = Lang.isNumber(p_nGroupIndex) ? p_nGroupIndex : 0,
        aGroup = this._getItemGroup(nGroupIndex),
        aArray,
        oItem,
        oUL;

    if (aGroup) {

        aArray = aGroup.splice(p_nItemIndex, 1);
        oItem = aArray[0];
    
        if (oItem) {
    
            // Update the index and className properties of each member        
            
            this._updateItemProperties(nGroupIndex);
    
            if (aGroup.length === 0) {
    
                // Remove the UL
    
                oUL = this._aListElements[nGroupIndex];
    
                if (oUL && oUL.parentNode) {
                    oUL.parentNode.removeChild(oUL);
                }
    
                // Remove the group from the array of items
    
                this._aItemGroups.splice(nGroupIndex, 1);
    
    
                // Remove the UL from the array of ULs
    
                this._aListElements.splice(nGroupIndex, 1);
    
    
                /*
                     Assign the "first-of-type" class to the new first UL 
                     in the collection
                */
    
                oUL = this._aListElements[0];
    
                if (oUL) {
    
                    Dom.addClass(oUL, _FIRST_OF_TYPE);
    
                }            
    
            }
    

            this.itemRemovedEvent.fire(oItem);
            this.changeContentEvent.fire();
    
        }

    }

    // Return a reference to the item that was removed

    return oItem;
    
},


/**
* @method _removeItemFromGroupByValue
* @description Removes a menu item from a group by reference.  Returns the 
* menu item that was removed.
* @private
* @param {Number} p_nGroupIndex Number indicating the group to which the
* menu item belongs.
* @param {YAHOO.widget.MenuItem} p_oItem Object reference for the MenuItem 
* instance to be removed.
* @return {YAHOO.widget.MenuItem}
*/    
_removeItemFromGroupByValue: function (p_nGroupIndex, p_oItem) {

    var aGroup = this._getItemGroup(p_nGroupIndex),
        nItems,
        nItemIndex,
        returnVal,
        i;

    if (aGroup) {

        nItems = aGroup.length;
        nItemIndex = -1;
    
        if (nItems > 0) {
    
            i = nItems-1;
        
            do {
        
                if (aGroup[i] == p_oItem) {
        
                    nItemIndex = i;
                    break;    
        
                }
        
            }
            while (i--);
        
            if (nItemIndex > -1) {
        
                returnVal = this._removeItemFromGroupByIndex(p_nGroupIndex, nItemIndex);
        
            }
    
        }
    
    }
    
    return returnVal;

},


/**
* @method _updateItemProperties
* @description Updates the "index," "groupindex," and "className" properties 
* of the menu items in the specified group. 
* @private
* @param {Number} p_nGroupIndex Number indicating the group of items to update.
*/
_updateItemProperties: function (p_nGroupIndex) {

    var aGroup = this._getItemGroup(p_nGroupIndex),
        nItems = aGroup.length,
        oItem,
        oLI,
        i;


    if (nItems > 0) {

        i = nItems - 1;

        // Update the index and className properties of each member
    
        do {

            oItem = aGroup[i];

            if (oItem) {
    
                oLI = oItem.element;

                oItem.index = i;
                oItem.groupIndex = p_nGroupIndex;

                oLI.setAttribute(_GROUP_INDEX, p_nGroupIndex);
                oLI.setAttribute(_INDEX, i);

                Dom.removeClass(oLI, _FIRST_OF_TYPE);

            }
    
        }
        while (i--);


        if (oLI) {

            Dom.addClass(oLI, _FIRST_OF_TYPE);

        }

    }

},


/**
* @method _createItemGroup
* @description Creates a new menu item group (array) and its associated 
* <code>&#60;ul&#62;</code> element. Returns an aray of menu item groups.
* @private
* @param {Number} p_nIndex Number indicating the group to create.
* @return {Array}
*/
_createItemGroup: function (p_nIndex) {

    var oUL,
        returnVal;

    if (!this._aItemGroups[p_nIndex]) {

        this._aItemGroups[p_nIndex] = [];

        oUL = document.createElement(_UL_LOWERCASE);

        this._aListElements[p_nIndex] = oUL;

        returnVal = this._aItemGroups[p_nIndex];

    }
    
    return returnVal;

},


/**
* @method _getItemGroup
* @description Returns the menu item group at the specified index.
* @private
* @param {Number} p_nIndex Number indicating the index of the menu item group 
* to be retrieved.
* @return {Array}
*/
_getItemGroup: function (p_nIndex) {

    var nIndex = Lang.isNumber(p_nIndex) ? p_nIndex : 0,
        aGroups = this._aItemGroups,
        returnVal;

    if (nIndex in aGroups) {

        returnVal = aGroups[nIndex];

    }
    
    return returnVal;

},


/**
* @method _configureSubmenu
* @description Subscribes the menu item's submenu to its parent menu's events.
* @private
* @param {YAHOO.widget.MenuItem} p_oItem Object reference for the MenuItem 
* instance with the submenu to be configured.
*/
_configureSubmenu: function (p_oItem) {

    var oSubmenu = p_oItem.cfg.getProperty(_SUBMENU);

    if (oSubmenu) {
            
        /*
            Listen for configuration changes to the parent menu 
            so they they can be applied to the submenu.
        */

        this.cfg.configChangedEvent.subscribe(this._onParentMenuConfigChange, oSubmenu, true);

        this.renderEvent.subscribe(this._onParentMenuRender, oSubmenu, true);

    }

},




/**
* @method _subscribeToItemEvents
* @description Subscribes a menu to a menu item's event.
* @private
* @param {YAHOO.widget.MenuItem} p_oItem Object reference for the MenuItem 
* instance whose events should be subscribed to.
*/
_subscribeToItemEvents: function (p_oItem) {

    p_oItem.destroyEvent.subscribe(this._onMenuItemDestroy, p_oItem, this);
    p_oItem.cfg.configChangedEvent.subscribe(this._onMenuItemConfigChange, p_oItem, this);

},


/**
* @method _onVisibleChange
* @description Change event handler for the the menu's "visible" configuration
* property.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onVisibleChange: function (p_sType, p_aArgs) {

    var bVisible = p_aArgs[0];
    
    if (bVisible) {

        Dom.addClass(this.element, _VISIBLE);

    }
    else {

        Dom.removeClass(this.element, _VISIBLE);

    }

},


/**
* @method _cancelHideDelay
* @description Cancels the call to "hideMenu."
* @private
*/
_cancelHideDelay: function () {

    var oTimer = this.getRoot()._hideDelayTimer;

    if (oTimer) {

        oTimer.cancel();

    }

},


/**
* @method _execHideDelay
* @description Hides the menu after the number of milliseconds specified by 
* the "hidedelay" configuration property.
* @private
*/
_execHideDelay: function () {

    this._cancelHideDelay();

    var oRoot = this.getRoot();

    oRoot._hideDelayTimer = Lang.later(oRoot.cfg.getProperty(_HIDE_DELAY), this, function () {
    
        if (oRoot.activeItem) {
            if (oRoot.hasFocus()) {
                oRoot.activeItem.focus();
            }
            oRoot.clearActiveItem();
        }

        if (oRoot == this && !(this instanceof YAHOO.widget.MenuBar) && 
            this.cfg.getProperty(_POSITION) == _DYNAMIC) {
            this.hide();
        }
    });

},


/**
* @method _cancelShowDelay
* @description Cancels the call to the "showMenu."
* @private
*/
_cancelShowDelay: function () {
    var oTimer = this.getRoot()._showDelayTimer;
    if (oTimer) {
        oTimer.cancel();
    }
},


/**
* @method _execSubmenuHideDelay
* @description Hides a submenu after the number of milliseconds specified by 
* the "submenuhidedelay" configuration property have elapsed.
* @private
* @param {YAHOO.widget.Menu} p_oSubmenu Object specifying the submenu that  
* should be hidden.
* @param {Number} p_nMouseX The x coordinate of the mouse when it left 
* the specified submenu's parent menu item.
* @param {Number} p_nHideDelay The number of milliseconds that should ellapse
* before the submenu is hidden.
*/
_execSubmenuHideDelay: function (p_oSubmenu, p_nMouseX, p_nHideDelay) {

    p_oSubmenu._submenuHideDelayTimer = Lang.later(50, this, function () {

        if (this._nCurrentMouseX > (p_nMouseX + 10)) {

            p_oSubmenu._submenuHideDelayTimer = Lang.later(p_nHideDelay, p_oSubmenu, function () {
        
                this.hide();

            });

        }
        else {

            p_oSubmenu.hide();
        
        }
    
    });

},



// Protected methods


/**
* @method _disableScrollHeader
* @description Disables the header used for scrolling the body of the menu.
* @protected
*/
_disableScrollHeader: function () {

    if (!this._bHeaderDisabled) {

        Dom.addClass(this.header, _TOP_SCROLLBAR_DISABLED);
        this._bHeaderDisabled = true;

    }

},


/**
* @method _disableScrollFooter
* @description Disables the footer used for scrolling the body of the menu.
* @protected
*/
_disableScrollFooter: function () {

    if (!this._bFooterDisabled) {

        Dom.addClass(this.footer, _BOTTOM_SCROLLBAR_DISABLED);
        this._bFooterDisabled = true;

    }

},


/**
* @method _enableScrollHeader
* @description Enables the header used for scrolling the body of the menu.
* @protected
*/
_enableScrollHeader: function () {

    if (this._bHeaderDisabled) {

        Dom.removeClass(this.header, _TOP_SCROLLBAR_DISABLED);
        this._bHeaderDisabled = false;

    }

},


/**
* @method _enableScrollFooter
* @description Enables the footer used for scrolling the body of the menu.
* @protected
*/
_enableScrollFooter: function () {

    if (this._bFooterDisabled) {

        Dom.removeClass(this.footer, _BOTTOM_SCROLLBAR_DISABLED);
        this._bFooterDisabled = false;

    }

},


/**
* @method _onMouseOver
* @description "mouseover" event handler for the menu.
* @protected
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onMouseOver: function (p_sType, p_aArgs) {

    var oEvent = p_aArgs[0],
        oItem = p_aArgs[1],
        oTarget = Event.getTarget(oEvent),
        oRoot = this.getRoot(),
        oSubmenuHideDelayTimer = this._submenuHideDelayTimer,
        oParentMenu,
        nShowDelay,
        bShowDelay,
        oActiveItem,
        oItemCfg,
        oSubmenu;


    var showSubmenu = function () {

        if (this.parent.cfg.getProperty(_SELECTED)) {

            this.show();

        }

    };


    if (!this._bStopMouseEventHandlers) {
    
        if (!this._bHandledMouseOverEvent && (oTarget == this.element || 
                Dom.isAncestor(this.element, oTarget))) {
    
            // Menu mouseover logic

            if (this._useHideDelay) {
                this._cancelHideDelay();
            }
    
            this._nCurrentMouseX = 0;
    
            Event.on(this.element, _MOUSEMOVE, this._onMouseMove, this, true);


            /*
                If the mouse is moving from the submenu back to its corresponding menu item, 
                don't hide the submenu or clear the active MenuItem.
            */

            if (!(oItem && Dom.isAncestor(oItem.element, Event.getRelatedTarget(oEvent)))) {

                this.clearActiveItem();

            }
    

            if (this.parent && oSubmenuHideDelayTimer) {
    
                oSubmenuHideDelayTimer.cancel();
    
                this.parent.cfg.setProperty(_SELECTED, true);
    
                oParentMenu = this.parent.parent;
    
                oParentMenu._bHandledMouseOutEvent = true;
                oParentMenu._bHandledMouseOverEvent = false;
    
            }
    
    
            this._bHandledMouseOverEvent = true;
            this._bHandledMouseOutEvent = false;
        
        }
    
    
        if (oItem && !oItem.handledMouseOverEvent && !oItem.cfg.getProperty(_DISABLED) && 
            (oTarget == oItem.element || Dom.isAncestor(oItem.element, oTarget))) {
    
            // Menu Item mouseover logic
    
            nShowDelay = this.cfg.getProperty(_SHOW_DELAY);
            bShowDelay = (nShowDelay > 0);
    
    
            if (bShowDelay) {
            
                this._cancelShowDelay();
            
            }
    
    
            oActiveItem = this.activeItem;
        
            if (oActiveItem) {
        
                oActiveItem.cfg.setProperty(_SELECTED, false);
        
            }
    
    
            oItemCfg = oItem.cfg;
        
            // Select and focus the current menu item
        
            oItemCfg.setProperty(_SELECTED, true);
    
    
            if (this.hasFocus() || oRoot._hasFocus) {
            
                oItem.focus();
                
                oRoot._hasFocus = false;
            
            }
    
    
            if (this.cfg.getProperty(_AUTO_SUBMENU_DISPLAY)) {
    
                // Show the submenu this menu item
    
                oSubmenu = oItemCfg.getProperty(_SUBMENU);
            
                if (oSubmenu) {
            
                    if (bShowDelay) {
    
                        oRoot._showDelayTimer = 
                            Lang.later(oRoot.cfg.getProperty(_SHOW_DELAY), oSubmenu, showSubmenu);
            
                    }
                    else {
    
                        oSubmenu.show();
    
                    }
    
                }
    
            }                        
    
            oItem.handledMouseOverEvent = true;
            oItem.handledMouseOutEvent = false;
    
        }
    
    }

},


/**
* @method _onMouseOut
* @description "mouseout" event handler for the menu.
* @protected
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onMouseOut: function (p_sType, p_aArgs) {

    var oEvent = p_aArgs[0],
        oItem = p_aArgs[1],
        oRelatedTarget = Event.getRelatedTarget(oEvent),
        bMovingToSubmenu = false,
        oItemCfg,
        oSubmenu,
        nSubmenuHideDelay,
        nShowDelay;


    YAHOO.log("onMouseout: this == " + this);

    if (!this._bStopMouseEventHandlers) {
    
        if (oItem && !oItem.cfg.getProperty(_DISABLED)) {
    
            oItemCfg = oItem.cfg;
            oSubmenu = oItemCfg.getProperty(_SUBMENU);
    
    
            if (oSubmenu && (oRelatedTarget == oSubmenu.element || Dom.isAncestor(oSubmenu.element, oRelatedTarget))) {
                bMovingToSubmenu = true;
            }
    
            if (!oItem.handledMouseOutEvent && ((oRelatedTarget != oItem.element && !Dom.isAncestor(oItem.element, oRelatedTarget)) || bMovingToSubmenu)) {
                if (!bMovingToSubmenu) {
                    oItem.cfg.setProperty(_SELECTED, false);
                    if (oSubmenu) {
                        
                        nSubmenuHideDelay = this.cfg.getProperty(_SUBMENU_HIDE_DELAY);
                        nShowDelay = this.cfg.getProperty(_SHOW_DELAY);
                        if (!(this instanceof YAHOO.widget.MenuBar) && nSubmenuHideDelay > 0 && nSubmenuHideDelay >= nShowDelay) {
                            this._execSubmenuHideDelay(oSubmenu, Event.getPageX(oEvent), nSubmenuHideDelay);
                        } else {
                            oSubmenu.hide();
                        }
                    }
                }
    
                oItem.handledMouseOutEvent = true;
                oItem.handledMouseOverEvent = false;
            }
        }

        YAHOO.log("onMouseout: oRelatedTarget = " + oRelatedTarget.className);
        YAHOO.log("onMouseout: this.element = " + this.element.id);
        YAHOO.log("onMouseout: Ancestorthis.element = " + Dom.isAncestor(this.element, oRelatedTarget));
        YAHOO.log("onMouseout: canHide = " + this._didMouseLeave(oRelatedTarget));        

        if (!this._bHandledMouseOutEvent) {
            if (this._didMouseLeave(oRelatedTarget) || bMovingToSubmenu) {
                // Menu mouseout logic
                if (this._useHideDelay) {
                    this._execHideDelay();
                }
    
                Event.removeListener(this.element, _MOUSEMOVE, this._onMouseMove);
        
                this._nCurrentMouseX = Event.getPageX(oEvent);
        
                this._bHandledMouseOutEvent = true;
                this._bHandledMouseOverEvent = false;
            }
        }
    }

},

/**
 * Utilility method to determine if we really moused out of the menu based on the related target
 * @method _didMouseLeave
 * @protected
 * @param {HTMLElement} oRelatedTarget The related target based on which we're making the decision
 * @return {boolean} true if it's OK to hide based on the related target.
 */
_didMouseLeave : function(oRelatedTarget) {
    // Hide if we're not moving back to the element from somewhere inside the element, or we're moving to an element inside the menu.
    // The shadow is treated as an edge case, inside inside the menu, but we get no further mouseouts, because it overflows the element,
    // so we need to close when moving to the menu. 
    return (oRelatedTarget === this._shadow || (oRelatedTarget != this.element && !Dom.isAncestor(this.element, oRelatedTarget)));
},

/**
* @method _onMouseMove
* @description "click" event handler for the menu.
* @protected
* @param {Event} p_oEvent Object representing the DOM event object passed 
* back by the event utility (YAHOO.util.Event).
* @param {YAHOO.widget.Menu} p_oMenu Object representing the menu that 
* fired the event.
*/
_onMouseMove: function (p_oEvent, p_oMenu) {

    if (!this._bStopMouseEventHandlers) {
    
        this._nCurrentMouseX = Event.getPageX(p_oEvent);
    
    }

},


/**
* @method _onClick
* @description "click" event handler for the menu.
* @protected
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onClick: function (p_sType, p_aArgs) {

    var oEvent = p_aArgs[0],
        oItem = p_aArgs[1],
        bInMenuAnchor = false,
        oSubmenu,
        oMenu,
        oRoot,
        sId,
        sURL,
        nHashPos,
        nLen;


    var hide = function () {
        
        oRoot = this.getRoot();

        if (oRoot instanceof YAHOO.widget.MenuBar || 
            oRoot.cfg.getProperty(_POSITION) == _STATIC) {

            oRoot.clearActiveItem();

        }
        else {

            oRoot.hide();
        
        }
    
    };


    if (oItem) {
    
        if (oItem.cfg.getProperty(_DISABLED)) {
        
            Event.preventDefault(oEvent);

            hide.call(this);

        }
        else {

            oSubmenu = oItem.cfg.getProperty(_SUBMENU);
    
            
            /*
                 Check if the URL of the anchor is pointing to an element that is 
                 a child of the menu.
            */
            
            sURL = oItem.cfg.getProperty(_URL);

        
            if (sURL) {
    
                nHashPos = sURL.indexOf(_HASH);
    
                nLen = sURL.length;
    
    
                if (nHashPos != -1) {
    
                    sURL = sURL.substr(nHashPos, nLen);
        
                    nLen = sURL.length;
    
    
                    if (nLen > 1) {
    
                        sId = sURL.substr(1, nLen);
    
                        oMenu = YAHOO.widget.MenuManager.getMenu(sId);
                        
                        if (oMenu) {

                            bInMenuAnchor = 
                                (this.getRoot() === oMenu.getRoot());

                        }
                        
                    }
                    else if (nLen === 1) {
    
                        bInMenuAnchor = true;
                    
                    }
    
                }
            
            }

    
            if (bInMenuAnchor && !oItem.cfg.getProperty(_TARGET)) {
    
                Event.preventDefault(oEvent);
                

                if (UA.webkit) {
                
                    oItem.focus();
                
                }
                else {

                    oItem.focusEvent.fire();
                
                }
            
            }
    
    
            if (!oSubmenu && !this.cfg.getProperty(_KEEP_OPEN)) {
    
                hide.call(this);
    
            }
            
        }
    
    }

},

/*
    This function is called to prevent a bug in Firefox.  In Firefox,
    moving a DOM element into a stationary mouse pointer will cause the 
    browser to fire mouse events.  This can result in the menu mouse
    event handlers being called uncessarily, especially when menus are 
    moved into a stationary mouse pointer as a result of a 
    key event handler.
*/
/**
 * Utility method to stop mouseevents from being fired if the DOM
 * changes under a stationary mouse pointer (as opposed to the mouse moving
 * over a DOM element).
 * 
 * @method _stopMouseEventHandlers
 * @private
 */
_stopMouseEventHandlers: function() {
    this._bStopMouseEventHandlers = true;

    Lang.later(10, this, function () {
        this._bStopMouseEventHandlers = false;
    });
},

/**
* @method _onKeyDown
* @description "keydown" event handler for the menu.
* @protected
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onKeyDown: function (p_sType, p_aArgs) {

    var oEvent = p_aArgs[0],
        oItem = p_aArgs[1],
        oSubmenu,
        oItemCfg,
        oParentItem,
        oRoot,
        oNextItem,
        oBody,
        nBodyScrollTop,
        nBodyOffsetHeight,
        aItems,
        nItems,
        nNextItemOffsetTop,
        nScrollTarget,
        oParentMenu,
        oFocusedEl;


    if (this._useHideDelay) {
        this._cancelHideDelay();
    }

    if (oItem && !oItem.cfg.getProperty(_DISABLED)) {

        oItemCfg = oItem.cfg;
        oParentItem = this.parent;

        switch(oEvent.keyCode) {
    
            case 38:    // Up arrow
            case 40:    // Down arrow
    
                oNextItem = (oEvent.keyCode == 38) ? 
                    oItem.getPreviousEnabledSibling() : 
                    oItem.getNextEnabledSibling();
        
                if (oNextItem) {

                    this.clearActiveItem();

                    oNextItem.cfg.setProperty(_SELECTED, true);
                    oNextItem.focus();

                    if (this.cfg.getProperty(_MAX_HEIGHT) > 0 || Dom.hasClass(this.body, _YUI_MENU_BODY_SCROLLED)) {

                        oBody = this.body;
                        nBodyScrollTop = oBody.scrollTop;
                        nBodyOffsetHeight = oBody.offsetHeight;
                        aItems = this.getItems();
                        nItems = aItems.length - 1;
                        nNextItemOffsetTop = oNextItem.element.offsetTop;


                        if (oEvent.keyCode == 40 ) {    // Down
                       
                            if (nNextItemOffsetTop >= (nBodyOffsetHeight + nBodyScrollTop)) {

                                oBody.scrollTop = nNextItemOffsetTop - nBodyOffsetHeight;

                            }
                            else if (nNextItemOffsetTop <= nBodyScrollTop) {
                            
                                oBody.scrollTop = 0;
                            
                            }


                            if (oNextItem == aItems[nItems]) {

                                oBody.scrollTop = oNextItem.element.offsetTop;

                            }

                        }
                        else {  // Up

                            if (nNextItemOffsetTop <= nBodyScrollTop) {

                                oBody.scrollTop = nNextItemOffsetTop - oNextItem.element.offsetHeight;
                            
                            }
                            else if (nNextItemOffsetTop >= (nBodyScrollTop + nBodyOffsetHeight)) {
                            
                                oBody.scrollTop = nNextItemOffsetTop;
                            
                            }


                            if (oNextItem == aItems[0]) {
                            
                                oBody.scrollTop = 0;
                            
                            }

                        }


                        nBodyScrollTop = oBody.scrollTop;
                        nScrollTarget = oBody.scrollHeight - oBody.offsetHeight;

                        if (nBodyScrollTop === 0) {

                            this._disableScrollHeader();
                            this._enableScrollFooter();

                        }
                        else if (nBodyScrollTop == nScrollTarget) {

                             this._enableScrollHeader();
                             this._disableScrollFooter();

                        }
                        else {

                            this._enableScrollHeader();
                            this._enableScrollFooter();

                        }

                    }

                }

    
                Event.preventDefault(oEvent);

                this._stopMouseEventHandlers();
    
            break;
            
    
            case 39:    // Right arrow
    
                oSubmenu = oItemCfg.getProperty(_SUBMENU);
    
                if (oSubmenu) {
    
                    if (!oItemCfg.getProperty(_SELECTED)) {
        
                        oItemCfg.setProperty(_SELECTED, true);
        
                    }
    
                    oSubmenu.show();
                    oSubmenu.setInitialFocus();
                    oSubmenu.setInitialSelection();
    
                }
                else {
    
                    oRoot = this.getRoot();
                    
                    if (oRoot instanceof YAHOO.widget.MenuBar) {
    
                        oNextItem = oRoot.activeItem.getNextEnabledSibling();
    
                        if (oNextItem) {
                        
                            oRoot.clearActiveItem();
    
                            oNextItem.cfg.setProperty(_SELECTED, true);
    
                            oSubmenu = oNextItem.cfg.getProperty(_SUBMENU);
    
                            if (oSubmenu) {
    
                                oSubmenu.show();
                                oSubmenu.setInitialFocus();
                            
                            }
                            else {
    
                                oNextItem.focus();
                            
                            }
                        
                        }
                    
                    }
                
                }
    
    
                Event.preventDefault(oEvent);

                this._stopMouseEventHandlers();

            break;
    
    
            case 37:    // Left arrow
    
                if (oParentItem) {
    
                    oParentMenu = oParentItem.parent;
    
                    if (oParentMenu instanceof YAHOO.widget.MenuBar) {
    
                        oNextItem = 
                            oParentMenu.activeItem.getPreviousEnabledSibling();
    
                        if (oNextItem) {
                        
                            oParentMenu.clearActiveItem();
    
                            oNextItem.cfg.setProperty(_SELECTED, true);
    
                            oSubmenu = oNextItem.cfg.getProperty(_SUBMENU);
    
                            if (oSubmenu) {
                            
                                oSubmenu.show();
                                oSubmenu.setInitialFocus();
                            
                            }
                            else {
    
                                oNextItem.focus();
                            
                            }
                        
                        } 
                    
                    }
                    else {
    
                        this.hide();
    
                        oParentItem.focus();
                    
                    }
    
                }
    
                Event.preventDefault(oEvent);

                this._stopMouseEventHandlers();

            break;        
    
        }


    }


    if (oEvent.keyCode == 27) { // Esc key

        if (this.cfg.getProperty(_POSITION) == _DYNAMIC) {
        
            this.hide();

            if (this.parent) {

                this.parent.focus();
            
            }
            else {
                // Focus the element that previously had focus

                oFocusedEl = this._focusedElement;

                if (oFocusedEl && oFocusedEl.focus) {

                    try {
                        oFocusedEl.focus();
                    }
                    catch(ex) {
                    }

                }
                
            }

        }
        else if (this.activeItem) {

            oSubmenu = this.activeItem.cfg.getProperty(_SUBMENU);

            if (oSubmenu && oSubmenu.cfg.getProperty(_VISIBLE)) {
            
                oSubmenu.hide();
                this.activeItem.focus();
            
            }
            else {

                this.activeItem.blur();
                this.activeItem.cfg.setProperty(_SELECTED, false);
        
            }
        
        }


        Event.preventDefault(oEvent);
    
    }
    
},


/**
* @method _onKeyPress
* @description "keypress" event handler for a Menu instance.
* @protected
* @param {String} p_sType The name of the event that was fired.
* @param {Array} p_aArgs Collection of arguments sent when the event 
* was fired.
*/
_onKeyPress: function (p_sType, p_aArgs) {
    
    var oEvent = p_aArgs[0];


    if (oEvent.keyCode == 40 || oEvent.keyCode == 38) {

        Event.preventDefault(oEvent);

    }

},


/**
* @method _onBlur
* @description "blur" event handler for a Menu instance.
* @protected
* @param {String} p_sType The name of the event that was fired.
* @param {Array} p_aArgs Collection of arguments sent when the event 
* was fired.
*/
_onBlur: function (p_sType, p_aArgs) {
        
    if (this._hasFocus) {
        this._hasFocus = false;
    }

},

/**
* @method _onYChange
* @description "y" event handler for a Menu instance.
* @protected
* @param {String} p_sType The name of the event that was fired.
* @param {Array} p_aArgs Collection of arguments sent when the event 
* was fired.
*/
_onYChange: function (p_sType, p_aArgs) {

    var oParent = this.parent,
        nScrollTop,
        oIFrame,
        nY;


    if (oParent) {

        nScrollTop = oParent.parent.body.scrollTop;


        if (nScrollTop > 0) {
    
            nY = (this.cfg.getProperty(_Y) - nScrollTop);
            
            Dom.setY(this.element, nY);

            oIFrame = this.iframe;            
    

            if (oIFrame) {
    
                Dom.setY(oIFrame, nY);
    
            }
            
            this.cfg.setProperty(_Y, nY, true);
        
        }
    
    }

},


/**
* @method _onScrollTargetMouseOver
* @description "mouseover" event handler for the menu's "header" and "footer" 
* elements.  Used to scroll the body of the menu up and down when the 
* menu's "maxheight" configuration property is set to a value greater than 0.
* @protected
* @param {Event} p_oEvent Object representing the DOM event object passed 
* back by the event utility (YAHOO.util.Event).
* @param {YAHOO.widget.Menu} p_oMenu Object representing the menu that 
* fired the event.
*/
_onScrollTargetMouseOver: function (p_oEvent, p_oMenu) {

    var oBodyScrollTimer = this._bodyScrollTimer;


    if (oBodyScrollTimer) {

        oBodyScrollTimer.cancel();

    }


    this._cancelHideDelay();


    var oTarget = Event.getTarget(p_oEvent),
        oBody = this.body,
        nScrollIncrement = this.cfg.getProperty(_SCROLL_INCREMENT),
        nScrollTarget,
        fnScrollFunction;


    function scrollBodyDown() {

        var nScrollTop = oBody.scrollTop;


        if (nScrollTop < nScrollTarget) {

            oBody.scrollTop = (nScrollTop + nScrollIncrement);

            this._enableScrollHeader();

        }
        else {

            oBody.scrollTop = nScrollTarget;

            this._bodyScrollTimer.cancel();

            this._disableScrollFooter();

        }

    }


    function scrollBodyUp() {

        var nScrollTop = oBody.scrollTop;


        if (nScrollTop > 0) {

            oBody.scrollTop = (nScrollTop - nScrollIncrement);

            this._enableScrollFooter();

        }
        else {

            oBody.scrollTop = 0;

            this._bodyScrollTimer.cancel();

            this._disableScrollHeader();

        }

    }

    
    if (Dom.hasClass(oTarget, _HD)) {

        fnScrollFunction = scrollBodyUp;
    
    }
    else {

        nScrollTarget = oBody.scrollHeight - oBody.offsetHeight;

        fnScrollFunction = scrollBodyDown;
    
    }
    

    this._bodyScrollTimer = Lang.later(10, this, fnScrollFunction, null, true);

},


/**
* @method _onScrollTargetMouseOut
* @description "mouseout" event handler for the menu's "header" and "footer" 
* elements.  Used to stop scrolling the body of the menu up and down when the 
* menu's "maxheight" configuration property is set to a value greater than 0.
* @protected
* @param {Event} p_oEvent Object representing the DOM event object passed 
* back by the event utility (YAHOO.util.Event).
* @param {YAHOO.widget.Menu} p_oMenu Object representing the menu that 
* fired the event.
*/
_onScrollTargetMouseOut: function (p_oEvent, p_oMenu) {

    var oBodyScrollTimer = this._bodyScrollTimer;

    if (oBodyScrollTimer) {

        oBodyScrollTimer.cancel();

    }
    
    this._cancelHideDelay();

},



// Private methods


/**
* @method _onInit
* @description "init" event handler for the menu.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onInit: function (p_sType, p_aArgs) {

    this.cfg.subscribeToConfigEvent(_VISIBLE, this._onVisibleChange);

    var bRootMenu = !this.parent,
        bLazyLoad = this.lazyLoad;


    /*
        Automatically initialize a menu's subtree if:

        1) This is the root menu and lazyload is off
        
        2) This is the root menu, lazyload is on, but the menu is 
           already visible

        3) This menu is a submenu and lazyload is off
    */



    if (((bRootMenu && !bLazyLoad) || 
        (bRootMenu && (this.cfg.getProperty(_VISIBLE) || 
        this.cfg.getProperty(_POSITION) == _STATIC)) || 
        (!bRootMenu && !bLazyLoad)) && this.getItemGroups().length === 0) {

        if (this.srcElement) {

            this._initSubTree();
        
        }


        if (this.itemData) {

            this.addItems(this.itemData);

        }
    
    }
    else if (bLazyLoad) {

        this.cfg.fireQueue();
    
    }

},


/**
* @method _onBeforeRender
* @description "beforerender" event handler for the menu.  Appends all of the 
* <code>&#60;ul&#62;</code>, <code>&#60;li&#62;</code> and their accompanying 
* title elements to the body element of the menu.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onBeforeRender: function (p_sType, p_aArgs) {

    var oEl = this.element,
        nListElements = this._aListElements.length,
        bFirstList = true,
        i = 0,
        oUL,
        oGroupTitle;

    if (nListElements > 0) {

        do {

            oUL = this._aListElements[i];

            if (oUL) {

                if (bFirstList) {
        
                    Dom.addClass(oUL, _FIRST_OF_TYPE);
                    bFirstList = false;
        
                }


                if (!Dom.isAncestor(oEl, oUL)) {

                    this.appendToBody(oUL);

                }


                oGroupTitle = this._aGroupTitleElements[i];

                if (oGroupTitle) {

                    if (!Dom.isAncestor(oEl, oGroupTitle)) {

                        oUL.parentNode.insertBefore(oGroupTitle, oUL);

                    }


                    Dom.addClass(oUL, _HAS_TITLE);

                }

            }

            i++;

        }
        while (i < nListElements);

    }

},


/**
* @method _onRender
* @description "render" event handler for the menu.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onRender: function (p_sType, p_aArgs) {

    if (this.cfg.getProperty(_POSITION) == _DYNAMIC) { 

        if (!this.cfg.getProperty(_VISIBLE)) {

            this.positionOffScreen();

        }
    
    }

},





/**
* @method _onBeforeShow
* @description "beforeshow" event handler for the menu.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onBeforeShow: function (p_sType, p_aArgs) {

    var nOptions,
        n,
        oSrcElement,
        oContainer = this.cfg.getProperty(_CONTAINER);


    if (this.lazyLoad && this.getItemGroups().length === 0) {

        if (this.srcElement) {
        
            this._initSubTree();

        }


        if (this.itemData) {

            if (this.parent && this.parent.parent && 
                this.parent.parent.srcElement && 
                this.parent.parent.srcElement.tagName.toUpperCase() == 
                _SELECT) {

                nOptions = this.itemData.length;
    
                for(n=0; n<nOptions; n++) {

                    if (this.itemData[n].tagName) {

                        this.addItem((new this.ITEM_TYPE(this.itemData[n])));
    
                    }
    
                }
            
            }
            else {

                this.addItems(this.itemData);
            
            }
        
        }


        oSrcElement = this.srcElement;

        if (oSrcElement) {

            if (oSrcElement.tagName.toUpperCase() == _SELECT) {

                if (Dom.inDocument(oSrcElement)) {

                    this.render(oSrcElement.parentNode);
                
                }
                else {
                
                    this.render(oContainer);
                
                }

            }
            else {

                this.render();

            }

        }
        else {

            if (this.parent) {

                this.render(this.parent.element);     

            }
            else {

                this.render(oContainer);

            }                

        }

    }



    var oParent = this.parent,
        aAlignment;


    if (!oParent && this.cfg.getProperty(_POSITION) == _DYNAMIC) {

        this.cfg.refireEvent(_XY);
   
    }


    if (oParent) {

        aAlignment = oParent.parent.cfg.getProperty(_SUBMENU_ALIGNMENT);
        
        this.cfg.setProperty(_CONTEXT, [oParent.element, aAlignment[0], aAlignment[1]]);
        this.align();
    
    }

},


getConstrainedY: function (y) {

    var oMenu = this,
    
        aContext = oMenu.cfg.getProperty(_CONTEXT),
        nInitialMaxHeight = oMenu.cfg.getProperty(_MAX_HEIGHT),

        nMaxHeight,

        oOverlapPositions = {

            "trbr": true,
            "tlbl": true,
            "bltl": true,
            "brtr": true

        },

        bPotentialContextOverlap = (aContext && oOverlapPositions[aContext[1] + aContext[2]]),
    
        oMenuEl = oMenu.element,
        nMenuOffsetHeight = oMenuEl.offsetHeight,
    
        nViewportOffset = Overlay.VIEWPORT_OFFSET,
        viewPortHeight = Dom.getViewportHeight(),
        scrollY = Dom.getDocumentScrollTop(),

        bCanConstrain = 
            (oMenu.cfg.getProperty(_MIN_SCROLL_HEIGHT) + nViewportOffset < viewPortHeight),

        nAvailableHeight,

        oContextEl,
        nContextElY,
        nContextElHeight,

        bFlipped = false,

        nTopRegionHeight,
        nBottomRegionHeight,

        topConstraint = scrollY + nViewportOffset,
        bottomConstraint = scrollY + viewPortHeight - nMenuOffsetHeight - nViewportOffset,

        yNew = y;
        

    var flipVertical = function () {

        var nNewY;
    
        // The Menu is below the context element, flip it above
        if ((oMenu.cfg.getProperty(_Y) - scrollY) > nContextElY) { 
            nNewY = (nContextElY - nMenuOffsetHeight);
        }
        else {	// The Menu is above the context element, flip it below
            nNewY = (nContextElY + nContextElHeight);
        }

        oMenu.cfg.setProperty(_Y, (nNewY + scrollY), true);
        
        return nNewY;
    
    };


    /*
         Uses the context element's position to calculate the availble height 
         above and below it to display its corresponding Menu.
    */

    var getDisplayRegionHeight = function () {

        // The Menu is below the context element
        if ((oMenu.cfg.getProperty(_Y) - scrollY) > nContextElY) {
            return (nBottomRegionHeight - nViewportOffset);				
        }
        else {	// The Menu is above the context element
            return (nTopRegionHeight - nViewportOffset);				
        }

    };


    /*
        Sets the Menu's "y" configuration property to the correct value based on its
        current orientation.
    */ 

    var alignY = function () {

        var nNewY;

        if ((oMenu.cfg.getProperty(_Y) - scrollY) > nContextElY) { 
            nNewY = (nContextElY + nContextElHeight);
        }
        else {	
            nNewY = (nContextElY - oMenuEl.offsetHeight);
        }

        oMenu.cfg.setProperty(_Y, (nNewY + scrollY), true);
    
    };


    //	Resets the maxheight of the Menu to the value set by the user

    var resetMaxHeight = function () {

        oMenu._setScrollHeight(this.cfg.getProperty(_MAX_HEIGHT));

        oMenu.hideEvent.unsubscribe(resetMaxHeight);
    
    };


    /*
        Trys to place the Menu in the best possible position (either above or 
        below its corresponding context element).
    */

    var setVerticalPosition = function () {

        var nDisplayRegionHeight = getDisplayRegionHeight(),
            bMenuHasItems = (oMenu.getItems().length > 0),
            nMenuMinScrollHeight,
            fnReturnVal;


        if (nMenuOffsetHeight > nDisplayRegionHeight) {

            nMenuMinScrollHeight = 
                bMenuHasItems ? oMenu.cfg.getProperty(_MIN_SCROLL_HEIGHT) : nMenuOffsetHeight;


            if ((nDisplayRegionHeight > nMenuMinScrollHeight) && bMenuHasItems) {
                nMaxHeight = nDisplayRegionHeight;
            }
            else {
                nMaxHeight = nInitialMaxHeight;
            }


            oMenu._setScrollHeight(nMaxHeight);
            oMenu.hideEvent.subscribe(resetMaxHeight);
            

            // Re-align the Menu since its height has just changed
            // as a result of the setting of the maxheight property.

            alignY();
            

            if (nDisplayRegionHeight < nMenuMinScrollHeight) {

                if (bFlipped) {
    
                    /*
                         All possible positions and values for the "maxheight" 
                         configuration property have been tried, but none were 
                         successful, so fall back to the original size and position.
                    */

                    flipVertical();
                    
                }
                else {
    
                    flipVertical();

                    bFlipped = true;
    
                    fnReturnVal = setVerticalPosition();
    
                }
                
            }
        
        }
        else if (nMaxHeight && (nMaxHeight !== nInitialMaxHeight)) {
        
            oMenu._setScrollHeight(nInitialMaxHeight);
            oMenu.hideEvent.subscribe(resetMaxHeight);

            // Re-align the Menu since its height has just changed
            // as a result of the setting of the maxheight property.

            alignY();
        
        }

        return fnReturnVal;

    };


    // Determine if the current value for the Menu's "y" configuration property will
    // result in the Menu being positioned outside the boundaries of the viewport

    if (y < topConstraint || y  > bottomConstraint) {

        // The current value for the Menu's "y" configuration property WILL
        // result in the Menu being positioned outside the boundaries of the viewport

        if (bCanConstrain) {

            if (oMenu.cfg.getProperty(_PREVENT_CONTEXT_OVERLAP) && bPotentialContextOverlap) {
        
                //	SOLUTION #1:
                //	If the "preventcontextoverlap" configuration property is set to "true", 
                //	try to flip and/or scroll the Menu to both keep it inside the boundaries of the 
                //	viewport AND from overlaping its context element (MenuItem or MenuBarItem).

                oContextEl = aContext[0];
                nContextElHeight = oContextEl.offsetHeight;
                nContextElY = (Dom.getY(oContextEl) - scrollY);
    
                nTopRegionHeight = nContextElY;
                nBottomRegionHeight = (viewPortHeight - (nContextElY + nContextElHeight));
    
                setVerticalPosition();
                
                yNew = oMenu.cfg.getProperty(_Y);
        
            }
            else if (!(oMenu instanceof YAHOO.widget.MenuBar) && 
                nMenuOffsetHeight >= viewPortHeight) {

                //	SOLUTION #2:
                //	If the Menu exceeds the height of the viewport, introduce scroll bars
                //	to keep the Menu inside the boundaries of the viewport

                nAvailableHeight = (viewPortHeight - (nViewportOffset * 2));
        
                if (nAvailableHeight > oMenu.cfg.getProperty(_MIN_SCROLL_HEIGHT)) {
        
                    oMenu._setScrollHeight(nAvailableHeight);
                    oMenu.hideEvent.subscribe(resetMaxHeight);
        
                    alignY();
                    
                    yNew = oMenu.cfg.getProperty(_Y);
                
                }
        
            }	
            else {

                //	SOLUTION #3:
            
                if (y < topConstraint) {
                    yNew  = topConstraint;
                } else if (y  > bottomConstraint) {
                    yNew  = bottomConstraint;
                }				
            
            }

        }
        else {
            //	The "y" configuration property cannot be set to a value that will keep
            //	entire Menu inside the boundary of the viewport.  Therefore, set  
            //	the "y" configuration property to scrollY to keep as much of the 
            //	Menu inside the viewport as possible.
            yNew = nViewportOffset + scrollY;
        }	

    }

    return yNew;

},


/**
* @method _onHide
* @description "hide" event handler for the menu.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onHide: function (p_sType, p_aArgs) {

    if (this.cfg.getProperty(_POSITION) === _DYNAMIC) {
    
        this.positionOffScreen();
    
    }

},


/**
* @method _onShow
* @description "show" event handler for the menu.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onShow: function (p_sType, p_aArgs) {

    var oParent = this.parent,
        oParentMenu,
        oElement,
        nOffsetWidth,
        sWidth;        


    function disableAutoSubmenuDisplay(p_oEvent) {

        var oTarget;

        if (p_oEvent.type == _MOUSEDOWN || (p_oEvent.type == _KEYDOWN && p_oEvent.keyCode == 27)) {

            /*  
                Set the "autosubmenudisplay" to "false" if the user
                clicks outside the menu bar.
            */

            oTarget = Event.getTarget(p_oEvent);

            if (oTarget != oParentMenu.element || !Dom.isAncestor(oParentMenu.element, oTarget)) {

                oParentMenu.cfg.setProperty(_AUTO_SUBMENU_DISPLAY, false);

                Event.removeListener(document, _MOUSEDOWN, disableAutoSubmenuDisplay);
                Event.removeListener(document, _KEYDOWN, disableAutoSubmenuDisplay);

            }
        
        }

    }


    function onSubmenuHide(p_sType, p_aArgs, p_sWidth) {
    
        this.cfg.setProperty(_WIDTH, _EMPTY_STRING);
        this.hideEvent.unsubscribe(onSubmenuHide, p_sWidth);
    
    }


    if (oParent) {

        oParentMenu = oParent.parent;


        if (!oParentMenu.cfg.getProperty(_AUTO_SUBMENU_DISPLAY) && 
            (oParentMenu instanceof YAHOO.widget.MenuBar || 
            oParentMenu.cfg.getProperty(_POSITION) == _STATIC)) {

            oParentMenu.cfg.setProperty(_AUTO_SUBMENU_DISPLAY, true);

            Event.on(document, _MOUSEDOWN, disableAutoSubmenuDisplay);                             
            Event.on(document, _KEYDOWN, disableAutoSubmenuDisplay);

        }


        //	The following fixes an issue with the selected state of a MenuItem 
        //	not rendering correctly when a submenu is aligned to the left of
        //	its parent Menu instance.

        if ((this.cfg.getProperty("x") < oParentMenu.cfg.getProperty("x")) && 
            (UA.gecko && UA.gecko < 1.9) && !this.cfg.getProperty(_WIDTH)) {

            oElement = this.element;
            nOffsetWidth = oElement.offsetWidth;
            
            /*
                Measuring the difference of the offsetWidth before and after
                setting the "width" style attribute allows us to compute the 
                about of padding and borders applied to the element, which in 
                turn allows us to set the "width" property correctly.
            */
            
            oElement.style.width = nOffsetWidth + _PX;
            
            sWidth = (nOffsetWidth - (oElement.offsetWidth - nOffsetWidth)) + _PX;
            
            this.cfg.setProperty(_WIDTH, sWidth);
        
            this.hideEvent.subscribe(onSubmenuHide, sWidth);
        
        }

    }


    /*
        Dynamically positioned, root Menus focus themselves when visible, and 
        will then, when hidden, restore focus to the UI control that had focus 
        before the Menu was made visible.
    */ 

    if (this === this.getRoot() && this.cfg.getProperty(_POSITION) === _DYNAMIC) {

        this._focusedElement = oFocusedElement;
        
        this.focus();
    
    }


},


/**
* @method _onBeforeHide
* @description "beforehide" event handler for the menu.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onBeforeHide: function (p_sType, p_aArgs) {

    var oActiveItem = this.activeItem,
        oRoot = this.getRoot(),
        oConfig,
        oSubmenu;


    if (oActiveItem) {

        oConfig = oActiveItem.cfg;

        oConfig.setProperty(_SELECTED, false);

        oSubmenu = oConfig.getProperty(_SUBMENU);

        if (oSubmenu) {

            oSubmenu.hide();

        }

    }


    /*
        Focus can get lost in IE when the mouse is moving from a submenu back to its parent Menu.  
        For this reason, it is necessary to maintain the focused state in a private property 
        so that the _onMouseOver event handler is able to determined whether or not to set focus
        to MenuItems as the user is moving the mouse.
    */ 

    if (UA.ie && this.cfg.getProperty(_POSITION) === _DYNAMIC && this.parent) {

        oRoot._hasFocus = this.hasFocus();
    
    }


    if (oRoot == this) {

        oRoot.blur();
    
    }

},


/**
* @method _onParentMenuConfigChange
* @description "configchange" event handler for a submenu.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
* @param {YAHOO.widget.Menu} p_oSubmenu Object representing the submenu that 
* subscribed to the event.
*/
_onParentMenuConfigChange: function (p_sType, p_aArgs, p_oSubmenu) {
    
    var sPropertyName = p_aArgs[0][0],
        oPropertyValue = p_aArgs[0][1];

    switch(sPropertyName) {

        case _IFRAME:
        case _CONSTRAIN_TO_VIEWPORT:
        case _HIDE_DELAY:
        case _SHOW_DELAY:
        case _SUBMENU_HIDE_DELAY:
        case _CLICK_TO_HIDE:
        case _EFFECT:
        case _CLASSNAME:
        case _SCROLL_INCREMENT:
        case _MAX_HEIGHT:
        case _MIN_SCROLL_HEIGHT:
        case _MONITOR_RESIZE:
        case _SHADOW:
        case _PREVENT_CONTEXT_OVERLAP:
        case _KEEP_OPEN:

            p_oSubmenu.cfg.setProperty(sPropertyName, oPropertyValue);
                
        break;
        
        case _SUBMENU_ALIGNMENT:

            if (!(this.parent.parent instanceof YAHOO.widget.MenuBar)) {
        
                p_oSubmenu.cfg.setProperty(sPropertyName, oPropertyValue);
        
            }
        
        break;
        
    }
    
},


/**
* @method _onParentMenuRender
* @description "render" event handler for a submenu.  Renders a  
* submenu in response to the firing of its parent's "render" event.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
* @param {YAHOO.widget.Menu} p_oSubmenu Object representing the submenu that 
* subscribed to the event.
*/
_onParentMenuRender: function (p_sType, p_aArgs, p_oSubmenu) {

    var oParentMenu = p_oSubmenu.parent.parent,
        oParentCfg = oParentMenu.cfg,

        oConfig = {

            constraintoviewport: oParentCfg.getProperty(_CONSTRAIN_TO_VIEWPORT),

            xy: [0,0],

            clicktohide: oParentCfg.getProperty(_CLICK_TO_HIDE),
                
            effect: oParentCfg.getProperty(_EFFECT),

            showdelay: oParentCfg.getProperty(_SHOW_DELAY),
            
            hidedelay: oParentCfg.getProperty(_HIDE_DELAY),

            submenuhidedelay: oParentCfg.getProperty(_SUBMENU_HIDE_DELAY),

            classname: oParentCfg.getProperty(_CLASSNAME),
            
            scrollincrement: oParentCfg.getProperty(_SCROLL_INCREMENT),
            
            maxheight: oParentCfg.getProperty(_MAX_HEIGHT),

            minscrollheight: oParentCfg.getProperty(_MIN_SCROLL_HEIGHT),
            
            iframe: oParentCfg.getProperty(_IFRAME),
            
            shadow: oParentCfg.getProperty(_SHADOW),

            preventcontextoverlap: oParentCfg.getProperty(_PREVENT_CONTEXT_OVERLAP),
            
            monitorresize: oParentCfg.getProperty(_MONITOR_RESIZE),

            keepopen: oParentCfg.getProperty(_KEEP_OPEN)

        },
        
        oLI;


    
    if (!(oParentMenu instanceof YAHOO.widget.MenuBar)) {

        oConfig[_SUBMENU_ALIGNMENT] = oParentCfg.getProperty(_SUBMENU_ALIGNMENT);

    }


    p_oSubmenu.cfg.applyConfig(oConfig);


    if (!this.lazyLoad) {

        oLI = this.parent.element;

        if (this.element.parentNode == oLI) {
    
            this.render();
    
        }
        else {

            this.render(oLI);
    
        }

    }
    
},


/**
* @method _onMenuItemDestroy
* @description "destroy" event handler for the menu's items.
* @private
* @param {String} p_sType String representing the name of the event 
* that was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
* @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item 
* that fired the event.
*/
_onMenuItemDestroy: function (p_sType, p_aArgs, p_oItem) {

    this._removeItemFromGroupByValue(p_oItem.groupIndex, p_oItem);

},


/**
* @method _onMenuItemConfigChange
* @description "configchange" event handler for the menu's items.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
* @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item 
* that fired the event.
*/
_onMenuItemConfigChange: function (p_sType, p_aArgs, p_oItem) {

    var sPropertyName = p_aArgs[0][0],
        oPropertyValue = p_aArgs[0][1],
        oSubmenu;


    switch(sPropertyName) {

        case _SELECTED:

            if (oPropertyValue === true) {

                this.activeItem = p_oItem;
            
            }

        break;

        case _SUBMENU:

            oSubmenu = p_aArgs[0][1];

            if (oSubmenu) {

                this._configureSubmenu(p_oItem);

            }

        break;

    }

},



// Public event handlers for configuration properties


/**
* @method configVisible
* @description Event handler for when the "visible" configuration property 
* the menu changes.
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
* @param {YAHOO.widget.Menu} p_oMenu Object representing the menu that 
* fired the event.
*/
configVisible: function (p_sType, p_aArgs, p_oMenu) {

    var bVisible,
        sDisplay;

    if (this.cfg.getProperty(_POSITION) == _DYNAMIC) {

        Menu.superclass.configVisible.call(this, p_sType, p_aArgs, p_oMenu);

    }
    else {

        bVisible = p_aArgs[0];
        sDisplay = Dom.getStyle(this.element, _DISPLAY);

        Dom.setStyle(this.element, _VISIBILITY, _VISIBLE);

        if (bVisible) {

            if (sDisplay != _BLOCK) {
                this.beforeShowEvent.fire();
                Dom.setStyle(this.element, _DISPLAY, _BLOCK);
                this.showEvent.fire();
            }
        
        }
        else {

            if (sDisplay == _BLOCK) {
                this.beforeHideEvent.fire();
                Dom.setStyle(this.element, _DISPLAY, _NONE);
                this.hideEvent.fire();
            }
        
        }

    }

},


/**
* @method configPosition
* @description Event handler for when the "position" configuration property 
* of the menu changes.
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
* @param {YAHOO.widget.Menu} p_oMenu Object representing the menu that 
* fired the event.
*/
configPosition: function (p_sType, p_aArgs, p_oMenu) {

    var oElement = this.element,
        sCSSPosition = p_aArgs[0] == _STATIC ? _STATIC : _ABSOLUTE,
        oCfg = this.cfg,
        nZIndex;


    Dom.setStyle(oElement, _POSITION, sCSSPosition);


    if (sCSSPosition == _STATIC) {

        // Statically positioned menus are visible by default
        
        Dom.setStyle(oElement, _DISPLAY, _BLOCK);

        oCfg.setProperty(_VISIBLE, true);

    }
    else {

        /*
            Even though the "visible" property is queued to 
            "false" by default, we need to set the "visibility" property to 
            "hidden" since Overlay's "configVisible" implementation checks the 
            element's "visibility" style property before deciding whether 
            or not to show an Overlay instance.
        */

        Dom.setStyle(oElement, _VISIBILITY, _HIDDEN);
    
    }


     if (sCSSPosition == _ABSOLUTE) {
         nZIndex = oCfg.getProperty(_ZINDEX);

         if (!nZIndex || nZIndex === 0) {
             oCfg.setProperty(_ZINDEX, 1);
         }

     }

},


/**
* @method configIframe
* @description Event handler for when the "iframe" configuration property of 
* the menu changes.
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
* @param {YAHOO.widget.Menu} p_oMenu Object representing the menu that 
* fired the event.
*/
configIframe: function (p_sType, p_aArgs, p_oMenu) {    

    if (this.cfg.getProperty(_POSITION) == _DYNAMIC) {

        Menu.superclass.configIframe.call(this, p_sType, p_aArgs, p_oMenu);

    }

},


/**
* @method configHideDelay
* @description Event handler for when the "hidedelay" configuration property 
* of the menu changes.
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
* @param {YAHOO.widget.Menu} p_oMenu Object representing the menu that 
* fired the event.
*/
configHideDelay: function (p_sType, p_aArgs, p_oMenu) {

    var nHideDelay = p_aArgs[0];

    this._useHideDelay = (nHideDelay > 0);

},


/**
* @method configContainer
* @description Event handler for when the "container" configuration property 
* of the menu changes.
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
* @param {YAHOO.widget.Menu} p_oMenu Object representing the menu that 
* fired the event.
*/
configContainer: function (p_sType, p_aArgs, p_oMenu) {

    var oElement = p_aArgs[0];

    if (Lang.isString(oElement)) {

        this.cfg.setProperty(_CONTAINER, Dom.get(oElement), true);

    }

},


/**
* @method _clearSetWidthFlag
* @description Change event listener for the "width" configuration property.  This listener is 
* added when a Menu's "width" configuration property is set by the "_setScrollHeight" method, and 
* is used to set the "_widthSetForScroll" property to "false" if the "width" configuration property 
* is changed after it was set by the "_setScrollHeight" method.  If the "_widthSetForScroll" 
* property is set to "false", and the "_setScrollHeight" method is in the process of tearing down 
* scrolling functionality, it will maintain the Menu's new width rather than reseting it.
* @private
*/
_clearSetWidthFlag: function () {

    this._widthSetForScroll = false;
    
    this.cfg.unsubscribeFromConfigEvent(_WIDTH, this._clearSetWidthFlag);

},

/**
 * @method _subscribeScrollHandlers
 * @param {HTMLElement} oHeader The scroll header element
 * @param {HTMLElement} oFooter The scroll footer element
 */
_subscribeScrollHandlers : function(oHeader, oFooter) {
    var fnMouseOver = this._onScrollTargetMouseOver;
    var fnMouseOut = this._onScrollTargetMouseOut;

    Event.on(oHeader, _MOUSEOVER, fnMouseOver, this, true);
    Event.on(oHeader, _MOUSEOUT, fnMouseOut, this, true);
    Event.on(oFooter, _MOUSEOVER, fnMouseOver, this, true);
    Event.on(oFooter, _MOUSEOUT, fnMouseOut, this, true);
},

/**
 * @method _unsubscribeScrollHandlers 
 * @param {HTMLElement} oHeader The scroll header element
 * @param {HTMLElement} oFooter The scroll footer element
 */
_unsubscribeScrollHandlers : function(oHeader, oFooter) {
    var fnMouseOver = this._onScrollTargetMouseOver;
    var fnMouseOut = this._onScrollTargetMouseOut;
    
    Event.removeListener(oHeader, _MOUSEOVER, fnMouseOver);
    Event.removeListener(oHeader, _MOUSEOUT, fnMouseOut);
    Event.removeListener(oFooter, _MOUSEOVER, fnMouseOver);
    Event.removeListener(oFooter, _MOUSEOUT, fnMouseOut);
},

/**
* @method _setScrollHeight
* @description 
* @param {String} p_nScrollHeight Number representing the scrolling height of the Menu.
* @private
*/
_setScrollHeight: function (p_nScrollHeight) {

    var nScrollHeight = p_nScrollHeight,
        bRefireIFrameAndShadow = false,
        bSetWidth = false,
        oElement,
        oBody,
        oHeader,
        oFooter,
        nMinScrollHeight,
        nHeight,
        nOffsetWidth,
        sWidth;

    if (this.getItems().length > 0) {

        oElement = this.element;
        oBody = this.body;
        oHeader = this.header;
        oFooter = this.footer;
        nMinScrollHeight = this.cfg.getProperty(_MIN_SCROLL_HEIGHT);

        if (nScrollHeight > 0 && nScrollHeight < nMinScrollHeight) {
            nScrollHeight = nMinScrollHeight;
        }

        Dom.setStyle(oBody, _HEIGHT, _EMPTY_STRING);
        Dom.removeClass(oBody, _YUI_MENU_BODY_SCROLLED);
        oBody.scrollTop = 0;

        //	Need to set a width for the Menu to fix the following problems in 
        //	Firefox 2 and IE:

        //	#1) Scrolled Menus will render at 1px wide in Firefox 2

        //	#2) There is a bug in gecko-based browsers where an element whose 
        //	"position" property is set to "absolute" and "overflow" property is 
        //	set to "hidden" will not render at the correct width when its 
        //	offsetParent's "position" property is also set to "absolute."  It is 
        //	possible to work around this bug by specifying a value for the width 
        //	property in addition to overflow.

        //	#3) In IE it is necessary to give the Menu a width before the 
        //	scrollbars are rendered to prevent the Menu from rendering with a 
        //	width that is 100% of the browser viewport.

        bSetWidth = ((UA.gecko && UA.gecko < 1.9) || UA.ie);

        if (nScrollHeight > 0 && bSetWidth && !this.cfg.getProperty(_WIDTH)) {

            nOffsetWidth = oElement.offsetWidth;
    
            /*
                Measuring the difference of the offsetWidth before and after
                setting the "width" style attribute allows us to compute the 
                about of padding and borders applied to the element, which in 
                turn allows us to set the "width" property correctly.
            */
            
            oElement.style.width = nOffsetWidth + _PX;
    
            sWidth = (nOffsetWidth - (oElement.offsetWidth - nOffsetWidth)) + _PX;


            this.cfg.unsubscribeFromConfigEvent(_WIDTH, this._clearSetWidthFlag);

            YAHOO.log("Setting the \"width\" configuration property to " + sWidth + " for srolling.", 
                "info", this.toString());

            this.cfg.setProperty(_WIDTH, sWidth);


            /*
                Set a flag (_widthSetForScroll) to maintain some history regarding how the 
                "width" configuration property was set.  If the "width" configuration property 
                is set by something other than the "_setScrollHeight" method, it will be 
                necessary to maintain that new value and not clear the width if scrolling 
                is turned off.
            */

            this._widthSetForScroll = true;

            this.cfg.subscribeToConfigEvent(_WIDTH, this._clearSetWidthFlag);
    
        }


        if (nScrollHeight > 0 && (!oHeader && !oFooter)) {

            YAHOO.log("Creating header and footer for scrolling.", "info", this.toString());

            this.setHeader(_NON_BREAKING_SPACE);
            this.setFooter(_NON_BREAKING_SPACE);

            oHeader = this.header;
            oFooter = this.footer;

            Dom.addClass(oHeader, _TOP_SCROLLBAR);
            Dom.addClass(oFooter, _BOTTOM_SCROLLBAR);

            oElement.insertBefore(oHeader, oBody);
            oElement.appendChild(oFooter);
        
        }

        nHeight = nScrollHeight;

        if (oHeader && oFooter) {
            nHeight = (nHeight - (oHeader.offsetHeight + oFooter.offsetHeight));
        }
    
    
        if ((nHeight > 0) && (oBody.offsetHeight > nScrollHeight)) {

            YAHOO.log("Setting up styles and event handlers for scrolling.", 
                "info", this.toString());
    
            Dom.addClass(oBody, _YUI_MENU_BODY_SCROLLED);
            Dom.setStyle(oBody, _HEIGHT, (nHeight + _PX));

            if (!this._hasScrollEventHandlers) {
                this._subscribeScrollHandlers(oHeader, oFooter);
                this._hasScrollEventHandlers = true;
            }
    
            this._disableScrollHeader();
            this._enableScrollFooter();
            
            bRefireIFrameAndShadow = true;			
    
        }
        else if (oHeader && oFooter) {

            YAHOO.log("Removing styles and event handlers for scrolling.", "info", this.toString());
    

            /*
                Only clear the the "width" configuration property if it was set the 
                "_setScrollHeight" method and wasn't changed by some other means after it was set.
            */	
    
            if (this._widthSetForScroll) {
    
                YAHOO.log("Clearing width used for scrolling.", "info", this.toString());

                this._widthSetForScroll = false;

                this.cfg.unsubscribeFromConfigEvent(_WIDTH, this._clearSetWidthFlag);
    
                this.cfg.setProperty(_WIDTH, _EMPTY_STRING);
            
            }
    
    
            this._enableScrollHeader();
            this._enableScrollFooter();
    
            if (this._hasScrollEventHandlers) {
                this._unsubscribeScrollHandlers(oHeader, oFooter);    
                this._hasScrollEventHandlers = false;
            }

            oElement.removeChild(oHeader);
            oElement.removeChild(oFooter);
    
            this.header = null;
            this.footer = null;
            
            bRefireIFrameAndShadow = true;
        
        }


        if (bRefireIFrameAndShadow) {
    
            this.cfg.refireEvent(_IFRAME);
            this.cfg.refireEvent(_SHADOW);
        
        }
    
    }

},


/**
* @method _setMaxHeight
* @description "renderEvent" handler used to defer the setting of the 
* "maxheight" configuration property until the menu is rendered in lazy 
* load scenarios.
* @param {String} p_sType The name of the event that was fired.
* @param {Array} p_aArgs Collection of arguments sent when the event 
* was fired.
* @param {Number} p_nMaxHeight Number representing the value to set for the 
* "maxheight" configuration property.
* @private
*/
_setMaxHeight: function (p_sType, p_aArgs, p_nMaxHeight) {

    this._setScrollHeight(p_nMaxHeight);
    this.renderEvent.unsubscribe(this._setMaxHeight);

},


/**
* @method configMaxHeight
* @description Event handler for when the "maxheight" configuration property of 
* a Menu changes.
* @param {String} p_sType The name of the event that was fired.
* @param {Array} p_aArgs Collection of arguments sent when the event 
* was fired.
* @param {YAHOO.widget.Menu} p_oMenu The Menu instance fired
* the event.
*/
configMaxHeight: function (p_sType, p_aArgs, p_oMenu) {

    var nMaxHeight = p_aArgs[0];

    if (this.lazyLoad && !this.body && nMaxHeight > 0) {
    
        this.renderEvent.subscribe(this._setMaxHeight, nMaxHeight, this);

    }
    else {

        this._setScrollHeight(nMaxHeight);
    
    }

},


/**
* @method configClassName
* @description Event handler for when the "classname" configuration property of 
* a menu changes.
* @param {String} p_sType The name of the event that was fired.
* @param {Array} p_aArgs Collection of arguments sent when the event was fired.
* @param {YAHOO.widget.Menu} p_oMenu The Menu instance fired the event.
*/
configClassName: function (p_sType, p_aArgs, p_oMenu) {

    var sClassName = p_aArgs[0];

    if (this._sClassName) {

        Dom.removeClass(this.element, this._sClassName);

    }

    Dom.addClass(this.element, sClassName);
    this._sClassName = sClassName;

},


/**
* @method _onItemAdded
* @description "itemadded" event handler for a Menu instance.
* @private
* @param {String} p_sType The name of the event that was fired.
* @param {Array} p_aArgs Collection of arguments sent when the event 
* was fired.
*/
_onItemAdded: function (p_sType, p_aArgs) {

    var oItem = p_aArgs[0];
    
    if (oItem) {

        oItem.cfg.setProperty(_DISABLED, true);
    
    }

},


/**
* @method configDisabled
* @description Event handler for when the "disabled" configuration property of 
* a menu changes.
* @param {String} p_sType The name of the event that was fired.
* @param {Array} p_aArgs Collection of arguments sent when the event was fired.
* @param {YAHOO.widget.Menu} p_oMenu The Menu instance fired the event.
*/
configDisabled: function (p_sType, p_aArgs, p_oMenu) {

    var bDisabled = p_aArgs[0],
        aItems = this.getItems(),
        nItems,
        i;

    if (Lang.isArray(aItems)) {

        nItems = aItems.length;
    
        if (nItems > 0) {
        
            i = nItems - 1;
    
            do {
    
                aItems[i].cfg.setProperty(_DISABLED, bDisabled);
            
            }
            while (i--);
        
        }


        if (bDisabled) {

            this.clearActiveItem(true);

            Dom.addClass(this.element, _DISABLED);

            this.itemAddedEvent.subscribe(this._onItemAdded);

        }
        else {

            Dom.removeClass(this.element, _DISABLED);

            this.itemAddedEvent.unsubscribe(this._onItemAdded);

        }
        
    }

},

/**
 * Resizes the shadow to match the container bounding element
 * 
 * @method _sizeShadow
 * @protected
 */
_sizeShadow : function () {

    var oElement = this.element,
        oShadow = this._shadow;

    if (oShadow && oElement) {
        // Clear the previous width
        if (oShadow.style.width && oShadow.style.height) {
            oShadow.style.width = _EMPTY_STRING;
            oShadow.style.height = _EMPTY_STRING;
        }

        oShadow.style.width = (oElement.offsetWidth + 6) + _PX;
        oShadow.style.height = (oElement.offsetHeight + 1) + _PX;
    }
},

/**
 * Replaces the shadow element in the DOM with the current shadow element (this._shadow)
 * 
 * @method _replaceShadow
 * @protected 
 */
_replaceShadow : function () {
    this.element.appendChild(this._shadow);
},

/**
 * Adds the classname marker for a visible shadow, to the shadow element
 * 
 * @method _addShadowVisibleClass
 * @protected
 */
_addShadowVisibleClass : function () {
    Dom.addClass(this._shadow, _YUI_MENU_SHADOW_VISIBLE);
},

/**
 * Removes the classname marker for a visible shadow, from the shadow element
 * 
 * @method _removeShadowVisibleClass
 * @protected
 */
_removeShadowVisibleClass : function () {
    Dom.removeClass(this._shadow, _YUI_MENU_SHADOW_VISIBLE);
},

/**
 * Removes the shadow element from the DOM, and unsubscribes all the listeners used to keep it in sync. Used
 * to handle setting the shadow to false.
 * 
 * @method _removeShadow
 * @protected
 */
_removeShadow : function() {

    var p = (this._shadow && this._shadow.parentNode);

    if (p) {
        p.removeChild(this._shadow);
    }

    this.beforeShowEvent.unsubscribe(this._addShadowVisibleClass);
    this.beforeHideEvent.unsubscribe(this._removeShadowVisibleClass);

    this.cfg.unsubscribeFromConfigEvent(_WIDTH, this._sizeShadow);
    this.cfg.unsubscribeFromConfigEvent(_HEIGHT, this._sizeShadow);
    this.cfg.unsubscribeFromConfigEvent(_MAX_HEIGHT, this._sizeShadow);
    this.cfg.unsubscribeFromConfigEvent(_MAX_HEIGHT, this._replaceShadow);

    this.changeContentEvent.unsubscribe(this._sizeShadow);

    Module.textResizeEvent.unsubscribe(this._sizeShadow);
},

/**
 * Used to create the shadow element, add it to the DOM, and subscribe listeners to keep it in sync.
 *
 * @method _createShadow
 * @protected
 */
_createShadow : function () {

    var oShadow = this._shadow,
        oElement;

    if (!oShadow) {
        oElement = this.element;

        if (!m_oShadowTemplate) {
            m_oShadowTemplate = document.createElement(_DIV_LOWERCASE);
            m_oShadowTemplate.className = _YUI_MENU_SHADOW_YUI_MENU_SHADOW_VISIBLE;
        }

        oShadow = m_oShadowTemplate.cloneNode(false);

        oElement.appendChild(oShadow);
        
        this._shadow = oShadow;

        this.beforeShowEvent.subscribe(this._addShadowVisibleClass);
        this.beforeHideEvent.subscribe(this._removeShadowVisibleClass);

        if (UA.ie) {
            /*
                 Need to call sizeShadow & syncIframe via setTimeout for 
                 IE 7 Quirks Mode and IE 6 Standards Mode and Quirks Mode 
                 or the shadow and iframe shim will not be sized and 
                 positioned properly.
            */
            Lang.later(0, this, function () {
                this._sizeShadow(); 
                this.syncIframe();
            });

            this.cfg.subscribeToConfigEvent(_WIDTH, this._sizeShadow);
            this.cfg.subscribeToConfigEvent(_HEIGHT, this._sizeShadow);
            this.cfg.subscribeToConfigEvent(_MAX_HEIGHT, this._sizeShadow);
            this.changeContentEvent.subscribe(this._sizeShadow);

            Module.textResizeEvent.subscribe(this._sizeShadow, this, true);

            this.destroyEvent.subscribe(function () {
                Module.textResizeEvent.unsubscribe(this._sizeShadow, this);
            });
        }

        this.cfg.subscribeToConfigEvent(_MAX_HEIGHT, this._replaceShadow);
    }
},

/**
 * The beforeShow event handler used to set up the shadow lazily when the menu is made visible.
 * @method _shadowBeforeShow
 * @protected 
 */
_shadowBeforeShow : function () {
    if (this._shadow) {

        // If called because the "shadow" event was refired - just append again and resize
        this._replaceShadow();

        if (UA.ie) {
            this._sizeShadow();
        }
    } else {
        this._createShadow();
    }

    this.beforeShowEvent.unsubscribe(this._shadowBeforeShow);
},

/**
* @method configShadow
* @description Event handler for when the "shadow" configuration property of 
* a menu changes.
* @param {String} p_sType The name of the event that was fired.
* @param {Array} p_aArgs Collection of arguments sent when the event was fired.
* @param {YAHOO.widget.Menu} p_oMenu The Menu instance fired the event.
*/
configShadow: function (p_sType, p_aArgs, p_oMenu) {

    var bShadow = p_aArgs[0];

    if (bShadow && this.cfg.getProperty(_POSITION) == _DYNAMIC) {
        if (this.cfg.getProperty(_VISIBLE)) {
            if (this._shadow) {
                // If the "shadow" event was refired - just append again and resize
                this._replaceShadow();
                
                if (UA.ie) {
                    this._sizeShadow();
                }
            } else {
                this._createShadow();
            }
        } else {
            this.beforeShowEvent.subscribe(this._shadowBeforeShow);
        }
    } else if (!bShadow) {
        this.beforeShowEvent.unsubscribe(this._shadowBeforeShow);
        this._removeShadow();
    }
},

// Public methods

/**
* @method initEvents
* @description Initializes the custom events for the menu.
*/
initEvents: function () {

    Menu.superclass.initEvents.call(this);

    // Create custom events

    var i = EVENT_TYPES.length - 1,
        aEventData,
        oCustomEvent;


    do {

        aEventData = EVENT_TYPES[i];

        oCustomEvent = this.createEvent(aEventData[1]);
        oCustomEvent.signature = CustomEvent.LIST;
        
        this[aEventData[0]] = oCustomEvent;

    }
    while (i--);

},


/**
* @method positionOffScreen
* @description Positions the menu outside of the boundaries of the browser's 
* viewport.  Called automatically when a menu is hidden to ensure that 
* it doesn't force the browser to render uncessary scrollbars.
*/
positionOffScreen: function () {

    var oIFrame = this.iframe,
        oElement = this.element,
        sPos = this.OFF_SCREEN_POSITION;
    
    oElement.style.top = _EMPTY_STRING;
    oElement.style.left = _EMPTY_STRING;
    
    if (oIFrame) {

        oIFrame.style.top = sPos;
        oIFrame.style.left = sPos;
    
    }

},


/**
* @method getRoot
* @description Finds the menu's root menu.
*/
getRoot: function () {

    var oItem = this.parent,
        oParentMenu,
        returnVal;

    if (oItem) {

        oParentMenu = oItem.parent;

        returnVal = oParentMenu ? oParentMenu.getRoot() : this;

    }
    else {
    
        returnVal = this;
    
    }
    
    return returnVal;

},


/**
* @method toString
* @description Returns a string representing the menu.
* @return {String}
*/
toString: function () {

    var sReturnVal = _MENU,
        sId = this.id;

    if (sId) {

        sReturnVal += (_SPACE + sId);
    
    }

    return sReturnVal;

},


/**
* @method setItemGroupTitle
* @description Sets the title of a group of menu items.
* @param {HTML} p_sGroupTitle String or markup specifying the title of the group. The title is inserted into the DOM as HTML, and should be escaped by the implementor if coming from an external source.
* @param {Number} p_nGroupIndex Optional. Number specifying the group to which
* the title belongs.
*/
setItemGroupTitle: function (p_sGroupTitle, p_nGroupIndex) {

    var nGroupIndex,
        oTitle,
        i,
        nFirstIndex;
        
    if (Lang.isString(p_sGroupTitle) && p_sGroupTitle.length > 0) {

        nGroupIndex = Lang.isNumber(p_nGroupIndex) ? p_nGroupIndex : 0;
        oTitle = this._aGroupTitleElements[nGroupIndex];


        if (oTitle) {

            oTitle.innerHTML = p_sGroupTitle;
            
        }
        else {

            oTitle = document.createElement(this.GROUP_TITLE_TAG_NAME);
                    
            oTitle.innerHTML = p_sGroupTitle;

            this._aGroupTitleElements[nGroupIndex] = oTitle;

        }


        i = this._aGroupTitleElements.length - 1;

        do {

            if (this._aGroupTitleElements[i]) {

                Dom.removeClass(this._aGroupTitleElements[i], _FIRST_OF_TYPE);

                nFirstIndex = i;

            }

        }
        while (i--);


        if (nFirstIndex !== null) {

            Dom.addClass(this._aGroupTitleElements[nFirstIndex], 
                _FIRST_OF_TYPE);

        }

        this.changeContentEvent.fire();

    }

},



/**
* @method addItem
* @description Appends an item to the menu.
* @param {YAHOO.widget.MenuItem} p_oItem Object reference for the MenuItem 
* instance to be added to the menu.
* @param {HTML} p_oItem String or markup specifying content of the item to be added 
* to the menu. The item text is inserted into the DOM as HTML, and should be escaped by the implementor if coming from an external source.
* @param {Object} p_oItem Object literal containing a set of menu item 
* configuration properties.
* @param {Number} p_nGroupIndex Optional. Number indicating the group to
* which the item belongs.
* @return {YAHOO.widget.MenuItem}
*/
addItem: function (p_oItem, p_nGroupIndex) {

    return this._addItemToGroup(p_nGroupIndex, p_oItem);

},


/**
* @method addItems
* @description Adds an array of items to the menu.
* @param {Array} p_aItems Array of items to be added to the menu.  The array 
* can contain strings specifying the markup for the content of each item to be created, object
* literals specifying each of the menu item configuration properties, 
* or MenuItem instances. The item content if provided as a string is inserted into the DOM as HTML, and should be escaped by the implementor if coming from an external source.
* @param {Number} p_nGroupIndex Optional. Number specifying the group to 
* which the items belongs.
* @return {Array}
*/
addItems: function (p_aItems, p_nGroupIndex) {

    var nItems,
        aItems,
        oItem,
        i,
        returnVal;


    if (Lang.isArray(p_aItems)) {

        nItems = p_aItems.length;
        aItems = [];

        for(i=0; i<nItems; i++) {

            oItem = p_aItems[i];

            if (oItem) {

                if (Lang.isArray(oItem)) {
    
                    aItems[aItems.length] = this.addItems(oItem, i);
    
                }
                else {
    
                    aItems[aItems.length] = this._addItemToGroup(p_nGroupIndex, oItem);
                
                }

            }
    
        }


        if (aItems.length) {
        
            returnVal = aItems;
        
        }

    }

    return returnVal;

},


/**
* @method insertItem
* @description Inserts an item into the menu at the specified index.
* @param {YAHOO.widget.MenuItem} p_oItem Object reference for the MenuItem 
* instance to be added to the menu.
* @param {String} p_oItem String specifying the text of the item to be added 
* to the menu.
* @param {Object} p_oItem Object literal containing a set of menu item 
* configuration properties.
* @param {Number} p_nItemIndex Number indicating the ordinal position at which
* the item should be added.
* @param {Number} p_nGroupIndex Optional. Number indicating the group to which 
* the item belongs.
* @return {YAHOO.widget.MenuItem}
*/
insertItem: function (p_oItem, p_nItemIndex, p_nGroupIndex) {
    
    return this._addItemToGroup(p_nGroupIndex, p_oItem, p_nItemIndex);

},


/**
* @method removeItem
* @description Removes the specified item from the menu.
* @param {YAHOO.widget.MenuItem} p_oObject Object reference for the MenuItem 
* instance to be removed from the menu.
* @param {Number} p_oObject Number specifying the index of the item 
* to be removed.
* @param {Number} p_nGroupIndex Optional. Number specifying the group to 
* which the item belongs.
* @return {YAHOO.widget.MenuItem}
*/
removeItem: function (p_oObject, p_nGroupIndex) {

    var oItem,
        returnVal;
    
    if (!Lang.isUndefined(p_oObject)) {

        if (p_oObject instanceof YAHOO.widget.MenuItem) {

            oItem = this._removeItemFromGroupByValue(p_nGroupIndex, p_oObject);           

        }
        else if (Lang.isNumber(p_oObject)) {

            oItem = this._removeItemFromGroupByIndex(p_nGroupIndex, p_oObject);

        }

        if (oItem) {

            oItem.destroy();

            YAHOO.log("Item removed." + 
                " Text: " + oItem.cfg.getProperty("text") + ", " + 
                " Index: " + oItem.index + ", " + 
                " Group Index: " + oItem.groupIndex, "info", this.toString());

            returnVal = oItem;

        }

    }

    return returnVal;

},


/**
* @method getItems
* @description Returns an array of all of the items in the menu.
* @return {Array}
*/
getItems: function () {

    var aGroups = this._aItemGroups,
        nGroups,
        returnVal,
        aItems = [];


    if (Lang.isArray(aGroups)) {

        nGroups = aGroups.length;

        returnVal = ((nGroups == 1) ? aGroups[0] : (Array.prototype.concat.apply(aItems, aGroups)));

    }

    return returnVal;

},


/**
* @method getItemGroups
* @description Multi-dimensional Array representing the menu items as they 
* are grouped in the menu.
* @return {Array}
*/        
getItemGroups: function () {

    return this._aItemGroups;

},


/**
* @method getItem
* @description Returns the item at the specified index.
* @param {Number} p_nItemIndex Number indicating the ordinal position of the 
* item to be retrieved.
* @param {Number} p_nGroupIndex Optional. Number indicating the group to which 
* the item belongs.
* @return {YAHOO.widget.MenuItem}
*/
getItem: function (p_nItemIndex, p_nGroupIndex) {
    
    var aGroup,
        returnVal;
    
    if (Lang.isNumber(p_nItemIndex)) {

        aGroup = this._getItemGroup(p_nGroupIndex);

        if (aGroup) {

            returnVal = aGroup[p_nItemIndex];
        
        }

    }
    
    return returnVal;
    
},


/**
* @method getSubmenus
* @description Returns an array of all of the submenus that are immediate 
* children of the menu.
* @return {Array}
*/
getSubmenus: function () {

    var aItems = this.getItems(),
        nItems = aItems.length,
        aSubmenus,
        oSubmenu,
        oItem,
        i;


    if (nItems > 0) {
        
        aSubmenus = [];

        for(i=0; i<nItems; i++) {

            oItem = aItems[i];
            
            if (oItem) {

                oSubmenu = oItem.cfg.getProperty(_SUBMENU);
                
                if (oSubmenu) {

                    aSubmenus[aSubmenus.length] = oSubmenu;

                }
            
            }
        
        }
    
    }

    return aSubmenus;

},


/**
* @method clearContent
* @description Removes all of the content from the menu, including the menu 
* items, group titles, header and footer.
*/
clearContent: function () {

    var aItems = this.getItems(),
        nItems = aItems.length,
        oElement = this.element,
        oBody = this.body,
        oHeader = this.header,
        oFooter = this.footer,
        oItem,
        oSubmenu,
        i;


    if (nItems > 0) {

        i = nItems - 1;

        do {

            oItem = aItems[i];

            if (oItem) {

                oSubmenu = oItem.cfg.getProperty(_SUBMENU);

                if (oSubmenu) {

                    this.cfg.configChangedEvent.unsubscribe(
                        this._onParentMenuConfigChange, oSubmenu);

                    this.renderEvent.unsubscribe(this._onParentMenuRender, 
                        oSubmenu);

                }
                
                this.removeItem(oItem, oItem.groupIndex);

            }
        
        }
        while (i--);

    }


    if (oHeader) {

        Event.purgeElement(oHeader);
        oElement.removeChild(oHeader);

    }
    

    if (oFooter) {

        Event.purgeElement(oFooter);
        oElement.removeChild(oFooter);
    }


    if (oBody) {

        Event.purgeElement(oBody);

        oBody.innerHTML = _EMPTY_STRING;

    }

    this.activeItem = null;

    this._aItemGroups = [];
    this._aListElements = [];
    this._aGroupTitleElements = [];

    this.cfg.setProperty(_WIDTH, null);

},


/**
* @method destroy
* @description Removes the menu's <code>&#60;div&#62;</code> element 
* (and accompanying child nodes) from the document.
* @param {boolean} shallowPurge If true, only the parent element's DOM event listeners are purged. If false, or not provided, all children are also purged of DOM event listeners. 
* NOTE: The flag is a "shallowPurge" flag, as opposed to what may be a more intuitive "purgeChildren" flag to maintain backwards compatibility with behavior prior to 2.9.0.
* 
*/
destroy: function (shallowPurge) {

    // Remove all items

    this.clearContent();

    this._aItemGroups = null;
    this._aListElements = null;
    this._aGroupTitleElements = null;


    // Continue with the superclass implementation of this method

    Menu.superclass.destroy.call(this, shallowPurge);
    
    YAHOO.log("Destroyed.", "info", this.toString());

},


/**
* @method setInitialFocus
* @description Sets focus to the menu's first enabled item.
*/
setInitialFocus: function () {

    var oItem = this._getFirstEnabledItem();
    
    if (oItem) {

        oItem.focus();

    }
    
},


/**
* @method setInitialSelection
* @description Sets the "selected" configuration property of the menu's first 
* enabled item to "true."
*/
setInitialSelection: function () {

    var oItem = this._getFirstEnabledItem();
    
    if (oItem) {
    
        oItem.cfg.setProperty(_SELECTED, true);
    }        

},


/**
* @method clearActiveItem
* @description Sets the "selected" configuration property of the menu's active
* item to "false" and hides the item's submenu.
* @param {Boolean} p_bBlur Boolean indicating if the menu's active item 
* should be blurred.  
*/
clearActiveItem: function (p_bBlur) {

    if (this.cfg.getProperty(_SHOW_DELAY) > 0) {
    
        this._cancelShowDelay();
    
    }


    var oActiveItem = this.activeItem,
        oConfig,
        oSubmenu;

    if (oActiveItem) {

        oConfig = oActiveItem.cfg;

        if (p_bBlur) {

            oActiveItem.blur();
            
            this.getRoot()._hasFocus = true;
        
        }

        oConfig.setProperty(_SELECTED, false);

        oSubmenu = oConfig.getProperty(_SUBMENU);


        if (oSubmenu) {

            oSubmenu.hide();

        }

        this.activeItem = null;  

    }

},


/**
* @method focus
* @description Causes the menu to receive focus and fires the "focus" event.
*/
focus: function () {

    if (!this.hasFocus()) {

        this.setInitialFocus();
    
    }

},


/**
* @method blur
* @description Causes the menu to lose focus and fires the "blur" event.
*/    
blur: function () {

    var oItem;

    if (this.hasFocus()) {
    
        oItem = MenuManager.getFocusedMenuItem();
        
        if (oItem) {

            oItem.blur();

        }

    }

},


/**
* @method hasFocus
* @description Returns a boolean indicating whether or not the menu has focus.
* @return {Boolean}
*/
hasFocus: function () {

    return (MenuManager.getFocusedMenu() == this.getRoot());

},


_doItemSubmenuSubscribe: function (p_sType, p_aArgs, p_oObject) {

    var oItem = p_aArgs[0],
        oSubmenu = oItem.cfg.getProperty(_SUBMENU);

    if (oSubmenu) {
        oSubmenu.subscribe.apply(oSubmenu, p_oObject);
    }

},


_doSubmenuSubscribe: function (p_sType, p_aArgs, p_oObject) { 

    var oSubmenu = this.cfg.getProperty(_SUBMENU);
    
    if (oSubmenu) {
        oSubmenu.subscribe.apply(oSubmenu, p_oObject);
    }

},


/**
* Adds the specified CustomEvent subscriber to the menu and each of 
* its submenus.
* @method subscribe
* @param p_type     {string}   the type, or name of the event
* @param p_fn       {function} the function to exectute when the event fires
* @param p_obj      {Object}   An object to be passed along when the event 
*                              fires
* @param p_override {boolean}  If true, the obj passed in becomes the 
*                              execution scope of the listener
*/
subscribe: function () {

    //	Subscribe to the event for this Menu instance
    Menu.superclass.subscribe.apply(this, arguments);

    //	Subscribe to the "itemAdded" event so that all future submenus
    //	also subscribe to this event
    Menu.superclass.subscribe.call(this, _ITEM_ADDED, this._doItemSubmenuSubscribe, arguments);


    var aItems = this.getItems(),
        nItems,
        oItem,
        oSubmenu,
        i;
        

    if (aItems) {

        nItems = aItems.length;
        
        if (nItems > 0) {
        
            i = nItems - 1;
            
            do {

                oItem = aItems[i];
                oSubmenu = oItem.cfg.getProperty(_SUBMENU);
                
                if (oSubmenu) {
                    oSubmenu.subscribe.apply(oSubmenu, arguments);
                }
                else {
                    oItem.cfg.subscribeToConfigEvent(_SUBMENU, this._doSubmenuSubscribe, arguments);
                }

            }
            while (i--);
        
        }

    }

},


unsubscribe: function () {

    //	Remove the event for this Menu instance
    Menu.superclass.unsubscribe.apply(this, arguments);

    //	Remove the "itemAdded" event so that all future submenus don't have 
    //	the event handler
    Menu.superclass.unsubscribe.call(this, _ITEM_ADDED, this._doItemSubmenuSubscribe, arguments);


    var aItems = this.getItems(),
        nItems,
        oItem,
        oSubmenu,
        i;
        

    if (aItems) {

        nItems = aItems.length;
        
        if (nItems > 0) {
        
            i = nItems - 1;
            
            do {

                oItem = aItems[i];
                oSubmenu = oItem.cfg.getProperty(_SUBMENU);
                
                if (oSubmenu) {
                    oSubmenu.unsubscribe.apply(oSubmenu, arguments);
                }
                else {
                    oItem.cfg.unsubscribeFromConfigEvent(_SUBMENU, this._doSubmenuSubscribe, arguments);
                }

            }
            while (i--);
        
        }

    }

},


/**
* @description Initializes the class's configurable properties which can be
* changed using the menu's Config object ("cfg").
* @method initDefaultConfig
*/
initDefaultConfig: function () {

    Menu.superclass.initDefaultConfig.call(this);

    var oConfig = this.cfg;


    // Module documentation overrides

    /**
    * @config effect
    * @description Object or array of objects representing the ContainerEffect 
    * classes that are active for animating the container.  When set this 
    * property is automatically applied to all submenus.
    * @type Object
    * @default null
    */

    // Overlay documentation overrides


    /**
    * @config x
    * @description Number representing the absolute x-coordinate position of 
    * the Menu.  This property is only applied when the "position" 
    * configuration property is set to dynamic.
    * @type Number
    * @default null
    */
    

    /**
    * @config y
    * @description Number representing the absolute y-coordinate position of 
    * the Menu.  This property is only applied when the "position" 
    * configuration property is set to dynamic.
    * @type Number
    * @default null
    */


    /**
    * @description Array of the absolute x and y positions of the Menu.  This 
    * property is only applied when the "position" configuration property is 
    * set to dynamic.
    * @config xy
    * @type Number[]
    * @default null
    */
    

    /**
    * @config context
    * @description Array of context arguments for context-sensitive positioning.  
    * The format is: [id or element, element corner, context corner]. 
    * For example, setting this property to ["img1", "tl", "bl"] would 
    * align the Menu's top left corner to the context element's 
    * bottom left corner.  This property is only applied when the "position" 
    * configuration property is set to dynamic.
    * @type Array
    * @default null
    */
    
    
    /**
    * @config fixedcenter
    * @description Boolean indicating if the Menu should be anchored to the 
    * center of the viewport.  This property is only applied when the 
    * "position" configuration property is set to dynamic.
    * @type Boolean
    * @default false
    */
    
    
    /**
    * @config iframe
    * @description Boolean indicating whether or not the Menu should 
    * have an IFRAME shim; used to prevent SELECT elements from 
    * poking through an Overlay instance in IE6.  When set to "true", 
    * the iframe shim is created when the Menu instance is intially
    * made visible.  This property is only applied when the "position" 
    * configuration property is set to dynamic and is automatically applied 
    * to all submenus.
    * @type Boolean
    * @default true for IE6 and below, false for all other browsers.
    */


    // Add configuration attributes

    /*
        Change the default value for the "visible" configuration 
        property to "false" by re-adding the property.
    */

    /**
    * @config visible
    * @description Boolean indicating whether or not the menu is visible.  If 
    * the menu's "position" configuration property is set to "dynamic" (the 
    * default), this property toggles the menu's <code>&#60;div&#62;</code> 
    * element's "visibility" style property between "visible" (true) or 
    * "hidden" (false).  If the menu's "position" configuration property is 
    * set to "static" this property toggles the menu's 
    * <code>&#60;div&#62;</code> element's "display" style property 
    * between "block" (true) or "none" (false).
    * @default false
    * @type Boolean
    */
    oConfig.addProperty(
        VISIBLE_CONFIG.key, 
        {
            handler: this.configVisible, 
            value: VISIBLE_CONFIG.value, 
            validator: VISIBLE_CONFIG.validator
        }
     );


    /*
        Change the default value for the "constraintoviewport" configuration 
        property (inherited by YAHOO.widget.Overlay) to "true" by re-adding the property.
    */

    /**
    * @config constraintoviewport
    * @description Boolean indicating if the menu will try to remain inside 
    * the boundaries of the size of viewport.  This property is only applied 
    * when the "position" configuration property is set to dynamic and is 
    * automatically applied to all submenus.
    * @default true
    * @type Boolean
    */
    oConfig.addProperty(
        CONSTRAIN_TO_VIEWPORT_CONFIG.key, 
        {
            handler: this.configConstrainToViewport, 
            value: CONSTRAIN_TO_VIEWPORT_CONFIG.value, 
            validator: CONSTRAIN_TO_VIEWPORT_CONFIG.validator, 
            supercedes: CONSTRAIN_TO_VIEWPORT_CONFIG.supercedes 
        } 
    );


    /*
        Change the default value for the "preventcontextoverlap" configuration 
        property (inherited by YAHOO.widget.Overlay) to "true" by re-adding the property.
    */

    /**
    * @config preventcontextoverlap
    * @description Boolean indicating whether or not a submenu should overlap its parent MenuItem 
    * when the "constraintoviewport" configuration property is set to "true".
    * @type Boolean
    * @default true
    */
    oConfig.addProperty(PREVENT_CONTEXT_OVERLAP_CONFIG.key, {

        value: PREVENT_CONTEXT_OVERLAP_CONFIG.value, 
        validator: PREVENT_CONTEXT_OVERLAP_CONFIG.validator, 
        supercedes: PREVENT_CONTEXT_OVERLAP_CONFIG.supercedes

    });


    /**
    * @config position
    * @description String indicating how a menu should be positioned on the 
    * screen.  Possible values are "static" and "dynamic."  Static menus are 
    * visible by default and reside in the normal flow of the document 
    * (CSS position: static).  Dynamic menus are hidden by default, reside 
    * out of the normal flow of the document (CSS position: absolute), and 
    * can overlay other elements on the screen.
    * @default dynamic
    * @type String
    */
    oConfig.addProperty(
        POSITION_CONFIG.key, 
        {
            handler: this.configPosition,
            value: POSITION_CONFIG.value, 
            validator: POSITION_CONFIG.validator,
            supercedes: POSITION_CONFIG.supercedes
        }
    );


    /**
    * @config submenualignment
    * @description Array defining how submenus should be aligned to their 
    * parent menu item. The format is: [itemCorner, submenuCorner]. By default
    * a submenu's top left corner is aligned to its parent menu item's top 
    * right corner.
    * @default ["tl","tr"]
    * @type Array
    */
    oConfig.addProperty(
        SUBMENU_ALIGNMENT_CONFIG.key, 
        { 
            value: SUBMENU_ALIGNMENT_CONFIG.value,
            suppressEvent: SUBMENU_ALIGNMENT_CONFIG.suppressEvent
        }
    );


    /**
    * @config autosubmenudisplay
    * @description Boolean indicating if submenus are automatically made 
    * visible when the user mouses over the menu's items.
    * @default true
    * @type Boolean
    */
    oConfig.addProperty(
       AUTO_SUBMENU_DISPLAY_CONFIG.key, 
       { 
           value: AUTO_SUBMENU_DISPLAY_CONFIG.value, 
           validator: AUTO_SUBMENU_DISPLAY_CONFIG.validator,
           suppressEvent: AUTO_SUBMENU_DISPLAY_CONFIG.suppressEvent
       } 
    );


    /**
    * @config showdelay
    * @description Number indicating the time (in milliseconds) that should 
    * expire before a submenu is made visible when the user mouses over 
    * the menu's items.  This property is only applied when the "position" 
    * configuration property is set to dynamic and is automatically applied 
    * to all submenus.
    * @default 250
    * @type Number
    */
    oConfig.addProperty(
       SHOW_DELAY_CONFIG.key, 
       { 
           value: SHOW_DELAY_CONFIG.value, 
           validator: SHOW_DELAY_CONFIG.validator,
           suppressEvent: SHOW_DELAY_CONFIG.suppressEvent
       } 
    );


    /**
    * @config hidedelay
    * @description Number indicating the time (in milliseconds) that should 
    * expire before the menu is hidden.  This property is only applied when 
    * the "position" configuration property is set to dynamic and is 
    * automatically applied to all submenus.
    * @default 0
    * @type Number
    */
    oConfig.addProperty(
       HIDE_DELAY_CONFIG.key, 
       { 
           handler: this.configHideDelay,
           value: HIDE_DELAY_CONFIG.value, 
           validator: HIDE_DELAY_CONFIG.validator, 
           suppressEvent: HIDE_DELAY_CONFIG.suppressEvent
       } 
    );


    /**
    * @config submenuhidedelay
    * @description Number indicating the time (in milliseconds) that should 
    * expire before a submenu is hidden when the user mouses out of a menu item 
    * heading in the direction of a submenu.  The value must be greater than or 
    * equal to the value specified for the "showdelay" configuration property.
    * This property is only applied when the "position" configuration property 
    * is set to dynamic and is automatically applied to all submenus.
    * @default 250
    * @type Number
    */
    oConfig.addProperty(
       SUBMENU_HIDE_DELAY_CONFIG.key, 
       { 
           value: SUBMENU_HIDE_DELAY_CONFIG.value, 
           validator: SUBMENU_HIDE_DELAY_CONFIG.validator,
           suppressEvent: SUBMENU_HIDE_DELAY_CONFIG.suppressEvent
       } 
    );


    /**
    * @config clicktohide
    * @description Boolean indicating if the menu will automatically be 
    * hidden if the user clicks outside of it.  This property is only 
    * applied when the "position" configuration property is set to dynamic 
    * and is automatically applied to all submenus.
    * @default true
    * @type Boolean
    */
    oConfig.addProperty(
        CLICK_TO_HIDE_CONFIG.key,
        {
            value: CLICK_TO_HIDE_CONFIG.value,
            validator: CLICK_TO_HIDE_CONFIG.validator,
            suppressEvent: CLICK_TO_HIDE_CONFIG.suppressEvent
        }
    );


    /**
    * @config container
    * @description HTML element reference or string specifying the id 
    * attribute of the HTML element that the menu's markup should be 
    * rendered into.
    * @type <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
    * level-one-html.html#ID-58190037">HTMLElement</a>|String
    * @default document.body
    */
    oConfig.addProperty(
       CONTAINER_CONFIG.key, 
       { 
           handler: this.configContainer,
           value: document.body,
           suppressEvent: CONTAINER_CONFIG.suppressEvent
       } 
   );


    /**
    * @config scrollincrement
    * @description Number used to control the scroll speed of a menu.  Used to 
    * increment the "scrollTop" property of the menu's body by when a menu's 
    * content is scrolling.  When set this property is automatically applied 
    * to all submenus.
    * @default 1
    * @type Number
    */
    oConfig.addProperty(
        SCROLL_INCREMENT_CONFIG.key, 
        { 
            value: SCROLL_INCREMENT_CONFIG.value, 
            validator: SCROLL_INCREMENT_CONFIG.validator,
            supercedes: SCROLL_INCREMENT_CONFIG.supercedes,
            suppressEvent: SCROLL_INCREMENT_CONFIG.suppressEvent
        }
    );


    /**
    * @config minscrollheight
    * @description Number defining the minimum threshold for the "maxheight" 
    * configuration property.  When set this property is automatically applied 
    * to all submenus.
    * @default 90
    * @type Number
    */
    oConfig.addProperty(
        MIN_SCROLL_HEIGHT_CONFIG.key, 
        { 
            value: MIN_SCROLL_HEIGHT_CONFIG.value, 
            validator: MIN_SCROLL_HEIGHT_CONFIG.validator,
            supercedes: MIN_SCROLL_HEIGHT_CONFIG.supercedes,
            suppressEvent: MIN_SCROLL_HEIGHT_CONFIG.suppressEvent
        }
    );


    /**
    * @config maxheight
    * @description Number defining the maximum height (in pixels) for a menu's 
    * body element (<code>&#60;div class="bd"&#62;</code>).  Once a menu's body 
    * exceeds this height, the contents of the body are scrolled to maintain 
    * this value.  This value cannot be set lower than the value of the 
    * "minscrollheight" configuration property.
    * @default 0
    * @type Number
    */
    oConfig.addProperty(
       MAX_HEIGHT_CONFIG.key, 
       {
            handler: this.configMaxHeight,
            value: MAX_HEIGHT_CONFIG.value,
            validator: MAX_HEIGHT_CONFIG.validator,
            suppressEvent: MAX_HEIGHT_CONFIG.suppressEvent,
            supercedes: MAX_HEIGHT_CONFIG.supercedes            
       } 
    );


    /**
    * @config classname
    * @description String representing the CSS class to be applied to the 
    * menu's root <code>&#60;div&#62;</code> element.  The specified class(es)  
    * are appended in addition to the default class as specified by the menu's
    * CSS_CLASS_NAME constant. When set this property is automatically 
    * applied to all submenus.
    * @default null
    * @type String
    */
    oConfig.addProperty(
        CLASS_NAME_CONFIG.key, 
        { 
            handler: this.configClassName,
            value: CLASS_NAME_CONFIG.value, 
            validator: CLASS_NAME_CONFIG.validator,
            supercedes: CLASS_NAME_CONFIG.supercedes      
        }
    );


    /**
    * @config disabled
    * @description Boolean indicating if the menu should be disabled.  
    * Disabling a menu disables each of its items.  (Disabled menu items are 
    * dimmed and will not respond to user input or fire events.)  Disabled
    * menus have a corresponding "disabled" CSS class applied to their root
    * <code>&#60;div&#62;</code> element.
    * @default false
    * @type Boolean
    */
    oConfig.addProperty(
        DISABLED_CONFIG.key, 
        { 
            handler: this.configDisabled,
            value: DISABLED_CONFIG.value, 
            validator: DISABLED_CONFIG.validator,
            suppressEvent: DISABLED_CONFIG.suppressEvent
        }
    );


    /**
    * @config shadow
    * @description Boolean indicating if the menu should have a shadow.
    * @default true
    * @type Boolean
    */
    oConfig.addProperty(
        SHADOW_CONFIG.key, 
        { 
            handler: this.configShadow,
            value: SHADOW_CONFIG.value, 
            validator: SHADOW_CONFIG.validator
        }
    );


    /**
    * @config keepopen
    * @description Boolean indicating if the menu should remain open when clicked.
    * @default false
    * @type Boolean
    */
    oConfig.addProperty(
        KEEP_OPEN_CONFIG.key, 
        { 
            value: KEEP_OPEN_CONFIG.value, 
            validator: KEEP_OPEN_CONFIG.validator
        }
    );

}

}); // END YAHOO.lang.extend

})();



(function () {

/**
* Creates an item for a menu.
* 
* @param {HTML} p_oObject Markup for the menu item content. The markup is inserted into the DOM as HTML, and should be escaped by the implementor if coming from an external source.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-74680021">HTMLLIElement</a>} p_oObject Object specifying 
* the <code>&#60;li&#62;</code> element of the menu item.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-38450247">HTMLOptGroupElement</a>} p_oObject Object 
* specifying the <code>&#60;optgroup&#62;</code> element of the menu item.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-70901257">HTMLOptionElement</a>} p_oObject Object 
* specifying the <code>&#60;option&#62;</code> element of the menu item.
* @param {Object} p_oConfig Optional. Object literal specifying the 
* configuration for the menu item. See configuration class documentation 
* for more details.
* @class MenuItem
* @constructor
*/
YAHOO.widget.MenuItem = function (p_oObject, p_oConfig) {

    if (p_oObject) {

        if (p_oConfig) {
    
            this.parent = p_oConfig.parent;
            this.value = p_oConfig.value;
            this.id = p_oConfig.id;

        }

        this.init(p_oObject, p_oConfig);

    }

};


var Dom = YAHOO.util.Dom,
    Module = YAHOO.widget.Module,
    Menu = YAHOO.widget.Menu,
    MenuItem = YAHOO.widget.MenuItem,
    CustomEvent = YAHOO.util.CustomEvent,
    UA = YAHOO.env.ua,
    Lang = YAHOO.lang,

    // Private string constants

    _TEXT = "text",
    _HASH = "#",
    _HYPHEN = "-",
    _HELP_TEXT = "helptext",
    _URL = "url",
    _TARGET = "target",
    _EMPHASIS = "emphasis",
    _STRONG_EMPHASIS = "strongemphasis",
    _CHECKED = "checked",
    _SUBMENU = "submenu",
    _DISABLED = "disabled",
    _SELECTED = "selected",
    _HAS_SUBMENU = "hassubmenu",
    _CHECKED_DISABLED = "checked-disabled",
    _HAS_SUBMENU_DISABLED = "hassubmenu-disabled",
    _HAS_SUBMENU_SELECTED = "hassubmenu-selected",
    _CHECKED_SELECTED = "checked-selected",
    _ONCLICK = "onclick",
    _CLASSNAME = "classname",
    _EMPTY_STRING = "",
    _OPTION = "OPTION",
    _OPTGROUP = "OPTGROUP",
    _LI_UPPERCASE = "LI",
    _HREF = "href",
    _SELECT = "SELECT",
    _DIV = "DIV",
    _START_HELP_TEXT = "<em class=\"helptext\">",
    _START_EM = "<em>",
    _END_EM = "</em>",
    _START_STRONG = "<strong>",
    _END_STRONG = "</strong>",
    _PREVENT_CONTEXT_OVERLAP = "preventcontextoverlap",
    _OBJ = "obj",
    _SCOPE = "scope",
    _NONE = "none",
    _VISIBLE = "visible",
    _SPACE = " ",
    _MENUITEM = "MenuItem",
    _CLICK = "click",
    _SHOW = "show",
    _HIDE = "hide",
    _LI_LOWERCASE = "li",
    _ANCHOR_TEMPLATE = "<a href=\"#\"></a>",

    EVENT_TYPES = [
    
        ["mouseOverEvent", "mouseover"],
        ["mouseOutEvent", "mouseout"],
        ["mouseDownEvent", "mousedown"],
        ["mouseUpEvent", "mouseup"],
        ["clickEvent", _CLICK],
        ["keyPressEvent", "keypress"],
        ["keyDownEvent", "keydown"],
        ["keyUpEvent", "keyup"],
        ["focusEvent", "focus"],
        ["blurEvent", "blur"],
        ["destroyEvent", "destroy"]
    
    ],

    TEXT_CONFIG = { 
        key: _TEXT, 
        value: _EMPTY_STRING, 
        validator: Lang.isString, 
        suppressEvent: true 
    }, 

    HELP_TEXT_CONFIG = { 
        key: _HELP_TEXT,
        supercedes: [_TEXT], 
        suppressEvent: true 
    },

    URL_CONFIG = { 
        key: _URL, 
        value: _HASH, 
        suppressEvent: true 
    }, 

    TARGET_CONFIG = { 
        key: _TARGET, 
        suppressEvent: true 
    }, 

    EMPHASIS_CONFIG = { 
        key: _EMPHASIS, 
        value: false, 
        validator: Lang.isBoolean, 
        suppressEvent: true, 
        supercedes: [_TEXT]
    }, 

    STRONG_EMPHASIS_CONFIG = { 
        key: _STRONG_EMPHASIS, 
        value: false, 
        validator: Lang.isBoolean, 
        suppressEvent: true,
        supercedes: [_TEXT]
    },

    CHECKED_CONFIG = { 
        key: _CHECKED, 
        value: false, 
        validator: Lang.isBoolean, 
        suppressEvent: true, 
        supercedes: [_DISABLED, _SELECTED]
    }, 

    SUBMENU_CONFIG = { 
        key: _SUBMENU,
        suppressEvent: true,
        supercedes: [_DISABLED, _SELECTED]
    },

    DISABLED_CONFIG = { 
        key: _DISABLED, 
        value: false, 
        validator: Lang.isBoolean, 
        suppressEvent: true,
        supercedes: [_TEXT, _SELECTED]
    },

    SELECTED_CONFIG = { 
        key: _SELECTED, 
        value: false, 
        validator: Lang.isBoolean, 
        suppressEvent: true
    },

    ONCLICK_CONFIG = { 
        key: _ONCLICK,
        suppressEvent: true
    },

    CLASS_NAME_CONFIG = { 
        key: _CLASSNAME, 
        value: null, 
        validator: Lang.isString,
        suppressEvent: true
    },
    
    KEY_LISTENER_CONFIG = {
        key: "keylistener", 
        value: null, 
        suppressEvent: true
    },

    m_oMenuItemTemplate = null,

    CLASS_NAMES = {};


/**
* @method getClassNameForState
* @description Returns a class name for the specified prefix and state.  If the class name does not 
* yet exist, it is created and stored in the CLASS_NAMES object to increase performance.
* @private
* @param {String} prefix String representing the prefix for the class name
* @param {String} state String representing a state - "disabled," "checked," etc.
*/  
var getClassNameForState = function (prefix, state) {

    var oClassNames = CLASS_NAMES[prefix];
    
    if (!oClassNames) {
        CLASS_NAMES[prefix] = {};
        oClassNames = CLASS_NAMES[prefix];
    }


    var sClassName = oClassNames[state];

    if (!sClassName) {
        sClassName = prefix + _HYPHEN + state;
        oClassNames[state] = sClassName;
    }

    return sClassName;
    
};


/**
* @method addClassNameForState
* @description Applies a class name to a MenuItem instance's &#60;LI&#62; and &#60;A&#62; elements
* that represents a MenuItem's state - "disabled," "checked," etc.
* @private
* @param {String} state String representing a state - "disabled," "checked," etc.
*/  
var addClassNameForState = function (state) {

    Dom.addClass(this.element, getClassNameForState(this.CSS_CLASS_NAME, state));
    Dom.addClass(this._oAnchor, getClassNameForState(this.CSS_LABEL_CLASS_NAME, state));

};

/**
* @method removeClassNameForState
* @description Removes a class name from a MenuItem instance's &#60;LI&#62; and &#60;A&#62; elements
* that represents a MenuItem's state - "disabled," "checked," etc.
* @private
* @param {String} state String representing a state - "disabled," "checked," etc.
*/  
var removeClassNameForState = function (state) {

    Dom.removeClass(this.element, getClassNameForState(this.CSS_CLASS_NAME, state));
    Dom.removeClass(this._oAnchor, getClassNameForState(this.CSS_LABEL_CLASS_NAME, state));

};


MenuItem.prototype = {

    /**
    * @property CSS_CLASS_NAME
    * @description String representing the CSS class(es) to be applied to the 
    * <code>&#60;li&#62;</code> element of the menu item.
    * @default "yuimenuitem"
    * @final
    * @type String
    */
    CSS_CLASS_NAME: "yuimenuitem",


    /**
    * @property CSS_LABEL_CLASS_NAME
    * @description String representing the CSS class(es) to be applied to the 
    * menu item's <code>&#60;a&#62;</code> element.
    * @default "yuimenuitemlabel"
    * @final
    * @type String
    */
    CSS_LABEL_CLASS_NAME: "yuimenuitemlabel",


    /**
    * @property SUBMENU_TYPE
    * @description Object representing the type of menu to instantiate and 
    * add when parsing the child nodes of the menu item's source HTML element.
    * @final
    * @type YAHOO.widget.Menu
    */
    SUBMENU_TYPE: null,



    // Private member variables
    

    /**
    * @property _oAnchor
    * @description Object reference to the menu item's 
    * <code>&#60;a&#62;</code> element.
    * @default null 
    * @private
    * @type <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
    * one-html.html#ID-48250443">HTMLAnchorElement</a>
    */
    _oAnchor: null,
    
    
    /**
    * @property _oHelpTextEM
    * @description Object reference to the menu item's help text 
    * <code>&#60;em&#62;</code> element.
    * @default null
    * @private
    * @type <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
    * one-html.html#ID-58190037">HTMLElement</a>
    */
    _oHelpTextEM: null,
    
    
    /**
    * @property _oSubmenu
    * @description Object reference to the menu item's submenu.
    * @default null
    * @private
    * @type YAHOO.widget.Menu
    */
    _oSubmenu: null,


    /** 
    * @property _oOnclickAttributeValue
    * @description Object reference to the menu item's current value for the 
    * "onclick" configuration attribute.
    * @default null
    * @private
    * @type Object
    */
    _oOnclickAttributeValue: null,


    /**
    * @property _sClassName
    * @description The current value of the "classname" configuration attribute.
    * @default null
    * @private
    * @type String
    */
    _sClassName: null,



    // Public properties


    /**
    * @property constructor
    * @description Object reference to the menu item's constructor function.
    * @default YAHOO.widget.MenuItem
    * @type YAHOO.widget.MenuItem
    */
    constructor: MenuItem,


    /**
    * @property index
    * @description Number indicating the ordinal position of the menu item in 
    * its group.
    * @default null
    * @type Number
    */
    index: null,


    /**
    * @property groupIndex
    * @description Number indicating the index of the group to which the menu 
    * item belongs.
    * @default null
    * @type Number
    */
    groupIndex: null,


    /**
    * @property parent
    * @description Object reference to the menu item's parent menu.
    * @default null
    * @type YAHOO.widget.Menu
    */
    parent: null,


    /**
    * @property element
    * @description Object reference to the menu item's 
    * <code>&#60;li&#62;</code> element.
    * @default <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level
    * -one-html.html#ID-74680021">HTMLLIElement</a>
    * @type <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
    * one-html.html#ID-74680021">HTMLLIElement</a>
    */
    element: null,


    /**
    * @property srcElement
    * @description Object reference to the HTML element (either 
    * <code>&#60;li&#62;</code>, <code>&#60;optgroup&#62;</code> or 
    * <code>&#60;option&#62;</code>) used create the menu item.
    * @default <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
    * level-one-html.html#ID-74680021">HTMLLIElement</a>|<a href="http://www.
    * w3.org/TR/2000/WD-DOM-Level-1-20000929/level-one-html.html#ID-38450247"
    * >HTMLOptGroupElement</a>|<a href="http://www.w3.org/TR/2000/WD-DOM-
    * Level-1-20000929/level-one-html.html#ID-70901257">HTMLOptionElement</a>
    * @type <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
    * one-html.html#ID-74680021">HTMLLIElement</a>|<a href="http://www.w3.
    * org/TR/2000/WD-DOM-Level-1-20000929/level-one-html.html#ID-38450247">
    * HTMLOptGroupElement</a>|<a href="http://www.w3.org/TR/2000/WD-DOM-
    * Level-1-20000929/level-one-html.html#ID-70901257">HTMLOptionElement</a>
    */
    srcElement: null,


    /**
    * @property value
    * @description Object reference to the menu item's value.
    * @default null
    * @type Object
    */
    value: null,


    /**
    * @property browser
    * @deprecated Use YAHOO.env.ua
    * @description String representing the browser.
    * @type String
    */
    browser: Module.prototype.browser,


    /**
    * @property id
    * @description Id of the menu item's root <code>&#60;li&#62;</code> 
    * element.  This property should be set via the constructor using the 
    * configuration object literal.  If an id is not specified, then one will 
    * be created using the "generateId" method of the Dom utility.
    * @default null
    * @type String
    */
    id: null,



    // Events


    /**
    * @event destroyEvent
    * @description Fires when the menu item's <code>&#60;li&#62;</code> 
    * element is removed from its parent <code>&#60;ul&#62;</code> element.
    * @type YAHOO.util.CustomEvent
    */


    /**
    * @event mouseOverEvent
    * @description Fires when the mouse has entered the menu item.  Passes 
    * back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */


    /**
    * @event mouseOutEvent
    * @description Fires when the mouse has left the menu item.  Passes back 
    * the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */


    /**
    * @event mouseDownEvent
    * @description Fires when the user mouses down on the menu item.  Passes 
    * back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */


    /**
    * @event mouseUpEvent
    * @description Fires when the user releases a mouse button while the mouse 
    * is over the menu item.  Passes back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */


    /**
    * @event clickEvent
    * @description Fires when the user clicks the on the menu item.  Passes 
    * back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */


    /**
    * @event keyPressEvent
    * @description Fires when the user presses an alphanumeric key when the 
    * menu item has focus.  Passes back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */


    /**
    * @event keyDownEvent
    * @description Fires when the user presses a key when the menu item has 
    * focus.  Passes back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */


    /**
    * @event keyUpEvent
    * @description Fires when the user releases a key when the menu item has 
    * focus.  Passes back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */


    /**
    * @event focusEvent
    * @description Fires when the menu item receives focus.
    * @type YAHOO.util.CustomEvent
    */


    /**
    * @event blurEvent
    * @description Fires when the menu item loses the input focus.
    * @type YAHOO.util.CustomEvent
    */


    /**
    * @method init
    * @description The MenuItem class's initialization method. This method is 
    * automatically called by the constructor, and sets up all DOM references 
    * for pre-existing markup, and creates required markup if it is not 
    * already present.
    * @param {HTML} p_oObject Markup for the menu item content. The markup is inserted into the DOM as HTML, and should be escaped by the implementor if coming from an external source.
    * @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
    * one-html.html#ID-74680021">HTMLLIElement</a>} p_oObject Object specifying 
    * the <code>&#60;li&#62;</code> element of the menu item.
    * @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
    * one-html.html#ID-38450247">HTMLOptGroupElement</a>} p_oObject Object 
    * specifying the <code>&#60;optgroup&#62;</code> element of the menu item.
    * @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
    * one-html.html#ID-70901257">HTMLOptionElement</a>} p_oObject Object 
    * specifying the <code>&#60;option&#62;</code> element of the menu item.
    * @param {Object} p_oConfig Optional. Object literal specifying the 
    * configuration for the menu item. See configuration class documentation 
    * for more details.
    */
    init: function (p_oObject, p_oConfig) {


        if (!this.SUBMENU_TYPE) {
    
            this.SUBMENU_TYPE = Menu;
    
        }


        // Create the config object

        this.cfg = new YAHOO.util.Config(this);

        this.initDefaultConfig();

        var oConfig = this.cfg,
            sURL = _HASH,
            oCustomEvent,
            aEventData,
            oAnchor,
            sTarget,
            sText,
            sId,
            i;


        if (Lang.isString(p_oObject)) {

            this._createRootNodeStructure();

            oConfig.queueProperty(_TEXT, p_oObject);

        }
        else if (p_oObject && p_oObject.tagName) {

            switch(p_oObject.tagName.toUpperCase()) {

                case _OPTION:

                    this._createRootNodeStructure();

                    oConfig.queueProperty(_TEXT, p_oObject.text);
                    oConfig.queueProperty(_DISABLED, p_oObject.disabled);

                    this.value = p_oObject.value;

                    this.srcElement = p_oObject;

                break;

                case _OPTGROUP:

                    this._createRootNodeStructure();

                    oConfig.queueProperty(_TEXT, p_oObject.label);
                    oConfig.queueProperty(_DISABLED, p_oObject.disabled);

                    this.srcElement = p_oObject;

                    this._initSubTree();

                break;

                case _LI_UPPERCASE:

                    // Get the anchor node (if it exists)
                    
                    oAnchor = Dom.getFirstChild(p_oObject);


                    // Capture the "text" and/or the "URL"

                    if (oAnchor) {

                        sURL = oAnchor.getAttribute(_HREF, 2);
                        sTarget = oAnchor.getAttribute(_TARGET);

                        sText = oAnchor.innerHTML;

                    }

                    this.srcElement = p_oObject;
                    this.element = p_oObject;
                    this._oAnchor = oAnchor;

                    /*
                        Set these properties silently to sync up the 
                        configuration object without making changes to the 
                        element's DOM
                    */ 

                    oConfig.setProperty(_TEXT, sText, true);
                    oConfig.setProperty(_URL, sURL, true);
                    oConfig.setProperty(_TARGET, sTarget, true);

                    this._initSubTree();

                break;

            }            

        }


        if (this.element) {

            sId = (this.srcElement || this.element).id;

            if (!sId) {

                sId = this.id || Dom.generateId();

                this.element.id = sId;

            }

            this.id = sId;


            Dom.addClass(this.element, this.CSS_CLASS_NAME);
            Dom.addClass(this._oAnchor, this.CSS_LABEL_CLASS_NAME);


            i = EVENT_TYPES.length - 1;

            do {

                aEventData = EVENT_TYPES[i];

                oCustomEvent = this.createEvent(aEventData[1]);
                oCustomEvent.signature = CustomEvent.LIST;
                
                this[aEventData[0]] = oCustomEvent;

            }
            while (i--);


            if (p_oConfig) {
    
                oConfig.applyConfig(p_oConfig);
    
            }        

            oConfig.fireQueue();

        }

    },



    // Private methods

    /**
    * @method _createRootNodeStructure
    * @description Creates the core DOM structure for the menu item.
    * @private
    */
    _createRootNodeStructure: function () {

        var oElement,
            oAnchor;

        if (!m_oMenuItemTemplate) {

            m_oMenuItemTemplate = document.createElement(_LI_LOWERCASE);
            m_oMenuItemTemplate.innerHTML = _ANCHOR_TEMPLATE;

        }

        oElement = m_oMenuItemTemplate.cloneNode(true);
        oElement.className = this.CSS_CLASS_NAME;

        oAnchor = oElement.firstChild;
        oAnchor.className = this.CSS_LABEL_CLASS_NAME;

        this.element = oElement;
        this._oAnchor = oAnchor;

    },


    /**
    * @method _initSubTree
    * @description Iterates the source element's childNodes collection and uses 
    * the child nodes to instantiate other menus.
    * @private
    */
    _initSubTree: function () {

        var oSrcEl = this.srcElement,
            oConfig = this.cfg,
            oNode,
            aOptions,
            nOptions,
            oMenu,
            n;


        if (oSrcEl.childNodes.length > 0) {

            if (this.parent.lazyLoad && this.parent.srcElement && 
                this.parent.srcElement.tagName.toUpperCase() == _SELECT) {

                oConfig.setProperty(
                        _SUBMENU, 
                        { id: Dom.generateId(), itemdata: oSrcEl.childNodes }
                    );

            }
            else {

                oNode = oSrcEl.firstChild;
                aOptions = [];
    
                do {
    
                    if (oNode && oNode.tagName) {
    
                        switch(oNode.tagName.toUpperCase()) {
                
                            case _DIV:
                
                                oConfig.setProperty(_SUBMENU, oNode);
                
                            break;
         
                            case _OPTION:
        
                                aOptions[aOptions.length] = oNode;
        
                            break;
               
                        }
                    
                    }
                
                }        
                while((oNode = oNode.nextSibling));
    
    
                nOptions = aOptions.length;
    
                if (nOptions > 0) {
    
                    oMenu = new this.SUBMENU_TYPE(Dom.generateId());
                    
                    oConfig.setProperty(_SUBMENU, oMenu);
    
                    for(n=0; n<nOptions; n++) {
        
                        oMenu.addItem((new oMenu.ITEM_TYPE(aOptions[n])));
        
                    }
        
                }
            
            }

        }

    },



    // Event handlers for configuration properties


    /**
    * @method configText
    * @description Event handler for when the "text" configuration property of 
    * the menu item changes.
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    * @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item
    * that fired the event.
    */
    configText: function (p_sType, p_aArgs, p_oItem) {

        var sText = p_aArgs[0],
            oConfig = this.cfg,
            oAnchor = this._oAnchor,
            sHelpText = oConfig.getProperty(_HELP_TEXT),
            sHelpTextHTML = _EMPTY_STRING,
            sEmphasisStartTag = _EMPTY_STRING,
            sEmphasisEndTag = _EMPTY_STRING;


        if (sText) {


            if (sHelpText) {
                    
                sHelpTextHTML = _START_HELP_TEXT + sHelpText + _END_EM;
            
            }


            if (oConfig.getProperty(_EMPHASIS)) {

                sEmphasisStartTag = _START_EM;
                sEmphasisEndTag = _END_EM;

            }


            if (oConfig.getProperty(_STRONG_EMPHASIS)) {

                sEmphasisStartTag = _START_STRONG;
                sEmphasisEndTag = _END_STRONG;
            
            }


            oAnchor.innerHTML = (sEmphasisStartTag + sText + sEmphasisEndTag + sHelpTextHTML);

        }

    },


    /**
    * @method configHelpText
    * @description Event handler for when the "helptext" configuration property 
    * of the menu item changes.
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    * @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item
    * that fired the event.
    */    
    configHelpText: function (p_sType, p_aArgs, p_oItem) {

        this.cfg.refireEvent(_TEXT);

    },


    /**
    * @method configURL
    * @description Event handler for when the "url" configuration property of 
    * the menu item changes.
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    * @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item
    * that fired the event.
    */    
    configURL: function (p_sType, p_aArgs, p_oItem) {

        var sURL = p_aArgs[0];

        if (!sURL) {

            sURL = _HASH;

        }

        var oAnchor = this._oAnchor;

        if (UA.opera) {

            oAnchor.removeAttribute(_HREF);
        
        }

        oAnchor.setAttribute(_HREF, sURL);

    },


    /**
    * @method configTarget
    * @description Event handler for when the "target" configuration property 
    * of the menu item changes.  
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    * @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item
    * that fired the event.
    */    
    configTarget: function (p_sType, p_aArgs, p_oItem) {

        var sTarget = p_aArgs[0],
            oAnchor = this._oAnchor;

        if (sTarget && sTarget.length > 0) {

            oAnchor.setAttribute(_TARGET, sTarget);

        }
        else {

            oAnchor.removeAttribute(_TARGET);
        
        }

    },


    /**
    * @method configEmphasis
    * @description Event handler for when the "emphasis" configuration property
    * of the menu item changes.
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    * @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item
    * that fired the event.
    */    
    configEmphasis: function (p_sType, p_aArgs, p_oItem) {

        var bEmphasis = p_aArgs[0],
            oConfig = this.cfg;


        if (bEmphasis && oConfig.getProperty(_STRONG_EMPHASIS)) {

            oConfig.setProperty(_STRONG_EMPHASIS, false);

        }


        oConfig.refireEvent(_TEXT);

    },


    /**
    * @method configStrongEmphasis
    * @description Event handler for when the "strongemphasis" configuration 
    * property of the menu item changes.
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    * @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item
    * that fired the event.
    */    
    configStrongEmphasis: function (p_sType, p_aArgs, p_oItem) {

        var bStrongEmphasis = p_aArgs[0],
            oConfig = this.cfg;


        if (bStrongEmphasis && oConfig.getProperty(_EMPHASIS)) {

            oConfig.setProperty(_EMPHASIS, false);

        }

        oConfig.refireEvent(_TEXT);

    },


    /**
    * @method configChecked
    * @description Event handler for when the "checked" configuration property 
    * of the menu item changes. 
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    * @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item
    * that fired the event.
    */    
    configChecked: function (p_sType, p_aArgs, p_oItem) {

        var bChecked = p_aArgs[0],
            oConfig = this.cfg;


        if (bChecked) {

            addClassNameForState.call(this, _CHECKED);

        }
        else {

            removeClassNameForState.call(this, _CHECKED);
        }


        oConfig.refireEvent(_TEXT);


        if (oConfig.getProperty(_DISABLED)) {

            oConfig.refireEvent(_DISABLED);

        }


        if (oConfig.getProperty(_SELECTED)) {

            oConfig.refireEvent(_SELECTED);

        }

    },



    /**
    * @method configDisabled
    * @description Event handler for when the "disabled" configuration property 
    * of the menu item changes. 
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    * @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item
    * that fired the event.
    */    
    configDisabled: function (p_sType, p_aArgs, p_oItem) {

        var bDisabled = p_aArgs[0],
            oConfig = this.cfg,
            oSubmenu = oConfig.getProperty(_SUBMENU),
            bChecked = oConfig.getProperty(_CHECKED);


        if (bDisabled) {

            if (oConfig.getProperty(_SELECTED)) {

                oConfig.setProperty(_SELECTED, false);

            }


            addClassNameForState.call(this, _DISABLED);


            if (oSubmenu) {

                addClassNameForState.call(this, _HAS_SUBMENU_DISABLED);
            
            }
            

            if (bChecked) {

                addClassNameForState.call(this, _CHECKED_DISABLED);

            }

        }
        else {

            removeClassNameForState.call(this, _DISABLED);


            if (oSubmenu) {

                removeClassNameForState.call(this, _HAS_SUBMENU_DISABLED);
            
            }
            

            if (bChecked) {

                removeClassNameForState.call(this, _CHECKED_DISABLED);

            }

        }

    },


    /**
    * @method configSelected
    * @description Event handler for when the "selected" configuration property 
    * of the menu item changes. 
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    * @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item
    * that fired the event.
    */    
    configSelected: function (p_sType, p_aArgs, p_oItem) {

        var oConfig = this.cfg,
            oAnchor = this._oAnchor,
            
            bSelected = p_aArgs[0],
            bChecked = oConfig.getProperty(_CHECKED),
            oSubmenu = oConfig.getProperty(_SUBMENU);


        if (UA.opera) {

            oAnchor.blur();
        
        }


        if (bSelected && !oConfig.getProperty(_DISABLED)) {

            addClassNameForState.call(this, _SELECTED);


            if (oSubmenu) {

                addClassNameForState.call(this, _HAS_SUBMENU_SELECTED);
            
            }


            if (bChecked) {

                addClassNameForState.call(this, _CHECKED_SELECTED);

            }

        }
        else {

            removeClassNameForState.call(this, _SELECTED);


            if (oSubmenu) {

                removeClassNameForState.call(this, _HAS_SUBMENU_SELECTED);
            
            }


            if (bChecked) {

                removeClassNameForState.call(this, _CHECKED_SELECTED);

            }

        }


        if (this.hasFocus() && UA.opera) {
        
            oAnchor.focus();
        
        }

    },


    /**
    * @method _onSubmenuBeforeHide
    * @description "beforehide" Custom Event handler for a submenu.
    * @private
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    */
    _onSubmenuBeforeHide: function (p_sType, p_aArgs) {

        var oItem = this.parent,
            oMenu;

        function onHide() {

            oItem._oAnchor.blur();
            oMenu.beforeHideEvent.unsubscribe(onHide);
        
        }


        if (oItem.hasFocus()) {

            oMenu = oItem.parent;

            oMenu.beforeHideEvent.subscribe(onHide);
        
        }
    
    },


    /**
    * @method configSubmenu
    * @description Event handler for when the "submenu" configuration property 
    * of the menu item changes. 
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    * @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item
    * that fired the event.
    */
    configSubmenu: function (p_sType, p_aArgs, p_oItem) {

        var oSubmenu = p_aArgs[0],
            oConfig = this.cfg,
            bLazyLoad = this.parent && this.parent.lazyLoad,
            oMenu,
            sSubmenuId,
            oSubmenuConfig;


        if (oSubmenu) {

            if (oSubmenu instanceof Menu) {

                oMenu = oSubmenu;
                oMenu.parent = this;
                oMenu.lazyLoad = bLazyLoad;

            }
            else if (Lang.isObject(oSubmenu) && oSubmenu.id && !oSubmenu.nodeType) {

                sSubmenuId = oSubmenu.id;
                oSubmenuConfig = oSubmenu;

                oSubmenuConfig.lazyload = bLazyLoad;
                oSubmenuConfig.parent = this;

                oMenu = new this.SUBMENU_TYPE(sSubmenuId, oSubmenuConfig);


                // Set the value of the property to the Menu instance

                oConfig.setProperty(_SUBMENU, oMenu, true);

            }
            else {

                oMenu = new this.SUBMENU_TYPE(oSubmenu, { lazyload: bLazyLoad, parent: this });


                // Set the value of the property to the Menu instance
                
                oConfig.setProperty(_SUBMENU, oMenu, true);

            }


            if (oMenu) {

                oMenu.cfg.setProperty(_PREVENT_CONTEXT_OVERLAP, true);

                addClassNameForState.call(this, _HAS_SUBMENU);


                if (oConfig.getProperty(_URL) === _HASH) {
                
                    oConfig.setProperty(_URL, (_HASH + oMenu.id));
                
                }


                this._oSubmenu = oMenu;


                if (UA.opera) {
                
                    oMenu.beforeHideEvent.subscribe(this._onSubmenuBeforeHide);               
                
                }
            
            }

        }
        else {

            removeClassNameForState.call(this, _HAS_SUBMENU);

            if (this._oSubmenu) {

                this._oSubmenu.destroy();

            }

        }


        if (oConfig.getProperty(_DISABLED)) {

            oConfig.refireEvent(_DISABLED);

        }


        if (oConfig.getProperty(_SELECTED)) {

            oConfig.refireEvent(_SELECTED);

        }

    },


    /**
    * @method configOnClick
    * @description Event handler for when the "onclick" configuration property 
    * of the menu item changes. 
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    * @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item
    * that fired the event.
    */
    configOnClick: function (p_sType, p_aArgs, p_oItem) {

        var oObject = p_aArgs[0];

        /*
            Remove any existing listeners if a "click" event handler has 
            already been specified.
        */

        if (this._oOnclickAttributeValue && (this._oOnclickAttributeValue != oObject)) {

            this.clickEvent.unsubscribe(this._oOnclickAttributeValue.fn, 
                                this._oOnclickAttributeValue.obj);

            this._oOnclickAttributeValue = null;

        }


        if (!this._oOnclickAttributeValue && Lang.isObject(oObject) && 
            Lang.isFunction(oObject.fn)) {
            
            this.clickEvent.subscribe(oObject.fn, 
                ((_OBJ in oObject) ? oObject.obj : this), 
                ((_SCOPE in oObject) ? oObject.scope : null) );

            this._oOnclickAttributeValue = oObject;

        }
    
    },


    /**
    * @method configClassName
    * @description Event handler for when the "classname" configuration 
    * property of a menu item changes.
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    * @param {YAHOO.widget.MenuItem} p_oItem Object representing the menu item
    * that fired the event.
    */
    configClassName: function (p_sType, p_aArgs, p_oItem) {
    
        var sClassName = p_aArgs[0];
    
        if (this._sClassName) {
    
            Dom.removeClass(this.element, this._sClassName);
    
        }
    
        Dom.addClass(this.element, sClassName);
        this._sClassName = sClassName;
    
    },


    /**
    * @method _dispatchClickEvent
    * @description Dispatches a DOM "click" event to the anchor element of a 
    * MenuItem instance.
    * @private	
    */
    _dispatchClickEvent: function () {

        var oMenuItem = this,
            oAnchor;

        if (!oMenuItem.cfg.getProperty(_DISABLED)) {
            oAnchor = Dom.getFirstChild(oMenuItem.element);

            //	Dispatch a "click" event to the MenuItem's anchor so that its
            //	"click" event handlers will get called in response to the user 
            //	pressing the keyboard shortcut defined by the "keylistener"
            //	configuration property.

            this._dispatchDOMClick(oAnchor);
        }
    },

    /**
     * Utility method to dispatch a DOM click event on the HTMLElement passed in
     *
     * @method _dispatchDOMClick
     * @protected
     * @param {HTMLElement} el
     */    
    _dispatchDOMClick : function(el) {
        var oEvent;

        // Choose the standards path for IE9
        if (UA.ie && UA.ie < 9) {
            el.fireEvent(_ONCLICK);
        } else {
            if ((UA.gecko && UA.gecko >= 1.9) || UA.opera || UA.webkit) {
                oEvent = document.createEvent("HTMLEvents");
                oEvent.initEvent(_CLICK, true, true);
            } else {
                oEvent = document.createEvent("MouseEvents");
                oEvent.initMouseEvent(_CLICK, true, true, window, 0, 0, 0, 0, 0, false, false, false, false, 0, null);
            }
            el.dispatchEvent(oEvent);
        }
    },

    /**
    * @method _createKeyListener
    * @description "show" event handler for a Menu instance - responsible for 
    * setting up the KeyListener instance for a MenuItem.
    * @private	
    * @param {String} type String representing the name of the event that 
    * was fired.
    * @param {Array} args Array of arguments sent when the event was fired.
    * @param {Array} keyData Array of arguments sent when the event was fired.
    */
    _createKeyListener: function (type, args, keyData) {

        var oMenuItem = this,
            oMenu = oMenuItem.parent;

        var oKeyListener = new YAHOO.util.KeyListener(
                                        oMenu.element.ownerDocument, 
                                        keyData, 
                                        {
                                            fn: oMenuItem._dispatchClickEvent, 
                                            scope: oMenuItem, 
                                            correctScope: true });


        if (oMenu.cfg.getProperty(_VISIBLE)) {
            oKeyListener.enable();
        }


        oMenu.subscribe(_SHOW, oKeyListener.enable, null, oKeyListener);
        oMenu.subscribe(_HIDE, oKeyListener.disable, null, oKeyListener);
        
        oMenuItem._keyListener = oKeyListener;
        
        oMenu.unsubscribe(_SHOW, oMenuItem._createKeyListener, keyData);
        
    },


    /**
    * @method configKeyListener
    * @description Event handler for when the "keylistener" configuration 
    * property of a menu item changes.
    * @param {String} p_sType String representing the name of the event that 
    * was fired.
    * @param {Array} p_aArgs Array of arguments sent when the event was fired.
    */
    configKeyListener: function (p_sType, p_aArgs) {

        var oKeyData = p_aArgs[0],
            oMenuItem = this,
            oMenu = oMenuItem.parent;

        if (oMenuItem._keyData) {

            //	Unsubscribe from the "show" event in case the keylistener 
            //	config was changed before the Menu was ever made visible.

            oMenu.unsubscribe(_SHOW, 
                    oMenuItem._createKeyListener, oMenuItem._keyData);

            oMenuItem._keyData = null;					
                    
        }


        //	Tear down for the previous value of the "keylistener" property

        if (oMenuItem._keyListener) {

            oMenu.unsubscribe(_SHOW, oMenuItem._keyListener.enable);
            oMenu.unsubscribe(_HIDE, oMenuItem._keyListener.disable);

            oMenuItem._keyListener.disable();
            oMenuItem._keyListener = null;

        }


        if (oKeyData) {
    
            oMenuItem._keyData = oKeyData;

            //	Defer the creation of the KeyListener instance until the 
            //	parent Menu is visible.  This is necessary since the 
            //	KeyListener instance needs to be bound to the document the 
            //	Menu has been rendered into.  Deferring creation of the 
            //	KeyListener instance also improves performance.

            oMenu.subscribe(_SHOW, oMenuItem._createKeyListener, 
                oKeyData, oMenuItem);
        }
    
    },


    // Public methods


    /**
    * @method initDefaultConfig
    * @description Initializes an item's configurable properties.
    */
    initDefaultConfig : function () {

        var oConfig = this.cfg;


        // Define the configuration attributes

        /**
        * @config text
        * @description String or markup specifying the text label for the menu item.  
        * When building a menu from existing HTML the value of this property
        * will be interpreted from the menu's markup. The text is inserted into the DOM as HTML, and should be escaped by the implementor if coming from an external source.
        * @default ""
        * @type HTML
        */
        oConfig.addProperty(
            TEXT_CONFIG.key, 
            { 
                handler: this.configText, 
                value: TEXT_CONFIG.value, 
                validator: TEXT_CONFIG.validator, 
                suppressEvent: TEXT_CONFIG.suppressEvent 
            }
        );
        

        /**
        * @config helptext
        * @description String or markup specifying additional instructional text to 
        * accompany the text for the menu item. The helptext is inserted into the DOM as HTML, and should be escaped by the implementor if coming from an external source.
        * @deprecated Use "text" configuration property to add help text markup.  
        * For example: <code>oMenuItem.cfg.setProperty("text", "Copy &#60;em 
        * class=\"helptext\"&#62;Ctrl + C&#60;/em&#62;");</code>
        * @default null
        * @type HTML|<a href="http://www.w3.org/TR/
        * 2000/WD-DOM-Level-1-20000929/level-one-html.html#ID-58190037">
        * HTMLElement</a>
        */
        oConfig.addProperty(
            HELP_TEXT_CONFIG.key,
            {
                handler: this.configHelpText, 
                supercedes: HELP_TEXT_CONFIG.supercedes,
                suppressEvent: HELP_TEXT_CONFIG.suppressEvent 
            }
        );


        /**
        * @config url
        * @description String specifying the URL for the menu item's anchor's 
        * "href" attribute.  When building a menu from existing HTML the value 
        * of this property will be interpreted from the menu's markup. Markup for the menu item content. The url is inserted into the DOM as an attribute value, and should be escaped by the implementor if coming from an external source.
        * @default "#"
        * @type String
        */        
        oConfig.addProperty(
            URL_CONFIG.key, 
            {
                handler: this.configURL, 
                value: URL_CONFIG.value, 
                suppressEvent: URL_CONFIG.suppressEvent
            }
        );


        /**
        * @config target
        * @description String specifying the value for the "target" attribute 
        * of the menu item's anchor element. <strong>Specifying a target will 
        * require the user to click directly on the menu item's anchor node in
        * order to cause the browser to navigate to the specified URL.</strong> 
        * When building a menu from existing HTML the value of this property 
        * will be interpreted from the menu's markup. The target is inserted into the DOM as an attribute value, and should be escaped by the implementor if coming from an external source.
        * @default null
        * @type String
        */        
        oConfig.addProperty(
            TARGET_CONFIG.key, 
            {
                handler: this.configTarget, 
                suppressEvent: TARGET_CONFIG.suppressEvent
            }
        );


        /**
        * @config emphasis
        * @description Boolean indicating if the text of the menu item will be 
        * rendered with emphasis.
        * @deprecated Use the "text" configuration property to add emphasis.  
        * For example: <code>oMenuItem.cfg.setProperty("text", "&#60;em&#62;Some 
        * Text&#60;/em&#62;");</code>
        * @default false
        * @type Boolean
        */
        oConfig.addProperty(
            EMPHASIS_CONFIG.key, 
            { 
                handler: this.configEmphasis, 
                value: EMPHASIS_CONFIG.value, 
                validator: EMPHASIS_CONFIG.validator, 
                suppressEvent: EMPHASIS_CONFIG.suppressEvent,
                supercedes: EMPHASIS_CONFIG.supercedes
            }
        );


        /**
        * @config strongemphasis
        * @description Boolean indicating if the text of the menu item will be 
        * rendered with strong emphasis.
        * @deprecated Use the "text" configuration property to add strong emphasis.  
        * For example: <code>oMenuItem.cfg.setProperty("text", "&#60;strong&#62; 
        * Some Text&#60;/strong&#62;");</code>
        * @default false
        * @type Boolean
        */
        oConfig.addProperty(
            STRONG_EMPHASIS_CONFIG.key,
            {
                handler: this.configStrongEmphasis,
                value: STRONG_EMPHASIS_CONFIG.value,
                validator: STRONG_EMPHASIS_CONFIG.validator,
                suppressEvent: STRONG_EMPHASIS_CONFIG.suppressEvent,
                supercedes: STRONG_EMPHASIS_CONFIG.supercedes
            }
        );


        /**
        * @config checked
        * @description Boolean indicating if the menu item should be rendered 
        * with a checkmark.
        * @default false
        * @type Boolean
        */
        oConfig.addProperty(
            CHECKED_CONFIG.key, 
            {
                handler: this.configChecked, 
                value: CHECKED_CONFIG.value, 
                validator: CHECKED_CONFIG.validator, 
                suppressEvent: CHECKED_CONFIG.suppressEvent,
                supercedes: CHECKED_CONFIG.supercedes
            } 
        );


        /**
        * @config disabled
        * @description Boolean indicating if the menu item should be disabled.  
        * (Disabled menu items are  dimmed and will not respond to user input 
        * or fire events.)
        * @default false
        * @type Boolean
        */
        oConfig.addProperty(
            DISABLED_CONFIG.key,
            {
                handler: this.configDisabled,
                value: DISABLED_CONFIG.value,
                validator: DISABLED_CONFIG.validator,
                suppressEvent: DISABLED_CONFIG.suppressEvent
            }
        );


        /**
        * @config selected
        * @description Boolean indicating if the menu item should 
        * be highlighted.
        * @default false
        * @type Boolean
        */
        oConfig.addProperty(
            SELECTED_CONFIG.key,
            {
                handler: this.configSelected,
                value: SELECTED_CONFIG.value,
                validator: SELECTED_CONFIG.validator,
                suppressEvent: SELECTED_CONFIG.suppressEvent
            }
        );


        /**
        * @config submenu
        * @description Object specifying the submenu to be appended to the 
        * menu item.  The value can be one of the following: <ul><li>Object 
        * specifying a Menu instance.</li><li>Object literal specifying the
        * menu to be created.  Format: <code>{ id: [menu id], itemdata: 
        * [<a href="YAHOO.widget.Menu.html#itemData">array of values for 
        * items</a>] }</code>.</li><li>String specifying the id attribute 
        * of the <code>&#60;div&#62;</code> element of the menu.</li><li>
        * Object specifying the <code>&#60;div&#62;</code> element of the 
        * menu.</li></ul>
        * @default null
        * @type Menu|String|Object|<a href="http://www.w3.org/TR/2000/
        * WD-DOM-Level-1-20000929/level-one-html.html#ID-58190037">
        * HTMLElement</a>
        */
        oConfig.addProperty(
            SUBMENU_CONFIG.key, 
            {
                handler: this.configSubmenu, 
                supercedes: SUBMENU_CONFIG.supercedes,
                suppressEvent: SUBMENU_CONFIG.suppressEvent
            }
        );


        /**
        * @config onclick
        * @description Object literal representing the code to be executed when 
        * the item is clicked.  Format:<br> <code> {<br> 
        * <strong>fn:</strong> Function,   &#47;&#47; The handler to call when 
        * the event fires.<br> <strong>obj:</strong> Object, &#47;&#47; An 
        * object to  pass back to the handler.<br> <strong>scope:</strong> 
        * Object &#47;&#47; The object to use for the scope of the handler.
        * <br> } </code>
        * @type Object
        * @default null
        */
        oConfig.addProperty(
            ONCLICK_CONFIG.key, 
            {
                handler: this.configOnClick, 
                suppressEvent: ONCLICK_CONFIG.suppressEvent 
            }
        );


        /**
        * @config classname
        * @description CSS class to be applied to the menu item's root 
        * <code>&#60;li&#62;</code> element.  The specified class(es) are 
        * appended in addition to the default class as specified by the menu 
        * item's CSS_CLASS_NAME constant.
        * @default null
        * @type String
        */
        oConfig.addProperty(
            CLASS_NAME_CONFIG.key, 
            { 
                handler: this.configClassName,
                value: CLASS_NAME_CONFIG.value, 
                validator: CLASS_NAME_CONFIG.validator,
                suppressEvent: CLASS_NAME_CONFIG.suppressEvent 
            }
        );


        /**
        * @config keylistener
        * @description Object literal representing the key(s) that can be used 
        * to trigger the MenuItem's "click" event.  Possible attributes are 
        * shift (boolean), alt (boolean), ctrl (boolean) and keys (either an int 
        * or an array of ints representing keycodes).
        * @default null
        * @type Object
        */
        oConfig.addProperty(
            KEY_LISTENER_CONFIG.key, 
            { 
                handler: this.configKeyListener,
                value: KEY_LISTENER_CONFIG.value, 
                suppressEvent: KEY_LISTENER_CONFIG.suppressEvent 
            }
        );

    },

    /**
    * @method getNextSibling
    * @description Finds the menu item's next sibling.
    * @return YAHOO.widget.MenuItem
    */
    getNextSibling: function () {
    
        var isUL = function (el) {
                return (el.nodeName.toLowerCase() === "ul");
            },
    
            menuitemEl = this.element,
            next = Dom.getNextSibling(menuitemEl),
            parent,
            sibling,
            list;
        
        if (!next) {
            
            parent = menuitemEl.parentNode;
            sibling = Dom.getNextSiblingBy(parent, isUL);
            
            if (sibling) {
                list = sibling;
            }
            else {
                list = Dom.getFirstChildBy(parent.parentNode, isUL);
            }
            
            next = Dom.getFirstChild(list);
            
        }

        return YAHOO.widget.MenuManager.getMenuItem(next.id);

    },

    /**
    * @method getNextEnabledSibling
    * @description Finds the menu item's next enabled sibling.
    * @return YAHOO.widget.MenuItem
    */
    getNextEnabledSibling: function () {
        
        var next = this.getNextSibling();
        
        return (next.cfg.getProperty(_DISABLED) || next.element.style.display == _NONE) ? next.getNextEnabledSibling() : next;
        
    },


    /**
    * @method getPreviousSibling
    * @description Finds the menu item's previous sibling.
    * @return {YAHOO.widget.MenuItem}
    */	
    getPreviousSibling: function () {

        var isUL = function (el) {
                return (el.nodeName.toLowerCase() === "ul");
            },

            menuitemEl = this.element,
            next = Dom.getPreviousSibling(menuitemEl),
            parent,
            sibling,
            list;
        
        if (!next) {
            
            parent = menuitemEl.parentNode;
            sibling = Dom.getPreviousSiblingBy(parent, isUL);
            
            if (sibling) {
                list = sibling;
            }
            else {
                list = Dom.getLastChildBy(parent.parentNode, isUL);
            }
            
            next = Dom.getLastChild(list);
            
        }

        return YAHOO.widget.MenuManager.getMenuItem(next.id);
        
    },


    /**
    * @method getPreviousEnabledSibling
    * @description Finds the menu item's previous enabled sibling.
    * @return {YAHOO.widget.MenuItem}
    */
    getPreviousEnabledSibling: function () {
        
        var next = this.getPreviousSibling();
        
        return (next.cfg.getProperty(_DISABLED) || next.element.style.display == _NONE) ? next.getPreviousEnabledSibling() : next;
        
    },


    /**
    * @method focus
    * @description Causes the menu item to receive the focus and fires the 
    * focus event.
    */
    focus: function () {

        var oParent = this.parent,
            oAnchor = this._oAnchor,
            oActiveItem = oParent.activeItem;


        function setFocus() {

            try {

                if (!(UA.ie && !document.hasFocus())) {
                
                    if (oActiveItem) {
        
                        oActiveItem.blurEvent.fire();
        
                    }
    
                    oAnchor.focus();
                    
                    this.focusEvent.fire();
                
                }

            }
            catch(e) {
            
            }

        }


        if (!this.cfg.getProperty(_DISABLED) && oParent && oParent.cfg.getProperty(_VISIBLE) && 
            this.element.style.display != _NONE) {


            /*
                Setting focus via a timer fixes a race condition in Firefox, IE 
                and Opera where the browser viewport jumps as it trys to 
                position and focus the menu.
            */

            Lang.later(0, this, setFocus);

        }

    },


    /**
    * @method blur
    * @description Causes the menu item to lose focus and fires the 
    * blur event.
    */    
    blur: function () {

        var oParent = this.parent;

        if (!this.cfg.getProperty(_DISABLED) && oParent && oParent.cfg.getProperty(_VISIBLE)) {

            Lang.later(0, this, function () {

                try {
    
                    this._oAnchor.blur();
                    this.blurEvent.fire();    

                } 
                catch (e) {
                
                }
                
            }, 0);

        }

    },


    /**
    * @method hasFocus
    * @description Returns a boolean indicating whether or not the menu item
    * has focus.
    * @return {Boolean}
    */
    hasFocus: function () {
    
        return (YAHOO.widget.MenuManager.getFocusedMenuItem() == this);
    
    },


    /**
    * @method destroy
    * @description Removes the menu item's <code>&#60;li&#62;</code> element 
    * from its parent <code>&#60;ul&#62;</code> element.
    */
    destroy: function () {

        var oEl = this.element,
            oSubmenu,
            oParentNode,
            aEventData,
            i;


        if (oEl) {


            // If the item has a submenu, destroy it first

            oSubmenu = this.cfg.getProperty(_SUBMENU);

            if (oSubmenu) {
            
                oSubmenu.destroy();
            
            }


            // Remove the element from the parent node

            oParentNode = oEl.parentNode;

            if (oParentNode) {

                oParentNode.removeChild(oEl);

                this.destroyEvent.fire();

            }


            // Remove CustomEvent listeners

            i = EVENT_TYPES.length - 1;

            do {

                aEventData = EVENT_TYPES[i];
                
                this[aEventData[0]].unsubscribeAll();

            }
            while (i--);
            
            
            this.cfg.configChangedEvent.unsubscribeAll();

        }

    },


    /**
    * @method toString
    * @description Returns a string representing the menu item.
    * @return {String}
    */
    toString: function () {

        var sReturnVal = _MENUITEM,
            sId = this.id;

        if (sId) {
    
            sReturnVal += (_SPACE + sId);
        
        }

        return sReturnVal;
    
    }

};

Lang.augmentProto(MenuItem, YAHOO.util.EventProvider);

})();
(function () {

    var _XY = "xy",
        _MOUSEDOWN = "mousedown",
        _CONTEXTMENU = "ContextMenu",
        _SPACE = " ";

/**
* Creates a list of options or commands which are made visible in response to 
* an HTML element's "contextmenu" event ("mousedown" for Opera).
*
* @param {String} p_oElement String specifying the id attribute of the 
* <code>&#60;div&#62;</code> element of the context menu.
* @param {String} p_oElement String specifying the id attribute of the 
* <code>&#60;select&#62;</code> element to be used as the data source for the 
* context menu.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-one-
* html.html#ID-22445964">HTMLDivElement</a>} p_oElement Object specifying the 
* <code>&#60;div&#62;</code> element of the context menu.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-one-
* html.html#ID-94282980">HTMLSelectElement</a>} p_oElement Object specifying 
* the <code>&#60;select&#62;</code> element to be used as the data source for 
* the context menu.
* @param {Object} p_oConfig Optional. Object literal specifying the 
* configuration for the context menu. See configuration class documentation 
* for more details.
* @class ContextMenu
* @constructor
* @extends YAHOO.widget.Menu
* @namespace YAHOO.widget
*/
YAHOO.widget.ContextMenu = function(p_oElement, p_oConfig) {
    YAHOO.widget.ContextMenu.superclass.constructor.call(this, p_oElement, p_oConfig);
};


var Event = YAHOO.util.Event,
    UA = YAHOO.env.ua,
    ContextMenu = YAHOO.widget.ContextMenu,



    /**
    * Constant representing the name of the ContextMenu's events
    * @property EVENT_TYPES
    * @private
    * @final
    * @type Object
    */
    EVENT_TYPES = {

        "TRIGGER_CONTEXT_MENU": "triggerContextMenu",
        "CONTEXT_MENU": (UA.opera ? _MOUSEDOWN : "contextmenu"),
        "CLICK": "click"

    },
    
    
    /**
    * Constant representing the ContextMenu's configuration properties
    * @property DEFAULT_CONFIG
    * @private
    * @final
    * @type Object
    */
    TRIGGER_CONFIG = { 
        key: "trigger",
        suppressEvent: true
    };


/**
* @method position
* @description "beforeShow" event handler used to position the contextmenu.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
* @param {Array} p_aPos Array representing the xy position for the context menu.
*/
function position(p_sType, p_aArgs, p_aPos) {
    this.cfg.setProperty(_XY, p_aPos);
    this.beforeShowEvent.unsubscribe(position, p_aPos);
}


YAHOO.lang.extend(ContextMenu, YAHOO.widget.Menu, {



// Private properties


/**
* @property _oTrigger
* @description Object reference to the current value of the "trigger" 
* configuration property.
* @default null
* @private
* @type String|<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/leve
* l-one-html.html#ID-58190037">HTMLElement</a>|Array
*/
_oTrigger: null,


/**
* @property _bCancelled
* @description Boolean indicating if the display of the context menu should 
* be cancelled.
* @default false
* @private
* @type Boolean
*/
_bCancelled: false,



// Public properties


/**
* @property contextEventTarget
* @description Object reference for the HTML element that was the target of the
* "contextmenu" DOM event ("mousedown" for Opera) that triggered the display of 
* the context menu.
* @default null
* @type <a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-one-
* html.html#ID-58190037">HTMLElement</a>
*/
contextEventTarget: null,



// Events


/**
* @event triggerContextMenuEvent
* @param type {String} The name of the event, "triggerContextMenu"
* @param args {Array} The array of event arguments. For this event, the underlying
* DOM event is the only argument, available from args[0].
* @description Custom Event wrapper for the "contextmenu" DOM event 
* ("mousedown" for Opera) fired by the element(s) that trigger the display of 
* the context menu.
*/
triggerContextMenuEvent: null,



/**
* @method init
* @description The ContextMenu class's initialization method. This method is 
* automatically called by the constructor, and sets up all DOM references for 
* pre-existing markup, and creates required markup if it is not already present.
* @param {String} p_oElement String specifying the id attribute of the 
* <code>&#60;div&#62;</code> element of the context menu.
* @param {String} p_oElement String specifying the id attribute of the 
* <code>&#60;select&#62;</code> element to be used as the data source for 
* the context menu.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-one-
* html.html#ID-22445964">HTMLDivElement</a>} p_oElement Object specifying the 
* <code>&#60;div&#62;</code> element of the context menu.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-one-
* html.html#ID-94282980">HTMLSelectElement</a>} p_oElement Object specifying 
* the <code>&#60;select&#62;</code> element to be used as the data source for 
* the context menu.
* @param {Object} p_oConfig Optional. Object literal specifying the 
* configuration for the context menu. See configuration class documentation 
* for more details.
*/
init: function(p_oElement, p_oConfig) {


    // Call the init of the superclass (YAHOO.widget.Menu)
    
    ContextMenu.superclass.init.call(this, p_oElement);

    this.beforeInitEvent.fire(ContextMenu);

    if (p_oConfig) {
        this.cfg.applyConfig(p_oConfig, true);
    }

    this.initEvent.fire(ContextMenu);
},


/**
* @method initEvents
* @description Initializes the custom events for the context menu.
*/
initEvents: function() {
    ContextMenu.superclass.initEvents.call(this);

    // Create custom events
    this.triggerContextMenuEvent = this.createEvent(EVENT_TYPES.TRIGGER_CONTEXT_MENU);
    this.triggerContextMenuEvent.signature = YAHOO.util.CustomEvent.LIST;
},

/**
* @method cancel
* @description Cancels the display of the context menu.
*/
cancel: function() {
    this._bCancelled = true;
},

// Private methods


/**
* @method _removeEventHandlers
* @description Removes all of the DOM event handlers from the HTML element(s) 
* whose "context menu" event ("click" for Opera) trigger the display of 
* the context menu.
* @private
*/
_removeEventHandlers: function() {

    var oTrigger = this._oTrigger;

    // Remove the event handlers from the trigger(s)
    if (oTrigger) {
        Event.removeListener(oTrigger, EVENT_TYPES.CONTEXT_MENU, this._onTriggerContextMenu);    

        if (UA.opera) {
            Event.removeListener(oTrigger, EVENT_TYPES.CLICK, this._onTriggerClick);
        }
    }

},

// Private event handlers

/**
* @method _onTriggerClick
* @description "click" event handler for the HTML element(s) identified as the 
* "trigger" for the context menu.  Used to cancel default behaviors in Opera.
* @private
* @param {Event} p_oEvent Object representing the DOM event object passed back 
* by the event utility (YAHOO.util.Event).
* @param {YAHOO.widget.ContextMenu} p_oMenu Object representing the context 
* menu that is handling the event.
*/
_onTriggerClick: function(p_oEvent, p_oMenu) {

    if (p_oEvent.ctrlKey) {
        Event.stopEvent(p_oEvent);
    }
    
},


/**
* @method _onTriggerContextMenu
* @description "contextmenu" event handler ("mousedown" for Opera) for the HTML 
* element(s) that trigger the display of the context menu.
* @private
* @param {Event} p_oEvent Object representing the DOM event object passed back 
* by the event utility (YAHOO.util.Event).
* @param {YAHOO.widget.ContextMenu} p_oMenu Object representing the context 
* menu that is handling the event.
*/
_onTriggerContextMenu: function(p_oEvent, p_oMenu) {

    var aXY;

    if (!(p_oEvent.type == _MOUSEDOWN && !p_oEvent.ctrlKey)) {
    
        this.contextEventTarget = Event.getTarget(p_oEvent);
    
        this.triggerContextMenuEvent.fire(p_oEvent);
        
    
        if (!this._bCancelled) {

            /*
                Prevent the browser's default context menu from appearing and 
                stop the propagation of the "contextmenu" event so that 
                other ContextMenu instances are not displayed.
            */

            Event.stopEvent(p_oEvent);


            // Hide any other Menu instances that might be visible

            YAHOO.widget.MenuManager.hideVisible();
            
    

            // Position and display the context menu
    
            aXY = Event.getXY(p_oEvent);
    
    
            if (!YAHOO.util.Dom.inDocument(this.element)) {
    
                this.beforeShowEvent.subscribe(position, aXY);
    
            }
            else {
    
                this.cfg.setProperty(_XY, aXY);
            
            }
    
    
            this.show();
    
        }
    
        this._bCancelled = false;

    }

},



// Public methods


/**
* @method toString
* @description Returns a string representing the context menu.
* @return {String}
*/
toString: function() {

    var sReturnVal = _CONTEXTMENU,
        sId = this.id;

    if (sId) {

        sReturnVal += (_SPACE + sId);
    
    }

    return sReturnVal;

},


/**
* @method initDefaultConfig
* @description Initializes the class's configurable properties which can be 
* changed using the context menu's Config object ("cfg").
*/
initDefaultConfig: function() {

    ContextMenu.superclass.initDefaultConfig.call(this);

    /**
    * @config trigger
    * @description The HTML element(s) whose "contextmenu" event ("mousedown" 
    * for Opera) trigger the display of the context menu.  Can be a string 
    * representing the id attribute of the HTML element, an object reference 
    * for the HTML element, or an array of strings or HTML element references.
    * @default null
    * @type String|<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/
    * level-one-html.html#ID-58190037">HTMLElement</a>|Array
    */
    this.cfg.addProperty(TRIGGER_CONFIG.key, 
        {
            handler: this.configTrigger, 
            suppressEvent: TRIGGER_CONFIG.suppressEvent 
        }
    );

},


/**
* @method destroy
* @description Removes the context menu's <code>&#60;div&#62;</code> element 
* (and accompanying child nodes) from the document.
* @param {boolean} shallowPurge If true, only the parent element's DOM event listeners are purged. If false, or not provided, all children are also purged of DOM event listeners. 
* NOTE: The flag is a "shallowPurge" flag, as opposed to what may be a more intuitive "purgeChildren" flag to maintain backwards compatibility with behavior prior to 2.9.0.
*/
destroy: function(shallowPurge) {

    // Remove the DOM event handlers from the current trigger(s)

    this._removeEventHandlers();


    // Continue with the superclass implementation of this method

    ContextMenu.superclass.destroy.call(this, shallowPurge);

},



// Public event handlers for configuration properties


/**
* @method configTrigger
* @description Event handler for when the value of the "trigger" configuration 
* property changes. 
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
* @param {YAHOO.widget.ContextMenu} p_oMenu Object representing the context 
* menu that fired the event.
*/
configTrigger: function(p_sType, p_aArgs, p_oMenu) {
    
    var oTrigger = p_aArgs[0];

    if (oTrigger) {

        /*
            If there is a current "trigger" - remove the event handlers 
            from that element(s) before assigning new ones
        */

        if (this._oTrigger) {
        
            this._removeEventHandlers();

        }

        this._oTrigger = oTrigger;


        /*
            Listen for the "mousedown" event in Opera b/c it does not 
            support the "contextmenu" event
        */ 
  
        Event.on(oTrigger, EVENT_TYPES.CONTEXT_MENU, this._onTriggerContextMenu, this, true);


        /*
            Assign a "click" event handler to the trigger element(s) for
            Opera to prevent default browser behaviors.
        */

        if (UA.opera) {
        
            Event.on(oTrigger, EVENT_TYPES.CLICK, this._onTriggerClick, this, true);

        }

    }
    else {
   
        this._removeEventHandlers();
    
    }
    
}

}); // END YAHOO.lang.extend

}());



/**
* Creates an item for a context menu.
* 
* @param {String} p_oObject String specifying the text of the context menu item.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-74680021">HTMLLIElement</a>} p_oObject Object specifying the 
* <code>&#60;li&#62;</code> element of the context menu item.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-38450247">HTMLOptGroupElement</a>} p_oObject Object 
* specifying the <code>&#60;optgroup&#62;</code> element of the context 
* menu item.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-70901257">HTMLOptionElement</a>} p_oObject Object specifying 
* the <code>&#60;option&#62;</code> element of the context menu item.
* @param {Object} p_oConfig Optional. Object literal specifying the 
* configuration for the context menu item. See configuration class 
* documentation for more details.
* @class ContextMenuItem
* @constructor
* @extends YAHOO.widget.MenuItem
* @deprecated As of version 2.4.0 items for YAHOO.widget.ContextMenu instances
* are of type YAHOO.widget.MenuItem.
*/
YAHOO.widget.ContextMenuItem = YAHOO.widget.MenuItem;
(function () {

    var Lang = YAHOO.lang,

        // String constants
    
        _STATIC = "static",
        _DYNAMIC_STATIC = "dynamic," + _STATIC,
        _DISABLED = "disabled",
        _SELECTED = "selected",
        _AUTO_SUBMENU_DISPLAY = "autosubmenudisplay",
        _SUBMENU = "submenu",
        _VISIBLE = "visible",
        _SPACE = " ",
        _SUBMENU_TOGGLE_REGION = "submenutoggleregion",
        _MENUBAR = "MenuBar";

/**
* Horizontal collection of items, each of which can contain a submenu.
* 
* @param {String} p_oElement String specifying the id attribute of the 
* <code>&#60;div&#62;</code> element of the menu bar.
* @param {String} p_oElement String specifying the id attribute of the 
* <code>&#60;select&#62;</code> element to be used as the data source for the 
* menu bar.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-22445964">HTMLDivElement</a>} p_oElement Object specifying 
* the <code>&#60;div&#62;</code> element of the menu bar.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-94282980">HTMLSelectElement</a>} p_oElement Object 
* specifying the <code>&#60;select&#62;</code> element to be used as the data 
* source for the menu bar.
* @param {Object} p_oConfig Optional. Object literal specifying the 
* configuration for the menu bar. See configuration class documentation for
* more details.
* @class MenuBar
* @constructor
* @extends YAHOO.widget.Menu
* @namespace YAHOO.widget
*/
YAHOO.widget.MenuBar = function(p_oElement, p_oConfig) {

    YAHOO.widget.MenuBar.superclass.constructor.call(this, p_oElement, p_oConfig);

};


/**
* @method checkPosition
* @description Checks to make sure that the value of the "position" property 
* is one of the supported strings. Returns true if the position is supported.
* @private
* @param {Object} p_sPosition String specifying the position of the menu.
* @return {Boolean}
*/
function checkPosition(p_sPosition) {

    var returnVal = false;

    if (Lang.isString(p_sPosition)) {

        returnVal = (_DYNAMIC_STATIC.indexOf((p_sPosition.toLowerCase())) != -1);

    }
    
    return returnVal;

}


var Event = YAHOO.util.Event,
    MenuBar = YAHOO.widget.MenuBar,

    POSITION_CONFIG =  { 
        key: "position", 
        value: _STATIC, 
        validator: checkPosition, 
        supercedes: [_VISIBLE] 
    }, 

    SUBMENU_ALIGNMENT_CONFIG =  { 
        key: "submenualignment", 
        value: ["tl","bl"]
    },

    AUTO_SUBMENU_DISPLAY_CONFIG =  { 
        key: _AUTO_SUBMENU_DISPLAY, 
        value: false, 
        validator: Lang.isBoolean,
        suppressEvent: true
    },
    
    SUBMENU_TOGGLE_REGION_CONFIG = {
        key: _SUBMENU_TOGGLE_REGION, 
        value: false, 
        validator: Lang.isBoolean
    };



Lang.extend(MenuBar, YAHOO.widget.Menu, {

/**
* @method init
* @description The MenuBar class's initialization method. This method is 
* automatically called by the constructor, and sets up all DOM references for 
* pre-existing markup, and creates required markup if it is not already present.
* @param {String} p_oElement String specifying the id attribute of the 
* <code>&#60;div&#62;</code> element of the menu bar.
* @param {String} p_oElement String specifying the id attribute of the 
* <code>&#60;select&#62;</code> element to be used as the data source for the 
* menu bar.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-22445964">HTMLDivElement</a>} p_oElement Object specifying 
* the <code>&#60;div&#62;</code> element of the menu bar.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-94282980">HTMLSelectElement</a>} p_oElement Object 
* specifying the <code>&#60;select&#62;</code> element to be used as the data 
* source for the menu bar.
* @param {Object} p_oConfig Optional. Object literal specifying the 
* configuration for the menu bar. See configuration class documentation for
* more details.
*/
init: function(p_oElement, p_oConfig) {

    if(!this.ITEM_TYPE) {

        this.ITEM_TYPE = YAHOO.widget.MenuBarItem;

    }


    // Call the init of the superclass (YAHOO.widget.Menu)

    MenuBar.superclass.init.call(this, p_oElement);


    this.beforeInitEvent.fire(MenuBar);


    if(p_oConfig) {

        this.cfg.applyConfig(p_oConfig, true);

    }

    this.initEvent.fire(MenuBar);

},



// Constants


/**
* @property CSS_CLASS_NAME
* @description String representing the CSS class(es) to be applied to the menu 
* bar's <code>&#60;div&#62;</code> element.
* @default "yuimenubar"
* @final
* @type String
*/
CSS_CLASS_NAME: "yuimenubar",


/**
* @property SUBMENU_TOGGLE_REGION_WIDTH
* @description Width (in pixels) of the area of a MenuBarItem that, when pressed, will toggle the
* display of the MenuBarItem's submenu.
* @default 20
* @final
* @type Number
*/
SUBMENU_TOGGLE_REGION_WIDTH: 20,


// Protected event handlers


/**
* @method _onKeyDown
* @description "keydown" Custom Event handler for the menu bar.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
* @param {YAHOO.widget.MenuBar} p_oMenuBar Object representing the menu bar 
* that fired the event.
*/
_onKeyDown: function(p_sType, p_aArgs, p_oMenuBar) {

    var oEvent = p_aArgs[0],
        oItem = p_aArgs[1],
        oSubmenu,
        oItemCfg,
        oNextItem;


    if(oItem && !oItem.cfg.getProperty(_DISABLED)) {

        oItemCfg = oItem.cfg;

        switch(oEvent.keyCode) {
    
            case 37:    // Left arrow
            case 39:    // Right arrow
    
                if(oItem == this.activeItem && !oItemCfg.getProperty(_SELECTED)) {
    
                    oItemCfg.setProperty(_SELECTED, true);
    
                }
                else {
    
                    oNextItem = (oEvent.keyCode == 37) ? 
                        oItem.getPreviousEnabledSibling() : 
                        oItem.getNextEnabledSibling();
            
                    if(oNextItem) {
    
                        this.clearActiveItem();
    
                        oNextItem.cfg.setProperty(_SELECTED, true);
                        
                        oSubmenu = oNextItem.cfg.getProperty(_SUBMENU);
                        
                        if(oSubmenu) {
                    
                            oSubmenu.show();
                            oSubmenu.setInitialFocus();
                        
                        }
                        else {
                            oNextItem.focus();  
                        }
    
                    }
    
                }
    
                Event.preventDefault(oEvent);
    
            break;
    
            case 40:    // Down arrow
    
                if(this.activeItem != oItem) {
    
                    this.clearActiveItem();
    
                    oItemCfg.setProperty(_SELECTED, true);
                    oItem.focus();
                
                }
    
                oSubmenu = oItemCfg.getProperty(_SUBMENU);
    
                if(oSubmenu) {
    
                    if(oSubmenu.cfg.getProperty(_VISIBLE)) {
    
                        oSubmenu.setInitialSelection();
                        oSubmenu.setInitialFocus();
                    
                    }
                    else {
    
                        oSubmenu.show();
                        oSubmenu.setInitialFocus();
                    
                    }
    
                }
    
                Event.preventDefault(oEvent);
    
            break;
    
        }

    }


    if(oEvent.keyCode == 27 && this.activeItem) { // Esc key

        oSubmenu = this.activeItem.cfg.getProperty(_SUBMENU);

        if(oSubmenu && oSubmenu.cfg.getProperty(_VISIBLE)) {
        
            oSubmenu.hide();
            this.activeItem.focus();
        
        }
        else {

            this.activeItem.cfg.setProperty(_SELECTED, false);
            this.activeItem.blur();
    
        }

        Event.preventDefault(oEvent);
    
    }

},


/**
* @method _onClick
* @description "click" event handler for the menu bar.
* @protected
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
* @param {YAHOO.widget.MenuBar} p_oMenuBar Object representing the menu bar 
* that fired the event.
*/
_onClick: function(p_sType, p_aArgs, p_oMenuBar) {

    MenuBar.superclass._onClick.call(this, p_sType, p_aArgs, p_oMenuBar);

    var oItem = p_aArgs[1],
        bReturnVal = true,
        oItemEl,
        oEvent,
        oTarget,
        oActiveItem,
        oConfig,
        oSubmenu,
        nMenuItemX,
        nToggleRegion;


    var toggleSubmenuDisplay = function () {

        if(oSubmenu.cfg.getProperty(_VISIBLE)) {
        
            oSubmenu.hide();
        
        }
        else {
        
            oSubmenu.show();                    
        
        }
    
    };
    

    if(oItem && !oItem.cfg.getProperty(_DISABLED)) {

        oEvent = p_aArgs[0];
        oTarget = Event.getTarget(oEvent);
        oActiveItem = this.activeItem;
        oConfig = this.cfg;


        // Hide any other submenus that might be visible
    
        if(oActiveItem && oActiveItem != oItem) {
    
            this.clearActiveItem();
    
        }

    
        oItem.cfg.setProperty(_SELECTED, true);
    

        // Show the submenu for the item
    
        oSubmenu = oItem.cfg.getProperty(_SUBMENU);


        if(oSubmenu) {

            oItemEl = oItem.element;
            nMenuItemX = YAHOO.util.Dom.getX(oItemEl);
            nToggleRegion = nMenuItemX + (oItemEl.offsetWidth - this.SUBMENU_TOGGLE_REGION_WIDTH);

            if (oConfig.getProperty(_SUBMENU_TOGGLE_REGION)) {

                if (Event.getPageX(oEvent) > nToggleRegion) {

                    toggleSubmenuDisplay();

                    Event.preventDefault(oEvent);

                    /*
                         Return false so that other click event handlers are not called when the 
                         user clicks inside the toggle region.
                    */
                    bReturnVal = false;
                
                }
        
            }
            else {

                toggleSubmenuDisplay();
            
            }
        
        }
    
    }


    return bReturnVal;

},



// Public methods

/**
* @method configSubmenuToggle
* @description Event handler for when the "submenutoggleregion" configuration property of 
* a MenuBar changes.
* @param {String} p_sType The name of the event that was fired.
* @param {Array} p_aArgs Collection of arguments sent when the event was fired.
*/
configSubmenuToggle: function (p_sType, p_aArgs) {

    var bSubmenuToggle = p_aArgs[0];
    
    if (bSubmenuToggle) {
    
        this.cfg.setProperty(_AUTO_SUBMENU_DISPLAY, false);
    
    }

},


/**
* @method toString
* @description Returns a string representing the menu bar.
* @return {String}
*/
toString: function() {

    var sReturnVal = _MENUBAR,
        sId = this.id;

    if(sId) {

        sReturnVal += (_SPACE + sId);
    
    }

    return sReturnVal;

},


/**
* @description Initializes the class's configurable properties which can be
* changed using the menu bar's Config object ("cfg").
* @method initDefaultConfig
*/
initDefaultConfig: function() {

    MenuBar.superclass.initDefaultConfig.call(this);

    var oConfig = this.cfg;

    // Add configuration properties


    /*
        Set the default value for the "position" configuration property
        to "static" by re-adding the property.
    */


    /**
    * @config position
    * @description String indicating how a menu bar should be positioned on the 
    * screen.  Possible values are "static" and "dynamic."  Static menu bars 
    * are visible by default and reside in the normal flow of the document 
    * (CSS position: static).  Dynamic menu bars are hidden by default, reside
    * out of the normal flow of the document (CSS position: absolute), and can 
    * overlay other elements on the screen.
    * @default static
    * @type String
    */
    oConfig.addProperty(
        POSITION_CONFIG.key, 
        {
            handler: this.configPosition, 
            value: POSITION_CONFIG.value, 
            validator: POSITION_CONFIG.validator,
            supercedes: POSITION_CONFIG.supercedes
        }
    );


    /*
        Set the default value for the "submenualignment" configuration property
        to ["tl","bl"] by re-adding the property.
    */

    /**
    * @config submenualignment
    * @description Array defining how submenus should be aligned to their 
    * parent menu bar item. The format is: [itemCorner, submenuCorner].
    * @default ["tl","bl"]
    * @type Array
    */
    oConfig.addProperty(
        SUBMENU_ALIGNMENT_CONFIG.key, 
        {
            value: SUBMENU_ALIGNMENT_CONFIG.value,
            suppressEvent: SUBMENU_ALIGNMENT_CONFIG.suppressEvent
        }
    );


    /*
        Change the default value for the "autosubmenudisplay" configuration 
        property to "false" by re-adding the property.
    */

    /**
    * @config autosubmenudisplay
    * @description Boolean indicating if submenus are automatically made 
    * visible when the user mouses over the menu bar's items.
    * @default false
    * @type Boolean
    */
    oConfig.addProperty(
       AUTO_SUBMENU_DISPLAY_CONFIG.key, 
       {
           value: AUTO_SUBMENU_DISPLAY_CONFIG.value, 
           validator: AUTO_SUBMENU_DISPLAY_CONFIG.validator,
           suppressEvent: AUTO_SUBMENU_DISPLAY_CONFIG.suppressEvent
       } 
    );


    /**
    * @config submenutoggleregion
    * @description Boolean indicating if only a specific region of a MenuBarItem should toggle the 
    * display of a submenu.  The default width of the region is determined by the value of the
    * SUBMENU_TOGGLE_REGION_WIDTH property.  If set to true, the autosubmenudisplay 
    * configuration property will be set to false, and any click event listeners will not be 
    * called when the user clicks inside the submenu toggle region of a MenuBarItem.  If the 
    * user clicks outside of the submenu toggle region, the MenuBarItem will maintain its 
    * standard behavior.
    * @default false
    * @type Boolean
    */
    oConfig.addProperty(
       SUBMENU_TOGGLE_REGION_CONFIG.key, 
       {
           value: SUBMENU_TOGGLE_REGION_CONFIG.value, 
           validator: SUBMENU_TOGGLE_REGION_CONFIG.validator,
           handler: this.configSubmenuToggle
       } 
    );

}
 
}); // END YAHOO.lang.extend

}());



/**
* Creates an item for a menu bar.
* 
* @param {HTML} p_oObject Markup for the menu item content. The markup is inserted into the DOM as HTML, and should be escaped by the implementor if coming from an external source.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-74680021">HTMLLIElement</a>} p_oObject Object specifying the 
* <code>&#60;li&#62;</code> element of the menu bar item.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-38450247">HTMLOptGroupElement</a>} p_oObject Object 
* specifying the <code>&#60;optgroup&#62;</code> element of the menu bar item.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-70901257">HTMLOptionElement</a>} p_oObject Object specifying 
* the <code>&#60;option&#62;</code> element of the menu bar item.
* @param {Object} p_oConfig Optional. Object literal specifying the 
* configuration for the menu bar item. See configuration class documentation 
* for more details.
* @class MenuBarItem
* @constructor
* @extends YAHOO.widget.MenuItem
*/
YAHOO.widget.MenuBarItem = function(p_oObject, p_oConfig) {

    YAHOO.widget.MenuBarItem.superclass.constructor.call(this, p_oObject, p_oConfig);

};

YAHOO.lang.extend(YAHOO.widget.MenuBarItem, YAHOO.widget.MenuItem, {



/**
* @method init
* @description The MenuBarItem class's initialization method. This method is 
* automatically called by the constructor, and sets up all DOM references for 
* pre-existing markup, and creates required markup if it is not already present.
* @param {HTML} p_oObject Markup for the menu item content. The markup is inserted into the DOM as HTML, and should be escaped by the implementor if coming from an external source.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-74680021">HTMLLIElement</a>} p_oObject Object specifying the 
* <code>&#60;li&#62;</code> element of the menu bar item.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-38450247">HTMLOptGroupElement</a>} p_oObject Object 
* specifying the <code>&#60;optgroup&#62;</code> element of the menu bar item.
* @param {<a href="http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929/level-
* one-html.html#ID-70901257">HTMLOptionElement</a>} p_oObject Object specifying 
* the <code>&#60;option&#62;</code> element of the menu bar item.
* @param {Object} p_oConfig Optional. Object literal specifying the 
* configuration for the menu bar item. See configuration class documentation 
* for more details.
*/
init: function(p_oObject, p_oConfig) {

    if(!this.SUBMENU_TYPE) {

        this.SUBMENU_TYPE = YAHOO.widget.Menu;

    }


    /* 
        Call the init of the superclass (YAHOO.widget.MenuItem)
        Note: We don't pass the user config in here yet 
        because we only want it executed once, at the lowest 
        subclass level.
    */ 

    YAHOO.widget.MenuBarItem.superclass.init.call(this, p_oObject);  


    var oConfig = this.cfg;

    if(p_oConfig) {

        oConfig.applyConfig(p_oConfig, true);

    }

    oConfig.fireQueue();

},



// Constants


/**
* @property CSS_CLASS_NAME
* @description String representing the CSS class(es) to be applied to the 
* <code>&#60;li&#62;</code> element of the menu bar item.
* @default "yuimenubaritem"
* @final
* @type String
*/
CSS_CLASS_NAME: "yuimenubaritem",


/**
* @property CSS_LABEL_CLASS_NAME
* @description String representing the CSS class(es) to be applied to the 
* menu bar item's <code>&#60;a&#62;</code> element.
* @default "yuimenubaritemlabel"
* @final
* @type String
*/
CSS_LABEL_CLASS_NAME: "yuimenubaritemlabel",



// Public methods


/**
* @method toString
* @description Returns a string representing the menu bar item.
* @return {String}
*/
toString: function() {

    var sReturnVal = "MenuBarItem";

    if(this.cfg && this.cfg.getProperty("text")) {

        sReturnVal += (": " + this.cfg.getProperty("text"));

    }

    return sReturnVal;

}
    
}); // END YAHOO.lang.extend
YAHOO.register("menu", YAHOO.widget.Menu, {version: "2.9.0", build: "2800"});
