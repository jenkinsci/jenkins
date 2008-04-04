/*
Copyright (c) 2008, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.net/yui/license.txt
version: 2.5.1
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

    var Dom = YAHOO.util.Dom,
        Event = YAHOO.util.Event;


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
            "keypress": "keyPressEvent"
        },
    
    
        m_oFocusedMenuItem = null;
    
    
        var m_oLogger = new YAHOO.widget.LogWriter("MenuManager");
    
    
    
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
        
            var oParentNode;
    
            if (p_oElement && p_oElement.tagName) {
            
                switch (p_oElement.tagName.toUpperCase()) {
                        
                case "DIV":
    
                    oParentNode = p_oElement.parentNode;
    
                    // Check if the DIV is the inner "body" node of a menu

                    if (
                        (
                            Dom.hasClass(p_oElement, "hd") ||
                            Dom.hasClass(p_oElement, "bd") ||
                            Dom.hasClass(p_oElement, "ft")
                        ) && 
                        oParentNode && 
                        oParentNode.tagName && 
                        oParentNode.tagName.toUpperCase() == "DIV") 
                    {
                    
                        return oParentNode;
                    
                    }
                    else {
                    
                        return p_oElement;
                    
                    }
                
                    break;

                case "LI":
    
                    return p_oElement;

                default:
    
                    oParentNode = p_oElement.parentNode;
    
                    if (oParentNode) {
                    
                        return getMenuRootElement(oParentNode);
                    
                    }
                
                    break;
                
                }
    
            }
            
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
            sCustomEventType,
            sTagName,
            sId,
            oMenuItem,
            oMenu; 
    
    
            if (oElement) {
    
                sTagName = oElement.tagName.toUpperCase();
        
                if (sTagName == "LI") {
            
                    sId = oElement.id;
            
                    if (sId && m_oItems[sId]) {
            
                        oMenuItem = m_oItems[sId];
                        oMenu = oMenuItem.parent;
            
                    }
                
                }
                else if (sTagName == "DIV") {
                
                    if (oElement.id) {
                    
                        oMenu = m_oMenus[oElement.id];
                    
                    }
                
                }
    
            }
    
    
            if (oMenu) {
    
                sCustomEventType = m_oEventTypes[p_oEvent.type];
    
    
                // Fire the Custom Event that corresponds the current DOM event    
        
                if (oMenuItem && !oMenuItem.cfg.getProperty("disabled")) {
    
                    oMenuItem[sCustomEventType].fire(p_oEvent);                   
    
    
                    if (
                            p_oEvent.type == "keyup" || 
                            p_oEvent.type == "mousedown") 
                    {
    
                        if (m_oFocusedMenuItem != oMenuItem) {
                        
                            if (m_oFocusedMenuItem) {
    
                                m_oFocusedMenuItem.blurEvent.fire();
                            
                            }
    
                            oMenuItem.focusEvent.fire();
                        
                        }
                    
                    }
    
                }
        
                oMenu[sCustomEventType].fire(p_oEvent, oMenuItem);
            
            }
            else if (p_oEvent.type == "mousedown") {
    
                if (m_oFocusedMenuItem) {
    
                    m_oFocusedMenuItem.blurEvent.fire();
    
                    m_oFocusedMenuItem = null;
    
                }
    
    
                /*
                    If the target of the event wasn't a menu, hide all 
                    dynamically positioned menus
                */
                
                for (var i in m_oVisibleMenus) {
        
                    if (YAHOO.lang.hasOwnProperty(m_oVisibleMenus, i)) {
        
                        oMenu = m_oVisibleMenus[i];
        
                        if (oMenu.cfg.getProperty("clicktohide") && 
                            !(oMenu instanceof YAHOO.widget.MenuBar) && 
                            oMenu.cfg.getProperty("position") == "dynamic") {
        
                            oMenu.hide();
        
                        }
                        else {
    
                            oMenu.clearActiveItem(true);
        
                        }
        
                    }
        
                } 
    
            }
            else if (p_oEvent.type == "keyup") { 
    
                if (m_oFocusedMenuItem) {
    
                    m_oFocusedMenuItem.blurEvent.fire();
    
                    m_oFocusedMenuItem = null;
    
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
    
            var oItem = p_aArgs[0];
    
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
                
                m_oLogger.log(
                            this + 
                            " added to the collection of visible menus.");
            
            }
            else if (m_oVisibleMenus[sId]) {
            
                delete m_oVisibleMenus[sId];
                
                m_oLogger.log( 
                            this + 
                            " removed from the collection of visible menus.");
            
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

    
        function removeItem(p_oMenuItem) {

            var sId = p_oMenuItem.id;
    
            if (sId && m_oItems[sId]) {
    
                if (m_oFocusedMenuItem == p_oMenuItem) {
    
                    m_oFocusedMenuItem = null;
    
                }
    
                delete m_oItems[sId];
                
                p_oMenuItem.destroyEvent.unsubscribe(onItemDestroy);
    
                m_oLogger.log(p_oMenuItem + " successfully unregistered.");
    
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
        
                    m_oLogger.log(oItem + " successfully registered.");
        
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
                
                        Event.on(oDoc, "mouseover", onDOMEvent, this, true);
                        Event.on(oDoc, "mouseout", onDOMEvent, this, true);
                        Event.on(oDoc, "mousedown", onDOMEvent, this, true);
                        Event.on(oDoc, "mouseup", onDOMEvent, this, true);
                        Event.on(oDoc, "click", onDOMEvent, this, true);
                        Event.on(oDoc, "keydown", onDOMEvent, this, true);
                        Event.on(oDoc, "keyup", onDOMEvent, this, true);
                        Event.on(oDoc, "keypress", onDOMEvent, this, true);
    
    
                        m_bInitializedEventHandlers = true;
                        
                        m_oLogger.log("DOM event handlers initialized.");
            
                    }
            
                    p_oMenu.cfg.subscribeToConfigEvent("visible", 
                        onMenuVisibleConfigChange);

                    p_oMenu.destroyEvent.subscribe(onMenuDestroy, p_oMenu, 
                                            this);
            
                    p_oMenu.itemAddedEvent.subscribe(onItemAdded);
                    p_oMenu.focusEvent.subscribe(onMenuFocus);
                    p_oMenu.blurEvent.subscribe(onMenuBlur);
        
                    m_oLogger.log(p_oMenu + " successfully registered.");
        
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
        
                    if (m_oMenus[sId] == p_oMenu) {

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
            
                        m_oLogger.log(p_oMenu + " successfully unregistered.");
        

                        /*
                             Unregister the menu from the collection of 
                             visible menus
                        */

                        if (m_oVisibleMenus[sId] == p_oMenu) {
            
                            delete m_oVisibleMenus[sId];
                            
                            m_oLogger.log(p_oMenu + " unregistered from the" + 
                                        " collection of visible menus.");
       
                        }


                        // Unsubscribe event listeners

                        if (p_oMenu.cfg) {

                            p_oMenu.cfg.unsubscribeFromConfigEvent("visible", 
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
        
                    if (YAHOO.lang.hasOwnProperty(m_oVisibleMenus, i)) {
        
                        oMenu = m_oVisibleMenus[i];
        
                        if (!(oMenu instanceof YAHOO.widget.MenuBar) && 
                            oMenu.cfg.getProperty("position") == "dynamic") {
        
                            oMenu.hide();
        
                        }
        
                    }
        
                }        
    
            },


            /**
            * @method getVisible
            * @description Returns a collection of all visible menus registered
            * with the menu manger.
            * @return {Array}
            */
            getVisible: function () {
            
                return m_oVisibleMenus;
            
            },

    
            /**
            * @method getMenus
            * @description Returns a collection of all menus registered with the 
            * menu manger.
            * @return {Array}
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
    
                var oMenu = m_oMenus[p_sId];
        
                if (oMenu) {
                
                    return oMenu;
                
                }
            
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
    
                var oItem = m_oItems[p_sId];
        
                if (oItem) {
                
                    return oItem;
                
                }
            
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
                    sId;
    

                if (oUL && oUL.tagName && 
                    oUL.tagName.toUpperCase() == "UL") {

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

                            return aItems;
                        
                        }

                    }
                
                }
            
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
    
                if (m_oFocusedMenuItem) {
    
                    return (m_oFocusedMenuItem.parent.getRoot());
                
                }
    
            },
    
        
            /**
            * @method toString
            * @description Returns a string representing the menu manager.
            * @return {String}
            */
            toString: function () {
            
                return "MenuManager";
            
            }
    
        };
    
    }();

})();



(function () {


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

    if (typeof p_sPosition == "string") {

        return ("dynamic,static".indexOf((p_sPosition.toLowerCase())) != -1);

    }

}


var Dom = YAHOO.util.Dom,
    Event = YAHOO.util.Event,
    Module = YAHOO.widget.Module,
    Overlay = YAHOO.widget.Overlay,
    Menu = YAHOO.widget.Menu,
    MenuManager = YAHOO.widget.MenuManager,
    CustomEvent = YAHOO.util.CustomEvent,
    Lang = YAHOO.lang,
    UA = YAHOO.env.ua,
    
    m_oShadowTemplate,

    /**
    * Constant representing the name of the Menu's events
    * @property EVENT_TYPES
    * @private
    * @final
    * @type Object
    */
    EVENT_TYPES = {
    
        "MOUSE_OVER": "mouseover",
        "MOUSE_OUT": "mouseout",
        "MOUSE_DOWN": "mousedown",
        "MOUSE_UP": "mouseup",
        "CLICK": "click",
        "KEY_PRESS": "keypress",
        "KEY_DOWN": "keydown",
        "KEY_UP": "keyup",
        "FOCUS": "focus",
        "BLUR": "blur",
        "ITEM_ADDED": "itemAdded",
        "ITEM_REMOVED": "itemRemoved"
    
    },


    /**
    * Constant representing the Menu's configuration properties
    * @property DEFAULT_CONFIG
    * @private
    * @final
    * @type Object
    */
    DEFAULT_CONFIG = {

        "VISIBLE": { 
            key: "visible", 
            value: false, 
            validator: Lang.isBoolean
        }, 
    
        "CONSTRAIN_TO_VIEWPORT": {
            key: "constraintoviewport", 
            value: true, 
            validator: Lang.isBoolean, 
            supercedes: ["iframe","x","y","xy"]
        }, 
    
        "POSITION": { 
            key: "position", 
            value: "dynamic", 
            validator: checkPosition, 
            supercedes: ["visible", "iframe"]
        }, 
    
        "SUBMENU_ALIGNMENT": { 
            key: "submenualignment", 
            value: ["tl","tr"],
            suppressEvent: true
        },
    
        "AUTO_SUBMENU_DISPLAY": { 
            key: "autosubmenudisplay", 
            value: true, 
            validator: Lang.isBoolean,
            suppressEvent: true
        }, 
    
        "SHOW_DELAY": { 
            key: "showdelay", 
            value: 250, 
            validator: Lang.isNumber, 
            suppressEvent: true
        }, 
    
        "HIDE_DELAY": { 
            key: "hidedelay", 
            value: 0, 
            validator: Lang.isNumber, 
            suppressEvent: true
        }, 
    
        "SUBMENU_HIDE_DELAY": { 
            key: "submenuhidedelay", 
            value: 250, 
            validator: Lang.isNumber,
            suppressEvent: true
        }, 
    
        "CLICK_TO_HIDE": { 
            key: "clicktohide", 
            value: true, 
            validator: Lang.isBoolean,
            suppressEvent: true
        },
    
        "CONTAINER": { 
            key: "container",
            suppressEvent: true
        }, 

        "SCROLL_INCREMENT": { 
            key: "scrollincrement", 
            value: 1, 
            validator: Lang.isNumber,
            supercedes: ["maxheight"],
            suppressEvent: true
        },

        "MIN_SCROLL_HEIGHT": { 
            key: "minscrollheight", 
            value: 90, 
            validator: Lang.isNumber,
            supercedes: ["maxheight"],
            suppressEvent: true
        },    
    
        "MAX_HEIGHT": { 
            key: "maxheight", 
            value: 0, 
            validator: Lang.isNumber,
            supercedes: ["iframe"],
            suppressEvent: true
        }, 
    
        "CLASS_NAME": { 
            key: "classname", 
            value: null, 
            validator: Lang.isString,
            suppressEvent: true
        }, 
    
        "DISABLED": { 
            key: "disabled", 
            value: false, 
            validator: Lang.isBoolean,
            suppressEvent: true
        }
    
    };



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
* @default [-10000, -10000]
* @final
* @type Array
*/
OFF_SCREEN_POSITION: [-10000, -10000],


// Private properties


/** 
* @property _nHideDelayId
* @description Number representing the time-out setting used to cancel the 
* hiding of a menu.
* @default null
* @private
* @type Number
*/
_nHideDelayId: null,


/** 
* @property _nShowDelayId
* @description Number representing the time-out setting used to cancel the 
* showing of a menu.
* @default null
* @private
* @type Number
*/
_nShowDelayId: null,


/** 
* @property _nSubmenuHideDelayId
* @description Number representing the time-out setting used to cancel the 
* hiding of a submenu.
* @default null
* @private
* @type Number
*/
_nSubmenuHideDelayId: null,


/** 
* @property _nBodyScrollId
* @description Number representing the time-out setting used to cancel the 
* scrolling of the menu's body element.
* @default null
* @private
* @type Number
*/
_nBodyScrollId: null,


/** 
* @property _bHideDelayEventHandlersAssigned
* @description Boolean indicating if the "mouseover" and "mouseout" event 
* handlers used for hiding the menu via a call to "window.setTimeout" have 
* already been assigned.
* @default false
* @private
* @type Boolean
*/
_bHideDelayEventHandlersAssigned: false,


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
mouseOverEvent: null,


/**
* @event mouseOutEvent
* @description Fires when the mouse has left the menu.  Passes back the DOM 
* Event object as an argument.
* @type YAHOO.util.CustomEvent
*/
mouseOutEvent: null,


/**
* @event mouseDownEvent
* @description Fires when the user mouses down on the menu.  Passes back the 
* DOM Event object as an argument.
* @type YAHOO.util.CustomEvent
*/
mouseDownEvent: null,


/**
* @event mouseUpEvent
* @description Fires when the user releases a mouse button while the mouse is 
* over the menu.  Passes back the DOM Event object as an argument.
* @type YAHOO.util.CustomEvent
*/
mouseUpEvent: null,


/**
* @event clickEvent
* @description Fires when the user clicks the on the menu.  Passes back the 
* DOM Event object as an argument.
* @type YAHOO.util.CustomEvent
*/
clickEvent: null,


/**
* @event keyPressEvent
* @description Fires when the user presses an alphanumeric key when one of the
* menu's items has focus.  Passes back the DOM Event object as an argument.
* @type YAHOO.util.CustomEvent
*/
keyPressEvent: null,


/**
* @event keyDownEvent
* @description Fires when the user presses a key when one of the menu's items 
* has focus.  Passes back the DOM Event object as an argument.
* @type YAHOO.util.CustomEvent
*/
keyDownEvent: null,


/**
* @event keyUpEvent
* @description Fires when the user releases a key when one of the menu's items 
* has focus.  Passes back the DOM Event object as an argument.
* @type YAHOO.util.CustomEvent
*/
keyUpEvent: null,


/**
* @event itemAddedEvent
* @description Fires when an item is added to the menu.
* @type YAHOO.util.CustomEvent
*/
itemAddedEvent: null,


/**
* @event itemRemovedEvent
* @description Fires when an item is removed to the menu.
* @type YAHOO.util.CustomEvent
*/
itemRemovedEvent: null,


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

    if (typeof p_oElement == "string") {

        oElement = document.getElementById(p_oElement);

    }
    else if (p_oElement.tagName) {

        oElement = p_oElement;

    }


    if (oElement && oElement.tagName) {

        switch(oElement.tagName.toUpperCase()) {
    
            case "DIV":

                this.srcElement = oElement;

                if (!oElement.id) {

                    oElement.setAttribute("id", Dom.generateId());

                }


                /* 
                    Note: we don't pass the user config in here yet 
                    because we only want it executed once, at the lowest 
                    subclass level.
                */ 
            
                Menu.superclass.init.call(this, oElement);

                this.beforeInitEvent.fire(Menu);

                this.logger = new YAHOO.widget.LogWriter(this.toString());

                this.logger.log("Source element: " + this.srcElement.tagName);
    
            break;
    
            case "SELECT":
    
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

                this.logger = new YAHOO.widget.LogWriter(this.toString());

                this.logger.log("Source element: " + this.srcElement.tagName);

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

        this.logger = new YAHOO.widget.LogWriter(this.toString());

        this.logger.log("No source element found.  " +
            "Created element with id: " + this.id);

    }


    if (this.element) {

        Dom.addClass(this.element, this.CSS_CLASS_NAME);


        // Subscribe to Custom Events

        this.initEvent.subscribe(this._onInit);
        this.beforeRenderEvent.subscribe(this._onBeforeRender);
        this.renderEvent.subscribe(this._onRender);
        this.renderEvent.subscribe(this.onRender);
        this.beforeShowEvent.subscribe(this._onBeforeShow);
        this.hideEvent.subscribe(this.positionOffScreen);
        this.showEvent.subscribe(this._onShow);
        this.beforeHideEvent.subscribe(this._onBeforeHide);
        this.mouseOverEvent.subscribe(this._onMouseOver);
        this.mouseOutEvent.subscribe(this._onMouseOut);
        this.clickEvent.subscribe(this._onClick);
        this.keyDownEvent.subscribe(this._onKeyDown);
        this.keyPressEvent.subscribe(this._onKeyPress);
        

        if (UA.gecko || UA.webkit) {

            this.cfg.subscribeToConfigEvent("y", this._onYChange);

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


        if (sSrcElementTagName == "DIV") {
    
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
        
                            case "UL":
        
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
                    the "first-of-type" CSS3 psuedo class.
                */
        
                if (this._aListElements[0]) {
        
                    Dom.addClass(this._aListElements[0], "first-of-type");
        
                }
            
            }
    
        }
    
    
        oNode = null;
    
        this.logger.log("Searching DOM for items to initialize.");
    

        if (sSrcElementTagName) {
    
            switch (sSrcElementTagName) {
        
                case "DIV":

                    aListElements = this._aListElements;
                    nListElements = aListElements.length;
        
                    if (nListElements > 0) {
        
                        this.logger.log("Found " + nListElements + 
                            " item groups to initialize.");
        
                        i = nListElements - 1;
        
                        do {
        
                            oNode = aListElements[i].firstChild;
            
                            if (oNode) {

                                this.logger.log("Scanning " + 
                                    aListElements[i].childNodes.length + 
                                    " child nodes for items to initialize.");
            
                                do {
                
                                    if (oNode && oNode.tagName && 
                                        oNode.tagName.toUpperCase() == "LI") {
                
                                        this.logger.log("Initializing " + 
                                            oNode.tagName + " node.");
        
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
        
                case "SELECT":
        
                    this.logger.log("Scanning " +  
                        oSrcElement.childNodes.length + 
                        " child nodes for items to initialize.");
        
                    oNode = oSrcElement.firstChild;
        
                    do {
        
                        if (oNode && oNode.tagName) {
                        
                            switch (oNode.tagName.toUpperCase()) {
            
                                case "OPTGROUP":
                                case "OPTION":
            
                                    this.logger.log("Initializing " +  
                                        oNode.tagName + " node.");
            
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
        oItem;
    
    for(var i=0; i<nItems; i++) {

        oItem = aItems[i];

        if (oItem && !oItem.cfg.getProperty("disabled") && 
            oItem.element.style.display != "none") {

            return oItem;

        }
    
    }
    
},


/**
* @method _addItemToGroup
* @description Adds a menu item to a group.
* @private
* @param {Number} p_nGroupIndex Number indicating the group to which the 
* item belongs.
* @param {YAHOO.widget.MenuItem} p_oItem Object reference for the MenuItem 
* instance to be added to the menu.
* @param {String} p_oItem String specifying the text of the item to be added 
* to the menu.
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
        nItemIndex;

    function getNextItemSibling(p_aArray, p_nStartIndex) {

        return (p_aArray[p_nStartIndex] || getNextItemSibling(p_aArray, 
                (p_nStartIndex+1)));

    }

    if (p_oItem instanceof this.ITEM_TYPE) {

        oItem = p_oItem;
        oItem.parent = this;

    }
    else if (typeof p_oItem == "string") {

        oItem = new this.ITEM_TYPE(p_oItem, { parent: this });
    
    }
    else if (typeof p_oItem == "object") {

        p_oItem.parent = this;

        oItem = new this.ITEM_TYPE(p_oItem.text, p_oItem);

    }


    if (oItem) {

        if (oItem.cfg.getProperty("selected")) {

            this.activeItem = oItem;
        
        }


        nGroupIndex = typeof p_nGroupIndex == "number" ? p_nGroupIndex : 0;
        aGroup = this._getItemGroup(nGroupIndex);



        if (!aGroup) {

            aGroup = this._createItemGroup(nGroupIndex);

        }


        if (typeof p_nItemIndex == "number") {

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
        
                    this._aListElements[nGroupIndex].appendChild(
                        oGroupItem.element);
    
                }
                else {
    
                    oNextItemSibling = getNextItemSibling(aGroup, 
                        (p_nItemIndex+1));
    
                    if (oNextItemSibling && (!oGroupItem.element.parentNode || 
                            oGroupItem.element.parentNode.nodeType == 11)) {
            
                        this._aListElements[nGroupIndex].insertBefore(
                                oGroupItem.element, 
                                oNextItemSibling.element);
        
                    }
    
                }
    

                oGroupItem.parent = this;
        
                this._subscribeToItemEvents(oGroupItem);
    
                this._configureSubmenu(oGroupItem);
                
                this._updateItemProperties(nGroupIndex);
        
                this.logger.log("Item inserted." + 
                    " Text: " + oGroupItem.cfg.getProperty("text") + ", " + 
                    " Index: " + oGroupItem.index + ", " + 
                    " Group Index: " + oGroupItem.groupIndex);

                this.itemAddedEvent.fire(oGroupItem);
                this.changeContentEvent.fire();

                return oGroupItem;
    
            }

        }
        else {
    
            nItemIndex = aGroup.length;
    
            aGroup[nItemIndex] = oItem;

            oGroupItem = aGroup[nItemIndex];
    

            if (oGroupItem) {
    
                if (!Dom.isAncestor(this._aListElements[nGroupIndex], 
                        oGroupItem.element)) {
    
                    this._aListElements[nGroupIndex].appendChild(
                        oGroupItem.element);
    
                }
    
                oGroupItem.element.setAttribute("groupindex", nGroupIndex);
                oGroupItem.element.setAttribute("index", nItemIndex);
        
                oGroupItem.parent = this;
    
                oGroupItem.index = nItemIndex;
                oGroupItem.groupIndex = nGroupIndex;
        
                this._subscribeToItemEvents(oGroupItem);
    
                this._configureSubmenu(oGroupItem);
    
                if (nItemIndex === 0) {
        
                    Dom.addClass(oGroupItem.element, "first-of-type");
        
                }

                this.logger.log("Item added." + 
                    " Text: " + oGroupItem.cfg.getProperty("text") + ", " + 
                    " Index: " + oGroupItem.index + ", " + 
                    " Group Index: " + oGroupItem.groupIndex);
        

                this.itemAddedEvent.fire(oGroupItem);
                this.changeContentEvent.fire();

                return oGroupItem;
    
            }
    
        }

    }
    
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

    var nGroupIndex = typeof p_nGroupIndex == "number" ? p_nGroupIndex : 0,
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
    
                if (this.body && oUL) {
    
                    this.body.removeChild(oUL);
    
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
    
                    Dom.addClass(oUL, "first-of-type");
    
                }            
    
            }
    

            this.itemRemovedEvent.fire(oItem);
            this.changeContentEvent.fire();


            // Return a reference to the item that was removed
        
            return oItem;
    
        }

    }
    
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
            while(i--);
        
            if (nItemIndex > -1) {
        
                return (this._removeItemFromGroupByIndex(p_nGroupIndex, 
                            nItemIndex));
        
            }
    
        }
    
    }

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

                oLI.setAttribute("groupindex", p_nGroupIndex);
                oLI.setAttribute("index", i);

                Dom.removeClass(oLI, "first-of-type");

            }
    
        }
        while(i--);


        if (oLI) {

            Dom.addClass(oLI, "first-of-type");

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

    var oUL;

    if (!this._aItemGroups[p_nIndex]) {

        this._aItemGroups[p_nIndex] = [];

        oUL = document.createElement("ul");

        this._aListElements[p_nIndex] = oUL;

        return this._aItemGroups[p_nIndex];

    }

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

    var nIndex = ((typeof p_nIndex == "number") ? p_nIndex : 0);

    return this._aItemGroups[nIndex];

},


/**
* @method _configureSubmenu
* @description Subscribes the menu item's submenu to its parent menu's events.
* @private
* @param {YAHOO.widget.MenuItem} p_oItem Object reference for the MenuItem 
* instance with the submenu to be configured.
*/
_configureSubmenu: function (p_oItem) {

    var oSubmenu = p_oItem.cfg.getProperty("submenu");

    if (oSubmenu) {
            
        /*
            Listen for configuration changes to the parent menu 
            so they they can be applied to the submenu.
        */

        this.cfg.configChangedEvent.subscribe(this._onParentMenuConfigChange, 
                oSubmenu, true);

        this.renderEvent.subscribe(this._onParentMenuRender, oSubmenu, true);

        oSubmenu.beforeShowEvent.subscribe(this._onSubmenuBeforeShow);

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

    p_oItem.focusEvent.subscribe(this._onMenuItemFocus);

    p_oItem.blurEvent.subscribe(this._onMenuItemBlur);

    p_oItem.destroyEvent.subscribe(this._onMenuItemDestroy, p_oItem, this);

    p_oItem.cfg.configChangedEvent.subscribe(this._onMenuItemConfigChange,
        p_oItem, this);

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

        Dom.addClass(this.element, "visible");

    }
    else {

        Dom.removeClass(this.element, "visible");

    }

},


/**
* @method _cancelHideDelay
* @description Cancels the call to "hideMenu."
* @private
*/
_cancelHideDelay: function () {

    var oRoot = this.getRoot();

    if (oRoot._nHideDelayId) {

        window.clearTimeout(oRoot._nHideDelayId);

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

    var oRoot = this.getRoot(),
        me = this;

    function hideMenu() {
    
        if (oRoot.activeItem) {

            oRoot.clearActiveItem();

        }

        if (oRoot == me && !(me instanceof YAHOO.widget.MenuBar) && 
            me.cfg.getProperty("position") == "dynamic") {

            me.hide();
        
        }
    
    }


    oRoot._nHideDelayId = 
        window.setTimeout(hideMenu, oRoot.cfg.getProperty("hidedelay"));

},


/**
* @method _cancelShowDelay
* @description Cancels the call to the "showMenu."
* @private
*/
_cancelShowDelay: function () {

    var oRoot = this.getRoot();

    if (oRoot._nShowDelayId) {

        window.clearTimeout(oRoot._nShowDelayId);

    }

},


/**
* @method _execShowDelay
* @description Shows the menu after the number of milliseconds specified by 
* the "showdelay" configuration property have ellapsed.
* @private
* @param {YAHOO.widget.Menu} p_oMenu Object specifying the menu that should 
* be made visible.
*/
_execShowDelay: function (p_oMenu) {

    var oRoot = this.getRoot();

    function showMenu() {

        if (p_oMenu.parent.cfg.getProperty("selected")) {

            p_oMenu.show();

        }

    }


    oRoot._nShowDelayId = 
        window.setTimeout(showMenu, oRoot.cfg.getProperty("showdelay"));

},


/**
* @method _execSubmenuHideDelay
* @description Hides a submenu after the number of milliseconds specified by 
* the "submenuhidedelay" configuration property have ellapsed.
* @private
* @param {YAHOO.widget.Menu} p_oSubmenu Object specifying the submenu that  
* should be hidden.
* @param {Number} p_nMouseX The x coordinate of the mouse when it left 
* the specified submenu's parent menu item.
* @param {Number} p_nHideDelay The number of milliseconds that should ellapse
* before the submenu is hidden.
*/
_execSubmenuHideDelay: function (p_oSubmenu, p_nMouseX, p_nHideDelay) {

    var me = this;

    p_oSubmenu._nSubmenuHideDelayId = window.setTimeout(function () {

        if (me._nCurrentMouseX > (p_nMouseX + 10)) {

            p_oSubmenu._nSubmenuHideDelayId = window.setTimeout(function () {
        
                p_oSubmenu.hide();

            }, p_nHideDelay);

        }
        else {

            p_oSubmenu.hide();
        
        }

    }, 50);

},



// Protected methods


/**
* @method _disableScrollHeader
* @description Disables the header used for scrolling the body of the menu.
* @protected
*/
_disableScrollHeader: function () {

    if (!this._bHeaderDisabled) {

        Dom.addClass(this.header, "topscrollbar_disabled");
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

        Dom.addClass(this.footer, "bottomscrollbar_disabled");
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

        Dom.removeClass(this.header, "topscrollbar_disabled");
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

        Dom.removeClass(this.footer, "bottomscrollbar_disabled");
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

    if (this._bStopMouseEventHandlers) {
    
        return false;
    
    }


    var oEvent = p_aArgs[0],
        oItem = p_aArgs[1],
        oTarget = Event.getTarget(oEvent),
        oParentMenu,
        nShowDelay,
        bShowDelay,
        oActiveItem,
        oItemCfg,
        oSubmenu;


    if (!this._bHandledMouseOverEvent && (oTarget == this.element || 
        Dom.isAncestor(this.element, oTarget))) {

        // Menu mouseover logic

        this._nCurrentMouseX = 0;

        Event.on(this.element, "mousemove", this._onMouseMove, this, true);


		if (!Dom.isAncestor(oItem.element, Event.getRelatedTarget(oEvent))) {

        	this.clearActiveItem();
        
        }


        if (this.parent && this._nSubmenuHideDelayId) {

            window.clearTimeout(this._nSubmenuHideDelayId);

            this.parent.cfg.setProperty("selected", true);

            oParentMenu = this.parent.parent;

            oParentMenu._bHandledMouseOutEvent = true;
            oParentMenu._bHandledMouseOverEvent = false;

        }


        this._bHandledMouseOverEvent = true;
        this._bHandledMouseOutEvent = false;
    
    }


    if (oItem && !oItem.handledMouseOverEvent && 
        !oItem.cfg.getProperty("disabled") && 
        (oTarget == oItem.element || Dom.isAncestor(oItem.element, oTarget))) {

        // Menu Item mouseover logic

        nShowDelay = this.cfg.getProperty("showdelay");
        bShowDelay = (nShowDelay > 0);


        if (bShowDelay) {
        
            this._cancelShowDelay();
        
        }


        oActiveItem = this.activeItem;
    
        if (oActiveItem) {
    
            oActiveItem.cfg.setProperty("selected", false);
    
        }


        oItemCfg = oItem.cfg;
    
        // Select and focus the current menu item
    
        oItemCfg.setProperty("selected", true);


        if (this.hasFocus()) {
        
            oItem.focus();
        
        }


        if (this.cfg.getProperty("autosubmenudisplay")) {

            // Show the submenu this menu item

            oSubmenu = oItemCfg.getProperty("submenu");
        
            if (oSubmenu) {
        
                if (bShowDelay) {

                    this._execShowDelay(oSubmenu);
        
                }
                else {

                    oSubmenu.show();

                }

            }

        }                        

        oItem.handledMouseOverEvent = true;
        oItem.handledMouseOutEvent = false;

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

    if (this._bStopMouseEventHandlers) {
    
        return false;
    
    }


    var oEvent = p_aArgs[0],
        oItem = p_aArgs[1],
        oRelatedTarget = Event.getRelatedTarget(oEvent),
        bMovingToSubmenu = false,
        oItemCfg,
        oSubmenu,
        nSubmenuHideDelay,
        nShowDelay;


    if (oItem && !oItem.cfg.getProperty("disabled")) {

        oItemCfg = oItem.cfg;
        oSubmenu = oItemCfg.getProperty("submenu");


        if (oSubmenu && (oRelatedTarget == oSubmenu.element ||
                Dom.isAncestor(oSubmenu.element, oRelatedTarget))) {

            bMovingToSubmenu = true;

        }


        if (!oItem.handledMouseOutEvent && ((oRelatedTarget != oItem.element &&  
            !Dom.isAncestor(oItem.element, oRelatedTarget)) || 
            bMovingToSubmenu)) {

            // Menu Item mouseout logic

            if (!bMovingToSubmenu) {

                oItem.cfg.setProperty("selected", false);


                if (oSubmenu) {

                    nSubmenuHideDelay = 
                        this.cfg.getProperty("submenuhidedelay");

                    nShowDelay = this.cfg.getProperty("showdelay");

                    if (!(this instanceof YAHOO.widget.MenuBar) && 
                        nSubmenuHideDelay > 0 && 
                        nShowDelay >= nSubmenuHideDelay) {

                        this._execSubmenuHideDelay(oSubmenu, 
                                Event.getPageX(oEvent),
                                nSubmenuHideDelay);

                    }
                    else {

                        oSubmenu.hide();

                    }

                }

            }


            oItem.handledMouseOutEvent = true;
            oItem.handledMouseOverEvent = false;
    
        }

    }


    if (!this._bHandledMouseOutEvent && ((oRelatedTarget != this.element &&  
        !Dom.isAncestor(this.element, oRelatedTarget)) || bMovingToSubmenu)) {

        // Menu mouseout logic

        Event.removeListener(this.element, "mousemove", this._onMouseMove);

        this._nCurrentMouseX = Event.getPageX(oEvent);

        this._bHandledMouseOutEvent = true;
        this._bHandledMouseOverEvent = false;

    }

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

    if (this._bStopMouseEventHandlers) {
    
        return false;
    
    }

    this._nCurrentMouseX = Event.getPageX(p_oEvent);

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

	var Event = YAHOO.util.Event,
		Dom = YAHOO.util.Dom,
		oEvent = p_aArgs[0],
		oItem = p_aArgs[1],
		oSubmenu,
		bInMenuAnchor = false,
		oRoot,
		sId,
		sURL,
		nHashPos,
		nLen;


	if (oItem) {
	
		if (oItem.cfg.getProperty("disabled")) {
		
			Event.preventDefault(oEvent);

		}
		else {

			oSubmenu = oItem.cfg.getProperty("submenu");
	
			
			/*
				 Check if the URL of the anchor is pointing to an element that is 
				 a child of the menu.
			*/
			
			sURL = oItem.cfg.getProperty("url");

		
			if (sURL) {
	
				nHashPos = sURL.indexOf("#");
	
				nLen = sURL.length;
	
	
				if (nHashPos != -1) {
	
					sURL = sURL.substr(nHashPos, nLen);
		
					nLen = sURL.length;
	
	
					if (nLen > 1) {
	
						sId = sURL.substr(1, nLen);
	
						bInMenuAnchor = Dom.isAncestor(this.element, sId);
						
					}
					else if (nLen === 1) {
	
						bInMenuAnchor = true;
					
					}
	
				}
			
			}


	
			if (bInMenuAnchor && !oItem.cfg.getProperty("target")) {
	
				Event.preventDefault(oEvent);
				

				if (UA.webkit) {
				
					oItem.focus();
				
				}
				else {

					oItem.focusEvent.fire();
				
				}
			
			}
	
	
			if (!oSubmenu) {
	
				oRoot = this.getRoot();
				
				if (oRoot instanceof YAHOO.widget.MenuBar || 
					oRoot.cfg.getProperty("position") == "static") {
	
					oRoot.clearActiveItem();
	
				}
				else {
	
					oRoot.hide();
				
				}
	
			}
			
		}
	
	}

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
        me = this,
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
        oParentMenu;


    /*
        This function is called to prevent a bug in Firefox.  In Firefox,
        moving a DOM element into a stationary mouse pointer will cause the 
        browser to fire mouse events.  This can result in the menu mouse
        event handlers being called uncessarily, especially when menus are 
        moved into a stationary mouse pointer as a result of a 
        key event handler.
    */
    function stopMouseEventHandlers() {

        me._bStopMouseEventHandlers = true;
        
        window.setTimeout(function () {
        
            me._bStopMouseEventHandlers = false;
        
        }, 10);

    }


    if (oItem && !oItem.cfg.getProperty("disabled")) {

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

                    oNextItem.cfg.setProperty("selected", true);
                    oNextItem.focus();


                    if (this.cfg.getProperty("maxheight") > 0) {

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

                stopMouseEventHandlers();
    
            break;
            
    
            case 39:    // Right arrow
    
                oSubmenu = oItemCfg.getProperty("submenu");
    
                if (oSubmenu) {
    
                    if (!oItemCfg.getProperty("selected")) {
        
                        oItemCfg.setProperty("selected", true);
        
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
    
                            oNextItem.cfg.setProperty("selected", true);
    
                            oSubmenu = oNextItem.cfg.getProperty("submenu");
    
                            if (oSubmenu) {
    
                                oSubmenu.show();
                            
                            }
    
                            oNextItem.focus();
                        
                        }
                    
                    }
                
                }
    
    
                Event.preventDefault(oEvent);

                stopMouseEventHandlers();

            break;
    
    
            case 37:    // Left arrow
    
                if (oParentItem) {
    
                    oParentMenu = oParentItem.parent;
    
                    if (oParentMenu instanceof YAHOO.widget.MenuBar) {
    
                        oNextItem = 
                            oParentMenu.activeItem.getPreviousEnabledSibling();
    
                        if (oNextItem) {
                        
                            oParentMenu.clearActiveItem();
    
                            oNextItem.cfg.setProperty("selected", true);
    
                            oSubmenu = oNextItem.cfg.getProperty("submenu");
    
                            if (oSubmenu) {
                            
                                oSubmenu.show();
                            
                            }
    
                            oNextItem.focus();
                        
                        } 
                    
                    }
                    else {
    
                        this.hide();
    
                        oParentItem.focus();
                    
                    }
    
                }
    
                Event.preventDefault(oEvent);

                stopMouseEventHandlers();

            break;        
    
        }


    }


    if (oEvent.keyCode == 27) { // Esc key

        if (this.cfg.getProperty("position") == "dynamic") {
        
            this.hide();

            if (this.parent) {

                this.parent.focus();
            
            }

        }
        else if (this.activeItem) {

            oSubmenu = this.activeItem.cfg.getProperty("submenu");

            if (oSubmenu && oSubmenu.cfg.getProperty("visible")) {
            
                oSubmenu.hide();
                this.activeItem.focus();
            
            }
            else {

                this.activeItem.blur();
                this.activeItem.cfg.setProperty("selected", false);
        
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
    
            nY = (this.cfg.getProperty("y") - nScrollTop);
            
            Dom.setY(this.element, nY);

            oIFrame = this.iframe;            
    

            if (oIFrame) {
    
                Dom.setY(oIFrame, nY);
    
            }
            
            this.cfg.setProperty("y", nY, true);
        
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

    this._cancelHideDelay();

    var oTarget = Event.getTarget(p_oEvent),
        oBody = this.body,
        me = this,
        nScrollIncrement = this.cfg.getProperty("scrollincrement"),
        nScrollTarget,
        fnScrollFunction;


    function scrollBodyDown() {

        var nScrollTop = oBody.scrollTop;


        if (nScrollTop < nScrollTarget) {

            oBody.scrollTop = (nScrollTop + nScrollIncrement);

            me._enableScrollHeader();

        }
        else {

            oBody.scrollTop = nScrollTarget;
            
            window.clearInterval(me._nBodyScrollId);

            me._disableScrollFooter();

        }

    }


    function scrollBodyUp() {

        var nScrollTop = oBody.scrollTop;


        if (nScrollTop > 0) {

            oBody.scrollTop = (nScrollTop - nScrollIncrement);

            me._enableScrollFooter();

        }
        else {

            oBody.scrollTop = 0;
            
            window.clearInterval(me._nBodyScrollId);

            me._disableScrollHeader();

        }

    }

    
    if (Dom.hasClass(oTarget, "hd")) {

        fnScrollFunction = scrollBodyUp;
    
    }
    else {

        nScrollTarget = oBody.scrollHeight - oBody.offsetHeight;

        fnScrollFunction = scrollBodyDown;
    
    }


    this._nBodyScrollId = window.setInterval(fnScrollFunction, 10);

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

    window.clearInterval(this._nBodyScrollId);

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

    this.cfg.subscribeToConfigEvent("visible", this._onVisibleChange);

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
        (bRootMenu && (this.cfg.getProperty("visible") || 
        this.cfg.getProperty("position") == "static")) || 
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
        
                    Dom.addClass(oUL, "first-of-type");
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


                    Dom.addClass(oUL, "hastitle");

                }

            }

            i++;

        }
        while(i < nListElements);

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

    if (this.cfg.getProperty("position") == "dynamic") { 

        if (!this.cfg.getProperty("visible")) {

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
        nViewportHeight,
        oRegion,
        oSrcElement;


    if (this.lazyLoad && this.getItemGroups().length === 0) {

        if (this.srcElement) {
        
            this._initSubTree();

        }


        if (this.itemData) {

            if (this.parent && this.parent.parent && 
                this.parent.parent.srcElement && 
                this.parent.parent.srcElement.tagName.toUpperCase() == 
                "SELECT") {

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

            if (oSrcElement.tagName.toUpperCase() == "SELECT") {

                if (Dom.inDocument(oSrcElement)) {

                    this.render(oSrcElement.parentNode);
                
                }
                else {
                
                    this.render(this.cfg.getProperty("container"));
                
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

                this.render(this.cfg.getProperty("container"));

            }                

        }

    }


    var nMaxHeight = this.cfg.getProperty("maxheight"),
        nMinScrollHeight = this.cfg.getProperty("minscrollheight"),
        bDynamicPos = this.cfg.getProperty("position") == "dynamic";


    if (!this.parent && bDynamicPos) {

        this.cfg.refireEvent("xy");
   
    }


    function clearMaxHeight() {
    
        this.cfg.setProperty("maxheight", 0);
    
        this.hideEvent.unsubscribe(clearMaxHeight);
    
    }


    if (!(this instanceof YAHOO.widget.MenuBar) && bDynamicPos) {


        if (nMaxHeight === 0) {

            nViewportHeight = Dom.getViewportHeight();
    
    
            if (this.parent && 
                this.parent.parent instanceof YAHOO.widget.MenuBar) {
               
                oRegion = YAHOO.util.Region.getRegion(this.parent.element);
                
                nViewportHeight = (nViewportHeight - oRegion.bottom);
    
            }
    
    
            if (this.element.offsetHeight >= nViewportHeight) {
    
                nMaxHeight = (nViewportHeight - (Overlay.VIEWPORT_OFFSET * 2));

                if (nMaxHeight < nMinScrollHeight) {

                    nMaxHeight = nMinScrollHeight;
                
                }

                this.cfg.setProperty("maxheight", nMaxHeight);

                this.hideEvent.subscribe(clearMaxHeight);

            }
        
        }

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
        aParentAlignment,
        aAlignment;


    function disableAutoSubmenuDisplay(p_oEvent) {

        var oTarget;

        if (p_oEvent.type == "mousedown" || (p_oEvent.type == "keydown" && 
            p_oEvent.keyCode == 27)) {

            /*  
                Set the "autosubmenudisplay" to "false" if the user
                clicks outside the menu bar.
            */

            oTarget = Event.getTarget(p_oEvent);

            if (oTarget != oParentMenu.element || 
                !Dom.isAncestor(oParentMenu.element, oTarget)) {

                oParentMenu.cfg.setProperty("autosubmenudisplay", false);

                Event.removeListener(document, "mousedown", 
                        disableAutoSubmenuDisplay);

                Event.removeListener(document, "keydown", 
                        disableAutoSubmenuDisplay);

            }
        
        }

    }


    if (oParent) {

        oParentMenu = oParent.parent;
        aParentAlignment = oParentMenu.cfg.getProperty("submenualignment");
        aAlignment = this.cfg.getProperty("submenualignment");


        if ((aParentAlignment[0] != aAlignment[0]) &&
            (aParentAlignment[1] != aAlignment[1])) {

            this.cfg.setProperty("submenualignment", 
                [aParentAlignment[0], aParentAlignment[1]]);
        
        }


        if (!oParentMenu.cfg.getProperty("autosubmenudisplay") && 
            (oParentMenu instanceof YAHOO.widget.MenuBar || 
            oParentMenu.cfg.getProperty("position") == "static")) {

            oParentMenu.cfg.setProperty("autosubmenudisplay", true);

            Event.on(document, "mousedown", disableAutoSubmenuDisplay);                             
            Event.on(document, "keydown", disableAutoSubmenuDisplay);

        }

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
        oConfig,
        oSubmenu;

    if (oActiveItem) {

        oConfig = oActiveItem.cfg;

        oConfig.setProperty("selected", false);

        oSubmenu = oConfig.getProperty("submenu");

        if (oSubmenu) {

            oSubmenu.hide();

        }

    }

    if (this.getRoot() == this) {

        this.blur();
    
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

        case "iframe":
        case "constraintoviewport":
        case "hidedelay":
        case "showdelay":
        case "submenuhidedelay":
        case "clicktohide":
        case "effect":
        case "classname":
        case "scrollincrement":
        case "minscrollheight":

            p_oSubmenu.cfg.setProperty(sPropertyName, oPropertyValue);
                
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

    var oParentCfg = p_oSubmenu.parent.parent.cfg,

        oConfig = {

            constraintoviewport: oParentCfg.getProperty("constraintoviewport"),

            xy: [0,0],

            clicktohide: oParentCfg.getProperty("clicktohide"),
                
            effect: oParentCfg.getProperty("effect"),

            showdelay: oParentCfg.getProperty("showdelay"),
            
            hidedelay: oParentCfg.getProperty("hidedelay"),

            submenuhidedelay: oParentCfg.getProperty("submenuhidedelay"),

            classname: oParentCfg.getProperty("classname"),
            
            scrollincrement: oParentCfg.getProperty("scrollincrement"),
            
            minscrollheight: oParentCfg.getProperty("minscrollheight"),
            
            iframe: oParentCfg.getProperty("iframe")

        },
        
        oLI;


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
* @method _onSubmenuBeforeShow
* @description "beforeshow" event handler for a submenu.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onSubmenuBeforeShow: function (p_sType, p_aArgs) {

    var oParent = this.parent,
        aAlignment = oParent.parent.cfg.getProperty("submenualignment");


    if (!this.cfg.getProperty("context")) {
    
        this.cfg.setProperty("context", 
            [oParent.element, aAlignment[0], aAlignment[1]]);

    }
    else {

        this.align();
    
    }

},


/**
* @method _onMenuItemFocus
* @description "focus" event handler for the menu's items.
* @private
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onMenuItemFocus: function (p_sType, p_aArgs) {

    this.parent.focusEvent.fire(this);

},


/**
* @method _onMenuItemBlur
* @description "blur" event handler for the menu's items.
* @private
* @param {String} p_sType String representing the name of the event 
* that was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
_onMenuItemBlur: function (p_sType, p_aArgs) {

    this.parent.blurEvent.fire(this);

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

        case "selected":

            if (oPropertyValue === true) {

                this.activeItem = p_oItem;
            
            }

        break;

        case "submenu":

            oSubmenu = p_aArgs[0][1];

            if (oSubmenu) {

                this._configureSubmenu(p_oItem);

            }

        break;

    }

},



// Public event handlers for configuration properties


/**
* @method enforceConstraints
* @description The default event handler executed when the moveEvent is fired,  
* if the "constraintoviewport" configuration property is set to true.
* @param {String} type The name of the event that was fired.
* @param {Array} args Collection of arguments sent when the 
* event was fired.
* @param {Array} obj Array containing the current Menu instance 
* and the item that fired the event.
*/
enforceConstraints: function (type, args, obj) {

	YAHOO.widget.Menu.superclass.enforceConstraints.apply(this, arguments);
	
	var oParent = this.parent,
		oParentMenu,
		nParentMenuX,
		nNewX,
		nX;
	
	
	if (oParent) {
	
		oParentMenu = oParent.parent;

		if (!(oParentMenu instanceof YAHOO.widget.MenuBar)) {
	
			nParentMenuX = oParentMenu.cfg.getProperty("x");
			nX = this.cfg.getProperty("x");
		
	
			if (nX < (nParentMenuX + oParent.element.offsetWidth)) {

				nNewX = (nParentMenuX - this.element.offsetWidth);
			
				this.cfg.setProperty("x",  nNewX, true);
				this.cfg.setProperty("xy", [nNewX, (this.cfg.getProperty("y"))], true);
			
			}
		
		}
	
	}

},


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

    if (this.cfg.getProperty("position") == "dynamic") {

        Menu.superclass.configVisible.call(this, p_sType, p_aArgs, p_oMenu);

    }
    else {

        bVisible = p_aArgs[0];
        sDisplay = Dom.getStyle(this.element, "display");

        Dom.setStyle(this.element, "visibility", "visible");

        if (bVisible) {

            if (sDisplay != "block") {
                this.beforeShowEvent.fire();
                Dom.setStyle(this.element, "display", "block");
                this.showEvent.fire();
            }
        
        }
        else {

			if (sDisplay == "block") {
				this.beforeHideEvent.fire();
				Dom.setStyle(this.element, "display", "none");
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
        sCSSPosition = p_aArgs[0] == "static" ? "static" : "absolute",
        oCfg = this.cfg,
        nZIndex;


    Dom.setStyle(oElement, "position", sCSSPosition);


    if (sCSSPosition == "static") {

        // Statically positioned menus are visible by default
        
        Dom.setStyle(oElement, "display", "block");

        oCfg.setProperty("visible", true);

    }
    else {

        /*
            Even though the "visible" property is queued to 
            "false" by default, we need to set the "visibility" property to 
            "hidden" since Overlay's "configVisible" implementation checks the 
            element's "visibility" style property before deciding whether 
            or not to show an Overlay instance.
        */

        Dom.setStyle(oElement, "visibility", "hidden");
    
    }


    if (sCSSPosition == "absolute") {

        nZIndex = oCfg.getProperty("zindex");

        if (!nZIndex || nZIndex === 0) {

            nZIndex = this.parent ? 
                (this.parent.parent.cfg.getProperty("zindex") + 1) : 1;

            oCfg.setProperty("zindex", nZIndex);

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

    if (this.cfg.getProperty("position") == "dynamic") {

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

    var nHideDelay = p_aArgs[0],
        oMouseOutEvent = this.mouseOutEvent,
        oMouseOverEvent = this.mouseOverEvent,
        oKeyDownEvent = this.keyDownEvent;

    if (nHideDelay > 0) {

        /*
            Only assign event handlers once. This way the user change 
            the value for the hidedelay as many times as they want.
        */

        if (!this._bHideDelayEventHandlersAssigned) {

            oMouseOutEvent.subscribe(this._execHideDelay);
            oMouseOverEvent.subscribe(this._cancelHideDelay);
            oKeyDownEvent.subscribe(this._cancelHideDelay);

            this._bHideDelayEventHandlersAssigned = true;
        
        }

    }
    else {

        oMouseOutEvent.unsubscribe(this._execHideDelay);
        oMouseOverEvent.unsubscribe(this._cancelHideDelay);
        oKeyDownEvent.unsubscribe(this._cancelHideDelay);

        this._bHideDelayEventHandlersAssigned = false;

    }

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

	if (typeof oElement == 'string') {

        this.cfg.setProperty("container", document.getElementById(oElement), 
                true);

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

    this.cfg.setProperty("maxheight", p_nMaxHeight);
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

    var nMaxHeight = p_aArgs[0],
        oElement = this.element,
        oBody = this.body,
        oHeader = this.header,
        oFooter = this.footer,
        fnMouseOver = this._onScrollTargetMouseOver,
        fnMouseOut = this._onScrollTargetMouseOut,
        nMinScrollHeight = this.cfg.getProperty("minscrollheight"),
        nHeight,
        nOffsetWidth,
        sWidth;


    if (nMaxHeight !== 0 && nMaxHeight < nMinScrollHeight) {
    
        nMaxHeight = nMinScrollHeight;
    
    }


    if (this.lazyLoad && !oBody) {

        this.renderEvent.unsubscribe(this._setMaxHeight);
    
        if (nMaxHeight > 0) {

            this.renderEvent.subscribe(this._setMaxHeight, nMaxHeight, this);

        }

        return;
    
    }


    Dom.setStyle(oBody, "height", "");
    Dom.removeClass(oBody, "yui-menu-body-scrolled");


    /*
        There is a bug in gecko-based browsers where an element whose 
        "position" property is set to "absolute" and "overflow" property is set 
        to "hidden" will not render at the correct width when its 
        offsetParent's "position" property is also set to "absolute."  It is 
        possible to work around this bug by specifying a value for the width 
        property in addition to overflow.

		In IE it is also necessary to give the Menu a width when the scrollbars are 
		rendered to prevent the Menu from rendering with a width that is 100% of
		the browser viewport.
    */

	var bSetWidth = ((UA.gecko && this.parent && this.parent.parent && 
        this.parent.parent.cfg.getProperty("position") == "dynamic") || UA.ie);


    if (bSetWidth) {

		if (!this.cfg.getProperty("width")) {

			nOffsetWidth = oElement.offsetWidth;
	
			/*
				Measuring the difference of the offsetWidth before and after
				setting the "width" style attribute allows us to compute the 
				about of padding and borders applied to the element, which in 
				turn allows us to set the "width" property correctly.
			*/
			
			oElement.style.width = nOffsetWidth + "px";
	
			sWidth = (nOffsetWidth - (oElement.offsetWidth - nOffsetWidth)) + "px";
	
			this.cfg.setProperty("width", sWidth);
		
		}

    }


    if (!oHeader && !oFooter) {

        this.setHeader("&#32;");
        this.setFooter("&#32;");

        oHeader = this.header;
        oFooter = this.footer;

        Dom.addClass(oHeader, "topscrollbar");
        Dom.addClass(oFooter, "bottomscrollbar");
        
        oElement.insertBefore(oHeader, oBody);
        oElement.appendChild(oFooter);
    
    }


    nHeight = (nMaxHeight - (oHeader.offsetHeight + oHeader.offsetHeight));


    if (nHeight > 0 && (oBody.offsetHeight > nMaxHeight)) {

        Dom.addClass(oBody, "yui-menu-body-scrolled");
        Dom.setStyle(oBody, "height", (nHeight + "px"));

        Event.on(oHeader, "mouseover", fnMouseOver, this, true);
        Event.on(oHeader, "mouseout", fnMouseOut, this, true);
        Event.on(oFooter, "mouseover", fnMouseOver, this, true);
        Event.on(oFooter, "mouseout", fnMouseOut, this, true);

        this._disableScrollHeader();
        this._enableScrollFooter();

    }
    else if (oHeader && oFooter) {

		if (bSetWidth) {

			this.cfg.setProperty("width", "");
		
		}


        this._enableScrollHeader();
        this._enableScrollFooter();

        Event.removeListener(oHeader, "mouseover", fnMouseOver);
        Event.removeListener(oHeader, "mouseout", fnMouseOut);
        Event.removeListener(oFooter, "mouseover", fnMouseOver);
        Event.removeListener(oFooter, "mouseout", fnMouseOut);

        oElement.removeChild(oHeader);
        oElement.removeChild(oFooter);

        this.header = null;
        this.footer = null;
    
    }

    this.cfg.refireEvent("iframe");

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

        oItem.cfg.setProperty("disabled", true);
    
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
    
                aItems[i].cfg.setProperty("disabled", bDisabled);
            
            }
            while (i--);
        
        }


        if (bDisabled) {

            this.clearActiveItem(true);

            Dom.addClass(this.element, "disabled");

            this.itemAddedEvent.subscribe(this._onItemAdded);

        }
        else {

            Dom.removeClass(this.element, "disabled");

            this.itemAddedEvent.unsubscribe(this._onItemAdded);

        }
        
    }

},


/**
* @method onRender
* @description "render" event handler for the menu.
* @param {String} p_sType String representing the name of the event that 
* was fired.
* @param {Array} p_aArgs Array of arguments sent when the event was fired.
*/
onRender: function (p_sType, p_aArgs) {

    function sizeShadow() {

        var oElement = this.element,
            oShadow = this._shadow;
    
        if (oShadow && oElement) {

            oShadow.style.width = (oElement.offsetWidth + 6) + "px";
            oShadow.style.height = (oElement.offsetHeight + 1) + "px";
            
        }
    
    }


    function replaceShadow() {

        this.element.appendChild(this._shadow);

    }


    function addShadowVisibleClass() {
    
        Dom.addClass(this._shadow, "yui-menu-shadow-visible");
    
    }
    

    function removeShadowVisibleClass() {

        Dom.removeClass(this._shadow, "yui-menu-shadow-visible");
    
    }


    function createShadow() {

        var oShadow = this._shadow,
            oElement,
            me;

        if (!oShadow) {

            oElement = this.element;
            me = this;

            if (!m_oShadowTemplate) {

                m_oShadowTemplate = document.createElement("div");
                m_oShadowTemplate.className = 
                    "yui-menu-shadow yui-menu-shadow-visible";
            
            }

            oShadow = m_oShadowTemplate.cloneNode(false);

            oElement.appendChild(oShadow);
            
            this._shadow = oShadow;

            this.beforeShowEvent.subscribe(addShadowVisibleClass);
            this.beforeHideEvent.subscribe(removeShadowVisibleClass);

            if (UA.ie) {
        
                /*
                     Need to call sizeShadow & syncIframe via setTimeout for 
                     IE 7 Quirks Mode and IE 6 Standards Mode and Quirks Mode 
                     or the shadow and iframe shim will not be sized and 
                     positioned properly.
                */
        
                window.setTimeout(function () { 
        
                    sizeShadow.call(me); 
                    me.syncIframe();
        
                }, 0);

                this.cfg.subscribeToConfigEvent("width", sizeShadow);
                this.cfg.subscribeToConfigEvent("height", sizeShadow);
                this.cfg.subscribeToConfigEvent("maxheight", sizeShadow);
                this.changeContentEvent.subscribe(sizeShadow);

                Module.textResizeEvent.subscribe(sizeShadow, me, true);
                
                this.destroyEvent.subscribe(function () {
                
                    Module.textResizeEvent.unsubscribe(sizeShadow, me);
                
                });
        
            }

            this.cfg.subscribeToConfigEvent("maxheight", replaceShadow);

        }

    }


    function onBeforeShow() {
    
        createShadow.call(this);

        this.beforeShowEvent.unsubscribe(onBeforeShow);
    
    }


    if (this.cfg.getProperty("position") == "dynamic") {

        if (this.cfg.getProperty("visible")) {

            createShadow.call(this);
        
        }
        else {

            this.beforeShowEvent.subscribe(onBeforeShow);
        
        }
    
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

    var SIGNATURE = CustomEvent.LIST;

    this.mouseOverEvent = this.createEvent(EVENT_TYPES.MOUSE_OVER);
    this.mouseOverEvent.signature = SIGNATURE;

    this.mouseOutEvent = this.createEvent(EVENT_TYPES.MOUSE_OUT);
    this.mouseOutEvent.signature = SIGNATURE;
    
    this.mouseDownEvent = this.createEvent(EVENT_TYPES.MOUSE_DOWN);
    this.mouseDownEvent.signature = SIGNATURE;

    this.mouseUpEvent = this.createEvent(EVENT_TYPES.MOUSE_UP);
    this.mouseUpEvent.signature = SIGNATURE;
    
    this.clickEvent = this.createEvent(EVENT_TYPES.CLICK);
    this.clickEvent.signature = SIGNATURE;
    
    this.keyPressEvent = this.createEvent(EVENT_TYPES.KEY_PRESS);
    this.keyPressEvent.signature = SIGNATURE;
    
    this.keyDownEvent = this.createEvent(EVENT_TYPES.KEY_DOWN);
    this.keyDownEvent.signature = SIGNATURE;
    
    this.keyUpEvent = this.createEvent(EVENT_TYPES.KEY_UP);
    this.keyUpEvent.signature = SIGNATURE;
    
    this.focusEvent = this.createEvent(EVENT_TYPES.FOCUS);
    this.focusEvent.signature = SIGNATURE;
    
    this.blurEvent = this.createEvent(EVENT_TYPES.BLUR);
    this.blurEvent.signature = SIGNATURE;
    
    this.itemAddedEvent = this.createEvent(EVENT_TYPES.ITEM_ADDED);
    this.itemAddedEvent.signature = SIGNATURE;
    
    this.itemRemovedEvent = this.createEvent(EVENT_TYPES.ITEM_REMOVED);
    this.itemRemovedEvent.signature = SIGNATURE;

},


/**
* @method positionOffScreen
* @description Positions the menu outside of the boundaries of the browser's 
* viewport.  Called automatically when a menu is hidden to ensure that 
* it doesn't force the browser to render uncessary scrollbars.
*/
positionOffScreen: function () {

    var oIFrame = this.iframe,
        aPos = this.OFF_SCREEN_POSITION;

    Dom.setXY(this.element, aPos);
    
    if (oIFrame) {

        Dom.setXY(oIFrame, aPos);
    
    }

},


/**
* @method getRoot
* @description Finds the menu's root menu.
*/
getRoot: function () {

    var oItem = this.parent,
        oParentMenu;

    if (oItem) {

        oParentMenu = oItem.parent;

        return oParentMenu ? oParentMenu.getRoot() : this;

    }
    else {
    
        return this;
    
    }

},


/**
* @method toString
* @description Returns a string representing the menu.
* @return {String}
*/
toString: function () {

    var sReturnVal = "Menu",
        sId = this.id;

    if (sId) {

        sReturnVal += (" " + sId);
    
    }

    return sReturnVal;

},


/**
* @method setItemGroupTitle
* @description Sets the title of a group of menu items.
* @param {String} p_sGroupTitle String specifying the title of the group.
* @param {Number} p_nGroupIndex Optional. Number specifying the group to which
* the title belongs.
*/
setItemGroupTitle: function (p_sGroupTitle, p_nGroupIndex) {

    var nGroupIndex,
        oTitle,
        i,
        nFirstIndex;
        
    if (typeof p_sGroupTitle == "string" && p_sGroupTitle.length > 0) {

        nGroupIndex = typeof p_nGroupIndex == "number" ? p_nGroupIndex : 0;
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

                Dom.removeClass(this._aGroupTitleElements[i], "first-of-type");

                nFirstIndex = i;

            }

        }
        while(i--);


        if (nFirstIndex !== null) {

            Dom.addClass(this._aGroupTitleElements[nFirstIndex], 
                "first-of-type");

        }

        this.changeContentEvent.fire();

    }

},



/**
* @method addItem
* @description Appends an item to the menu.
* @param {YAHOO.widget.MenuItem} p_oItem Object reference for the MenuItem 
* instance to be added to the menu.
* @param {String} p_oItem String specifying the text of the item to be added 
* to the menu.
* @param {Object} p_oItem Object literal containing a set of menu item 
* configuration properties.
* @param {Number} p_nGroupIndex Optional. Number indicating the group to
* which the item belongs.
* @return {YAHOO.widget.MenuItem}
*/
addItem: function (p_oItem, p_nGroupIndex) {

    if (p_oItem) {

        return this._addItemToGroup(p_nGroupIndex, p_oItem);
        
    }

},


/**
* @method addItems
* @description Adds an array of items to the menu.
* @param {Array} p_aItems Array of items to be added to the menu.  The array 
* can contain strings specifying the text for each item to be created, object
* literals specifying each of the menu item configuration properties, 
* or MenuItem instances.
* @param {Number} p_nGroupIndex Optional. Number specifying the group to 
* which the items belongs.
* @return {Array}
*/
addItems: function (p_aItems, p_nGroupIndex) {

    var nItems,
        aItems,
        oItem,
        i;

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
    
                    aItems[aItems.length] = 
                        this._addItemToGroup(p_nGroupIndex, oItem);
                
                }

            }
    
        }


        if (aItems.length) {
        
            return aItems;
        
        }

    }

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
    
    if (p_oItem) {

        return this._addItemToGroup(p_nGroupIndex, p_oItem, p_nItemIndex);

    }

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

    var oItem;
    
    if (typeof p_oObject != "undefined") {

        if (p_oObject instanceof YAHOO.widget.MenuItem) {

            oItem = this._removeItemFromGroupByValue(p_nGroupIndex, p_oObject);           

        }
        else if (typeof p_oObject == "number") {

            oItem = this._removeItemFromGroupByIndex(p_nGroupIndex, p_oObject);

        }

        if (oItem) {

            oItem.destroy();

            this.logger.log("Item removed." + 
                " Text: " + oItem.cfg.getProperty("text") + ", " + 
                " Index: " + oItem.index + ", " + 
                " Group Index: " + oItem.groupIndex);

            return oItem;

        }

    }

},


/**
* @method getItems
* @description Returns an array of all of the items in the menu.
* @return {Array}
*/
getItems: function () {

    var aGroups = this._aItemGroups,
        nGroups,
        aItems = [];

    if (Lang.isArray(aGroups)) {

        nGroups = aGroups.length;

        return ((nGroups == 1) ? aGroups[0] : 
                    (Array.prototype.concat.apply(aItems, aGroups)));

    }

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
    
    var aGroup;
    
    if (typeof p_nItemIndex == "number") {

        aGroup = this._getItemGroup(p_nGroupIndex);

        if (aGroup) {

            return aGroup[p_nItemIndex];
        
        }

    }
    
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

                oSubmenu = oItem.cfg.getProperty("submenu");
                
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

                oSubmenu = oItem.cfg.getProperty("submenu");

                if (oSubmenu) {

                    this.cfg.configChangedEvent.unsubscribe(
                        this._onParentMenuConfigChange, oSubmenu);

                    this.renderEvent.unsubscribe(this._onParentMenuRender, 
                        oSubmenu);

                }
                
                this.removeItem(oItem);

            }
        
        }
        while(i--);

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

        oBody.innerHTML = "";

    }

    this.activeItem = null;

    this._aItemGroups = [];
    this._aListElements = [];
    this._aGroupTitleElements = [];

    this.cfg.setProperty("width", null);

},


/**
* @method destroy
* @description Removes the menu's <code>&#60;div&#62;</code> element 
* (and accompanying child nodes) from the document.
*/
destroy: function () {

    // Remove all items

    this.clearContent();

    this._aItemGroups = null;
    this._aListElements = null;
    this._aGroupTitleElements = null;


    // Continue with the superclass implementation of this method

    Menu.superclass.destroy.call(this);
    
    this.logger.log("Destroyed.");

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
    
        oItem.cfg.setProperty("selected", true);
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

    if (this.cfg.getProperty("showdelay") > 0) {
    
        this._cancelShowDelay();
    
    }


    var oActiveItem = this.activeItem,
        oConfig,
        oSubmenu;

    if (oActiveItem) {

        oConfig = oActiveItem.cfg;

        if (p_bBlur) {

            oActiveItem.blur();
        
        }

        oConfig.setProperty("selected", false);

        oSubmenu = oConfig.getProperty("submenu");

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

    function onItemAdded(p_sType, p_aArgs, p_oObject) {

        var oItem = p_aArgs[0],
            oSubmenu = oItem.cfg.getProperty("submenu");

        if (oSubmenu) {

            oSubmenu.subscribe.apply(oSubmenu, p_oObject);

        }
    
    }


    function onSubmenuAdded(p_sType, p_aArgs, p_oObject) { 
    
        var oSubmenu = this.cfg.getProperty("submenu");
        
        if (oSubmenu) {

            oSubmenu.subscribe.apply(oSubmenu, p_oObject);
        
        }
    
    }


    Menu.superclass.subscribe.apply(this, arguments);
    Menu.superclass.subscribe.call(this, "itemAdded", onItemAdded, arguments);


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
                
                oSubmenu = oItem.cfg.getProperty("submenu");
                
                if (oSubmenu) {
                
                    oSubmenu.subscribe.apply(oSubmenu, arguments);
                
                }
                else {
                
                    oItem.cfg.subscribeToConfigEvent("submenu", onSubmenuAdded, arguments);
                
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
    * align the Mnu's top left corner to the context element's 
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
    * @config zindex
    * @description Number representing the CSS z-index of the Menu.  This 
    * property is only applied when the "position" configuration property is 
    * set to dynamic.
    * @type Number
    * @default null
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
        DEFAULT_CONFIG.VISIBLE.key, 
        {
            handler: this.configVisible, 
            value: DEFAULT_CONFIG.VISIBLE.value, 
            validator: DEFAULT_CONFIG.VISIBLE.validator
         }
     );


    /*
        Change the default value for the "constraintoviewport" configuration 
        property to "true" by re-adding the property.
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
        DEFAULT_CONFIG.CONSTRAIN_TO_VIEWPORT.key, 
        {
            handler: this.configConstrainToViewport, 
            value: DEFAULT_CONFIG.CONSTRAIN_TO_VIEWPORT.value, 
            validator: DEFAULT_CONFIG.CONSTRAIN_TO_VIEWPORT.validator, 
            supercedes: DEFAULT_CONFIG.CONSTRAIN_TO_VIEWPORT.supercedes 
        } 
    );


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
        DEFAULT_CONFIG.POSITION.key, 
        {
            handler: this.configPosition,
            value: DEFAULT_CONFIG.POSITION.value, 
            validator: DEFAULT_CONFIG.POSITION.validator,
            supercedes: DEFAULT_CONFIG.POSITION.supercedes
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
        DEFAULT_CONFIG.SUBMENU_ALIGNMENT.key, 
        { 
            value: DEFAULT_CONFIG.SUBMENU_ALIGNMENT.value,
            suppressEvent: DEFAULT_CONFIG.SUBMENU_ALIGNMENT.suppressEvent
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
	   DEFAULT_CONFIG.AUTO_SUBMENU_DISPLAY.key, 
	   { 
	       value: DEFAULT_CONFIG.AUTO_SUBMENU_DISPLAY.value, 
	       validator: DEFAULT_CONFIG.AUTO_SUBMENU_DISPLAY.validator,
	       suppressEvent: DEFAULT_CONFIG.AUTO_SUBMENU_DISPLAY.suppressEvent
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
	   DEFAULT_CONFIG.SHOW_DELAY.key, 
	   { 
	       value: DEFAULT_CONFIG.SHOW_DELAY.value, 
	       validator: DEFAULT_CONFIG.SHOW_DELAY.validator,
	       suppressEvent: DEFAULT_CONFIG.SHOW_DELAY.suppressEvent
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
	   DEFAULT_CONFIG.HIDE_DELAY.key, 
	   { 
	       handler: this.configHideDelay,
	       value: DEFAULT_CONFIG.HIDE_DELAY.value, 
	       validator: DEFAULT_CONFIG.HIDE_DELAY.validator, 
	       suppressEvent: DEFAULT_CONFIG.HIDE_DELAY.suppressEvent
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
	   DEFAULT_CONFIG.SUBMENU_HIDE_DELAY.key, 
	   { 
	       value: DEFAULT_CONFIG.SUBMENU_HIDE_DELAY.value, 
	       validator: DEFAULT_CONFIG.SUBMENU_HIDE_DELAY.validator,
	       suppressEvent: DEFAULT_CONFIG.SUBMENU_HIDE_DELAY.suppressEvent
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
        DEFAULT_CONFIG.CLICK_TO_HIDE.key,
        {
            value: DEFAULT_CONFIG.CLICK_TO_HIDE.value,
            validator: DEFAULT_CONFIG.CLICK_TO_HIDE.validator,
            suppressEvent: DEFAULT_CONFIG.CLICK_TO_HIDE.suppressEvent
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
	   DEFAULT_CONFIG.CONTAINER.key, 
	   { 
	       handler: this.configContainer,
	       value: document.body,
           suppressEvent: DEFAULT_CONFIG.CONTAINER.suppressEvent
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
        DEFAULT_CONFIG.SCROLL_INCREMENT.key, 
        { 
            value: DEFAULT_CONFIG.SCROLL_INCREMENT.value, 
            validator: DEFAULT_CONFIG.SCROLL_INCREMENT.validator,
            supercedes: DEFAULT_CONFIG.SCROLL_INCREMENT.supercedes,
            suppressEvent: DEFAULT_CONFIG.SCROLL_INCREMENT.suppressEvent
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
        DEFAULT_CONFIG.MIN_SCROLL_HEIGHT.key, 
        { 
            value: DEFAULT_CONFIG.MIN_SCROLL_HEIGHT.value, 
            validator: DEFAULT_CONFIG.MIN_SCROLL_HEIGHT.validator,
            supercedes: DEFAULT_CONFIG.MIN_SCROLL_HEIGHT.supercedes,
            suppressEvent: DEFAULT_CONFIG.MIN_SCROLL_HEIGHT.suppressEvent
        }
    );


    /**
    * @config maxheight
    * @description Number defining the maximum height (in pixels) for a menu's 
    * body element (<code>&#60;div class="bd"&#60;</code>).  Once a menu's body 
    * exceeds this height, the contents of the body are scrolled to maintain 
    * this value.  This value cannot be set lower than the value of the 
    * "minscrollheight" configuration property.
    * @default 0
    * @type Number
    */
    oConfig.addProperty(
       DEFAULT_CONFIG.MAX_HEIGHT.key, 
       {
            handler: this.configMaxHeight,
            value: DEFAULT_CONFIG.MAX_HEIGHT.value,
            validator: DEFAULT_CONFIG.MAX_HEIGHT.validator,
            suppressEvent: DEFAULT_CONFIG.MAX_HEIGHT.suppressEvent,
            supercedes: DEFAULT_CONFIG.MAX_HEIGHT.supercedes            
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
        DEFAULT_CONFIG.CLASS_NAME.key, 
        { 
            handler: this.configClassName,
            value: DEFAULT_CONFIG.CLASS_NAME.value, 
            validator: DEFAULT_CONFIG.CLASS_NAME.validator,
            supercedes: DEFAULT_CONFIG.CLASS_NAME.supercedes      
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
        DEFAULT_CONFIG.DISABLED.key, 
        { 
            handler: this.configDisabled,
            value: DEFAULT_CONFIG.DISABLED.value, 
            validator: DEFAULT_CONFIG.DISABLED.validator,
            suppressEvent: DEFAULT_CONFIG.DISABLED.suppressEvent
        }
    );

}

}); // END YAHOO.lang.extend

})();



(function () {


/**
* Creates an item for a menu.
* 
* @param {String} p_oObject String specifying the text of the menu item.
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
    Lang = YAHOO.lang,

    m_oMenuItemTemplate,

    /**
    * Constant representing the name of the MenuItem's events
    * @property EVENT_TYPES
    * @private
    * @final
    * @type Object
    */
    EVENT_TYPES = {
    
        "MOUSE_OVER": "mouseover",
        "MOUSE_OUT": "mouseout",
        "MOUSE_DOWN": "mousedown",
        "MOUSE_UP": "mouseup",
        "CLICK": "click",
        "KEY_PRESS": "keypress",
        "KEY_DOWN": "keydown",
        "KEY_UP": "keyup",
        "ITEM_ADDED": "itemAdded",
        "ITEM_REMOVED": "itemRemoved",
        "FOCUS": "focus",
        "BLUR": "blur",
        "DESTROY": "destroy"
    
    },

    /**
    * Constant representing the MenuItem's configuration properties
    * @property DEFAULT_CONFIG
    * @private
    * @final
    * @type Object
    */
    DEFAULT_CONFIG = {
    
        "TEXT": { 
            key: "text", 
            value: "", 
            validator: Lang.isString, 
            suppressEvent: true 
        }, 
    
        "HELP_TEXT": { 
            key: "helptext",
            supercedes: ["text"], 
            suppressEvent: true 
        },
    
        "URL": { 
            key: "url", 
            value: "#", 
            suppressEvent: true 
        }, 
    
        "TARGET": { 
            key: "target", 
            suppressEvent: true 
        }, 
    
        "EMPHASIS": { 
            key: "emphasis", 
            value: false, 
            validator: Lang.isBoolean, 
            suppressEvent: true, 
            supercedes: ["text"]
        }, 
    
        "STRONG_EMPHASIS": { 
            key: "strongemphasis", 
            value: false, 
            validator: Lang.isBoolean, 
            suppressEvent: true,
            supercedes: ["text"]
        },
    
        "CHECKED": { 
            key: "checked", 
            value: false, 
            validator: Lang.isBoolean, 
            suppressEvent: true, 
            supercedes: ["disabled", "selected"]
        }, 

        "SUBMENU": { 
            key: "submenu",
            suppressEvent: true,
            supercedes: ["disabled", "selected"]
        },
    
        "DISABLED": { 
            key: "disabled", 
            value: false, 
            validator: Lang.isBoolean, 
            suppressEvent: true,
            supercedes: ["text", "selected"]
        },
    
        "SELECTED": { 
            key: "selected", 
            value: false, 
            validator: Lang.isBoolean, 
            suppressEvent: true
        },
    
        "ONCLICK": { 
            key: "onclick",
            suppressEvent: true
        },
    
        "CLASS_NAME": { 
            key: "classname", 
            value: null, 
            validator: Lang.isString,
            suppressEvent: true
        }
    
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
    destroyEvent: null,


    /**
    * @event mouseOverEvent
    * @description Fires when the mouse has entered the menu item.  Passes 
    * back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */
    mouseOverEvent: null,


    /**
    * @event mouseOutEvent
    * @description Fires when the mouse has left the menu item.  Passes back 
    * the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */
    mouseOutEvent: null,


    /**
    * @event mouseDownEvent
    * @description Fires when the user mouses down on the menu item.  Passes 
    * back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */
    mouseDownEvent: null,


    /**
    * @event mouseUpEvent
    * @description Fires when the user releases a mouse button while the mouse 
    * is over the menu item.  Passes back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */
    mouseUpEvent: null,


    /**
    * @event clickEvent
    * @description Fires when the user clicks the on the menu item.  Passes 
    * back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */
    clickEvent: null,


    /**
    * @event keyPressEvent
    * @description Fires when the user presses an alphanumeric key when the 
    * menu item has focus.  Passes back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */
    keyPressEvent: null,


    /**
    * @event keyDownEvent
    * @description Fires when the user presses a key when the menu item has 
    * focus.  Passes back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */
    keyDownEvent: null,


    /**
    * @event keyUpEvent
    * @description Fires when the user releases a key when the menu item has 
    * focus.  Passes back the DOM Event object as an argument.
    * @type YAHOO.util.CustomEvent
    */
    keyUpEvent: null,


    /**
    * @event focusEvent
    * @description Fires when the menu item receives focus.
    * @type YAHOO.util.CustomEvent
    */
    focusEvent: null,


    /**
    * @event blurEvent
    * @description Fires when the menu item loses the input focus.
    * @type YAHOO.util.CustomEvent
    */
    blurEvent: null,


    /**
    * @method init
    * @description The MenuItem class's initialization method. This method is 
    * automatically called by the constructor, and sets up all DOM references 
    * for pre-existing markup, and creates required markup if it is not 
    * already present.
    * @param {String} p_oObject String specifying the text of the menu item.
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

        var SIGNATURE = CustomEvent.LIST,
            oConfig = this.cfg,
            sURL = "#",
            oAnchor,
            sTarget,
            sText,
            sId;


        if (Lang.isString(p_oObject)) {

            this._createRootNodeStructure();

            oConfig.queueProperty("text", p_oObject);

        }
        else if (p_oObject && p_oObject.tagName) {

            switch(p_oObject.tagName.toUpperCase()) {

                case "OPTION":

                    this._createRootNodeStructure();

                    oConfig.queueProperty("text", p_oObject.text);
                    oConfig.queueProperty("disabled", p_oObject.disabled);

                    this.value = p_oObject.value;

                    this.srcElement = p_oObject;

                break;

                case "OPTGROUP":

                    this._createRootNodeStructure();

                    oConfig.queueProperty("text", p_oObject.label);
                    oConfig.queueProperty("disabled", p_oObject.disabled);

                    this.srcElement = p_oObject;

                    this._initSubTree();

                break;

                case "LI":

                    // Get the anchor node (if it exists)
                    
                    oAnchor = Dom.getFirstChild(p_oObject);


                    // Capture the "text" and/or the "URL"

                    if (oAnchor) {

                        sURL = oAnchor.getAttribute("href", 2);
                        sTarget = oAnchor.getAttribute("target");

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

                    oConfig.setProperty("text", sText, true);
                    oConfig.setProperty("url", sURL, true);
                    oConfig.setProperty("target", sTarget, true);

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


            // Create custom events

            this.mouseOverEvent = this.createEvent(EVENT_TYPES.MOUSE_OVER);
            this.mouseOverEvent.signature = SIGNATURE;

            this.mouseOutEvent = this.createEvent(EVENT_TYPES.MOUSE_OUT);
            this.mouseOutEvent.signature = SIGNATURE;

            this.mouseDownEvent = this.createEvent(EVENT_TYPES.MOUSE_DOWN);
            this.mouseDownEvent.signature = SIGNATURE;

            this.mouseUpEvent = this.createEvent(EVENT_TYPES.MOUSE_UP);
            this.mouseUpEvent.signature = SIGNATURE;

            this.clickEvent = this.createEvent(EVENT_TYPES.CLICK);
            this.clickEvent.signature = SIGNATURE;

            this.keyPressEvent = this.createEvent(EVENT_TYPES.KEY_PRESS);
            this.keyPressEvent.signature = SIGNATURE;

            this.keyDownEvent = this.createEvent(EVENT_TYPES.KEY_DOWN);
            this.keyDownEvent.signature = SIGNATURE;

            this.keyUpEvent = this.createEvent(EVENT_TYPES.KEY_UP);
            this.keyUpEvent.signature = SIGNATURE;

            this.focusEvent = this.createEvent(EVENT_TYPES.FOCUS);
            this.focusEvent.signature = SIGNATURE;

            this.blurEvent = this.createEvent(EVENT_TYPES.BLUR);
            this.blurEvent.signature = SIGNATURE;

            this.destroyEvent = this.createEvent(EVENT_TYPES.DESTROY);
            this.destroyEvent.signature = SIGNATURE;

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

            m_oMenuItemTemplate = document.createElement("li");
            m_oMenuItemTemplate.innerHTML = "<a href=\"#\"></a>";

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
                this.parent.srcElement.tagName.toUpperCase() == "SELECT") {

                oConfig.setProperty(
                        "submenu", 
                        { id: Dom.generateId(), itemdata: oSrcEl.childNodes }
                    );

            }
            else {

                oNode = oSrcEl.firstChild;
                aOptions = [];
    
                do {
    
                    if (oNode && oNode.tagName) {
    
                        switch(oNode.tagName.toUpperCase()) {
                
                            case "DIV":
                
                                oConfig.setProperty("submenu", oNode);
                
                            break;
         
                            case "OPTION":
        
                                aOptions[aOptions.length] = oNode;
        
                            break;
               
                        }
                    
                    }
                
                }        
                while((oNode = oNode.nextSibling));
    
    
                nOptions = aOptions.length;
    
                if (nOptions > 0) {
    
                    oMenu = new this.SUBMENU_TYPE(Dom.generateId());
                    
                    oConfig.setProperty("submenu", oMenu);
    
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
            sHelpText = oConfig.getProperty("helptext"),
            sHelpTextHTML = "",
            sEmphasisStartTag = "",
            sEmphasisEndTag = "";


        if (sText) {


            if (sHelpText) {
                    
                sHelpTextHTML = "<em class=\"helptext\">" + sHelpText + "</em>";
            
            }


            if (oConfig.getProperty("emphasis")) {

                sEmphasisStartTag = "<em>";
                sEmphasisEndTag = "</em>";

            }


            if (oConfig.getProperty("strongemphasis")) {

                sEmphasisStartTag = "<strong>";
                sEmphasisEndTag = "</strong>";
            
            }


            oAnchor.innerHTML = (sEmphasisStartTag + sText + 
                sEmphasisEndTag + sHelpTextHTML);

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

        this.cfg.refireEvent("text");

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

            sURL = "#";

        }

        var oAnchor = this._oAnchor;

        if (YAHOO.env.ua.opera) {

            oAnchor.removeAttribute("href");
        
        }

        oAnchor.setAttribute("href", sURL);

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

            oAnchor.setAttribute("target", sTarget);

        }
        else {

            oAnchor.removeAttribute("target");
        
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


        if (bEmphasis && oConfig.getProperty("strongemphasis")) {

            oConfig.setProperty("strongemphasis", false);

        }


        oConfig.refireEvent("text");

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


        if (bStrongEmphasis && oConfig.getProperty("emphasis")) {

            oConfig.setProperty("emphasis", false);

        }

        oConfig.refireEvent("text");

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
            oElement = this.element,
            oAnchor = this._oAnchor,
            oConfig = this.cfg,
            sState = "-checked",
            sClassName = this.CSS_CLASS_NAME + sState,
            sLabelClassName = this.CSS_LABEL_CLASS_NAME + sState;


        if (bChecked) {

            Dom.addClass(oElement, sClassName);
            Dom.addClass(oAnchor, sLabelClassName);

        }
        else {

            Dom.removeClass(oElement, sClassName);
            Dom.removeClass(oAnchor, sLabelClassName);
        
        }


        oConfig.refireEvent("text");


        if (oConfig.getProperty("disabled")) {

            oConfig.refireEvent("disabled");

        }


        if (oConfig.getProperty("selected")) {

            oConfig.refireEvent("selected");

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
            oSubmenu = oConfig.getProperty("submenu"),
            bChecked = oConfig.getProperty("checked"),
            oElement = this.element,
            oAnchor = this._oAnchor,
            sState = "-disabled",
            sCheckedState = "-checked" + sState,
            sSubmenuState = "-hassubmenu" + sState,
            sClassName = this.CSS_CLASS_NAME + sState,
            sLabelClassName = this.CSS_LABEL_CLASS_NAME + sState,
            sCheckedClassName = this.CSS_CLASS_NAME + sCheckedState,
            sLabelCheckedClassName = this.CSS_LABEL_CLASS_NAME + sCheckedState,
            sSubmenuClassName = this.CSS_CLASS_NAME + sSubmenuState,
            sLabelSubmenuClassName = this.CSS_LABEL_CLASS_NAME + sSubmenuState;


        if (bDisabled) {

            if (oConfig.getProperty("selected")) {

                oConfig.setProperty("selected", false);

            }

            Dom.addClass(oElement, sClassName);
            Dom.addClass(oAnchor, sLabelClassName);


            if (oSubmenu) {

                Dom.addClass(oElement, sSubmenuClassName);
                Dom.addClass(oAnchor, sLabelSubmenuClassName);
            
            }
            

            if (bChecked) {

                Dom.addClass(oElement, sCheckedClassName);
                Dom.addClass(oAnchor, sLabelCheckedClassName);

            }

        }
        else {

            Dom.removeClass(oElement, sClassName);
            Dom.removeClass(oAnchor, sLabelClassName);


            if (oSubmenu) {

                Dom.removeClass(oElement, sSubmenuClassName);
                Dom.removeClass(oAnchor, sLabelSubmenuClassName);
            
            }
            

            if (bChecked) {

                Dom.removeClass(oElement, sCheckedClassName);
                Dom.removeClass(oAnchor, sLabelCheckedClassName);

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
            bSelected = p_aArgs[0],
            oElement = this.element,
            oAnchor = this._oAnchor,
            bChecked = oConfig.getProperty("checked"),
            oSubmenu = oConfig.getProperty("submenu"),
            sState = "-selected",
            sCheckedState = "-checked" + sState,
            sSubmenuState = "-hassubmenu" + sState,
            sClassName = this.CSS_CLASS_NAME + sState,
            sLabelClassName = this.CSS_LABEL_CLASS_NAME + sState,
            sCheckedClassName = this.CSS_CLASS_NAME + sCheckedState,
            sLabelCheckedClassName = this.CSS_LABEL_CLASS_NAME + sCheckedState,
            sSubmenuClassName = this.CSS_CLASS_NAME + sSubmenuState,
            sLabelSubmenuClassName = this.CSS_LABEL_CLASS_NAME + sSubmenuState;


        if (YAHOO.env.ua.opera) {

            oAnchor.blur();
        
        }


        if (bSelected && !oConfig.getProperty("disabled")) {

            Dom.addClass(oElement, sClassName);
            Dom.addClass(oAnchor, sLabelClassName);


            if (oSubmenu) {

                Dom.addClass(oElement, sSubmenuClassName);
                Dom.addClass(oAnchor, sLabelSubmenuClassName);
            
            }


            if (bChecked) {

                Dom.addClass(oElement, sCheckedClassName);
                Dom.addClass(oAnchor, sLabelCheckedClassName);

            }

        }
        else {

            Dom.removeClass(oElement, sClassName);
            Dom.removeClass(oAnchor, sLabelClassName);


            if (oSubmenu) {

                Dom.removeClass(oElement, sSubmenuClassName);
                Dom.removeClass(oAnchor, sLabelSubmenuClassName);
            
            }

        
            if (bChecked) {

                Dom.removeClass(oElement, sCheckedClassName);
                Dom.removeClass(oAnchor, sLabelCheckedClassName);

            }

        }


        if (this.hasFocus() && YAHOO.env.ua.opera) {
        
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
            oElement = this.element,
            oAnchor = this._oAnchor,
            bLazyLoad = this.parent && this.parent.lazyLoad,
            sState = "-hassubmenu",
            sClassName = this.CSS_CLASS_NAME + sState,
            sLabelClassName = this.CSS_LABEL_CLASS_NAME + sState,
            oMenu,
            sSubmenuId,
            oSubmenuConfig;


        if (oSubmenu) {

            if (oSubmenu instanceof Menu) {

                oMenu = oSubmenu;
                oMenu.parent = this;
                oMenu.lazyLoad = bLazyLoad;

            }
            else if (typeof oSubmenu == "object" && oSubmenu.id && 
                !oSubmenu.nodeType) {

                sSubmenuId = oSubmenu.id;
                oSubmenuConfig = oSubmenu;

                oSubmenuConfig.lazyload = bLazyLoad;
                oSubmenuConfig.parent = this;

                oMenu = new this.SUBMENU_TYPE(sSubmenuId, oSubmenuConfig);


                // Set the value of the property to the Menu instance

                oConfig.setProperty("submenu", oMenu, true);

            }
            else {

                oMenu = new this.SUBMENU_TYPE(oSubmenu,
                                { lazyload: bLazyLoad, parent: this });


                // Set the value of the property to the Menu instance
                
                oConfig.setProperty("submenu", oMenu, true);

            }


            if (oMenu) {

                Dom.addClass(oElement, sClassName);
                Dom.addClass(oAnchor, sLabelClassName);

                this._oSubmenu = oMenu;

                if (YAHOO.env.ua.opera) {
                
                    oMenu.beforeHideEvent.subscribe(this._onSubmenuBeforeHide);               
                
                }
            
            }

        }
        else {

            Dom.removeClass(oElement, sClassName);
            Dom.removeClass(oAnchor, sLabelClassName);

            if (this._oSubmenu) {

                this._oSubmenu.destroy();

            }

        }


        if (oConfig.getProperty("disabled")) {

            oConfig.refireEvent("disabled");

        }


        if (oConfig.getProperty("selected")) {

            oConfig.refireEvent("selected");

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

        if (this._oOnclickAttributeValue && 
            (this._oOnclickAttributeValue != oObject)) {

            this.clickEvent.unsubscribe(this._oOnclickAttributeValue.fn, 
                                this._oOnclickAttributeValue.obj);

            this._oOnclickAttributeValue = null;

        }


        if (!this._oOnclickAttributeValue && typeof oObject == "object" && 
            typeof oObject.fn == "function") {
            
            this.clickEvent.subscribe(oObject.fn, 
                ((!YAHOO.lang.isUndefined(oObject.obj)) ? oObject.obj : this), 
                oObject.scope);

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
        * @description String specifying the text label for the menu item.  
        * When building a menu from existing HTML the value of this property
        * will be interpreted from the menu's markup.
        * @default ""
        * @type String
        */
        oConfig.addProperty(
            DEFAULT_CONFIG.TEXT.key, 
            { 
                handler: this.configText, 
                value: DEFAULT_CONFIG.TEXT.value, 
                validator: DEFAULT_CONFIG.TEXT.validator, 
                suppressEvent: DEFAULT_CONFIG.TEXT.suppressEvent 
            }
        );
        

        /**
        * @config helptext
        * @description String specifying additional instructional text to 
        * accompany the text for the menu item.
        * @deprecated Use "text" configuration property to add help text markup.  
        * For example: <code>oMenuItem.cfg.setProperty("text", "Copy &#60;em 
        * class=\"helptext\"&#62;Ctrl + C&#60;/em&#62;");</code>
        * @default null
        * @type String|<a href="http://www.w3.org/TR/
        * 2000/WD-DOM-Level-1-20000929/level-one-html.html#ID-58190037">
        * HTMLElement</a>
        */
        oConfig.addProperty(
            DEFAULT_CONFIG.HELP_TEXT.key,
            {
                handler: this.configHelpText, 
                supercedes: DEFAULT_CONFIG.HELP_TEXT.supercedes,
                suppressEvent: DEFAULT_CONFIG.HELP_TEXT.suppressEvent 
            }
        );


        /**
        * @config url
        * @description String specifying the URL for the menu item's anchor's 
        * "href" attribute.  When building a menu from existing HTML the value 
        * of this property will be interpreted from the menu's markup.
        * @default "#"
        * @type String
        */        
        oConfig.addProperty(
            DEFAULT_CONFIG.URL.key, 
            {
                handler: this.configURL, 
                value: DEFAULT_CONFIG.URL.value, 
                suppressEvent: DEFAULT_CONFIG.URL.suppressEvent
            }
        );


        /**
        * @config target
        * @description String specifying the value for the "target" attribute 
        * of the menu item's anchor element. <strong>Specifying a target will 
        * require the user to click directly on the menu item's anchor node in
        * order to cause the browser to navigate to the specified URL.</strong> 
        * When building a menu from existing HTML the value of this property 
        * will be interpreted from the menu's markup.
        * @default null
        * @type String
        */        
        oConfig.addProperty(
            DEFAULT_CONFIG.TARGET.key, 
            {
                handler: this.configTarget, 
                suppressEvent: DEFAULT_CONFIG.TARGET.suppressEvent
            }
        );


        /**
        * @config emphasis
        * @description Boolean indicating if the text of the menu item will be 
        * rendered with emphasis.
        * @deprecated Use "text" configuration property to add emphasis.  
        * For example: <code>oMenuItem.cfg.setProperty("text", "&#60;em&#62;Some 
        * Text&#60;/em&#62;");</code>
        * @default false
        * @type Boolean
        */
        oConfig.addProperty(
            DEFAULT_CONFIG.EMPHASIS.key, 
            { 
                handler: this.configEmphasis, 
                value: DEFAULT_CONFIG.EMPHASIS.value, 
                validator: DEFAULT_CONFIG.EMPHASIS.validator, 
                suppressEvent: DEFAULT_CONFIG.EMPHASIS.suppressEvent,
                supercedes: DEFAULT_CONFIG.EMPHASIS.supercedes
            }
        );


        /**
        * @config strongemphasis
        * @description Boolean indicating if the text of the menu item will be 
        * rendered with strong emphasis.
        * @deprecated Use "text" configuration property to add strong emphasis.  
        * For example: <code>oMenuItem.cfg.setProperty("text", "&#60;strong&#62; 
        * Some Text&#60;/strong&#62;");</code>
        * @default false
        * @type Boolean
        */
        oConfig.addProperty(
            DEFAULT_CONFIG.STRONG_EMPHASIS.key,
            {
                handler: this.configStrongEmphasis,
                value: DEFAULT_CONFIG.STRONG_EMPHASIS.value,
                validator: DEFAULT_CONFIG.STRONG_EMPHASIS.validator,
                suppressEvent: DEFAULT_CONFIG.STRONG_EMPHASIS.suppressEvent,
                supercedes: DEFAULT_CONFIG.STRONG_EMPHASIS.supercedes
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
            DEFAULT_CONFIG.CHECKED.key, 
            {
                handler: this.configChecked, 
                value: DEFAULT_CONFIG.CHECKED.value, 
                validator: DEFAULT_CONFIG.CHECKED.validator, 
                suppressEvent: DEFAULT_CONFIG.CHECKED.suppressEvent,
                supercedes: DEFAULT_CONFIG.CHECKED.supercedes
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
            DEFAULT_CONFIG.DISABLED.key,
            {
                handler: this.configDisabled,
                value: DEFAULT_CONFIG.DISABLED.value,
                validator: DEFAULT_CONFIG.DISABLED.validator,
                suppressEvent: DEFAULT_CONFIG.DISABLED.suppressEvent
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
            DEFAULT_CONFIG.SELECTED.key,
            {
                handler: this.configSelected,
                value: DEFAULT_CONFIG.SELECTED.value,
                validator: DEFAULT_CONFIG.SELECTED.validator,
                suppressEvent: DEFAULT_CONFIG.SELECTED.suppressEvent
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
            DEFAULT_CONFIG.SUBMENU.key, 
            {
                handler: this.configSubmenu, 
                supercedes: DEFAULT_CONFIG.SUBMENU.supercedes,
                suppressEvent: DEFAULT_CONFIG.SUBMENU.suppressEvent
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
            DEFAULT_CONFIG.ONCLICK.key, 
            {
                handler: this.configOnClick, 
                suppressEvent: DEFAULT_CONFIG.ONCLICK.suppressEvent 
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
            DEFAULT_CONFIG.CLASS_NAME.key, 
            { 
                handler: this.configClassName,
                value: DEFAULT_CONFIG.CLASS_NAME.value, 
                validator: DEFAULT_CONFIG.CLASS_NAME.validator,
                suppressEvent: DEFAULT_CONFIG.CLASS_NAME.suppressEvent 
            }
        );

	},


    /**
    * @method getNextEnabledSibling
    * @description Finds the menu item's next enabled sibling.
    * @return YAHOO.widget.MenuItem
    */
    getNextEnabledSibling: function () {

        var nGroupIndex,
            aItemGroups,
            oNextItem,
            nNextGroupIndex,
            aNextGroup;

        function getNextArrayItem(p_aArray, p_nStartIndex) {

            return p_aArray[p_nStartIndex] || 
                getNextArrayItem(p_aArray, (p_nStartIndex+1));

        }

        if (this.parent instanceof Menu) {

            nGroupIndex = this.groupIndex;
    
            aItemGroups = this.parent.getItemGroups();
    
            if (this.index < (aItemGroups[nGroupIndex].length - 1)) {
    
                oNextItem = getNextArrayItem(aItemGroups[nGroupIndex], 
                        (this.index+1));
    
            }
            else {
    
                if (nGroupIndex < (aItemGroups.length - 1)) {
    
                    nNextGroupIndex = nGroupIndex + 1;
    
                }
                else {
    
                    nNextGroupIndex = 0;
    
                }
    
                aNextGroup = getNextArrayItem(aItemGroups, nNextGroupIndex);
    
                // Retrieve the first menu item in the next group
    
                oNextItem = getNextArrayItem(aNextGroup, 0);
    
            }
    
            return (oNextItem.cfg.getProperty("disabled") || 
                oNextItem.element.style.display == "none") ? 
                oNextItem.getNextEnabledSibling() : oNextItem;

        }

    },


    /**
    * @method getPreviousEnabledSibling
    * @description Finds the menu item's previous enabled sibling.
    * @return {YAHOO.widget.MenuItem}
    */
    getPreviousEnabledSibling: function () {

        var nGroupIndex,
            aItemGroups,
            oPreviousItem,
            nPreviousGroupIndex,
            aPreviousGroup;

        function getPreviousArrayItem(p_aArray, p_nStartIndex) {

            return p_aArray[p_nStartIndex] ||  
                getPreviousArrayItem(p_aArray, (p_nStartIndex-1));

        }

        function getFirstItemIndex(p_aArray, p_nStartIndex) {

            return p_aArray[p_nStartIndex] ? p_nStartIndex : 
                getFirstItemIndex(p_aArray, (p_nStartIndex+1));

        }

       if (this.parent instanceof Menu) {

            nGroupIndex = this.groupIndex;
            aItemGroups = this.parent.getItemGroups();

    
            if (this.index > getFirstItemIndex(aItemGroups[nGroupIndex], 0)) {
    
                oPreviousItem = getPreviousArrayItem(aItemGroups[nGroupIndex], 
                        (this.index-1));
    
            }
            else {
    
                if (nGroupIndex > getFirstItemIndex(aItemGroups, 0)) {
    
                    nPreviousGroupIndex = nGroupIndex - 1;
    
                }
                else {
    
                    nPreviousGroupIndex = aItemGroups.length - 1;
    
                }
    
                aPreviousGroup = getPreviousArrayItem(aItemGroups, 
                    nPreviousGroupIndex);
    
                oPreviousItem = getPreviousArrayItem(aPreviousGroup, 
                        (aPreviousGroup.length - 1));
    
            }

            return (oPreviousItem.cfg.getProperty("disabled") || 
                oPreviousItem.element.style.display == "none") ? 
                oPreviousItem.getPreviousEnabledSibling() : oPreviousItem;

        }

    },


    /**
    * @method focus
    * @description Causes the menu item to receive the focus and fires the 
    * focus event.
    */
    focus: function () {

        var oParent = this.parent,
            oAnchor = this._oAnchor,
            oActiveItem = oParent.activeItem,
            me = this;


        function setFocus() {

            try {

                if (YAHOO.env.ua.ie && !document.hasFocus()) {
                
                    return;
                
                }

                if (oActiveItem) {
    
                    oActiveItem.blurEvent.fire();
    
                }

                oAnchor.focus();
                
                me.focusEvent.fire();

            }
            catch(e) {
            
            }

        }


        if (!this.cfg.getProperty("disabled") && oParent && 
            oParent.cfg.getProperty("visible") && 
            this.element.style.display != "none") {


            /*
                Setting focus via a timer fixes a race condition in Firefox, IE 
                and Opera where the browser viewport jumps as it trys to 
                position and focus the menu.
            */

            window.setTimeout(setFocus, 0);

        }

    },


    /**
    * @method blur
    * @description Causes the menu item to lose focus and fires the 
    * blur event.
    */    
    blur: function () {

        var oParent = this.parent;

        if (!this.cfg.getProperty("disabled") && oParent && 
            oParent.cfg.getProperty("visible")) {


            var me = this;
            
            window.setTimeout(function () {

                try {
    
                    me._oAnchor.blur();
                    me.blurEvent.fire();    

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
            oParentNode;

        if (oEl) {


            // If the item has a submenu, destroy it first

            oSubmenu = this.cfg.getProperty("submenu");

            if (oSubmenu) {
            
                oSubmenu.destroy();
            
            }


            // Remove CustomEvent listeners
    
            this.mouseOverEvent.unsubscribeAll();
            this.mouseOutEvent.unsubscribeAll();
            this.mouseDownEvent.unsubscribeAll();
            this.mouseUpEvent.unsubscribeAll();
            this.clickEvent.unsubscribeAll();
            this.keyPressEvent.unsubscribeAll();
            this.keyDownEvent.unsubscribeAll();
            this.keyUpEvent.unsubscribeAll();
            this.focusEvent.unsubscribeAll();
            this.blurEvent.unsubscribeAll();
            this.cfg.configChangedEvent.unsubscribeAll();


            // Remove the element from the parent node

            oParentNode = oEl.parentNode;

            if (oParentNode) {

                oParentNode.removeChild(oEl);

                this.destroyEvent.fire();

            }

            this.destroyEvent.unsubscribeAll();

        }

    },


    /**
    * @method toString
    * @description Returns a string representing the menu item.
    * @return {String}
    */
    toString: function () {

        var sReturnVal = "MenuItem",
            sId = this.id;

        if (sId) {
    
            sReturnVal += (" " + sId);
        
        }

        return sReturnVal;
    
    }

};

Lang.augmentProto(MenuItem, YAHOO.util.EventProvider);

})();
(function () {


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

    YAHOO.widget.ContextMenu.superclass.constructor.call(this, 
            p_oElement, p_oConfig);

};


var Event = YAHOO.util.Event,
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
        "CONTEXT_MENU": (YAHOO.env.ua.opera ? "mousedown" : "contextmenu"),
        "CLICK": "click"

    },
    
    
    /**
    * Constant representing the ContextMenu's configuration properties
    * @property DEFAULT_CONFIG
    * @private
    * @final
    * @type Object
    */
    DEFAULT_CONFIG = {
    
        "TRIGGER": { 
            key: "trigger",
            suppressEvent: true
        }
    
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

    this.cfg.setProperty("xy", p_aPos);
    
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


    if(p_oConfig) {

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

    this.triggerContextMenuEvent = 
        this.createEvent(EVENT_TYPES.TRIGGER_CONTEXT_MENU);

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

        Event.removeListener(oTrigger, EVENT_TYPES.CONTEXT_MENU, 
            this._onTriggerContextMenu);    
        
        if(YAHOO.env.ua.opera) {
        
            Event.removeListener(oTrigger, EVENT_TYPES.CLICK, 
                this._onTriggerClick);
    
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

    if(p_oEvent.ctrlKey) {
    
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

    if (p_oEvent.type == "mousedown" && !p_oEvent.ctrlKey) {

        return;

    }


    var aXY;


    /*
        Prevent the browser's default context menu from appearing and 
        stop the propagation of the "contextmenu" event so that 
        other ContextMenu instances are not displayed.
    */

    Event.stopEvent(p_oEvent);


    this.contextEventTarget = Event.getTarget(p_oEvent);

    this.triggerContextMenuEvent.fire(p_oEvent);


    // Hide any other Menu instances that might be visible

    YAHOO.widget.MenuManager.hideVisible();
    


    if(!this._bCancelled) {

        // Position and display the context menu

        aXY = Event.getXY(p_oEvent);


        if (!YAHOO.util.Dom.inDocument(this.element)) {

            this.beforeShowEvent.subscribe(position, aXY);

        }
        else {

            this.cfg.setProperty("xy", aXY);
        
        }


        this.show();

    }

    this._bCancelled = false;

},



// Public methods


/**
* @method toString
* @description Returns a string representing the context menu.
* @return {String}
*/
toString: function() {

    var sReturnVal = "ContextMenu",
        sId = this.id;

    if(sId) {

        sReturnVal += (" " + sId);
    
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
    this.cfg.addProperty(DEFAULT_CONFIG.TRIGGER.key, 
        {
            handler: this.configTrigger, 
            suppressEvent: DEFAULT_CONFIG.TRIGGER.suppressEvent 
        }
    );

},


/**
* @method destroy
* @description Removes the context menu's <code>&#60;div&#62;</code> element 
* (and accompanying child nodes) from the document.
*/
destroy: function() {

    // Remove the DOM event handlers from the current trigger(s)

    this._removeEventHandlers();


    // Continue with the superclass implementation of this method

    ContextMenu.superclass.destroy.call(this);

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

    if(oTrigger) {

        /*
            If there is a current "trigger" - remove the event handlers 
            from that element(s) before assigning new ones
        */

        if(this._oTrigger) {
        
            this._removeEventHandlers();

        }

        this._oTrigger = oTrigger;


        /*
            Listen for the "mousedown" event in Opera b/c it does not 
            support the "contextmenu" event
        */ 
  
        Event.on(oTrigger, EVENT_TYPES.CONTEXT_MENU, 
            this._onTriggerContextMenu, this, true);


        /*
            Assign a "click" event handler to the trigger element(s) for
            Opera to prevent default browser behaviors.
        */

        if(YAHOO.env.ua.opera) {
        
            Event.on(oTrigger, EVENT_TYPES.CLICK, this._onTriggerClick, 
                this, true);

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

    YAHOO.widget.MenuBar.superclass.constructor.call(this, 
        p_oElement, p_oConfig);

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

    if (typeof p_sPosition == "string") {

        return ("dynamic,static".indexOf((p_sPosition.toLowerCase())) != -1);

    }

}


var Event = YAHOO.util.Event,
    MenuBar = YAHOO.widget.MenuBar,

    /**
    * Constant representing the MenuBar's configuration properties
    * @property DEFAULT_CONFIG
    * @private
    * @final
    * @type Object
    */
    DEFAULT_CONFIG = {
    
        "POSITION": { 
            key: "position", 
            value: "static", 
            validator: checkPosition, 
            supercedes: ["visible"] 
        }, 
    
        "SUBMENU_ALIGNMENT": { 
            key: "submenualignment", 
            value: ["tl","bl"],
            suppressEvent: true 
        },
    
        "AUTO_SUBMENU_DISPLAY": { 
            key: "autosubmenudisplay", 
            value: false, 
            validator: YAHOO.lang.isBoolean,
            suppressEvent: true
        }
    
    };



YAHOO.lang.extend(MenuBar, YAHOO.widget.Menu, {

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


    if(oItem && !oItem.cfg.getProperty("disabled")) {

        oItemCfg = oItem.cfg;

        switch(oEvent.keyCode) {
    
            case 37:    // Left arrow
            case 39:    // Right arrow
    
                if(oItem == this.activeItem && 
                    !oItemCfg.getProperty("selected")) {
    
                    oItemCfg.setProperty("selected", true);
    
                }
                else {
    
                    oNextItem = (oEvent.keyCode == 37) ? 
                        oItem.getPreviousEnabledSibling() : 
                        oItem.getNextEnabledSibling();
            
                    if(oNextItem) {
    
                        this.clearActiveItem();
    
                        oNextItem.cfg.setProperty("selected", true);
    
    
                        if(this.cfg.getProperty("autosubmenudisplay")) {
                        
                            oSubmenu = oNextItem.cfg.getProperty("submenu");
                            
                            if(oSubmenu) {
                        
                                oSubmenu.show();
                            
                            }
                
                        }           
    
                        oNextItem.focus();
    
                    }
    
                }
    
                Event.preventDefault(oEvent);
    
            break;
    
            case 40:    // Down arrow
    
                if(this.activeItem != oItem) {
    
                    this.clearActiveItem();
    
                    oItemCfg.setProperty("selected", true);
                    oItem.focus();
                
                }
    
                oSubmenu = oItemCfg.getProperty("submenu");
    
                if(oSubmenu) {
    
                    if(oSubmenu.cfg.getProperty("visible")) {
    
                        oSubmenu.setInitialSelection();
                        oSubmenu.setInitialFocus();
                    
                    }
                    else {
    
                        oSubmenu.show();
                    
                    }
    
                }
    
                Event.preventDefault(oEvent);
    
            break;
    
        }

    }


    if(oEvent.keyCode == 27 && this.activeItem) { // Esc key

        oSubmenu = this.activeItem.cfg.getProperty("submenu");

        if(oSubmenu && oSubmenu.cfg.getProperty("visible")) {
        
            oSubmenu.hide();
            this.activeItem.focus();
        
        }
        else {

            this.activeItem.cfg.setProperty("selected", false);
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
        oEvent,
        oTarget,
        oActiveItem,
        oConfig,
        oSubmenu;
    

    if(oItem && !oItem.cfg.getProperty("disabled")) {

        oEvent = p_aArgs[0];
        oTarget = Event.getTarget(oEvent);
        oActiveItem = this.activeItem;
        oConfig = this.cfg;


        // Hide any other submenus that might be visible
    
        if(oActiveItem && oActiveItem != oItem) {
    
            this.clearActiveItem();
    
        }

    
        oItem.cfg.setProperty("selected", true);
    

        // Show the submenu for the item
    
        oSubmenu = oItem.cfg.getProperty("submenu");


        if(oSubmenu) {
        
            if(oSubmenu.cfg.getProperty("visible")) {
            
                oSubmenu.hide();
            
            }
            else {
            
                oSubmenu.show();                    
            
            }
        
        }
    
    }

},



// Public methods


/**
* @method toString
* @description Returns a string representing the menu bar.
* @return {String}
*/
toString: function() {

    var sReturnVal = "MenuBar",
        sId = this.id;

    if(sId) {

        sReturnVal += (" " + sId);
    
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
        DEFAULT_CONFIG.POSITION.key, 
        {
            handler: this.configPosition, 
            value: DEFAULT_CONFIG.POSITION.value, 
            validator: DEFAULT_CONFIG.POSITION.validator,
            supercedes: DEFAULT_CONFIG.POSITION.supercedes
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
        DEFAULT_CONFIG.SUBMENU_ALIGNMENT.key, 
        {
            value: DEFAULT_CONFIG.SUBMENU_ALIGNMENT.value,
            suppressEvent: DEFAULT_CONFIG.SUBMENU_ALIGNMENT.suppressEvent
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
	   DEFAULT_CONFIG.AUTO_SUBMENU_DISPLAY.key, 
	   {
	       value: DEFAULT_CONFIG.AUTO_SUBMENU_DISPLAY.value, 
	       validator: DEFAULT_CONFIG.AUTO_SUBMENU_DISPLAY.validator,
	       suppressEvent: DEFAULT_CONFIG.AUTO_SUBMENU_DISPLAY.suppressEvent
       } 
    );

}
 
}); // END YAHOO.lang.extend

}());



/**
* Creates an item for a menu bar.
* 
* @param {String} p_oObject String specifying the text of the menu bar item.
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

    YAHOO.widget.MenuBarItem.superclass.constructor.call(this, 
        p_oObject, p_oConfig);

};

YAHOO.lang.extend(YAHOO.widget.MenuBarItem, YAHOO.widget.MenuItem, {



/**
* @method init
* @description The MenuBarItem class's initialization method. This method is 
* automatically called by the constructor, and sets up all DOM references for 
* pre-existing markup, and creates required markup if it is not already present.
* @param {String} p_oObject String specifying the text of the menu bar item.
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
YAHOO.register("menu", YAHOO.widget.Menu, {version: "2.5.1", build: "984"});
