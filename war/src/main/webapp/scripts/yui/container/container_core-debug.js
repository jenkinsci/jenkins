/*
Copyright (c) 2011, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.com/yui/license.html
version: 2.9.0
*/
(function () {

    /**
    * Config is a utility used within an Object to allow the implementer to
    * maintain a list of local configuration properties and listen for changes 
    * to those properties dynamically using CustomEvent. The initial values are 
    * also maintained so that the configuration can be reset at any given point 
    * to its initial state.
    * @namespace YAHOO.util
    * @class Config
    * @constructor
    * @param {Object} owner The owner Object to which this Config Object belongs
    */
    YAHOO.util.Config = function (owner) {

        if (owner) {
            this.init(owner);
        }

        if (!owner) {  YAHOO.log("No owner specified for Config object", "error", "Config"); }

    };


    var Lang = YAHOO.lang,
        CustomEvent = YAHOO.util.CustomEvent,
        Config = YAHOO.util.Config;


    /**
     * Constant representing the CustomEvent type for the config changed event.
     * @property YAHOO.util.Config.CONFIG_CHANGED_EVENT
     * @private
     * @static
     * @final
     */
    Config.CONFIG_CHANGED_EVENT = "configChanged";
    
    /**
     * Constant representing the boolean type string
     * @property YAHOO.util.Config.BOOLEAN_TYPE
     * @private
     * @static
     * @final
     */
    Config.BOOLEAN_TYPE = "boolean";
    
    Config.prototype = {
     
        /**
        * Object reference to the owner of this Config Object
        * @property owner
        * @type Object
        */
        owner: null,
        
        /**
        * Boolean flag that specifies whether a queue is currently 
        * being executed
        * @property queueInProgress
        * @type Boolean
        */
        queueInProgress: false,
        
        /**
        * Maintains the local collection of configuration property objects and 
        * their specified values
        * @property config
        * @private
        * @type Object
        */ 
        config: null,
        
        /**
        * Maintains the local collection of configuration property objects as 
        * they were initially applied.
        * This object is used when resetting a property.
        * @property initialConfig
        * @private
        * @type Object
        */ 
        initialConfig: null,
        
        /**
        * Maintains the local, normalized CustomEvent queue
        * @property eventQueue
        * @private
        * @type Object
        */ 
        eventQueue: null,
        
        /**
        * Custom Event, notifying subscribers when Config properties are set 
        * (setProperty is called without the silent flag
        * @event configChangedEvent
        */
        configChangedEvent: null,
    
        /**
        * Initializes the configuration Object and all of its local members.
        * @method init
        * @param {Object} owner The owner Object to which this Config 
        * Object belongs
        */
        init: function (owner) {
    
            this.owner = owner;
    
            this.configChangedEvent = 
                this.createEvent(Config.CONFIG_CHANGED_EVENT);
    
            this.configChangedEvent.signature = CustomEvent.LIST;
            this.queueInProgress = false;
            this.config = {};
            this.initialConfig = {};
            this.eventQueue = [];
        
        },
        
        /**
        * Validates that the value passed in is a Boolean.
        * @method checkBoolean
        * @param {Object} val The value to validate
        * @return {Boolean} true, if the value is valid
        */ 
        checkBoolean: function (val) {
            return (typeof val == Config.BOOLEAN_TYPE);
        },
        
        /**
        * Validates that the value passed in is a number.
        * @method checkNumber
        * @param {Object} val The value to validate
        * @return {Boolean} true, if the value is valid
        */
        checkNumber: function (val) {
            return (!isNaN(val));
        },
        
        /**
        * Fires a configuration property event using the specified value. 
        * @method fireEvent
        * @private
        * @param {String} key The configuration property's name
        * @param {value} Object The value of the correct type for the property
        */ 
        fireEvent: function ( key, value ) {
            YAHOO.log("Firing Config event: " + key + "=" + value, "info", "Config");
            var property = this.config[key];
        
            if (property && property.event) {
                property.event.fire(value);
            } 
        },
        
        /**
        * Adds a property to the Config Object's private config hash.
        * @method addProperty
        * @param {String} key The configuration property's name
        * @param {Object} propertyObject The Object containing all of this 
        * property's arguments
        */
        addProperty: function ( key, propertyObject ) {
            key = key.toLowerCase();
            YAHOO.log("Added property: " + key, "info", "Config");
        
            this.config[key] = propertyObject;
        
            propertyObject.event = this.createEvent(key, { scope: this.owner });
            propertyObject.event.signature = CustomEvent.LIST;
            
            
            propertyObject.key = key;
        
            if (propertyObject.handler) {
                propertyObject.event.subscribe(propertyObject.handler, 
                    this.owner);
            }
        
            this.setProperty(key, propertyObject.value, true);
            
            if (! propertyObject.suppressEvent) {
                this.queueProperty(key, propertyObject.value);
            }
            
        },
        
        /**
        * Returns a key-value configuration map of the values currently set in  
        * the Config Object.
        * @method getConfig
        * @return {Object} The current config, represented in a key-value map
        */
        getConfig: function () {
        
            var cfg = {},
                currCfg = this.config,
                prop,
                property;
                
            for (prop in currCfg) {
                if (Lang.hasOwnProperty(currCfg, prop)) {
                    property = currCfg[prop];
                    if (property && property.event) {
                        cfg[prop] = property.value;
                    }
                }
            }

            return cfg;
        },
        
        /**
        * Returns the value of specified property.
        * @method getProperty
        * @param {String} key The name of the property
        * @return {Object}  The value of the specified property
        */
        getProperty: function (key) {
            var property = this.config[key.toLowerCase()];
            if (property && property.event) {
                return property.value;
            } else {
                return undefined;
            }
        },
        
        /**
        * Resets the specified property's value to its initial value.
        * @method resetProperty
        * @param {String} key The name of the property
        * @return {Boolean} True is the property was reset, false if not
        */
        resetProperty: function (key) {
            key = key.toLowerCase();

            var property = this.config[key];

            if (property && property.event) {
                if (key in this.initialConfig) {
                    this.setProperty(key, this.initialConfig[key]);
                    return true;
                }
            } else {
                return false;
            }
        },
        
        /**
        * Sets the value of a property. If the silent property is passed as 
        * true, the property's event will not be fired.
        * @method setProperty
        * @param {String} key The name of the property
        * @param {String} value The value to set the property to
        * @param {Boolean} silent Whether the value should be set silently, 
        * without firing the property event.
        * @return {Boolean} True, if the set was successful, false if it failed.
        */
        setProperty: function (key, value, silent) {
        
            var property;
        
            key = key.toLowerCase();
            YAHOO.log("setProperty: " + key + "=" + value, "info", "Config");
        
            if (this.queueInProgress && ! silent) {
                // Currently running through a queue... 
                this.queueProperty(key,value);
                return true;
    
            } else {
                property = this.config[key];
                if (property && property.event) {
                    if (property.validator && !property.validator(value)) {
                        return false;
                    } else {
                        property.value = value;
                        if (! silent) {
                            this.fireEvent(key, value);
                            this.configChangedEvent.fire([key, value]);
                        }
                        return true;
                    }
                } else {
                    return false;
                }
            }
        },
        
        /**
        * Sets the value of a property and queues its event to execute. If the 
        * event is already scheduled to execute, it is
        * moved from its current position to the end of the queue.
        * @method queueProperty
        * @param {String} key The name of the property
        * @param {String} value The value to set the property to
        * @return {Boolean}  true, if the set was successful, false if 
        * it failed.
        */ 
        queueProperty: function (key, value) {
        
            key = key.toLowerCase();
            YAHOO.log("queueProperty: " + key + "=" + value, "info", "Config");
        
            var property = this.config[key],
                foundDuplicate = false,
                iLen,
                queueItem,
                queueItemKey,
                queueItemValue,
                sLen,
                supercedesCheck,
                qLen,
                queueItemCheck,
                queueItemCheckKey,
                queueItemCheckValue,
                i,
                s,
                q;
                                
            if (property && property.event) {
    
                if (!Lang.isUndefined(value) && property.validator && 
                    !property.validator(value)) { // validator
                    return false;
                } else {
        
                    if (!Lang.isUndefined(value)) {
                        property.value = value;
                    } else {
                        value = property.value;
                    }
        
                    foundDuplicate = false;
                    iLen = this.eventQueue.length;
        
                    for (i = 0; i < iLen; i++) {
                        queueItem = this.eventQueue[i];
        
                        if (queueItem) {
                            queueItemKey = queueItem[0];
                            queueItemValue = queueItem[1];

                            if (queueItemKey == key) {
    
                                /*
                                    found a dupe... push to end of queue, null 
                                    current item, and break
                                */
    
                                this.eventQueue[i] = null;
    
                                this.eventQueue.push(
                                    [key, (!Lang.isUndefined(value) ? 
                                    value : queueItemValue)]);
    
                                foundDuplicate = true;
                                break;
                            }
                        }
                    }
                    
                    // this is a refire, or a new property in the queue
    
                    if (! foundDuplicate && !Lang.isUndefined(value)) { 
                        this.eventQueue.push([key, value]);
                    }
                }
        
                if (property.supercedes) {

                    sLen = property.supercedes.length;

                    for (s = 0; s < sLen; s++) {

                        supercedesCheck = property.supercedes[s];
                        qLen = this.eventQueue.length;

                        for (q = 0; q < qLen; q++) {
                            queueItemCheck = this.eventQueue[q];

                            if (queueItemCheck) {
                                queueItemCheckKey = queueItemCheck[0];
                                queueItemCheckValue = queueItemCheck[1];

                                if (queueItemCheckKey == 
                                    supercedesCheck.toLowerCase() ) {

                                    this.eventQueue.push([queueItemCheckKey, 
                                        queueItemCheckValue]);

                                    this.eventQueue[q] = null;
                                    break;

                                }
                            }
                        }
                    }
                }

                YAHOO.log("Config event queue: " + this.outputEventQueue(), "info", "Config");

                return true;
            } else {
                return false;
            }
        },
        
        /**
        * Fires the event for a property using the property's current value.
        * @method refireEvent
        * @param {String} key The name of the property
        */
        refireEvent: function (key) {
    
            key = key.toLowerCase();
        
            var property = this.config[key];
    
            if (property && property.event && 
    
                !Lang.isUndefined(property.value)) {
    
                if (this.queueInProgress) {
    
                    this.queueProperty(key);
    
                } else {
    
                    this.fireEvent(key, property.value);
    
                }
    
            }
        },
        
        /**
        * Applies a key-value Object literal to the configuration, replacing  
        * any existing values, and queueing the property events.
        * Although the values will be set, fireQueue() must be called for their 
        * associated events to execute.
        * @method applyConfig
        * @param {Object} userConfig The configuration Object literal
        * @param {Boolean} init  When set to true, the initialConfig will 
        * be set to the userConfig passed in, so that calling a reset will 
        * reset the properties to the passed values.
        */
        applyConfig: function (userConfig, init) {
        
            var sKey,
                oConfig;

            if (init) {
                oConfig = {};
                for (sKey in userConfig) {
                    if (Lang.hasOwnProperty(userConfig, sKey)) {
                        oConfig[sKey.toLowerCase()] = userConfig[sKey];
                    }
                }
                this.initialConfig = oConfig;
            }

            for (sKey in userConfig) {
                if (Lang.hasOwnProperty(userConfig, sKey)) {
                    this.queueProperty(sKey, userConfig[sKey]);
                }
            }
        },
        
        /**
        * Refires the events for all configuration properties using their 
        * current values.
        * @method refresh
        */
        refresh: function () {

            var prop;

            for (prop in this.config) {
                if (Lang.hasOwnProperty(this.config, prop)) {
                    this.refireEvent(prop);
                }
            }
        },
        
        /**
        * Fires the normalized list of queued property change events
        * @method fireQueue
        */
        fireQueue: function () {
        
            var i, 
                queueItem,
                key,
                value,
                property;
        
            this.queueInProgress = true;
            for (i = 0;i < this.eventQueue.length; i++) {
                queueItem = this.eventQueue[i];
                if (queueItem) {
        
                    key = queueItem[0];
                    value = queueItem[1];
                    property = this.config[key];

                    property.value = value;

                    // Clear out queue entry, to avoid it being 
                    // re-added to the queue by any queueProperty/supercedes
                    // calls which are invoked during fireEvent
                    this.eventQueue[i] = null;

                    this.fireEvent(key,value);
                }
            }
            
            this.queueInProgress = false;
            this.eventQueue = [];
        },
        
        /**
        * Subscribes an external handler to the change event for any 
        * given property. 
        * @method subscribeToConfigEvent
        * @param {String} key The property name
        * @param {Function} handler The handler function to use subscribe to 
        * the property's event
        * @param {Object} obj The Object to use for scoping the event handler 
        * (see CustomEvent documentation)
        * @param {Boolean} overrideContext Optional. If true, will override
        * "this" within the handler to map to the scope Object passed into the
        * method.
        * @return {Boolean} True, if the subscription was successful, 
        * otherwise false.
        */ 
        subscribeToConfigEvent: function (key, handler, obj, overrideContext) {
    
            var property = this.config[key.toLowerCase()];
    
            if (property && property.event) {
                if (!Config.alreadySubscribed(property.event, handler, obj)) {
                    property.event.subscribe(handler, obj, overrideContext);
                }
                return true;
            } else {
                return false;
            }
    
        },
        
        /**
        * Unsubscribes an external handler from the change event for any 
        * given property. 
        * @method unsubscribeFromConfigEvent
        * @param {String} key The property name
        * @param {Function} handler The handler function to use subscribe to 
        * the property's event
        * @param {Object} obj The Object to use for scoping the event 
        * handler (see CustomEvent documentation)
        * @return {Boolean} True, if the unsubscription was successful, 
        * otherwise false.
        */
        unsubscribeFromConfigEvent: function (key, handler, obj) {
            var property = this.config[key.toLowerCase()];
            if (property && property.event) {
                return property.event.unsubscribe(handler, obj);
            } else {
                return false;
            }
        },
        
        /**
        * Returns a string representation of the Config object
        * @method toString
        * @return {String} The Config object in string format.
        */
        toString: function () {
            var output = "Config";
            if (this.owner) {
                output += " [" + this.owner.toString() + "]";
            }
            return output;
        },
        
        /**
        * Returns a string representation of the Config object's current 
        * CustomEvent queue
        * @method outputEventQueue
        * @return {String} The string list of CustomEvents currently queued 
        * for execution
        */
        outputEventQueue: function () {

            var output = "",
                queueItem,
                q,
                nQueue = this.eventQueue.length;
              
            for (q = 0; q < nQueue; q++) {
                queueItem = this.eventQueue[q];
                if (queueItem) {
                    output += queueItem[0] + "=" + queueItem[1] + ", ";
                }
            }
            return output;
        },

        /**
        * Sets all properties to null, unsubscribes all listeners from each 
        * property's change event and all listeners from the configChangedEvent.
        * @method destroy
        */
        destroy: function () {

            var oConfig = this.config,
                sProperty,
                oProperty;


            for (sProperty in oConfig) {
            
                if (Lang.hasOwnProperty(oConfig, sProperty)) {

                    oProperty = oConfig[sProperty];

                    oProperty.event.unsubscribeAll();
                    oProperty.event = null;

                }
            
            }
            
            this.configChangedEvent.unsubscribeAll();
            
            this.configChangedEvent = null;
            this.owner = null;
            this.config = null;
            this.initialConfig = null;
            this.eventQueue = null;
        
        }

    };
    
    
    
    /**
    * Checks to determine if a particular function/Object pair are already 
    * subscribed to the specified CustomEvent
    * @method YAHOO.util.Config.alreadySubscribed
    * @static
    * @param {YAHOO.util.CustomEvent} evt The CustomEvent for which to check 
    * the subscriptions
    * @param {Function} fn The function to look for in the subscribers list
    * @param {Object} obj The execution scope Object for the subscription
    * @return {Boolean} true, if the function/Object pair is already subscribed 
    * to the CustomEvent passed in
    */
    Config.alreadySubscribed = function (evt, fn, obj) {
    
        var nSubscribers = evt.subscribers.length,
            subsc,
            i;

        if (nSubscribers > 0) {
            i = nSubscribers - 1;
            do {
                subsc = evt.subscribers[i];
                if (subsc && subsc.obj == obj && subsc.fn == fn) {
                    return true;
                }
            }
            while (i--);
        }

        return false;

    };

    YAHOO.lang.augmentProto(Config, YAHOO.util.EventProvider);

}());
(function () {

    /**
    * The Container family of components is designed to enable developers to 
    * create different kinds of content-containing modules on the web. Module 
    * and Overlay are the most basic containers, and they can be used directly 
    * or extended to build custom containers. Also part of the Container family 
    * are four UI controls that extend Module and Overlay: Tooltip, Panel, 
    * Dialog, and SimpleDialog.
    * @module container
    * @title Container
    * @requires yahoo, dom, event 
    * @optional dragdrop, animation, button
    */
    
    /**
    * Module is a JavaScript representation of the Standard Module Format. 
    * Standard Module Format is a simple standard for markup containers where 
    * child nodes representing the header, body, and footer of the content are 
    * denoted using the CSS classes "hd", "bd", and "ft" respectively. 
    * Module is the base class for all other classes in the YUI 
    * Container package.
    * @namespace YAHOO.widget
    * @class Module
    * @constructor
    * @param {String} el The element ID representing the Module <em>OR</em>
    * @param {HTMLElement} el The element representing the Module
    * @param {Object} userConfig The configuration Object literal containing 
    * the configuration that should be set for this module. See configuration 
    * documentation for more details.
    */
    YAHOO.widget.Module = function (el, userConfig) {
        if (el) {
            this.init(el, userConfig);
        } else {
            YAHOO.log("No element or element ID specified" + 
                " for Module instantiation", "error");
        }
    };

    var Dom = YAHOO.util.Dom,
        Config = YAHOO.util.Config,
        Event = YAHOO.util.Event,
        CustomEvent = YAHOO.util.CustomEvent,
        Module = YAHOO.widget.Module,
        UA = YAHOO.env.ua,

        m_oModuleTemplate,
        m_oHeaderTemplate,
        m_oBodyTemplate,
        m_oFooterTemplate,

        /**
        * Constant representing the name of the Module's events
        * @property EVENT_TYPES
        * @private
        * @final
        * @type Object
        */
        EVENT_TYPES = {
            "BEFORE_INIT": "beforeInit",
            "INIT": "init",
            "APPEND": "append",
            "BEFORE_RENDER": "beforeRender",
            "RENDER": "render",
            "CHANGE_HEADER": "changeHeader",
            "CHANGE_BODY": "changeBody",
            "CHANGE_FOOTER": "changeFooter",
            "CHANGE_CONTENT": "changeContent",
            "DESTROY": "destroy",
            "BEFORE_SHOW": "beforeShow",
            "SHOW": "show",
            "BEFORE_HIDE": "beforeHide",
            "HIDE": "hide"
        },
            
        /**
        * Constant representing the Module's configuration properties
        * @property DEFAULT_CONFIG
        * @private
        * @final
        * @type Object
        */
        DEFAULT_CONFIG = {
        
            "VISIBLE": { 
                key: "visible", 
                value: true, 
                validator: YAHOO.lang.isBoolean 
            },

            "EFFECT": {
                key: "effect",
                suppressEvent: true,
                supercedes: ["visible"]
            },

            "MONITOR_RESIZE": {
                key: "monitorresize",
                value: true
            },

            "APPEND_TO_DOCUMENT_BODY": {
                key: "appendtodocumentbody",
                value: false
            }
        };

    /**
    * Constant representing the prefix path to use for non-secure images
    * @property YAHOO.widget.Module.IMG_ROOT
    * @static
    * @final
    * @type String
    */
    Module.IMG_ROOT = null;
    
    /**
    * Constant representing the prefix path to use for securely served images
    * @property YAHOO.widget.Module.IMG_ROOT_SSL
    * @static
    * @final
    * @type String
    */
    Module.IMG_ROOT_SSL = null;
    
    /**
    * Constant for the default CSS class name that represents a Module
    * @property YAHOO.widget.Module.CSS_MODULE
    * @static
    * @final
    * @type String
    */
    Module.CSS_MODULE = "yui-module";
    
    /**
    * CSS classname representing the module header. NOTE: The classname is inserted into the DOM as HTML, and should be escaped by the implementor if coming from an external source.
    * @property YAHOO.widget.Module.CSS_HEADER
    * @static
    * @final
    * @type String
    */
    Module.CSS_HEADER = "hd";

    /**
    * CSS classname representing the module body. NOTE: The classname is inserted into the DOM as HTML, and should be escaped by the implementor if coming from an external source.
    * @property YAHOO.widget.Module.CSS_BODY
    * @static
    * @final
    * @type String
    */
    Module.CSS_BODY = "bd";
    
    /**
    * CSS classname representing the module footer. NOTE: The classname is inserted into the DOM as HTML, and should be escaped by the implementor if coming from an external source.
    * @property YAHOO.widget.Module.CSS_FOOTER
    * @static
    * @final
    * @type String
    */
    Module.CSS_FOOTER = "ft";
    
    /**
    * Constant representing the url for the "src" attribute of the iframe 
    * used to monitor changes to the browser's base font size
    * @property YAHOO.widget.Module.RESIZE_MONITOR_SECURE_URL
    * @static
    * @final
    * @type String
    */
    Module.RESIZE_MONITOR_SECURE_URL = "javascript:false;";

    /**
    * Constant representing the buffer amount (in pixels) to use when positioning
    * the text resize monitor offscreen. The resize monitor is positioned
    * offscreen by an amount eqaul to its offsetHeight + the buffer value.
    * 
    * @property YAHOO.widget.Module.RESIZE_MONITOR_BUFFER
    * @static
    * @type Number
    */
    // Set to 1, to work around pixel offset in IE8, which increases when zoom is used
    Module.RESIZE_MONITOR_BUFFER = 1;

    /**
    * Singleton CustomEvent fired when the font size is changed in the browser.
    * Opera's "zoom" functionality currently does not support text 
    * size detection.
    * @event YAHOO.widget.Module.textResizeEvent
    */
    Module.textResizeEvent = new CustomEvent("textResize");

    /**
     * Helper utility method, which forces a document level 
     * redraw for Opera, which can help remove repaint
     * irregularities after applying DOM changes.
     *
     * @method YAHOO.widget.Module.forceDocumentRedraw
     * @static
     */
    Module.forceDocumentRedraw = function() {
        var docEl = document.documentElement;
        if (docEl) {
            docEl.className += " ";
            docEl.className = YAHOO.lang.trim(docEl.className);
        }
    };

    function createModuleTemplate() {

        if (!m_oModuleTemplate) {
            m_oModuleTemplate = document.createElement("div");
            
            m_oModuleTemplate.innerHTML = ("<div class=\"" + 
                Module.CSS_HEADER + "\"></div>" + "<div class=\"" + 
                Module.CSS_BODY + "\"></div><div class=\"" + 
                Module.CSS_FOOTER + "\"></div>");

            m_oHeaderTemplate = m_oModuleTemplate.firstChild;
            m_oBodyTemplate = m_oHeaderTemplate.nextSibling;
            m_oFooterTemplate = m_oBodyTemplate.nextSibling;
        }

        return m_oModuleTemplate;
    }

    function createHeader() {
        if (!m_oHeaderTemplate) {
            createModuleTemplate();
        }
        return (m_oHeaderTemplate.cloneNode(false));
    }

    function createBody() {
        if (!m_oBodyTemplate) {
            createModuleTemplate();
        }
        return (m_oBodyTemplate.cloneNode(false));
    }

    function createFooter() {
        if (!m_oFooterTemplate) {
            createModuleTemplate();
        }
        return (m_oFooterTemplate.cloneNode(false));
    }

    Module.prototype = {

        /**
        * The class's constructor function
        * @property contructor
        * @type Function
        */
        constructor: Module,
        
        /**
        * The main module element that contains the header, body, and footer
        * @property element
        * @type HTMLElement
        */
        element: null,

        /**
        * The header element, denoted with CSS class "hd"
        * @property header
        * @type HTMLElement
        */
        header: null,

        /**
        * The body element, denoted with CSS class "bd"
        * @property body
        * @type HTMLElement
        */
        body: null,

        /**
        * The footer element, denoted with CSS class "ft"
        * @property footer
        * @type HTMLElement
        */
        footer: null,

        /**
        * The id of the element
        * @property id
        * @type String
        */
        id: null,

        /**
        * A string representing the root path for all images created by
        * a Module instance.
        * @deprecated It is recommend that any images for a Module be applied
        * via CSS using the "background-image" property.
        * @property imageRoot
        * @type String
        */
        imageRoot: Module.IMG_ROOT,

        /**
        * Initializes the custom events for Module which are fired 
        * automatically at appropriate times by the Module class.
        * @method initEvents
        */
        initEvents: function () {

            var SIGNATURE = CustomEvent.LIST;

            /**
            * CustomEvent fired prior to class initalization.
            * @event beforeInitEvent
            * @param {class} classRef class reference of the initializing 
            * class, such as this.beforeInitEvent.fire(Module)
            */
            this.beforeInitEvent = this.createEvent(EVENT_TYPES.BEFORE_INIT);
            this.beforeInitEvent.signature = SIGNATURE;

            /**
            * CustomEvent fired after class initalization.
            * @event initEvent
            * @param {class} classRef class reference of the initializing 
            * class, such as this.beforeInitEvent.fire(Module)
            */  
            this.initEvent = this.createEvent(EVENT_TYPES.INIT);
            this.initEvent.signature = SIGNATURE;

            /**
            * CustomEvent fired when the Module is appended to the DOM
            * @event appendEvent
            */
            this.appendEvent = this.createEvent(EVENT_TYPES.APPEND);
            this.appendEvent.signature = SIGNATURE;

            /**
            * CustomEvent fired before the Module is rendered
            * @event beforeRenderEvent
            */
            this.beforeRenderEvent = this.createEvent(EVENT_TYPES.BEFORE_RENDER);
            this.beforeRenderEvent.signature = SIGNATURE;
        
            /**
            * CustomEvent fired after the Module is rendered
            * @event renderEvent
            */
            this.renderEvent = this.createEvent(EVENT_TYPES.RENDER);
            this.renderEvent.signature = SIGNATURE;
        
            /**
            * CustomEvent fired when the header content of the Module 
            * is modified
            * @event changeHeaderEvent
            * @param {String/HTMLElement} content String/element representing 
            * the new header content
            */
            this.changeHeaderEvent = this.createEvent(EVENT_TYPES.CHANGE_HEADER);
            this.changeHeaderEvent.signature = SIGNATURE;
            
            /**
            * CustomEvent fired when the body content of the Module is modified
            * @event changeBodyEvent
            * @param {String/HTMLElement} content String/element representing 
            * the new body content
            */  
            this.changeBodyEvent = this.createEvent(EVENT_TYPES.CHANGE_BODY);
            this.changeBodyEvent.signature = SIGNATURE;
            
            /**
            * CustomEvent fired when the footer content of the Module 
            * is modified
            * @event changeFooterEvent
            * @param {String/HTMLElement} content String/element representing 
            * the new footer content
            */
            this.changeFooterEvent = this.createEvent(EVENT_TYPES.CHANGE_FOOTER);
            this.changeFooterEvent.signature = SIGNATURE;
        
            /**
            * CustomEvent fired when the content of the Module is modified
            * @event changeContentEvent
            */
            this.changeContentEvent = this.createEvent(EVENT_TYPES.CHANGE_CONTENT);
            this.changeContentEvent.signature = SIGNATURE;

            /**
            * CustomEvent fired when the Module is destroyed
            * @event destroyEvent
            */
            this.destroyEvent = this.createEvent(EVENT_TYPES.DESTROY);
            this.destroyEvent.signature = SIGNATURE;

            /**
            * CustomEvent fired before the Module is shown
            * @event beforeShowEvent
            */
            this.beforeShowEvent = this.createEvent(EVENT_TYPES.BEFORE_SHOW);
            this.beforeShowEvent.signature = SIGNATURE;

            /**
            * CustomEvent fired after the Module is shown
            * @event showEvent
            */
            this.showEvent = this.createEvent(EVENT_TYPES.SHOW);
            this.showEvent.signature = SIGNATURE;

            /**
            * CustomEvent fired before the Module is hidden
            * @event beforeHideEvent
            */
            this.beforeHideEvent = this.createEvent(EVENT_TYPES.BEFORE_HIDE);
            this.beforeHideEvent.signature = SIGNATURE;

            /**
            * CustomEvent fired after the Module is hidden
            * @event hideEvent
            */
            this.hideEvent = this.createEvent(EVENT_TYPES.HIDE);
            this.hideEvent.signature = SIGNATURE;
        }, 

        /**
        * String identifying whether the current platform is windows or mac. This property
        * currently only identifies these 2 platforms, and returns false otherwise. 
        * @property platform
        * @deprecated Use YAHOO.env.ua
        * @type {String|Boolean}
        */
        platform: function () {
            var ua = navigator.userAgent.toLowerCase();

            if (ua.indexOf("windows") != -1 || ua.indexOf("win32") != -1) {
                return "windows";
            } else if (ua.indexOf("macintosh") != -1) {
                return "mac";
            } else {
                return false;
            }
        }(),
        
        /**
        * String representing the user-agent of the browser
        * @deprecated Use YAHOO.env.ua
        * @property browser
        * @type {String|Boolean}
        */
        browser: function () {
            var ua = navigator.userAgent.toLowerCase();
            /*
                 Check Opera first in case of spoof and check Safari before
                 Gecko since Safari's user agent string includes "like Gecko"
            */
            if (ua.indexOf('opera') != -1) { 
                return 'opera';
            } else if (ua.indexOf('msie 7') != -1) {
                return 'ie7';
            } else if (ua.indexOf('msie') != -1) {
                return 'ie';
            } else if (ua.indexOf('safari') != -1) { 
                return 'safari';
            } else if (ua.indexOf('gecko') != -1) {
                return 'gecko';
            } else {
                return false;
            }
        }(),
        
        /**
        * Boolean representing whether or not the current browsing context is 
        * secure (https)
        * @property isSecure
        * @type Boolean
        */
        isSecure: function () {
            if (window.location.href.toLowerCase().indexOf("https") === 0) {
                return true;
            } else {
                return false;
            }
        }(),
        
        /**
        * Initializes the custom events for Module which are fired 
        * automatically at appropriate times by the Module class.
        */
        initDefaultConfig: function () {
            // Add properties //
            /**
            * Specifies whether the Module is visible on the page.
            * @config visible
            * @type Boolean
            * @default true
            */
            this.cfg.addProperty(DEFAULT_CONFIG.VISIBLE.key, {
                handler: this.configVisible, 
                value: DEFAULT_CONFIG.VISIBLE.value, 
                validator: DEFAULT_CONFIG.VISIBLE.validator
            });

            /**
            * <p>
            * Object or array of objects representing the ContainerEffect 
            * classes that are active for animating the container.
            * </p>
            * <p>
            * <strong>NOTE:</strong> Although this configuration 
            * property is introduced at the Module level, an out of the box
            * implementation is not shipped for the Module class so setting
            * the proroperty on the Module class has no effect. The Overlay 
            * class is the first class to provide out of the box ContainerEffect 
            * support.
            * </p>
            * @config effect
            * @type Object
            * @default null
            */
            this.cfg.addProperty(DEFAULT_CONFIG.EFFECT.key, {
                handler: this.configEffect,
                suppressEvent: DEFAULT_CONFIG.EFFECT.suppressEvent, 
                supercedes: DEFAULT_CONFIG.EFFECT.supercedes
            });

            /**
            * Specifies whether to create a special proxy iframe to monitor 
            * for user font resizing in the document
            * @config monitorresize
            * @type Boolean
            * @default true
            */
            this.cfg.addProperty(DEFAULT_CONFIG.MONITOR_RESIZE.key, {
                handler: this.configMonitorResize,
                value: DEFAULT_CONFIG.MONITOR_RESIZE.value
            });

            /**
            * Specifies if the module should be rendered as the first child 
            * of document.body or appended as the last child when render is called
            * with document.body as the "appendToNode".
            * <p>
            * Appending to the body while the DOM is still being constructed can 
            * lead to Operation Aborted errors in IE hence this flag is set to 
            * false by default.
            * </p>
            * 
            * @config appendtodocumentbody
            * @type Boolean
            * @default false
            */
            this.cfg.addProperty(DEFAULT_CONFIG.APPEND_TO_DOCUMENT_BODY.key, {
                value: DEFAULT_CONFIG.APPEND_TO_DOCUMENT_BODY.value
            });
        },

        /**
        * The Module class's initialization method, which is executed for
        * Module and all of its subclasses. This method is automatically 
        * called by the constructor, and  sets up all DOM references for 
        * pre-existing markup, and creates required markup if it is not 
        * already present.
        * <p>
        * If the element passed in does not have an id, one will be generated
        * for it.
        * </p>
        * @method init
        * @param {String} el The element ID representing the Module <em>OR</em>
        * @param {HTMLElement} el The element representing the Module
        * @param {Object} userConfig The configuration Object literal 
        * containing the configuration that should be set for this module. 
        * See configuration documentation for more details.
        */
        init: function (el, userConfig) {

            var elId, child;

            this.initEvents();
            this.beforeInitEvent.fire(Module);

            /**
            * The Module's Config object used for monitoring 
            * configuration properties.
            * @property cfg
            * @type YAHOO.util.Config
            */
            this.cfg = new Config(this);

            if (this.isSecure) {
                this.imageRoot = Module.IMG_ROOT_SSL;
            }

            if (typeof el == "string") {
                elId = el;
                el = document.getElementById(el);
                if (! el) {
                    el = (createModuleTemplate()).cloneNode(false);
                    el.id = elId;
                }
            }

            this.id = Dom.generateId(el);
            this.element = el;

            child = this.element.firstChild;

            if (child) {
                var fndHd = false, fndBd = false, fndFt = false;
                do {
                    // We're looking for elements
                    if (1 == child.nodeType) {
                        if (!fndHd && Dom.hasClass(child, Module.CSS_HEADER)) {
                            this.header = child;
                            fndHd = true;
                        } else if (!fndBd && Dom.hasClass(child, Module.CSS_BODY)) {
                            this.body = child;
                            fndBd = true;
                        } else if (!fndFt && Dom.hasClass(child, Module.CSS_FOOTER)){
                            this.footer = child;
                            fndFt = true;
                        }
                    }
                } while ((child = child.nextSibling));
            }

            this.initDefaultConfig();

            Dom.addClass(this.element, Module.CSS_MODULE);

            if (userConfig) {
                this.cfg.applyConfig(userConfig, true);
            }

            /*
                Subscribe to the fireQueue() method of Config so that any 
                queued configuration changes are excecuted upon render of 
                the Module
            */ 

            if (!Config.alreadySubscribed(this.renderEvent, this.cfg.fireQueue, this.cfg)) {
                this.renderEvent.subscribe(this.cfg.fireQueue, this.cfg, true);
            }

            this.initEvent.fire(Module);
        },

        /**
        * Initialize an empty IFRAME that is placed out of the visible area 
        * that can be used to detect text resize.
        * @method initResizeMonitor
        */
        initResizeMonitor: function () {

            var isGeckoWin = (UA.gecko && this.platform == "windows");
            if (isGeckoWin) {
                // Help prevent spinning loading icon which 
                // started with FireFox 2.0.0.8/Win
                var self = this;
                setTimeout(function(){self._initResizeMonitor();}, 0);
            } else {
                this._initResizeMonitor();
            }
        },

        /**
         * Create and initialize the text resize monitoring iframe.
         * 
         * @protected
         * @method _initResizeMonitor
         */
        _initResizeMonitor : function() {

            var oDoc, 
                oIFrame, 
                sHTML;

            function fireTextResize() {
                Module.textResizeEvent.fire();
            }

            if (!UA.opera) {
                oIFrame = Dom.get("_yuiResizeMonitor");

                var supportsCWResize = this._supportsCWResize();

                if (!oIFrame) {
                    oIFrame = document.createElement("iframe");

                    if (this.isSecure && Module.RESIZE_MONITOR_SECURE_URL && UA.ie) {
                        oIFrame.src = Module.RESIZE_MONITOR_SECURE_URL;
                    }

                    if (!supportsCWResize) {
                        // Can't monitor on contentWindow, so fire from inside iframe
                        sHTML = ["<html><head><script ",
                                 "type=\"text/javascript\">",
                                 "window.onresize=function(){window.parent.",
                                 "YAHOO.widget.Module.textResizeEvent.",
                                 "fire();};<",
                                 "\/script></head>",
                                 "<body></body></html>"].join('');

                        oIFrame.src = "data:text/html;charset=utf-8," + encodeURIComponent(sHTML);
                    }

                    oIFrame.id = "_yuiResizeMonitor";
                    oIFrame.title = "Text Resize Monitor";
                    oIFrame.tabIndex = -1;
                    oIFrame.setAttribute("role", "presentation");

                    /*
                        Need to set "position" property before inserting the 
                        iframe into the document or Safari's status bar will 
                        forever indicate the iframe is loading 
                        (See YUILibrary bug #1723064)
                    */
                    oIFrame.style.position = "absolute";
                    oIFrame.style.visibility = "hidden";

                    var db = document.body,
                        fc = db.firstChild;
                    if (fc) {
                        db.insertBefore(oIFrame, fc);
                    } else {
                        db.appendChild(oIFrame);
                    }

                    // Setting the background color fixes an issue with IE6/IE7, where
                    // elements in the DOM, with -ve margin-top which positioned them 
                    // offscreen (so they would be overlapped by the iframe and its -ve top
                    // setting), would have their -ve margin-top ignored, when the iframe 
                    // was added.
                    oIFrame.style.backgroundColor = "transparent";

                    oIFrame.style.borderWidth = "0";
                    oIFrame.style.width = "2em";
                    oIFrame.style.height = "2em";
                    oIFrame.style.left = "0";
                    oIFrame.style.top = (-1 * (oIFrame.offsetHeight + Module.RESIZE_MONITOR_BUFFER)) + "px";
                    oIFrame.style.visibility = "visible";

                    /*
                       Don't open/close the document for Gecko like we used to, since it
                       leads to duplicate cookies. (See YUILibrary bug #1721755)
                    */
                    if (UA.webkit) {
                        oDoc = oIFrame.contentWindow.document;
                        oDoc.open();
                        oDoc.close();
                    }
                }

                if (oIFrame && oIFrame.contentWindow) {
                    Module.textResizeEvent.subscribe(this.onDomResize, this, true);

                    if (!Module.textResizeInitialized) {
                        if (supportsCWResize) {
                            if (!Event.on(oIFrame.contentWindow, "resize", fireTextResize)) {
                                /*
                                     This will fail in IE if document.domain has 
                                     changed, so we must change the listener to 
                                     use the oIFrame element instead
                                */
                                Event.on(oIFrame, "resize", fireTextResize);
                            }
                        }
                        Module.textResizeInitialized = true;
                    }
                    this.resizeMonitor = oIFrame;
                }
            }
        },

        /**
         * Text resize monitor helper method.
         * Determines if the browser supports resize events on iframe content windows.
         * 
         * @private
         * @method _supportsCWResize
         */
        _supportsCWResize : function() {
            /*
                Gecko 1.8.0 (FF1.5), 1.8.1.0-5 (FF2) won't fire resize on contentWindow.
                Gecko 1.8.1.6+ (FF2.0.0.6+) and all other browsers will fire resize on contentWindow.

                We don't want to start sniffing for patch versions, so fire textResize the same
                way on all FF2 flavors
             */
            var bSupported = true;
            if (UA.gecko && UA.gecko <= 1.8) {
                bSupported = false;
            }
            return bSupported;
        },

        /**
        * Event handler fired when the resize monitor element is resized.
        * @method onDomResize
        * @param {DOMEvent} e The DOM resize event
        * @param {Object} obj The scope object passed to the handler
        */
        onDomResize: function (e, obj) {

            var nTop = -1 * (this.resizeMonitor.offsetHeight + Module.RESIZE_MONITOR_BUFFER);

            this.resizeMonitor.style.top = nTop + "px";
            this.resizeMonitor.style.left = "0";
        },

        /**
        * Sets the Module's header content to the markup specified, or appends 
        * the passed element to the header. 
        * 
        * If no header is present, one will 
        * be automatically created. An empty string can be passed to the method
        * to clear the contents of the header.
        * 
        * @method setHeader
        * @param {HTML} headerContent The markup used to set the header content.
        * As a convenience, non HTMLElement objects can also be passed into 
        * the method, and will be treated as strings, with the header innerHTML
        * set to their default toString implementations. 
        * 
        * <p>NOTE: Markup passed into this method is added to the DOM as HTML, and should be escaped by the implementor if coming from an external source.</p>
        * 
        * <em>OR</em>
        * @param {HTMLElement} headerContent The HTMLElement to append to 
        * <em>OR</em>
        * @param {DocumentFragment} headerContent The document fragment 
        * containing elements which are to be added to the header
        */
        setHeader: function (headerContent) {
            var oHeader = this.header || (this.header = createHeader());

            if (headerContent.nodeName) {
                oHeader.innerHTML = "";
                oHeader.appendChild(headerContent);
            } else {
                oHeader.innerHTML = headerContent;
            }

            if (this._rendered) {
                this._renderHeader();
            }

            this.changeHeaderEvent.fire(headerContent);
            this.changeContentEvent.fire();

        },

        /**
        * Appends the passed element to the header. If no header is present, 
        * one will be automatically created.
        * @method appendToHeader
        * @param {HTMLElement | DocumentFragment} element The element to 
        * append to the header. In the case of a document fragment, the
        * children of the fragment will be appended to the header.
        */
        appendToHeader: function (element) {
            var oHeader = this.header || (this.header = createHeader());

            oHeader.appendChild(element);

            this.changeHeaderEvent.fire(element);
            this.changeContentEvent.fire();

        },

        /**
        * Sets the Module's body content to the HTML specified. 
        * 
        * If no body is present, one will be automatically created. 
        * 
        * An empty string can be passed to the method to clear the contents of the body.
        * @method setBody
        * @param {HTML} bodyContent The HTML used to set the body content 
        * As a convenience, non HTMLElement objects can also be passed into 
        * the method, and will be treated as strings, with the body innerHTML
        * set to their default toString implementations.
        * 
        * <p>NOTE: Markup passed into this method is added to the DOM as HTML, and should be escaped by the implementor if coming from an external source.</p>
        * 
        * <em>OR</em>
        * @param {HTMLElement} bodyContent The HTMLElement to add as the first and only
        * child of the body element.
        * <em>OR</em>
        * @param {DocumentFragment} bodyContent The document fragment 
        * containing elements which are to be added to the body
        */
        setBody: function (bodyContent) {
            var oBody = this.body || (this.body = createBody());

            if (bodyContent.nodeName) {
                oBody.innerHTML = "";
                oBody.appendChild(bodyContent);
            } else {
                oBody.innerHTML = bodyContent;
            }

            if (this._rendered) {
                this._renderBody();
            }

            this.changeBodyEvent.fire(bodyContent);
            this.changeContentEvent.fire();
        },

        /**
        * Appends the passed element to the body. If no body is present, one 
        * will be automatically created.
        * @method appendToBody
        * @param {HTMLElement | DocumentFragment} element The element to 
        * append to the body. In the case of a document fragment, the
        * children of the fragment will be appended to the body.
        * 
        */
        appendToBody: function (element) {
            var oBody = this.body || (this.body = createBody());
        
            oBody.appendChild(element);

            this.changeBodyEvent.fire(element);
            this.changeContentEvent.fire();

        },

        /**
        * Sets the Module's footer content to the HTML specified, or appends 
        * the passed element to the footer. If no footer is present, one will 
        * be automatically created. An empty string can be passed to the method
        * to clear the contents of the footer.
        * @method setFooter
        * @param {HTML} footerContent The HTML used to set the footer 
        * As a convenience, non HTMLElement objects can also be passed into 
        * the method, and will be treated as strings, with the footer innerHTML
        * set to their default toString implementations.
        * 
        * <p>NOTE: Markup passed into this method is added to the DOM as HTML, and should be escaped by the implementor if coming from an external source.</p>
        * 
        * <em>OR</em>
        * @param {HTMLElement} footerContent The HTMLElement to append to 
        * the footer
        * <em>OR</em>
        * @param {DocumentFragment} footerContent The document fragment containing 
        * elements which are to be added to the footer
        */
        setFooter: function (footerContent) {

            var oFooter = this.footer || (this.footer = createFooter());

            if (footerContent.nodeName) {
                oFooter.innerHTML = "";
                oFooter.appendChild(footerContent);
            } else {
                oFooter.innerHTML = footerContent;
            }

            if (this._rendered) {
                this._renderFooter();
            }

            this.changeFooterEvent.fire(footerContent);
            this.changeContentEvent.fire();
        },

        /**
        * Appends the passed element to the footer. If no footer is present, 
        * one will be automatically created.
        * @method appendToFooter
        * @param {HTMLElement | DocumentFragment} element The element to 
        * append to the footer. In the case of a document fragment, the
        * children of the fragment will be appended to the footer
        */
        appendToFooter: function (element) {

            var oFooter = this.footer || (this.footer = createFooter());

            oFooter.appendChild(element);

            this.changeFooterEvent.fire(element);
            this.changeContentEvent.fire();

        },

        /**
        * Renders the Module by inserting the elements that are not already 
        * in the main Module into their correct places. Optionally appends 
        * the Module to the specified node prior to the render's execution. 
        * <p>
        * For Modules without existing markup, the appendToNode argument 
        * is REQUIRED. If this argument is ommitted and the current element is 
        * not present in the document, the function will return false, 
        * indicating that the render was a failure.
        * </p>
        * <p>
        * NOTE: As of 2.3.1, if the appendToNode is the document's body element
        * then the module is rendered as the first child of the body element, 
        * and not appended to it, to avoid Operation Aborted errors in IE when 
        * rendering the module before window's load event is fired. You can 
        * use the appendtodocumentbody configuration property to change this 
        * to append to document.body if required.
        * </p>
        * @method render
        * @param {String} appendToNode The element id to which the Module 
        * should be appended to prior to rendering <em>OR</em>
        * @param {HTMLElement} appendToNode The element to which the Module 
        * should be appended to prior to rendering
        * @param {HTMLElement} moduleElement OPTIONAL. The element that 
        * represents the actual Standard Module container.
        * @return {Boolean} Success or failure of the render
        */
        render: function (appendToNode, moduleElement) {

            var me = this;

            function appendTo(parentNode) {
                if (typeof parentNode == "string") {
                    parentNode = document.getElementById(parentNode);
                }

                if (parentNode) {
                    me._addToParent(parentNode, me.element);
                    me.appendEvent.fire();
                }
            }

            this.beforeRenderEvent.fire();

            if (! moduleElement) {
                moduleElement = this.element;
            }

            if (appendToNode) {
                appendTo(appendToNode);
            } else { 
                // No node was passed in. If the element is not already in the Dom, this fails
                if (! Dom.inDocument(this.element)) {
                    YAHOO.log("Render failed. Must specify appendTo node if " + " Module isn't already in the DOM.", "error");
                    return false;
                }
            }

            this._renderHeader(moduleElement);
            this._renderBody(moduleElement);
            this._renderFooter(moduleElement);

            this._rendered = true;

            this.renderEvent.fire();
            return true;
        },

        /**
         * Renders the currently set header into it's proper position under the 
         * module element. If the module element is not provided, "this.element" 
         * is used.
         * 
         * @method _renderHeader
         * @protected
         * @param {HTMLElement} moduleElement Optional. A reference to the module element
         */
        _renderHeader: function(moduleElement){
            moduleElement = moduleElement || this.element;

            // Need to get everything into the DOM if it isn't already
            if (this.header && !Dom.inDocument(this.header)) {
                // There is a header, but it's not in the DOM yet. Need to add it.
                var firstChild = moduleElement.firstChild;
                if (firstChild) {
                    moduleElement.insertBefore(this.header, firstChild);
                } else {
                    moduleElement.appendChild(this.header);
                }
            }
        },

        /**
         * Renders the currently set body into it's proper position under the 
         * module element. If the module element is not provided, "this.element" 
         * is used.
         * 
         * @method _renderBody
         * @protected
         * @param {HTMLElement} moduleElement Optional. A reference to the module element.
         */
        _renderBody: function(moduleElement){
            moduleElement = moduleElement || this.element;

            if (this.body && !Dom.inDocument(this.body)) {
                // There is a body, but it's not in the DOM yet. Need to add it.
                if (this.footer && Dom.isAncestor(moduleElement, this.footer)) {
                    moduleElement.insertBefore(this.body, this.footer);
                } else {
                    moduleElement.appendChild(this.body);
                }
            }
        },

        /**
         * Renders the currently set footer into it's proper position under the 
         * module element. If the module element is not provided, "this.element" 
         * is used.
         * 
         * @method _renderFooter
         * @protected
         * @param {HTMLElement} moduleElement Optional. A reference to the module element
         */
        _renderFooter: function(moduleElement){
            moduleElement = moduleElement || this.element;

            if (this.footer && !Dom.inDocument(this.footer)) {
                // There is a footer, but it's not in the DOM yet. Need to add it.
                moduleElement.appendChild(this.footer);
            }
        },

        /**
        * Removes the Module element from the DOM, sets all child elements to null, and purges the bounding element of event listeners.
        * @method destroy
        * @param {boolean} shallowPurge If true, only the parent element's DOM event listeners are purged. If false, or not provided, all children are also purged of DOM event listeners. 
        * NOTE: The flag is a "shallowPurge" flag, as opposed to what may be a more intuitive "purgeChildren" flag to maintain backwards compatibility with behavior prior to 2.9.0.
        */
        destroy: function (shallowPurge) {

            var parent,
                purgeChildren = !(shallowPurge);

            if (this.element) {
                Event.purgeElement(this.element, purgeChildren);
                parent = this.element.parentNode;
            }

            if (parent) {
                parent.removeChild(this.element);
            }
        
            this.element = null;
            this.header = null;
            this.body = null;
            this.footer = null;

            Module.textResizeEvent.unsubscribe(this.onDomResize, this);

            this.cfg.destroy();
            this.cfg = null;

            this.destroyEvent.fire();
        },

        /**
        * Shows the Module element by setting the visible configuration 
        * property to true. Also fires two events: beforeShowEvent prior to 
        * the visibility change, and showEvent after.
        * @method show
        */
        show: function () {
            this.cfg.setProperty("visible", true);
        },

        /**
        * Hides the Module element by setting the visible configuration 
        * property to false. Also fires two events: beforeHideEvent prior to 
        * the visibility change, and hideEvent after.
        * @method hide
        */
        hide: function () {
            this.cfg.setProperty("visible", false);
        },
        
        // BUILT-IN EVENT HANDLERS FOR MODULE //
        /**
        * Default event handler for changing the visibility property of a 
        * Module. By default, this is achieved by switching the "display" style 
        * between "block" and "none".
        * This method is responsible for firing showEvent and hideEvent.
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        * @method configVisible
        */
        configVisible: function (type, args, obj) {
            var visible = args[0];
            if (visible) {
                if(this.beforeShowEvent.fire()) {
                    Dom.setStyle(this.element, "display", "block");
                    this.showEvent.fire();
                }
            } else {
                if (this.beforeHideEvent.fire()) {
                    Dom.setStyle(this.element, "display", "none");
                    this.hideEvent.fire();
                }
            }
        },

        /**
        * Default event handler for the "effect" configuration property
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        * @method configEffect
        */
        configEffect: function (type, args, obj) {
            this._cachedEffects = (this.cacheEffects) ? this._createEffects(args[0]) : null;
        },

        /**
         * If true, ContainerEffects (and Anim instances) are cached when "effect" is set, and reused. 
         * If false, new instances are created each time the container is hidden or shown, as was the 
         * behavior prior to 2.9.0. 
         *
         * @property cacheEffects
         * @since 2.9.0
         * @default true
         * @type boolean
         */
        cacheEffects : true,

        /**
         * Creates an array of ContainerEffect instances from the provided configs
         * 
         * @method _createEffects
         * @param {Array|Object} effectCfg An effect configuration or array of effect configurations
         * @return {Array} An array of ContainerEffect instances.
         * @protected
         */
        _createEffects: function(effectCfg) {
            var effectInstances = null,
                n, 
                i,
                eff;

            if (effectCfg) {
                if (effectCfg instanceof Array) {
                    effectInstances = [];
                    n = effectCfg.length;
                    for (i = 0; i < n; i++) {
                        eff = effectCfg[i];
                        if (eff.effect) {
                            effectInstances[effectInstances.length] = eff.effect(this, eff.duration);
                        }
                    }
                } else if (effectCfg.effect) {
                    effectInstances = [effectCfg.effect(this, effectCfg.duration)];
                }
            }

            return effectInstances;
        },

        /**
        * Default event handler for the "monitorresize" configuration property
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        * @method configMonitorResize
        */
        configMonitorResize: function (type, args, obj) {
            var monitor = args[0];
            if (monitor) {
                this.initResizeMonitor();
            } else {
                Module.textResizeEvent.unsubscribe(this.onDomResize, this, true);
                this.resizeMonitor = null;
            }
        },

        /**
         * This method is a protected helper, used when constructing the DOM structure for the module 
         * to account for situations which may cause Operation Aborted errors in IE. It should not 
         * be used for general DOM construction.
         * <p>
         * If the parentNode is not document.body, the element is appended as the last element.
         * </p>
         * <p>
         * If the parentNode is document.body the element is added as the first child to help
         * prevent Operation Aborted errors in IE.
         * </p>
         *
         * @param {parentNode} The HTML element to which the element will be added
         * @param {element} The HTML element to be added to parentNode's children
         * @method _addToParent
         * @protected
         */
        _addToParent: function(parentNode, element) {
            if (!this.cfg.getProperty("appendtodocumentbody") && parentNode === document.body && parentNode.firstChild) {
                parentNode.insertBefore(element, parentNode.firstChild);
            } else {
                parentNode.appendChild(element);
            }
        },

        /**
        * Returns a String representation of the Object.
        * @method toString
        * @return {String} The string representation of the Module
        */
        toString: function () {
            return "Module " + this.id;
        }
    };

    YAHOO.lang.augmentProto(Module, YAHOO.util.EventProvider);

}());
(function () {

    /**
    * Overlay is a Module that is absolutely positioned above the page flow. It 
    * has convenience methods for positioning and sizing, as well as options for 
    * controlling zIndex and constraining the Overlay's position to the current 
    * visible viewport. Overlay also contains a dynamicly generated IFRAME which 
    * is placed beneath it for Internet Explorer 6 and 5.x so that it will be 
    * properly rendered above SELECT elements.
    * @namespace YAHOO.widget
    * @class Overlay
    * @extends YAHOO.widget.Module
    * @param {String} el The element ID representing the Overlay <em>OR</em>
    * @param {HTMLElement} el The element representing the Overlay
    * @param {Object} userConfig The configuration object literal containing 
    * the configuration that should be set for this Overlay. See configuration 
    * documentation for more details.
    * @constructor
    */
    YAHOO.widget.Overlay = function (el, userConfig) {
        YAHOO.widget.Overlay.superclass.constructor.call(this, el, userConfig);
    };

    var Lang = YAHOO.lang,
        CustomEvent = YAHOO.util.CustomEvent,
        Module = YAHOO.widget.Module,
        Event = YAHOO.util.Event,
        Dom = YAHOO.util.Dom,
        Config = YAHOO.util.Config,
        UA = YAHOO.env.ua,
        Overlay = YAHOO.widget.Overlay,

        _SUBSCRIBE = "subscribe",
        _UNSUBSCRIBE = "unsubscribe",
        _CONTAINED = "contained",

        m_oIFrameTemplate,

        /**
        * Constant representing the name of the Overlay's events
        * @property EVENT_TYPES
        * @private
        * @final
        * @type Object
        */
        EVENT_TYPES = {
            "BEFORE_MOVE": "beforeMove",
            "MOVE": "move"
        },

        /**
        * Constant representing the Overlay's configuration properties
        * @property DEFAULT_CONFIG
        * @private
        * @final
        * @type Object
        */
        DEFAULT_CONFIG = {

            "X": { 
                key: "x", 
                validator: Lang.isNumber, 
                suppressEvent: true, 
                supercedes: ["iframe"]
            },

            "Y": { 
                key: "y", 
                validator: Lang.isNumber, 
                suppressEvent: true, 
                supercedes: ["iframe"]
            },

            "XY": { 
                key: "xy", 
                suppressEvent: true, 
                supercedes: ["iframe"] 
            },

            "CONTEXT": { 
                key: "context", 
                suppressEvent: true, 
                supercedes: ["iframe"] 
            },

            "FIXED_CENTER": { 
                key: "fixedcenter", 
                value: false, 
                supercedes: ["iframe", "visible"] 
            },

            "WIDTH": { 
                key: "width",
                suppressEvent: true,
                supercedes: ["context", "fixedcenter", "iframe"]
            }, 

            "HEIGHT": { 
                key: "height", 
                suppressEvent: true, 
                supercedes: ["context", "fixedcenter", "iframe"] 
            },

            "AUTO_FILL_HEIGHT" : {
                key: "autofillheight",
                supercedes: ["height"],
                value:"body"
            },

            "ZINDEX": { 
                key: "zindex", 
                value: null 
            },

            "CONSTRAIN_TO_VIEWPORT": { 
                key: "constraintoviewport", 
                value: false, 
                validator: Lang.isBoolean, 
                supercedes: ["iframe", "x", "y", "xy"]
            }, 

            "IFRAME": { 
                key: "iframe", 
                value: (UA.ie == 6 ? true : false), 
                validator: Lang.isBoolean, 
                supercedes: ["zindex"] 
            },

            "PREVENT_CONTEXT_OVERLAP": {
                key: "preventcontextoverlap",
                value: false,
                validator: Lang.isBoolean,  
                supercedes: ["constraintoviewport"]
            }

        };

    /**
    * The URL that will be placed in the iframe
    * @property YAHOO.widget.Overlay.IFRAME_SRC
    * @static
    * @final
    * @type String
    */
    Overlay.IFRAME_SRC = "javascript:false;";

    /**
    * Number representing how much the iframe shim should be offset from each 
    * side of an Overlay instance, in pixels.
    * @property YAHOO.widget.Overlay.IFRAME_SRC
    * @default 3
    * @static
    * @final
    * @type Number
    */
    Overlay.IFRAME_OFFSET = 3;

    /**
    * Number representing the minimum distance an Overlay instance should be 
    * positioned relative to the boundaries of the browser's viewport, in pixels.
    * @property YAHOO.widget.Overlay.VIEWPORT_OFFSET
    * @default 10
    * @static
    * @final
    * @type Number
    */
    Overlay.VIEWPORT_OFFSET = 10;

    /**
    * Constant representing the top left corner of an element, used for 
    * configuring the context element alignment
    * @property YAHOO.widget.Overlay.TOP_LEFT
    * @static
    * @final
    * @type String
    */
    Overlay.TOP_LEFT = "tl";

    /**
    * Constant representing the top right corner of an element, used for 
    * configuring the context element alignment
    * @property YAHOO.widget.Overlay.TOP_RIGHT
    * @static
    * @final
    * @type String
    */
    Overlay.TOP_RIGHT = "tr";

    /**
    * Constant representing the top bottom left corner of an element, used for 
    * configuring the context element alignment
    * @property YAHOO.widget.Overlay.BOTTOM_LEFT
    * @static
    * @final
    * @type String
    */
    Overlay.BOTTOM_LEFT = "bl";

    /**
    * Constant representing the bottom right corner of an element, used for 
    * configuring the context element alignment
    * @property YAHOO.widget.Overlay.BOTTOM_RIGHT
    * @static
    * @final
    * @type String
    */
    Overlay.BOTTOM_RIGHT = "br";

    Overlay.PREVENT_OVERLAP_X = {
        "tltr": true,
        "blbr": true,
        "brbl": true,
        "trtl": true
    };
            
    Overlay.PREVENT_OVERLAP_Y = {
        "trbr": true,
        "tlbl": true,
        "bltl": true,
        "brtr": true
    };

    /**
    * Constant representing the default CSS class used for an Overlay
    * @property YAHOO.widget.Overlay.CSS_OVERLAY
    * @static
    * @final
    * @type String
    */
    Overlay.CSS_OVERLAY = "yui-overlay";

    /**
    * Constant representing the default hidden CSS class used for an Overlay. This class is 
    * applied to the overlay's outer DIV whenever it's hidden.
    *
    * @property YAHOO.widget.Overlay.CSS_HIDDEN
    * @static
    * @final
    * @type String
    */
    Overlay.CSS_HIDDEN = "yui-overlay-hidden";

    /**
    * Constant representing the default CSS class used for an Overlay iframe shim.
    * 
    * @property YAHOO.widget.Overlay.CSS_IFRAME
    * @static
    * @final
    * @type String
    */
    Overlay.CSS_IFRAME = "yui-overlay-iframe";

    /**
     * Constant representing the names of the standard module elements
     * used in the overlay.
     * @property YAHOO.widget.Overlay.STD_MOD_RE
     * @static
     * @final
     * @type RegExp
     */
    Overlay.STD_MOD_RE = /^\s*?(body|footer|header)\s*?$/i;

    /**
    * A singleton CustomEvent used for reacting to the DOM event for 
    * window scroll
    * @event YAHOO.widget.Overlay.windowScrollEvent
    */
    Overlay.windowScrollEvent = new CustomEvent("windowScroll");

    /**
    * A singleton CustomEvent used for reacting to the DOM event for
    * window resize
    * @event YAHOO.widget.Overlay.windowResizeEvent
    */
    Overlay.windowResizeEvent = new CustomEvent("windowResize");

    /**
    * The DOM event handler used to fire the CustomEvent for window scroll
    * @method YAHOO.widget.Overlay.windowScrollHandler
    * @static
    * @param {DOMEvent} e The DOM scroll event
    */
    Overlay.windowScrollHandler = function (e) {
        var t = Event.getTarget(e);

        // - Webkit (Safari 2/3) and Opera 9.2x bubble scroll events from elements to window
        // - FF2/3 and IE6/7, Opera 9.5x don't bubble scroll events from elements to window
        // - IE doesn't recognize scroll registered on the document.
        //
        // Also, when document view is scrolled, IE doesn't provide a target, 
        // rest of the browsers set target to window.document, apart from opera 
        // which sets target to window.
        if (!t || t === window || t === window.document) {
            if (UA.ie) {

                if (! window.scrollEnd) {
                    window.scrollEnd = -1;
                }

                clearTimeout(window.scrollEnd);
        
                window.scrollEnd = setTimeout(function () { 
                    Overlay.windowScrollEvent.fire(); 
                }, 1);
        
            } else {
                Overlay.windowScrollEvent.fire();
            }
        }
    };

    /**
    * The DOM event handler used to fire the CustomEvent for window resize
    * @method YAHOO.widget.Overlay.windowResizeHandler
    * @static
    * @param {DOMEvent} e The DOM resize event
    */
    Overlay.windowResizeHandler = function (e) {

        if (UA.ie) {
            if (! window.resizeEnd) {
                window.resizeEnd = -1;
            }

            clearTimeout(window.resizeEnd);

            window.resizeEnd = setTimeout(function () {
                Overlay.windowResizeEvent.fire(); 
            }, 100);
        } else {
            Overlay.windowResizeEvent.fire();
        }
    };

    /**
    * A boolean that indicated whether the window resize and scroll events have 
    * already been subscribed to.
    * @property YAHOO.widget.Overlay._initialized
    * @private
    * @type Boolean
    */
    Overlay._initialized = null;

    if (Overlay._initialized === null) {
        Event.on(window, "scroll", Overlay.windowScrollHandler);
        Event.on(window, "resize", Overlay.windowResizeHandler);
        Overlay._initialized = true;
    }

    /**
     * Internal map of special event types, which are provided
     * by the instance. It maps the event type to the custom event 
     * instance. Contains entries for the "windowScroll", "windowResize" and
     * "textResize" static container events.
     *
     * @property YAHOO.widget.Overlay._TRIGGER_MAP
     * @type Object
     * @static
     * @private
     */
    Overlay._TRIGGER_MAP = {
        "windowScroll" : Overlay.windowScrollEvent,
        "windowResize" : Overlay.windowResizeEvent,
        "textResize"   : Module.textResizeEvent
    };

    YAHOO.extend(Overlay, Module, {

        /**
         * <p>
         * Array of default event types which will trigger
         * context alignment for the Overlay class.
         * </p>
         * <p>The array is empty by default for Overlay,
         * but maybe populated in future releases, so classes extending
         * Overlay which need to define their own set of CONTEXT_TRIGGERS
         * should concatenate their super class's prototype.CONTEXT_TRIGGERS 
         * value with their own array of values.
         * </p>
         * <p>
         * E.g.:
         * <code>CustomOverlay.prototype.CONTEXT_TRIGGERS = YAHOO.widget.Overlay.prototype.CONTEXT_TRIGGERS.concat(["windowScroll"]);</code>
         * </p>
         * 
         * @property CONTEXT_TRIGGERS
         * @type Array
         * @final
         */
        CONTEXT_TRIGGERS : [],

        /**
        * The Overlay initialization method, which is executed for Overlay and  
        * all of its subclasses. This method is automatically called by the 
        * constructor, and  sets up all DOM references for pre-existing markup, 
        * and creates required markup if it is not already present.
        * @method init
        * @param {String} el The element ID representing the Overlay <em>OR</em>
        * @param {HTMLElement} el The element representing the Overlay
        * @param {Object} userConfig The configuration object literal 
        * containing the configuration that should be set for this Overlay. 
        * See configuration documentation for more details.
        */
        init: function (el, userConfig) {

            /*
                 Note that we don't pass the user config in here yet because we
                 only want it executed once, at the lowest subclass level
            */

            Overlay.superclass.init.call(this, el/*, userConfig*/);

            this.beforeInitEvent.fire(Overlay);

            Dom.addClass(this.element, Overlay.CSS_OVERLAY);

            if (userConfig) {
                this.cfg.applyConfig(userConfig, true);
            }

            if (this.platform == "mac" && UA.gecko) {

                if (! Config.alreadySubscribed(this.showEvent,
                    this.showMacGeckoScrollbars, this)) {

                    this.showEvent.subscribe(this.showMacGeckoScrollbars, 
                        this, true);

                }

                if (! Config.alreadySubscribed(this.hideEvent, 
                    this.hideMacGeckoScrollbars, this)) {

                    this.hideEvent.subscribe(this.hideMacGeckoScrollbars, 
                        this, true);

                }
            }

            this.initEvent.fire(Overlay);
        },
        
        /**
        * Initializes the custom events for Overlay which are fired  
        * automatically at appropriate times by the Overlay class.
        * @method initEvents
        */
        initEvents: function () {

            Overlay.superclass.initEvents.call(this);

            var SIGNATURE = CustomEvent.LIST;

            /**
            * CustomEvent fired before the Overlay is moved.
            * @event beforeMoveEvent
            * @param {Number} x x coordinate
            * @param {Number} y y coordinate
            */
            this.beforeMoveEvent = this.createEvent(EVENT_TYPES.BEFORE_MOVE);
            this.beforeMoveEvent.signature = SIGNATURE;

            /**
            * CustomEvent fired after the Overlay is moved.
            * @event moveEvent
            * @param {Number} x x coordinate
            * @param {Number} y y coordinate
            */
            this.moveEvent = this.createEvent(EVENT_TYPES.MOVE);
            this.moveEvent.signature = SIGNATURE;

        },
        
        /**
        * Initializes the class's configurable properties which can be changed 
        * using the Overlay's Config object (cfg).
        * @method initDefaultConfig
        */
        initDefaultConfig: function () {
    
            Overlay.superclass.initDefaultConfig.call(this);

            var cfg = this.cfg;

            // Add overlay config properties //
            
            /**
            * The absolute x-coordinate position of the Overlay
            * @config x
            * @type Number
            * @default null
            */
            cfg.addProperty(DEFAULT_CONFIG.X.key, { 
    
                handler: this.configX, 
                validator: DEFAULT_CONFIG.X.validator, 
                suppressEvent: DEFAULT_CONFIG.X.suppressEvent, 
                supercedes: DEFAULT_CONFIG.X.supercedes
    
            });

            /**
            * The absolute y-coordinate position of the Overlay
            * @config y
            * @type Number
            * @default null
            */
            cfg.addProperty(DEFAULT_CONFIG.Y.key, {

                handler: this.configY, 
                validator: DEFAULT_CONFIG.Y.validator, 
                suppressEvent: DEFAULT_CONFIG.Y.suppressEvent, 
                supercedes: DEFAULT_CONFIG.Y.supercedes

            });

            /**
            * An array with the absolute x and y positions of the Overlay
            * @config xy
            * @type Number[]
            * @default null
            */
            cfg.addProperty(DEFAULT_CONFIG.XY.key, {
                handler: this.configXY, 
                suppressEvent: DEFAULT_CONFIG.XY.suppressEvent, 
                supercedes: DEFAULT_CONFIG.XY.supercedes
            });

            /**
            * <p>
            * The array of context arguments for context-sensitive positioning. 
            * </p>
            *
            * <p>
            * The format of the array is: <code>[contextElementOrId, overlayCorner, contextCorner, arrayOfTriggerEvents (optional), xyOffset (optional)]</code>, the
            * the 5 array elements described in detail below:
            * </p>
            *
            * <dl>
            * <dt>contextElementOrId &#60;String|HTMLElement&#62;</dt>
            * <dd>A reference to the context element to which the overlay should be aligned (or it's id).</dd>
            * <dt>overlayCorner &#60;String&#62;</dt>
            * <dd>The corner of the overlay which is to be used for alignment. This corner will be aligned to the 
            * corner of the context element defined by the "contextCorner" entry which follows. Supported string values are: 
            * "tr" (top right), "tl" (top left), "br" (bottom right), or "bl" (bottom left).</dd>
            * <dt>contextCorner &#60;String&#62;</dt>
            * <dd>The corner of the context element which is to be used for alignment. Supported string values are the same ones listed for the "overlayCorner" entry above.</dd>
            * <dt>arrayOfTriggerEvents (optional) &#60;Array[String|CustomEvent]&#62;</dt>
            * <dd>
            * <p>
            * By default, context alignment is a one time operation, aligning the Overlay to the context element when context configuration property is set, or when the <a href="#method_align">align</a> 
            * method is invoked. However, you can use the optional "arrayOfTriggerEvents" entry to define the list of events which should force the overlay to re-align itself with the context element. 
            * This is useful in situations where the layout of the document may change, resulting in the context element's position being modified.
            * </p>
            * <p>
            * The array can contain either event type strings for events the instance publishes (e.g. "beforeShow") or CustomEvent instances. Additionally the following
            * 3 static container event types are also currently supported : <code>"windowResize", "windowScroll", "textResize"</code> (defined in <a href="#property__TRIGGER_MAP">_TRIGGER_MAP</a> private property).
            * </p>
            * </dd>
            * <dt>xyOffset &#60;Number[]&#62;</dt>
            * <dd>
            * A 2 element Array specifying the X and Y pixel amounts by which the Overlay should be offset from the aligned corner. e.g. [5,0] offsets the Overlay 5 pixels to the left, <em>after</em> aligning the given context corners.
            * NOTE: If using this property and no triggers need to be defined, the arrayOfTriggerEvents property should be set to null to maintain correct array positions for the arguments. 
            * </dd>
            * </dl>
            *
            * <p>
            * For example, setting this property to <code>["img1", "tl", "bl"]</code> will 
            * align the Overlay's top left corner to the bottom left corner of the
            * context element with id "img1".
            * </p>
            * <p>
            * Setting this property to <code>["img1", "tl", "bl", null, [0,5]</code> will 
            * align the Overlay's top left corner to the bottom left corner of the
            * context element with id "img1", and then offset it by 5 pixels on the Y axis (providing a 5 pixel gap between the bottom of the context element and top of the overlay).
            * </p>
            * <p>
            * Adding the optional trigger values: <code>["img1", "tl", "bl", ["beforeShow", "windowResize"], [0,5]]</code>,
            * will re-align the overlay position, whenever the "beforeShow" or "windowResize" events are fired.
            * </p>
            *
            * @config context
            * @type Array
            * @default null
            */
            cfg.addProperty(DEFAULT_CONFIG.CONTEXT.key, {
                handler: this.configContext, 
                suppressEvent: DEFAULT_CONFIG.CONTEXT.suppressEvent, 
                supercedes: DEFAULT_CONFIG.CONTEXT.supercedes
            });

            /**
            * Determines whether or not the Overlay should be anchored 
            * to the center of the viewport.
            * 
            * <p>This property can be set to:</p>
            * 
            * <dl>
            * <dt>true</dt>
            * <dd>
            * To enable fixed center positioning
            * <p>
            * When enabled, the overlay will 
            * be positioned in the center of viewport when initially displayed, and 
            * will remain in the center of the viewport whenever the window is 
            * scrolled or resized.
            * </p>
            * <p>
            * If the overlay is too big for the viewport, 
            * it's top left corner will be aligned with the top left corner of the viewport.
            * </p>
            * </dd>
            * <dt>false</dt>
            * <dd>
            * To disable fixed center positioning.
            * <p>In this case the overlay can still be 
            * centered as a one-off operation, by invoking the <code>center()</code> method,
            * however it will not remain centered when the window is scrolled/resized.
            * </dd>
            * <dt>"contained"<dt>
            * <dd>To enable fixed center positioning, as with the <code>true</code> option.
            * <p>However, unlike setting the property to <code>true</code>, 
            * when the property is set to <code>"contained"</code>, if the overlay is 
            * too big for the viewport, it will not get automatically centered when the 
            * user scrolls or resizes the window (until the window is large enough to contain the 
            * overlay). This is useful in cases where the Overlay has both header and footer 
            * UI controls which the user may need to access.
            * </p>
            * </dd>
            * </dl>
            *
            * @config fixedcenter
            * @type Boolean | String
            * @default false
            */
            cfg.addProperty(DEFAULT_CONFIG.FIXED_CENTER.key, {
                handler: this.configFixedCenter,
                value: DEFAULT_CONFIG.FIXED_CENTER.value, 
                validator: DEFAULT_CONFIG.FIXED_CENTER.validator, 
                supercedes: DEFAULT_CONFIG.FIXED_CENTER.supercedes
            });
    
            /**
            * CSS width of the Overlay.
            * @config width
            * @type String
            * @default null
            */
            cfg.addProperty(DEFAULT_CONFIG.WIDTH.key, {
                handler: this.configWidth, 
                suppressEvent: DEFAULT_CONFIG.WIDTH.suppressEvent, 
                supercedes: DEFAULT_CONFIG.WIDTH.supercedes
            });

            /**
            * CSS height of the Overlay.
            * @config height
            * @type String
            * @default null
            */
            cfg.addProperty(DEFAULT_CONFIG.HEIGHT.key, {
                handler: this.configHeight, 
                suppressEvent: DEFAULT_CONFIG.HEIGHT.suppressEvent, 
                supercedes: DEFAULT_CONFIG.HEIGHT.supercedes
            });

            /**
            * Standard module element which should auto fill out the height of the Overlay if the height config property is set.
            * Supported values are "header", "body", "footer".
            *
            * @config autofillheight
            * @type String
            * @default null
            */
            cfg.addProperty(DEFAULT_CONFIG.AUTO_FILL_HEIGHT.key, {
                handler: this.configAutoFillHeight, 
                value : DEFAULT_CONFIG.AUTO_FILL_HEIGHT.value,
                validator : this._validateAutoFill,
                supercedes: DEFAULT_CONFIG.AUTO_FILL_HEIGHT.supercedes
            });

            /**
            * CSS z-index of the Overlay.
            * @config zIndex
            * @type Number
            * @default null
            */
            cfg.addProperty(DEFAULT_CONFIG.ZINDEX.key, {
                handler: this.configzIndex,
                value: DEFAULT_CONFIG.ZINDEX.value
            });

            /**
            * True if the Overlay should be prevented from being positioned 
            * out of the viewport.
            * @config constraintoviewport
            * @type Boolean
            * @default false
            */
            cfg.addProperty(DEFAULT_CONFIG.CONSTRAIN_TO_VIEWPORT.key, {

                handler: this.configConstrainToViewport, 
                value: DEFAULT_CONFIG.CONSTRAIN_TO_VIEWPORT.value, 
                validator: DEFAULT_CONFIG.CONSTRAIN_TO_VIEWPORT.validator, 
                supercedes: DEFAULT_CONFIG.CONSTRAIN_TO_VIEWPORT.supercedes

            });

            /**
            * @config iframe
            * @description Boolean indicating whether or not the Overlay should 
            * have an IFRAME shim; used to prevent SELECT elements from 
            * poking through an Overlay instance in IE6.  When set to "true", 
            * the iframe shim is created when the Overlay instance is intially
            * made visible.
            * @type Boolean
            * @default true for IE6 and below, false for all other browsers.
            */
            cfg.addProperty(DEFAULT_CONFIG.IFRAME.key, {

                handler: this.configIframe, 
                value: DEFAULT_CONFIG.IFRAME.value, 
                validator: DEFAULT_CONFIG.IFRAME.validator, 
                supercedes: DEFAULT_CONFIG.IFRAME.supercedes

            });

            /**
            * @config preventcontextoverlap
            * @description Boolean indicating whether or not the Overlay should overlap its 
            * context element (defined using the "context" configuration property) when the 
            * "constraintoviewport" configuration property is set to "true".
            * @type Boolean
            * @default false
            */
            cfg.addProperty(DEFAULT_CONFIG.PREVENT_CONTEXT_OVERLAP.key, {
                value: DEFAULT_CONFIG.PREVENT_CONTEXT_OVERLAP.value, 
                validator: DEFAULT_CONFIG.PREVENT_CONTEXT_OVERLAP.validator, 
                supercedes: DEFAULT_CONFIG.PREVENT_CONTEXT_OVERLAP.supercedes
            });
        },

        /**
        * Moves the Overlay to the specified position. This function is  
        * identical to calling this.cfg.setProperty("xy", [x,y]);
        * @method moveTo
        * @param {Number} x The Overlay's new x position
        * @param {Number} y The Overlay's new y position
        */
        moveTo: function (x, y) {
            this.cfg.setProperty("xy", [x, y]);
        },

        /**
        * Adds a CSS class ("hide-scrollbars") and removes a CSS class 
        * ("show-scrollbars") to the Overlay to fix a bug in Gecko on Mac OS X 
        * (https://bugzilla.mozilla.org/show_bug.cgi?id=187435)
        * @method hideMacGeckoScrollbars
        */
        hideMacGeckoScrollbars: function () {
            Dom.replaceClass(this.element, "show-scrollbars", "hide-scrollbars");
        },

        /**
        * Adds a CSS class ("show-scrollbars") and removes a CSS class 
        * ("hide-scrollbars") to the Overlay to fix a bug in Gecko on Mac OS X 
        * (https://bugzilla.mozilla.org/show_bug.cgi?id=187435)
        * @method showMacGeckoScrollbars
        */
        showMacGeckoScrollbars: function () {
            Dom.replaceClass(this.element, "hide-scrollbars", "show-scrollbars");
        },

        /**
         * Internal implementation to set the visibility of the overlay in the DOM.
         *
         * @method _setDomVisibility
         * @param {boolean} visible Whether to show or hide the Overlay's outer element
         * @protected
         */
        _setDomVisibility : function(show) {
            Dom.setStyle(this.element, "visibility", (show) ? "visible" : "hidden");
            var hiddenClass = Overlay.CSS_HIDDEN;

            if (show) {
                Dom.removeClass(this.element, hiddenClass);
            } else {
                Dom.addClass(this.element, hiddenClass);
            }
        },

        // BEGIN BUILT-IN PROPERTY EVENT HANDLERS //
        /**
        * The default event handler fired when the "visible" property is 
        * changed.  This method is responsible for firing showEvent
        * and hideEvent.
        * @method configVisible
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        */
        configVisible: function (type, args, obj) {

            var visible = args[0],
                currentVis = Dom.getStyle(this.element, "visibility"),
                effects = this._cachedEffects || this._createEffects(this.cfg.getProperty("effect")),
                isMacGecko = (this.platform == "mac" && UA.gecko),
                alreadySubscribed = Config.alreadySubscribed,
                ei, e, j, k, h,
                nEffectInstances;

            if (currentVis == "inherit") {
                e = this.element.parentNode;

                while (e.nodeType != 9 && e.nodeType != 11) {
                    currentVis = Dom.getStyle(e, "visibility");

                    if (currentVis != "inherit") {
                        break;
                    }

                    e = e.parentNode;
                }

                if (currentVis == "inherit") {
                    currentVis = "visible";
                }
            }

            if (visible) { // Show

                if (isMacGecko) {
                    this.showMacGeckoScrollbars();
                }

                if (effects) { // Animate in
                    if (visible) { // Animate in if not showing

                         // Fading out is a bit of a hack, but didn't want to risk doing 
                         // something broader (e.g a generic this._animatingOut) for 2.9.0

                        if (currentVis != "visible" || currentVis === "" || this._fadingOut) {
                            if (this.beforeShowEvent.fire()) {

                                nEffectInstances = effects.length;

                                for (j = 0; j < nEffectInstances; j++) {
                                    ei = effects[j];
                                    if (j === 0 && !alreadySubscribed(ei.animateInCompleteEvent, this.showEvent.fire, this.showEvent)) {
                                        ei.animateInCompleteEvent.subscribe(this.showEvent.fire, this.showEvent, true);
                                    }
                                    ei.animateIn();
                                }
                            }
                        }
                    }
                } else { // Show
                    if (currentVis != "visible" || currentVis === "") {
                        if (this.beforeShowEvent.fire()) {
                            this._setDomVisibility(true);
                            this.cfg.refireEvent("iframe");
                            this.showEvent.fire();
                        }
                    } else {
                        this._setDomVisibility(true);
                    }
                }
            } else { // Hide

                if (isMacGecko) {
                    this.hideMacGeckoScrollbars();
                }

                if (effects) { // Animate out if showing
                    if (currentVis == "visible" || this._fadingIn) {
                        if (this.beforeHideEvent.fire()) {
                            nEffectInstances = effects.length;
                            for (k = 0; k < nEffectInstances; k++) {
                                h = effects[k];
        
                                if (k === 0 && !alreadySubscribed(h.animateOutCompleteEvent, this.hideEvent.fire, this.hideEvent)) {
                                    h.animateOutCompleteEvent.subscribe(this.hideEvent.fire, this.hideEvent, true);
                                }
                                h.animateOut();
                            }
                        }

                    } else if (currentVis === "") {
                        this._setDomVisibility(false);
                    }

                } else { // Simple hide

                    if (currentVis == "visible" || currentVis === "") {
                        if (this.beforeHideEvent.fire()) {
                            this._setDomVisibility(false);
                            this.hideEvent.fire();
                        }
                    } else {
                        this._setDomVisibility(false);
                    }
                }
            }
        },

        /**
        * Fixed center event handler used for centering on scroll/resize, but only if 
        * the overlay is visible and, if "fixedcenter" is set to "contained", only if 
        * the overlay fits within the viewport.
        *
        * @method doCenterOnDOMEvent
        */
        doCenterOnDOMEvent: function () {
            var cfg = this.cfg,
                fc = cfg.getProperty("fixedcenter");

            if (cfg.getProperty("visible")) {
                if (fc && (fc !== _CONTAINED || this.fitsInViewport())) {
                    this.center();
                }
            }
        },

        /**
         * Determines if the Overlay (including the offset value defined by Overlay.VIEWPORT_OFFSET) 
         * will fit entirely inside the viewport, in both dimensions - width and height.
         * 
         * @method fitsInViewport
         * @return boolean true if the Overlay will fit, false if not
         */
        fitsInViewport : function() {
            var nViewportOffset = Overlay.VIEWPORT_OFFSET,
                element = this.element,
                elementWidth = element.offsetWidth,
                elementHeight = element.offsetHeight,
                viewportWidth = Dom.getViewportWidth(),
                viewportHeight = Dom.getViewportHeight();

            return ((elementWidth + nViewportOffset < viewportWidth) && (elementHeight + nViewportOffset < viewportHeight));
        },

        /**
        * The default event handler fired when the "fixedcenter" property 
        * is changed.
        * @method configFixedCenter
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        */
        configFixedCenter: function (type, args, obj) {

            var val = args[0],
                alreadySubscribed = Config.alreadySubscribed,
                windowResizeEvent = Overlay.windowResizeEvent,
                windowScrollEvent = Overlay.windowScrollEvent;

            if (val) {
                this.center();

                if (!alreadySubscribed(this.beforeShowEvent, this.center)) {
                    this.beforeShowEvent.subscribe(this.center);
                }

                if (!alreadySubscribed(windowResizeEvent, this.doCenterOnDOMEvent, this)) {
                    windowResizeEvent.subscribe(this.doCenterOnDOMEvent, this, true);
                }

                if (!alreadySubscribed(windowScrollEvent, this.doCenterOnDOMEvent, this)) {
                    windowScrollEvent.subscribe(this.doCenterOnDOMEvent, this, true);
                }

            } else {
                this.beforeShowEvent.unsubscribe(this.center);

                windowResizeEvent.unsubscribe(this.doCenterOnDOMEvent, this);
                windowScrollEvent.unsubscribe(this.doCenterOnDOMEvent, this);
            }
        },

        /**
        * The default event handler fired when the "height" property is changed.
        * @method configHeight
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        */
        configHeight: function (type, args, obj) {

            var height = args[0],
                el = this.element;

            Dom.setStyle(el, "height", height);
            this.cfg.refireEvent("iframe");
        },

        /**
         * The default event handler fired when the "autofillheight" property is changed.
         * @method configAutoFillHeight
         *
         * @param {String} type The CustomEvent type (usually the property name)
         * @param {Object[]} args The CustomEvent arguments. For configuration 
         * handlers, args[0] will equal the newly applied value for the property.
         * @param {Object} obj The scope object. For configuration handlers, 
         * this will usually equal the owner.
         */
        configAutoFillHeight: function (type, args, obj) {
            var fillEl = args[0],
                cfg = this.cfg,
                autoFillHeight = "autofillheight",
                height = "height",
                currEl = cfg.getProperty(autoFillHeight),
                autoFill = this._autoFillOnHeightChange;

            cfg.unsubscribeFromConfigEvent(height, autoFill);
            Module.textResizeEvent.unsubscribe(autoFill);
            this.changeContentEvent.unsubscribe(autoFill);

            if (currEl && fillEl !== currEl && this[currEl]) {
                Dom.setStyle(this[currEl], height, "");
            }

            if (fillEl) {
                fillEl = Lang.trim(fillEl.toLowerCase());

                cfg.subscribeToConfigEvent(height, autoFill, this[fillEl], this);
                Module.textResizeEvent.subscribe(autoFill, this[fillEl], this);
                this.changeContentEvent.subscribe(autoFill, this[fillEl], this);

                cfg.setProperty(autoFillHeight, fillEl, true);
            }
        },

        /**
        * The default event handler fired when the "width" property is changed.
        * @method configWidth
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        */
        configWidth: function (type, args, obj) {

            var width = args[0],
                el = this.element;

            Dom.setStyle(el, "width", width);
            this.cfg.refireEvent("iframe");
        },

        /**
        * The default event handler fired when the "zIndex" property is changed.
        * @method configzIndex
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        */
        configzIndex: function (type, args, obj) {

            var zIndex = args[0],
                el = this.element;

            if (! zIndex) {
                zIndex = Dom.getStyle(el, "zIndex");
                if (! zIndex || isNaN(zIndex)) {
                    zIndex = 0;
                }
            }

            if (this.iframe || this.cfg.getProperty("iframe") === true) {
                if (zIndex <= 0) {
                    zIndex = 1;
                }
            }

            Dom.setStyle(el, "zIndex", zIndex);
            this.cfg.setProperty("zIndex", zIndex, true);

            if (this.iframe) {
                this.stackIframe();
            }
        },

        /**
        * The default event handler fired when the "xy" property is changed.
        * @method configXY
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        */
        configXY: function (type, args, obj) {

            var pos = args[0],
                x = pos[0],
                y = pos[1];

            this.cfg.setProperty("x", x);
            this.cfg.setProperty("y", y);

            this.beforeMoveEvent.fire([x, y]);

            x = this.cfg.getProperty("x");
            y = this.cfg.getProperty("y");

            YAHOO.log(("xy: " + [x, y]), "iframe");

            this.cfg.refireEvent("iframe");
            this.moveEvent.fire([x, y]);
        },

        /**
        * The default event handler fired when the "x" property is changed.
        * @method configX
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        */
        configX: function (type, args, obj) {

            var x = args[0],
                y = this.cfg.getProperty("y");

            this.cfg.setProperty("x", x, true);
            this.cfg.setProperty("y", y, true);

            this.beforeMoveEvent.fire([x, y]);

            x = this.cfg.getProperty("x");
            y = this.cfg.getProperty("y");

            Dom.setX(this.element, x, true);

            this.cfg.setProperty("xy", [x, y], true);

            this.cfg.refireEvent("iframe");
            this.moveEvent.fire([x, y]);
        },

        /**
        * The default event handler fired when the "y" property is changed.
        * @method configY
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        */
        configY: function (type, args, obj) {

            var x = this.cfg.getProperty("x"),
                y = args[0];

            this.cfg.setProperty("x", x, true);
            this.cfg.setProperty("y", y, true);

            this.beforeMoveEvent.fire([x, y]);

            x = this.cfg.getProperty("x");
            y = this.cfg.getProperty("y");

            Dom.setY(this.element, y, true);

            this.cfg.setProperty("xy", [x, y], true);

            this.cfg.refireEvent("iframe");
            this.moveEvent.fire([x, y]);
        },
        
        /**
        * Shows the iframe shim, if it has been enabled.
        * @method showIframe
        */
        showIframe: function () {

            var oIFrame = this.iframe,
                oParentNode;

            if (oIFrame) {
                oParentNode = this.element.parentNode;

                if (oParentNode != oIFrame.parentNode) {
                    this._addToParent(oParentNode, oIFrame);
                }
                oIFrame.style.display = "block";
            }
        },

        /**
        * Hides the iframe shim, if it has been enabled.
        * @method hideIframe
        */
        hideIframe: function () {
            if (this.iframe) {
                this.iframe.style.display = "none";
            }
        },

        /**
        * Syncronizes the size and position of iframe shim to that of its 
        * corresponding Overlay instance.
        * @method syncIframe
        */
        syncIframe: function () {

            var oIFrame = this.iframe,
                oElement = this.element,
                nOffset = Overlay.IFRAME_OFFSET,
                nDimensionOffset = (nOffset * 2),
                aXY;

            if (oIFrame) {
                // Size <iframe>
                oIFrame.style.width = (oElement.offsetWidth + nDimensionOffset + "px");
                oIFrame.style.height = (oElement.offsetHeight + nDimensionOffset + "px");

                // Position <iframe>
                aXY = this.cfg.getProperty("xy");

                if (!Lang.isArray(aXY) || (isNaN(aXY[0]) || isNaN(aXY[1]))) {
                    this.syncPosition();
                    aXY = this.cfg.getProperty("xy");
                }
                Dom.setXY(oIFrame, [(aXY[0] - nOffset), (aXY[1] - nOffset)]);
            }
        },

        /**
         * Sets the zindex of the iframe shim, if it exists, based on the zindex of
         * the Overlay element. The zindex of the iframe is set to be one less 
         * than the Overlay element's zindex.
         * 
         * <p>NOTE: This method will not bump up the zindex of the Overlay element
         * to ensure that the iframe shim has a non-negative zindex.
         * If you require the iframe zindex to be 0 or higher, the zindex of 
         * the Overlay element should be set to a value greater than 0, before 
         * this method is called.
         * </p>
         * @method stackIframe
         */
        stackIframe: function () {
            if (this.iframe) {
                var overlayZ = Dom.getStyle(this.element, "zIndex");
                if (!YAHOO.lang.isUndefined(overlayZ) && !isNaN(overlayZ)) {
                    Dom.setStyle(this.iframe, "zIndex", (overlayZ - 1));
                }
            }
        },

        /**
        * The default event handler fired when the "iframe" property is changed.
        * @method configIframe
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        */
        configIframe: function (type, args, obj) {

            var bIFrame = args[0];

            function createIFrame() {

                var oIFrame = this.iframe,
                    oElement = this.element,
                    oParent;

                if (!oIFrame) {
                    if (!m_oIFrameTemplate) {
                        m_oIFrameTemplate = document.createElement("iframe");

                        if (this.isSecure) {
                            m_oIFrameTemplate.src = Overlay.IFRAME_SRC;
                        }

                        /*
                            Set the opacity of the <iframe> to 0 so that it 
                            doesn't modify the opacity of any transparent 
                            elements that may be on top of it (like a shadow).
                        */
                        if (UA.ie) {
                            m_oIFrameTemplate.style.filter = "alpha(opacity=0)";
                            /*
                                 Need to set the "frameBorder" property to 0 
                                 supress the default <iframe> border in IE.  
                                 Setting the CSS "border" property alone 
                                 doesn't supress it.
                            */
                            m_oIFrameTemplate.frameBorder = 0;
                        }
                        else {
                            m_oIFrameTemplate.style.opacity = "0";
                        }

                        m_oIFrameTemplate.style.position = "absolute";
                        m_oIFrameTemplate.style.border = "none";
                        m_oIFrameTemplate.style.margin = "0";
                        m_oIFrameTemplate.style.padding = "0";
                        m_oIFrameTemplate.style.display = "none";
                        m_oIFrameTemplate.tabIndex = -1;
                        m_oIFrameTemplate.className = Overlay.CSS_IFRAME;
                    }

                    oIFrame = m_oIFrameTemplate.cloneNode(false);
                    oIFrame.id = this.id + "_f";
                    oParent = oElement.parentNode;

                    var parentNode = oParent || document.body;

                    this._addToParent(parentNode, oIFrame);
                    this.iframe = oIFrame;
                }

                /*
                     Show the <iframe> before positioning it since the "setXY" 
                     method of DOM requires the element be in the document 
                     and visible.
                */
                this.showIframe();

                /*
                     Syncronize the size and position of the <iframe> to that 
                     of the Overlay.
                */
                this.syncIframe();
                this.stackIframe();

                // Add event listeners to update the <iframe> when necessary
                if (!this._hasIframeEventListeners) {
                    this.showEvent.subscribe(this.showIframe);
                    this.hideEvent.subscribe(this.hideIframe);
                    this.changeContentEvent.subscribe(this.syncIframe);

                    this._hasIframeEventListeners = true;
                }
            }

            function onBeforeShow() {
                createIFrame.call(this);
                this.beforeShowEvent.unsubscribe(onBeforeShow);
                this._iframeDeferred = false;
            }

            if (bIFrame) { // <iframe> shim is enabled

                if (this.cfg.getProperty("visible")) {
                    createIFrame.call(this);
                } else {
                    if (!this._iframeDeferred) {
                        this.beforeShowEvent.subscribe(onBeforeShow);
                        this._iframeDeferred = true;
                    }
                }

            } else {    // <iframe> shim is disabled
                this.hideIframe();

                if (this._hasIframeEventListeners) {
                    this.showEvent.unsubscribe(this.showIframe);
                    this.hideEvent.unsubscribe(this.hideIframe);
                    this.changeContentEvent.unsubscribe(this.syncIframe);

                    this._hasIframeEventListeners = false;
                }
            }
        },

        /**
         * Set's the container's XY value from DOM if not already set.
         * 
         * Differs from syncPosition, in that the XY value is only sync'd with DOM if 
         * not already set. The method also refire's the XY config property event, so any
         * beforeMove, Move event listeners are invoked.
         * 
         * @method _primeXYFromDOM
         * @protected
         */
        _primeXYFromDOM : function() {
            if (YAHOO.lang.isUndefined(this.cfg.getProperty("xy"))) {
                // Set CFG XY based on DOM XY
                this.syncPosition();
                // Account for XY being set silently in syncPosition (no moveTo fired/called)
                this.cfg.refireEvent("xy");
                this.beforeShowEvent.unsubscribe(this._primeXYFromDOM);
            }
        },

        /**
        * The default event handler fired when the "constraintoviewport" 
        * property is changed.
        * @method configConstrainToViewport
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for 
        * the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        */
        configConstrainToViewport: function (type, args, obj) {
            var val = args[0];

            if (val) {
                if (! Config.alreadySubscribed(this.beforeMoveEvent, this.enforceConstraints, this)) {
                    this.beforeMoveEvent.subscribe(this.enforceConstraints, this, true);
                }
                if (! Config.alreadySubscribed(this.beforeShowEvent, this._primeXYFromDOM)) {
                    this.beforeShowEvent.subscribe(this._primeXYFromDOM);
                }
            } else {
                this.beforeShowEvent.unsubscribe(this._primeXYFromDOM);
                this.beforeMoveEvent.unsubscribe(this.enforceConstraints, this);
            }
        },

         /**
        * The default event handler fired when the "context" property
        * is changed.
        *
        * @method configContext
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        */
        configContext: function (type, args, obj) {

            var contextArgs = args[0],
                contextEl,
                elementMagnetCorner,
                contextMagnetCorner,
                triggers,
                offset,
                defTriggers = this.CONTEXT_TRIGGERS;

            if (contextArgs) {

                contextEl = contextArgs[0];
                elementMagnetCorner = contextArgs[1];
                contextMagnetCorner = contextArgs[2];
                triggers = contextArgs[3];
                offset = contextArgs[4];

                if (defTriggers && defTriggers.length > 0) {
                    triggers = (triggers || []).concat(defTriggers);
                }

                if (contextEl) {
                    if (typeof contextEl == "string") {
                        this.cfg.setProperty("context", [
                                document.getElementById(contextEl), 
                                elementMagnetCorner,
                                contextMagnetCorner,
                                triggers,
                                offset],
                                true);
                    }

                    if (elementMagnetCorner && contextMagnetCorner) {
                        this.align(elementMagnetCorner, contextMagnetCorner, offset);
                    }

                    if (this._contextTriggers) {
                        // Unsubscribe Old Set
                        this._processTriggers(this._contextTriggers, _UNSUBSCRIBE, this._alignOnTrigger);
                    }

                    if (triggers) {
                        // Subscribe New Set
                        this._processTriggers(triggers, _SUBSCRIBE, this._alignOnTrigger);
                        this._contextTriggers = triggers;
                    }
                }
            }
        },

        /**
         * Custom Event handler for context alignment triggers. Invokes the align method
         * 
         * @method _alignOnTrigger
         * @protected
         * 
         * @param {String} type The event type (not used by the default implementation)
         * @param {Any[]} args The array of arguments for the trigger event (not used by the default implementation)
         */
        _alignOnTrigger: function(type, args) {
            this.align();
        },

        /**
         * Helper method to locate the custom event instance for the event name string
         * passed in. As a convenience measure, any custom events passed in are returned.
         *
         * @method _findTriggerCE
         * @private
         *
         * @param {String|CustomEvent} t Either a CustomEvent, or event type (e.g. "windowScroll") for which a 
         * custom event instance needs to be looked up from the Overlay._TRIGGER_MAP.
         */
        _findTriggerCE : function(t) {
            var tce = null;
            if (t instanceof CustomEvent) {
                tce = t;
            } else if (Overlay._TRIGGER_MAP[t]) {
                tce = Overlay._TRIGGER_MAP[t];
            }
            return tce;
        },

        /**
         * Utility method that subscribes or unsubscribes the given 
         * function from the list of trigger events provided.
         *
         * @method _processTriggers
         * @protected 
         *
         * @param {Array[String|CustomEvent]} triggers An array of either CustomEvents, event type strings 
         * (e.g. "beforeShow", "windowScroll") to/from which the provided function should be 
         * subscribed/unsubscribed respectively.
         *
         * @param {String} mode Either "subscribe" or "unsubscribe", specifying whether or not
         * we are subscribing or unsubscribing trigger listeners
         * 
         * @param {Function} fn The function to be subscribed/unsubscribed to/from the trigger event.
         * Context is always set to the overlay instance, and no additional object argument 
         * get passed to the subscribed function.
         */
        _processTriggers : function(triggers, mode, fn) {
            var t, tce;

            for (var i = 0, l = triggers.length; i < l; ++i) {
                t = triggers[i];
                tce = this._findTriggerCE(t);
                if (tce) {
                    tce[mode](fn, this, true);
                } else {
                    this[mode](t, fn);
                }
            }
        },

        // END BUILT-IN PROPERTY EVENT HANDLERS //
        /**
        * Aligns the Overlay to its context element using the specified corner 
        * points (represented by the constants TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, 
        * and BOTTOM_RIGHT.
        * @method align
        * @param {String} elementAlign  The String representing the corner of 
        * the Overlay that should be aligned to the context element
        * @param {String} contextAlign  The corner of the context element 
        * that the elementAlign corner should stick to.
        * @param {Number[]} xyOffset Optional. A 2 element array specifying the x and y pixel offsets which should be applied
        * after aligning the element and context corners. For example, passing in [5, -10] for this value, would offset the 
        * Overlay by 5 pixels along the X axis (horizontally) and -10 pixels along the Y axis (vertically) after aligning the specified corners.
        */
        align: function (elementAlign, contextAlign, xyOffset) {

            var contextArgs = this.cfg.getProperty("context"),
                me = this,
                context,
                element,
                contextRegion;

            function doAlign(v, h) {

                var alignX = null, alignY = null;

                switch (elementAlign) {
    
                    case Overlay.TOP_LEFT:
                        alignX = h;
                        alignY = v;
                        break;
        
                    case Overlay.TOP_RIGHT:
                        alignX = h - element.offsetWidth;
                        alignY = v;
                        break;
        
                    case Overlay.BOTTOM_LEFT:
                        alignX = h;
                        alignY = v - element.offsetHeight;
                        break;
        
                    case Overlay.BOTTOM_RIGHT:
                        alignX = h - element.offsetWidth; 
                        alignY = v - element.offsetHeight;
                        break;
                }

                if (alignX !== null && alignY !== null) {
                    if (xyOffset) {
                        alignX += xyOffset[0];
                        alignY += xyOffset[1];
                    }
                    me.moveTo(alignX, alignY);
                }
            }

            if (contextArgs) {
                context = contextArgs[0];
                element = this.element;
                me = this;

                if (! elementAlign) {
                    elementAlign = contextArgs[1];
                }

                if (! contextAlign) {
                    contextAlign = contextArgs[2];
                }

                if (!xyOffset && contextArgs[4]) {
                    xyOffset = contextArgs[4];
                }

                if (element && context) {
                    contextRegion = Dom.getRegion(context);

                    switch (contextAlign) {
    
                        case Overlay.TOP_LEFT:
                            doAlign(contextRegion.top, contextRegion.left);
                            break;
        
                        case Overlay.TOP_RIGHT:
                            doAlign(contextRegion.top, contextRegion.right);
                            break;
        
                        case Overlay.BOTTOM_LEFT:
                            doAlign(contextRegion.bottom, contextRegion.left);
                            break;
        
                        case Overlay.BOTTOM_RIGHT:
                            doAlign(contextRegion.bottom, contextRegion.right);
                            break;
                    }
                }
            }
        },

        /**
        * The default event handler executed when the moveEvent is fired, if the 
        * "constraintoviewport" is set to true.
        * @method enforceConstraints
        * @param {String} type The CustomEvent type (usually the property name)
        * @param {Object[]} args The CustomEvent arguments. For configuration 
        * handlers, args[0] will equal the newly applied value for the property.
        * @param {Object} obj The scope object. For configuration handlers, 
        * this will usually equal the owner.
        */
        enforceConstraints: function (type, args, obj) {
            var pos = args[0];

            var cXY = this.getConstrainedXY(pos[0], pos[1]);
            this.cfg.setProperty("x", cXY[0], true);
            this.cfg.setProperty("y", cXY[1], true);
            this.cfg.setProperty("xy", cXY, true);
        },

        /**
         * Shared implementation method for getConstrainedX and getConstrainedY.
         * 
         * <p>
         * Given a coordinate value, returns the calculated coordinate required to 
         * position the Overlay if it is to be constrained to the viewport, based on the 
         * current element size, viewport dimensions, scroll values and preventoverlap 
         * settings
         * </p>
         *
         * @method _getConstrainedPos
         * @protected
         * @param {String} pos The coordinate which needs to be constrained, either "x" or "y"
         * @param {Number} The coordinate value which needs to be constrained
         * @return {Number} The constrained coordinate value
         */
        _getConstrainedPos: function(pos, val) {

            var overlayEl = this.element,

                buffer = Overlay.VIEWPORT_OFFSET,

                x = (pos == "x"),

                overlaySize      = (x) ? overlayEl.offsetWidth : overlayEl.offsetHeight,
                viewportSize     = (x) ? Dom.getViewportWidth() : Dom.getViewportHeight(),
                docScroll        = (x) ? Dom.getDocumentScrollLeft() : Dom.getDocumentScrollTop(),
                overlapPositions = (x) ? Overlay.PREVENT_OVERLAP_X : Overlay.PREVENT_OVERLAP_Y,

                context = this.cfg.getProperty("context"),

                bOverlayFitsInViewport = (overlaySize + buffer < viewportSize),
                bPreventContextOverlap = this.cfg.getProperty("preventcontextoverlap") && context && overlapPositions[(context[1] + context[2])],

                minConstraint = docScroll + buffer,
                maxConstraint = docScroll + viewportSize - overlaySize - buffer,

                constrainedVal = val;

            if (val < minConstraint || val > maxConstraint) {
                if (bPreventContextOverlap) {
                    constrainedVal = this._preventOverlap(pos, context[0], overlaySize, viewportSize, docScroll);
                } else {
                    if (bOverlayFitsInViewport) {
                        if (val < minConstraint) {
                            constrainedVal = minConstraint;
                        } else if (val > maxConstraint) {
                            constrainedVal = maxConstraint;
                        }
                    } else {
                        constrainedVal = minConstraint;
                    }
                }
            }

            return constrainedVal;
        },

        /**
         * Helper method, used to position the Overlap to prevent overlap with the 
         * context element (used when preventcontextoverlap is enabled)
         *
         * @method _preventOverlap
         * @protected
         * @param {String} pos The coordinate to prevent overlap for, either "x" or "y".
         * @param {HTMLElement} contextEl The context element
         * @param {Number} overlaySize The related overlay dimension value (for "x", the width, for "y", the height)
         * @param {Number} viewportSize The related viewport dimension value (for "x", the width, for "y", the height)
         * @param {Object} docScroll  The related document scroll value (for "x", the scrollLeft, for "y", the scrollTop)
         *
         * @return {Number} The new coordinate value which was set to prevent overlap
         */
        _preventOverlap : function(pos, contextEl, overlaySize, viewportSize, docScroll) {
            
            var x = (pos == "x"),

                buffer = Overlay.VIEWPORT_OFFSET,

                overlay = this,

                contextElPos   = ((x) ? Dom.getX(contextEl) : Dom.getY(contextEl)) - docScroll,
                contextElSize  = (x) ? contextEl.offsetWidth : contextEl.offsetHeight,

                minRegionSize = contextElPos - buffer,
                maxRegionSize = (viewportSize - (contextElPos + contextElSize)) - buffer,

                bFlipped = false,

                flip = function () {
                    var flippedVal;

                    if ((overlay.cfg.getProperty(pos) - docScroll) > contextElPos) {
                        flippedVal = (contextElPos - overlaySize);
                    } else {
                        flippedVal = (contextElPos + contextElSize);
                    }

                    overlay.cfg.setProperty(pos, (flippedVal + docScroll), true);

                    return flippedVal;
                },

                setPosition = function () {

                    var displayRegionSize = ((overlay.cfg.getProperty(pos) - docScroll) > contextElPos) ? maxRegionSize : minRegionSize,
                        position;

                    if (overlaySize > displayRegionSize) {
                        if (bFlipped) {
                            /*
                                 All possible positions and values have been 
                                 tried, but none were successful, so fall back 
                                 to the original size and position.
                            */
                            flip();
                        } else {
                            flip();
                            bFlipped = true;
                            position = setPosition();
                        }
                    }

                    return position;
                };

            setPosition();

            return this.cfg.getProperty(pos);
        },

        /**
         * Given x coordinate value, returns the calculated x coordinate required to 
         * position the Overlay if it is to be constrained to the viewport, based on the 
         * current element size, viewport dimensions and scroll values.
         *
         * @param {Number} x The X coordinate value to be constrained
         * @return {Number} The constrained x coordinate
         */		
        getConstrainedX: function (x) {
            return this._getConstrainedPos("x", x);
        },

        /**
         * Given y coordinate value, returns the calculated y coordinate required to 
         * position the Overlay if it is to be constrained to the viewport, based on the 
         * current element size, viewport dimensions and scroll values.
         *
         * @param {Number} y The Y coordinate value to be constrained
         * @return {Number} The constrained y coordinate
         */		
        getConstrainedY : function (y) {
            return this._getConstrainedPos("y", y);
        },

        /**
         * Given x, y coordinate values, returns the calculated coordinates required to 
         * position the Overlay if it is to be constrained to the viewport, based on the 
         * current element size, viewport dimensions and scroll values.
         *
         * @param {Number} x The X coordinate value to be constrained
         * @param {Number} y The Y coordinate value to be constrained
         * @return {Array} The constrained x and y coordinates at index 0 and 1 respectively;
         */
        getConstrainedXY: function(x, y) {
            return [this.getConstrainedX(x), this.getConstrainedY(y)];
        },

        /**
        * Centers the container in the viewport.
        * @method center
        */
        center: function () {

            var nViewportOffset = Overlay.VIEWPORT_OFFSET,
                elementWidth = this.element.offsetWidth,
                elementHeight = this.element.offsetHeight,
                viewPortWidth = Dom.getViewportWidth(),
                viewPortHeight = Dom.getViewportHeight(),
                x,
                y;

            if (elementWidth < viewPortWidth) {
                x = (viewPortWidth / 2) - (elementWidth / 2) + Dom.getDocumentScrollLeft();
            } else {
                x = nViewportOffset + Dom.getDocumentScrollLeft();
            }

            if (elementHeight < viewPortHeight) {
                y = (viewPortHeight / 2) - (elementHeight / 2) + Dom.getDocumentScrollTop();
            } else {
                y = nViewportOffset + Dom.getDocumentScrollTop();
            }

            this.cfg.setProperty("xy", [parseInt(x, 10), parseInt(y, 10)]);
            this.cfg.refireEvent("iframe");

            if (UA.webkit) {
                this.forceContainerRedraw();
            }
        },

        /**
        * Synchronizes the Panel's "xy", "x", and "y" properties with the 
        * Panel's position in the DOM. This is primarily used to update  
        * position information during drag & drop.
        * @method syncPosition
        */
        syncPosition: function () {

            var pos = Dom.getXY(this.element);

            this.cfg.setProperty("x", pos[0], true);
            this.cfg.setProperty("y", pos[1], true);
            this.cfg.setProperty("xy", pos, true);

        },

        /**
        * Event handler fired when the resize monitor element is resized.
        * @method onDomResize
        * @param {DOMEvent} e The resize DOM event
        * @param {Object} obj The scope object
        */
        onDomResize: function (e, obj) {

            var me = this;

            Overlay.superclass.onDomResize.call(this, e, obj);

            setTimeout(function () {
                me.syncPosition();
                me.cfg.refireEvent("iframe");
                me.cfg.refireEvent("context");
            }, 0);
        },

        /**
         * Determines the content box height of the given element (height of the element, without padding or borders) in pixels.
         *
         * @method _getComputedHeight
         * @private
         * @param {HTMLElement} el The element for which the content height needs to be determined
         * @return {Number} The content box height of the given element, or null if it could not be determined.
         */
        _getComputedHeight : (function() {

            if (document.defaultView && document.defaultView.getComputedStyle) {
                return function(el) {
                    var height = null;
                    if (el.ownerDocument && el.ownerDocument.defaultView) {
                        var computed = el.ownerDocument.defaultView.getComputedStyle(el, '');
                        if (computed) {
                            height = parseInt(computed.height, 10);
                        }
                    }
                    return (Lang.isNumber(height)) ? height : null;
                };
            } else {
                return function(el) {
                    var height = null;
                    if (el.style.pixelHeight) {
                        height = el.style.pixelHeight;
                    }
                    return (Lang.isNumber(height)) ? height : null;
                };
            }
        })(),

        /**
         * autofillheight validator. Verifies that the autofill value is either null 
         * or one of the strings : "body", "header" or "footer".
         *
         * @method _validateAutoFillHeight
         * @protected
         * @param {String} val
         * @return true, if valid, false otherwise
         */
        _validateAutoFillHeight : function(val) {
            return (!val) || (Lang.isString(val) && Overlay.STD_MOD_RE.test(val));
        },

        /**
         * The default custom event handler executed when the overlay's height is changed, 
         * if the autofillheight property has been set.
         *
         * @method _autoFillOnHeightChange
         * @protected
         * @param {String} type The event type
         * @param {Array} args The array of arguments passed to event subscribers
         * @param {HTMLElement} el The header, body or footer element which is to be resized to fill
         * out the containers height
         */
        _autoFillOnHeightChange : function(type, args, el) {
            var height = this.cfg.getProperty("height");
            if ((height && height !== "auto") || (height === 0)) {
                this.fillHeight(el);
            }
        },

        /**
         * Returns the sub-pixel height of the el, using getBoundingClientRect, if available,
         * otherwise returns the offsetHeight
         * @method _getPreciseHeight
         * @private
         * @param {HTMLElement} el
         * @return {Float} The sub-pixel height if supported by the browser, else the rounded height.
         */
        _getPreciseHeight : function(el) {
            var height = el.offsetHeight;

            if (el.getBoundingClientRect) {
                var rect = el.getBoundingClientRect();
                height = rect.bottom - rect.top;
            }

            return height;
        },

        /**
         * <p>
         * Sets the height on the provided header, body or footer element to 
         * fill out the height of the container. It determines the height of the 
         * containers content box, based on it's configured height value, and 
         * sets the height of the autofillheight element to fill out any 
         * space remaining after the other standard module element heights 
         * have been accounted for.
         * </p>
         * <p><strong>NOTE:</strong> This method is not designed to work if an explicit 
         * height has not been set on the container, since for an "auto" height container, 
         * the heights of the header/body/footer will drive the height of the container.</p>
         *
         * @method fillHeight
         * @param {HTMLElement} el The element which should be resized to fill out the height
         * of the container element.
         */
        fillHeight : function(el) {
            if (el) {
                var container = this.innerElement || this.element,
                    containerEls = [this.header, this.body, this.footer],
                    containerEl,
                    total = 0,
                    filled = 0,
                    remaining = 0,
                    validEl = false;

                for (var i = 0, l = containerEls.length; i < l; i++) {
                    containerEl = containerEls[i];
                    if (containerEl) {
                        if (el !== containerEl) {
                            filled += this._getPreciseHeight(containerEl);
                        } else {
                            validEl = true;
                        }
                    }
                }

                if (validEl) {

                    if (UA.ie || UA.opera) {
                        // Need to set height to 0, to allow height to be reduced
                        Dom.setStyle(el, 'height', 0 + 'px');
                    }

                    total = this._getComputedHeight(container);

                    // Fallback, if we can't get computed value for content height
                    if (total === null) {
                        Dom.addClass(container, "yui-override-padding");
                        total = container.clientHeight; // Content, No Border, 0 Padding (set by yui-override-padding)
                        Dom.removeClass(container, "yui-override-padding");
                    }
    
                    remaining = Math.max(total - filled, 0);
    
                    Dom.setStyle(el, "height", remaining + "px");
    
                    // Re-adjust height if required, to account for el padding and border
                    if (el.offsetHeight != remaining) {
                        remaining = Math.max(remaining - (el.offsetHeight - remaining), 0);
                    }
                    Dom.setStyle(el, "height", remaining + "px");
                }
            }
        },

        /**
        * Places the Overlay on top of all other instances of 
        * YAHOO.widget.Overlay.
        * @method bringToTop
        */
        bringToTop: function () {

            var aOverlays = [],
                oElement = this.element;

            function compareZIndexDesc(p_oOverlay1, p_oOverlay2) {

                var sZIndex1 = Dom.getStyle(p_oOverlay1, "zIndex"),
                    sZIndex2 = Dom.getStyle(p_oOverlay2, "zIndex"),

                    nZIndex1 = (!sZIndex1 || isNaN(sZIndex1)) ? 0 : parseInt(sZIndex1, 10),
                    nZIndex2 = (!sZIndex2 || isNaN(sZIndex2)) ? 0 : parseInt(sZIndex2, 10);

                if (nZIndex1 > nZIndex2) {
                    return -1;
                } else if (nZIndex1 < nZIndex2) {
                    return 1;
                } else {
                    return 0;
                }
            }

            function isOverlayElement(p_oElement) {

                var isOverlay = Dom.hasClass(p_oElement, Overlay.CSS_OVERLAY),
                    Panel = YAHOO.widget.Panel;

                if (isOverlay && !Dom.isAncestor(oElement, p_oElement)) {
                    if (Panel && Dom.hasClass(p_oElement, Panel.CSS_PANEL)) {
                        aOverlays[aOverlays.length] = p_oElement.parentNode;
                    } else {
                        aOverlays[aOverlays.length] = p_oElement;
                    }
                }
            }

            Dom.getElementsBy(isOverlayElement, "div", document.body);

            aOverlays.sort(compareZIndexDesc);

            var oTopOverlay = aOverlays[0],
                nTopZIndex;

            if (oTopOverlay) {
                nTopZIndex = Dom.getStyle(oTopOverlay, "zIndex");

                if (!isNaN(nTopZIndex)) {
                    var bRequiresBump = false;

                    if (oTopOverlay != oElement) {
                        bRequiresBump = true;
                    } else if (aOverlays.length > 1) {
                        var nNextZIndex = Dom.getStyle(aOverlays[1], "zIndex");
                        // Don't rely on DOM order to stack if 2 overlays are at the same zindex.
                        if (!isNaN(nNextZIndex) && (nTopZIndex == nNextZIndex)) {
                            bRequiresBump = true;
                        }
                    }
                    if (bRequiresBump) {
                        this.cfg.setProperty("zindex", (parseInt(nTopZIndex, 10) + 2));
                    }
                }
            }
        },

        /**
        * Removes the Overlay element from the DOM and sets all child 
        * elements to null.
        * @method destroy
        * @param {boolean} shallowPurge If true, only the parent element's DOM event listeners are purged. If false, or not provided, all children are also purged of DOM event listeners. 
        * NOTE: The flag is a "shallowPurge" flag, as opposed to what may be a more intuitive "purgeChildren" flag to maintain backwards compatibility with behavior prior to 2.9.0.
        */
        destroy: function (shallowPurge) {

            if (this.iframe) {
                this.iframe.parentNode.removeChild(this.iframe);
            }

            this.iframe = null;

            Overlay.windowResizeEvent.unsubscribe(
                this.doCenterOnDOMEvent, this);
    
            Overlay.windowScrollEvent.unsubscribe(
                this.doCenterOnDOMEvent, this);

            Module.textResizeEvent.unsubscribe(this._autoFillOnHeightChange);

            if (this._contextTriggers) {
                // Unsubscribe context triggers - to cover context triggers which listen for global
                // events such as windowResize and windowScroll. Easier just to unsubscribe all
                this._processTriggers(this._contextTriggers, _UNSUBSCRIBE, this._alignOnTrigger);
            }

            Overlay.superclass.destroy.call(this, shallowPurge);
        },

        /**
         * Can be used to force the container to repaint/redraw it's contents.
         * <p>
         * By default applies and then removes a 1px bottom margin through the 
         * application/removal of a "yui-force-redraw" class.
         * </p>
         * <p>
         * It is currently used by Overlay to force a repaint for webkit 
         * browsers, when centering.
         * </p>
         * @method forceContainerRedraw
         */
        forceContainerRedraw : function() {
            var c = this;
            Dom.addClass(c.element, "yui-force-redraw");
            setTimeout(function() {
                Dom.removeClass(c.element, "yui-force-redraw");
            }, 0);
        },

        /**
        * Returns a String representation of the object.
        * @method toString
        * @return {String} The string representation of the Overlay.
        */
        toString: function () {
            return "Overlay " + this.id;
        }

    });
}());
(function () {

    /**
    * OverlayManager is used for maintaining the focus status of 
    * multiple Overlays.
    * @namespace YAHOO.widget
    * @namespace YAHOO.widget
    * @class OverlayManager
    * @constructor
    * @param {Array} overlays Optional. A collection of Overlays to register 
    * with the manager.
    * @param {Object} userConfig  The object literal representing the user 
    * configuration of the OverlayManager
    */
    YAHOO.widget.OverlayManager = function (userConfig) {
        this.init(userConfig);
    };

    var Overlay = YAHOO.widget.Overlay,
        Event = YAHOO.util.Event,
        Dom = YAHOO.util.Dom,
        Config = YAHOO.util.Config,
        CustomEvent = YAHOO.util.CustomEvent,
        OverlayManager = YAHOO.widget.OverlayManager;

    /**
    * The CSS class representing a focused Overlay
    * @property OverlayManager.CSS_FOCUSED
    * @static
    * @final
    * @type String
    */
    OverlayManager.CSS_FOCUSED = "focused";

    OverlayManager.prototype = {

        /**
        * The class's constructor function
        * @property contructor
        * @type Function
        */
        constructor: OverlayManager,

        /**
        * The array of Overlays that are currently registered
        * @property overlays
        * @type YAHOO.widget.Overlay[]
        */
        overlays: null,

        /**
        * Initializes the default configuration of the OverlayManager
        * @method initDefaultConfig
        */
        initDefaultConfig: function () {
            /**
            * The collection of registered Overlays in use by 
            * the OverlayManager
            * @config overlays
            * @type YAHOO.widget.Overlay[]
            * @default null
            */
            this.cfg.addProperty("overlays", { suppressEvent: true } );

            /**
            * The default DOM event that should be used to focus an Overlay
            * @config focusevent
            * @type String
            * @default "mousedown"
            */
            this.cfg.addProperty("focusevent", { value: "mousedown" } );
        },

        /**
        * Initializes the OverlayManager
        * @method init
        * @param {Overlay[]} overlays Optional. A collection of Overlays to 
        * register with the manager.
        * @param {Object} userConfig  The object literal representing the user 
        * configuration of the OverlayManager
        */
        init: function (userConfig) {

            /**
            * The OverlayManager's Config object used for monitoring 
            * configuration properties.
            * @property cfg
            * @type Config
            */
            this.cfg = new Config(this);

            this.initDefaultConfig();

            if (userConfig) {
                this.cfg.applyConfig(userConfig, true);
            }
            this.cfg.fireQueue();

            /**
            * The currently activated Overlay
            * @property activeOverlay
            * @private
            * @type YAHOO.widget.Overlay
            */
            var activeOverlay = null;

            /**
            * Returns the currently focused Overlay
            * @method getActive
            * @return {Overlay} The currently focused Overlay
            */
            this.getActive = function () {
                return activeOverlay;
            };

            /**
            * Focuses the specified Overlay
            * @method focus
            * @param {Overlay} overlay The Overlay to focus
            * @param {String} overlay The id of the Overlay to focus
            */
            this.focus = function (overlay) {
                var o = this.find(overlay);
                if (o) {
                    o.focus();
                }
            };

            /**
            * Removes the specified Overlay from the manager
            * @method remove
            * @param {Overlay} overlay The Overlay to remove
            * @param {String} overlay The id of the Overlay to remove
            */
            this.remove = function (overlay) {

                var o = this.find(overlay), 
                        originalZ;

                if (o) {
                    if (activeOverlay == o) {
                        activeOverlay = null;
                    }

                    var bDestroyed = (o.element === null && o.cfg === null) ? true : false;

                    if (!bDestroyed) {
                        // Set it's zindex so that it's sorted to the end.
                        originalZ = Dom.getStyle(o.element, "zIndex");
                        o.cfg.setProperty("zIndex", -1000, true);
                    }

                    this.overlays.sort(this.compareZIndexDesc);
                    this.overlays = this.overlays.slice(0, (this.overlays.length - 1));

                    o.hideEvent.unsubscribe(o.blur);
                    o.destroyEvent.unsubscribe(this._onOverlayDestroy, o);
                    o.focusEvent.unsubscribe(this._onOverlayFocusHandler, o);
                    o.blurEvent.unsubscribe(this._onOverlayBlurHandler, o);

                    if (!bDestroyed) {
                        Event.removeListener(o.element, this.cfg.getProperty("focusevent"), this._onOverlayElementFocus);
                        o.cfg.setProperty("zIndex", originalZ, true);
                        o.cfg.setProperty("manager", null);
                    }

                    /* _managed Flag for custom or existing. Don't want to remove existing */
                    if (o.focusEvent._managed) { o.focusEvent = null; }
                    if (o.blurEvent._managed) { o.blurEvent = null; }

                    if (o.focus._managed) { o.focus = null; }
                    if (o.blur._managed) { o.blur = null; }
                }
            };

            /**
            * Removes focus from all registered Overlays in the manager
            * @method blurAll
            */
            this.blurAll = function () {

                var nOverlays = this.overlays.length,
                    i;

                if (nOverlays > 0) {
                    i = nOverlays - 1;
                    do {
                        this.overlays[i].blur();
                    }
                    while(i--);
                }
            };

            /**
             * Updates the state of the OverlayManager and overlay, as a result of the overlay
             * being blurred.
             * 
             * @method _manageBlur
             * @param {Overlay} overlay The overlay instance which got blurred.
             * @protected
             */
            this._manageBlur = function (overlay) {
                var changed = false;
                if (activeOverlay == overlay) {
                    Dom.removeClass(activeOverlay.element, OverlayManager.CSS_FOCUSED);
                    activeOverlay = null;
                    changed = true;
                }
                return changed;
            };

            /**
             * Updates the state of the OverlayManager and overlay, as a result of the overlay 
             * receiving focus.
             *
             * @method _manageFocus
             * @param {Overlay} overlay The overlay instance which got focus.
             * @protected
             */
            this._manageFocus = function(overlay) {
                var changed = false;
                if (activeOverlay != overlay) {
                    if (activeOverlay) {
                        activeOverlay.blur();
                    }
                    activeOverlay = overlay;
                    this.bringToTop(activeOverlay);
                    Dom.addClass(activeOverlay.element, OverlayManager.CSS_FOCUSED);
                    changed = true;
                }
                return changed;
            };

            var overlays = this.cfg.getProperty("overlays");

            if (! this.overlays) {
                this.overlays = [];
            }

            if (overlays) {
                this.register(overlays);
                this.overlays.sort(this.compareZIndexDesc);
            }
        },

        /**
        * @method _onOverlayElementFocus
        * @description Event handler for the DOM event that is used to focus 
        * the Overlay instance as specified by the "focusevent" 
        * configuration property.
        * @private
        * @param {Event} p_oEvent Object representing the DOM event 
        * object passed back by the event utility (Event).
        */
        _onOverlayElementFocus: function (p_oEvent) {

            var oTarget = Event.getTarget(p_oEvent),
                oClose = this.close;

            if (oClose && (oTarget == oClose || Dom.isAncestor(oClose, oTarget))) {
                this.blur();
            } else {
                this.focus();
            }
        },

        /**
        * @method _onOverlayDestroy
        * @description "destroy" event handler for the Overlay.
        * @private
        * @param {String} p_sType String representing the name of the event  
        * that was fired.
        * @param {Array} p_aArgs Array of arguments sent when the event 
        * was fired.
        * @param {Overlay} p_oOverlay Object representing the overlay that 
        * fired the event.
        */
        _onOverlayDestroy: function (p_sType, p_aArgs, p_oOverlay) {
            this.remove(p_oOverlay);
        },

        /**
        * @method _onOverlayFocusHandler
        *
        * @description focusEvent Handler, used to delegate to _manageFocus with the correct arguments.
        *
        * @private
        * @param {String} p_sType String representing the name of the event  
        * that was fired.
        * @param {Array} p_aArgs Array of arguments sent when the event 
        * was fired.
        * @param {Overlay} p_oOverlay Object representing the overlay that 
        * fired the event.
        */
        _onOverlayFocusHandler: function(p_sType, p_aArgs, p_oOverlay) {
            this._manageFocus(p_oOverlay);
        },

        /**
        * @method _onOverlayBlurHandler
        * @description blurEvent Handler, used to delegate to _manageBlur with the correct arguments.
        *
        * @private
        * @param {String} p_sType String representing the name of the event  
        * that was fired.
        * @param {Array} p_aArgs Array of arguments sent when the event 
        * was fired.
        * @param {Overlay} p_oOverlay Object representing the overlay that 
        * fired the event.
        */
        _onOverlayBlurHandler: function(p_sType, p_aArgs, p_oOverlay) {
            this._manageBlur(p_oOverlay);
        },

        /**
         * Subscribes to the Overlay based instance focusEvent, to allow the OverlayManager to
         * monitor focus state.
         * 
         * If the instance already has a focusEvent (e.g. Menu), OverlayManager will subscribe 
         * to the existing focusEvent, however if a focusEvent or focus method does not exist
         * on the instance, the _bindFocus method will add them, and the focus method will 
         * update the OverlayManager's state directly.
         * 
         * @method _bindFocus
         * @param {Overlay} overlay The overlay for which focus needs to be managed
         * @protected
         */
        _bindFocus : function(overlay) {
            var mgr = this;

            if (!overlay.focusEvent) {
                overlay.focusEvent = overlay.createEvent("focus");
                overlay.focusEvent.signature = CustomEvent.LIST;
                overlay.focusEvent._managed = true;
            } else {
                overlay.focusEvent.subscribe(mgr._onOverlayFocusHandler, overlay, mgr);
            }

            if (!overlay.focus) {
                Event.on(overlay.element, mgr.cfg.getProperty("focusevent"), mgr._onOverlayElementFocus, null, overlay);
                overlay.focus = function () {
                    if (mgr._manageFocus(this)) {
                        // For Panel/Dialog
                        if (this.cfg.getProperty("visible") && this.focusFirst) {
                            this.focusFirst();
                        }
                        this.focusEvent.fire();
                    }
                };
                overlay.focus._managed = true;
            }
        },

        /**
         * Subscribes to the Overlay based instance's blurEvent to allow the OverlayManager to
         * monitor blur state.
         *
         * If the instance already has a blurEvent (e.g. Menu), OverlayManager will subscribe 
         * to the existing blurEvent, however if a blurEvent or blur method does not exist
         * on the instance, the _bindBlur method will add them, and the blur method 
         * update the OverlayManager's state directly.
         *
         * @method _bindBlur
         * @param {Overlay} overlay The overlay for which blur needs to be managed
         * @protected
         */
        _bindBlur : function(overlay) {
            var mgr = this;

            if (!overlay.blurEvent) {
                overlay.blurEvent = overlay.createEvent("blur");
                overlay.blurEvent.signature = CustomEvent.LIST;
                overlay.focusEvent._managed = true;
            } else {
                overlay.blurEvent.subscribe(mgr._onOverlayBlurHandler, overlay, mgr);
            }

            if (!overlay.blur) {
                overlay.blur = function () {
                    if (mgr._manageBlur(this)) {
                        this.blurEvent.fire();
                    }
                };
                overlay.blur._managed = true;
            }

            overlay.hideEvent.subscribe(overlay.blur);
        },

        /**
         * Subscribes to the Overlay based instance's destroyEvent, to allow the Overlay
         * to be removed for the OverlayManager when destroyed.
         * 
         * @method _bindDestroy
         * @param {Overlay} overlay The overlay instance being managed
         * @protected
         */
        _bindDestroy : function(overlay) {
            var mgr = this;
            overlay.destroyEvent.subscribe(mgr._onOverlayDestroy, overlay, mgr);
        },

        /**
         * Ensures the zIndex configuration property on the managed overlay based instance
         * is set to the computed zIndex value from the DOM (with "auto" translating to 0).
         *
         * @method _syncZIndex
         * @param {Overlay} overlay The overlay instance being managed
         * @protected
         */
        _syncZIndex : function(overlay) {
            var zIndex = Dom.getStyle(overlay.element, "zIndex");
            if (!isNaN(zIndex)) {
                overlay.cfg.setProperty("zIndex", parseInt(zIndex, 10));
            } else {
                overlay.cfg.setProperty("zIndex", 0);
            }
        },

        /**
        * Registers an Overlay or an array of Overlays with the manager. Upon 
        * registration, the Overlay receives functions for focus and blur, 
        * along with CustomEvents for each.
        *
        * @method register
        * @param {Overlay} overlay  An Overlay to register with the manager.
        * @param {Overlay[]} overlay  An array of Overlays to register with 
        * the manager.
        * @return {boolean} true if any Overlays are registered.
        */
        register: function (overlay) {

            var registered = false,
                i,
                n;

            if (overlay instanceof Overlay) {

                overlay.cfg.addProperty("manager", { value: this } );

                this._bindFocus(overlay);
                this._bindBlur(overlay);
                this._bindDestroy(overlay);
                this._syncZIndex(overlay);

                this.overlays.push(overlay);
                this.bringToTop(overlay);

                registered = true;

            } else if (overlay instanceof Array) {

                for (i = 0, n = overlay.length; i < n; i++) {
                    registered = this.register(overlay[i]) || registered;
                }

            }

            return registered;
        },

        /**
        * Places the specified Overlay instance on top of all other 
        * Overlay instances.
        * @method bringToTop
        * @param {YAHOO.widget.Overlay} p_oOverlay Object representing an 
        * Overlay instance.
        * @param {String} p_oOverlay String representing the id of an 
        * Overlay instance.
        */        
        bringToTop: function (p_oOverlay) {

            var oOverlay = this.find(p_oOverlay),
                nTopZIndex,
                oTopOverlay,
                aOverlays;

            if (oOverlay) {

                aOverlays = this.overlays;
                aOverlays.sort(this.compareZIndexDesc);

                oTopOverlay = aOverlays[0];

                if (oTopOverlay) {
                    nTopZIndex = Dom.getStyle(oTopOverlay.element, "zIndex");

                    if (!isNaN(nTopZIndex)) {

                        var bRequiresBump = false;

                        if (oTopOverlay !== oOverlay) {
                            bRequiresBump = true;
                        } else if (aOverlays.length > 1) {
                            var nNextZIndex = Dom.getStyle(aOverlays[1].element, "zIndex");
                            // Don't rely on DOM order to stack if 2 overlays are at the same zindex.
                            if (!isNaN(nNextZIndex) && (nTopZIndex == nNextZIndex)) {
                                bRequiresBump = true;
                            }
                        }

                        if (bRequiresBump) {
                            oOverlay.cfg.setProperty("zindex", (parseInt(nTopZIndex, 10) + 2));
                        }
                    }
                    aOverlays.sort(this.compareZIndexDesc);
                }
            }
        },

        /**
        * Attempts to locate an Overlay by instance or ID.
        * @method find
        * @param {Overlay} overlay  An Overlay to locate within the manager
        * @param {String} overlay  An Overlay id to locate within the manager
        * @return {Overlay} The requested Overlay, if found, or null if it 
        * cannot be located.
        */
        find: function (overlay) {

            var isInstance = overlay instanceof Overlay,
                overlays = this.overlays,
                n = overlays.length,
                found = null,
                o,
                i;

            if (isInstance || typeof overlay == "string") {
                for (i = n-1; i >= 0; i--) {
                    o = overlays[i];
                    if ((isInstance && (o === overlay)) || (o.id == overlay)) {
                        found = o;
                        break;
                    }
                }
            }

            return found;
        },

        /**
        * Used for sorting the manager's Overlays by z-index.
        * @method compareZIndexDesc
        * @private
        * @return {Number} 0, 1, or -1, depending on where the Overlay should 
        * fall in the stacking order.
        */
        compareZIndexDesc: function (o1, o2) {

            var zIndex1 = (o1.cfg) ? o1.cfg.getProperty("zIndex") : null, // Sort invalid (destroyed)
                zIndex2 = (o2.cfg) ? o2.cfg.getProperty("zIndex") : null; // objects at bottom.

            if (zIndex1 === null && zIndex2 === null) {
                return 0;
            } else if (zIndex1 === null){
                return 1;
            } else if (zIndex2 === null) {
                return -1;
            } else if (zIndex1 > zIndex2) {
                return -1;
            } else if (zIndex1 < zIndex2) {
                return 1;
            } else {
                return 0;
            }
        },

        /**
        * Shows all Overlays in the manager.
        * @method showAll
        */
        showAll: function () {
            var overlays = this.overlays,
                n = overlays.length,
                i;

            for (i = n - 1; i >= 0; i--) {
                overlays[i].show();
            }
        },

        /**
        * Hides all Overlays in the manager.
        * @method hideAll
        */
        hideAll: function () {
            var overlays = this.overlays,
                n = overlays.length,
                i;

            for (i = n - 1; i >= 0; i--) {
                overlays[i].hide();
            }
        },

        /**
        * Returns a string representation of the object.
        * @method toString
        * @return {String} The string representation of the OverlayManager
        */
        toString: function () {
            return "OverlayManager";
        }
    };
}());
(function () {

    /**
    * ContainerEffect encapsulates animation transitions that are executed when 
    * an Overlay is shown or hidden.
    * @namespace YAHOO.widget
    * @class ContainerEffect
    * @constructor
    * @param {YAHOO.widget.Overlay} overlay The Overlay that the animation 
    * should be associated with
    * @param {Object} attrIn The object literal representing the animation 
    * arguments to be used for the animate-in transition. The arguments for 
    * this literal are: attributes(object, see YAHOO.util.Anim for description), 
    * duration(Number), and method(i.e. Easing.easeIn).
    * @param {Object} attrOut The object literal representing the animation 
    * arguments to be used for the animate-out transition. The arguments for  
    * this literal are: attributes(object, see YAHOO.util.Anim for description), 
    * duration(Number), and method(i.e. Easing.easeIn).
    * @param {HTMLElement} targetElement Optional. The target element that  
    * should be animated during the transition. Defaults to overlay.element.
    * @param {class} Optional. The animation class to instantiate. Defaults to 
    * YAHOO.util.Anim. Other options include YAHOO.util.Motion.
    */
    YAHOO.widget.ContainerEffect = function (overlay, attrIn, attrOut, targetElement, animClass) {

        if (!animClass) {
            animClass = YAHOO.util.Anim;
        }

        /**
        * The overlay to animate
        * @property overlay
        * @type YAHOO.widget.Overlay
        */
        this.overlay = overlay;
    
        /**
        * The animation attributes to use when transitioning into view
        * @property attrIn
        * @type Object
        */
        this.attrIn = attrIn;
    
        /**
        * The animation attributes to use when transitioning out of view
        * @property attrOut
        * @type Object
        */
        this.attrOut = attrOut;
    
        /**
        * The target element to be animated
        * @property targetElement
        * @type HTMLElement
        */
        this.targetElement = targetElement || overlay.element;
    
        /**
        * The animation class to use for animating the overlay
        * @property animClass
        * @type class
        */
        this.animClass = animClass;
    };

    var Dom = YAHOO.util.Dom,
        CustomEvent = YAHOO.util.CustomEvent,
        ContainerEffect = YAHOO.widget.ContainerEffect;

    /**
    * A pre-configured ContainerEffect instance that can be used for fading 
    * an overlay in and out.
    * @method FADE
    * @static
    * @param {YAHOO.widget.Overlay} overlay The Overlay object to animate
    * @param {Number} dur The duration of the animation
    * @return {YAHOO.widget.ContainerEffect} The configured ContainerEffect object
    */
    ContainerEffect.FADE = function (overlay, dur) {

        var Easing = YAHOO.util.Easing,
            fin = {
                attributes: {opacity:{from:0, to:1}},
                duration: dur,
                method: Easing.easeIn
            },
            fout = {
                attributes: {opacity:{to:0}},
                duration: dur,
                method: Easing.easeOut
            },
            fade = new ContainerEffect(overlay, fin, fout, overlay.element);

        fade.handleUnderlayStart = function() {
            var underlay = this.overlay.underlay;
            if (underlay && YAHOO.env.ua.ie) {
                var hasFilters = (underlay.filters && underlay.filters.length > 0);
                if(hasFilters) {
                    Dom.addClass(overlay.element, "yui-effect-fade");
                }
            }
        };

        fade.handleUnderlayComplete = function() {
            var underlay = this.overlay.underlay;
            if (underlay && YAHOO.env.ua.ie) {
                Dom.removeClass(overlay.element, "yui-effect-fade");
            }
        };

        fade.handleStartAnimateIn = function (type, args, obj) {
            obj.overlay._fadingIn = true;

            Dom.addClass(obj.overlay.element, "hide-select");

            if (!obj.overlay.underlay) {
                obj.overlay.cfg.refireEvent("underlay");
            }

            obj.handleUnderlayStart();

            obj.overlay._setDomVisibility(true);
            Dom.setStyle(obj.overlay.element, "opacity", 0);
        };

        fade.handleCompleteAnimateIn = function (type,args,obj) {
            obj.overlay._fadingIn = false;
            
            Dom.removeClass(obj.overlay.element, "hide-select");

            if (obj.overlay.element.style.filter) {
                obj.overlay.element.style.filter = null;
            }

            obj.handleUnderlayComplete();

            obj.overlay.cfg.refireEvent("iframe");
            obj.animateInCompleteEvent.fire();
        };

        fade.handleStartAnimateOut = function (type, args, obj) {
            obj.overlay._fadingOut = true;
            Dom.addClass(obj.overlay.element, "hide-select");
            obj.handleUnderlayStart();
        };

        fade.handleCompleteAnimateOut =  function (type, args, obj) {
            obj.overlay._fadingOut = false;
            Dom.removeClass(obj.overlay.element, "hide-select");

            if (obj.overlay.element.style.filter) {
                obj.overlay.element.style.filter = null;
            }
            obj.overlay._setDomVisibility(false);
            Dom.setStyle(obj.overlay.element, "opacity", 1);

            obj.handleUnderlayComplete();

            obj.overlay.cfg.refireEvent("iframe");
            obj.animateOutCompleteEvent.fire();
        };

        fade.init();
        return fade;
    };
    
    
    /**
    * A pre-configured ContainerEffect instance that can be used for sliding an 
    * overlay in and out.
    * @method SLIDE
    * @static
    * @param {YAHOO.widget.Overlay} overlay The Overlay object to animate
    * @param {Number} dur The duration of the animation
    * @return {YAHOO.widget.ContainerEffect} The configured ContainerEffect object
    */
    ContainerEffect.SLIDE = function (overlay, dur) {
        var Easing = YAHOO.util.Easing,

            x = overlay.cfg.getProperty("x") || Dom.getX(overlay.element),
            y = overlay.cfg.getProperty("y") || Dom.getY(overlay.element),
            clientWidth = Dom.getClientWidth(),
            offsetWidth = overlay.element.offsetWidth,

            sin =  { 
                attributes: { points: { to: [x, y] } },
                duration: dur,
                method: Easing.easeIn 
            },

            sout = {
                attributes: { points: { to: [(clientWidth + 25), y] } },
                duration: dur,
                method: Easing.easeOut 
            },

            slide = new ContainerEffect(overlay, sin, sout, overlay.element, YAHOO.util.Motion);

        slide.handleStartAnimateIn = function (type,args,obj) {
            obj.overlay.element.style.left = ((-25) - offsetWidth) + "px";
            obj.overlay.element.style.top  = y + "px";
        };

        slide.handleTweenAnimateIn = function (type, args, obj) {
        
            var pos = Dom.getXY(obj.overlay.element),
                currentX = pos[0],
                currentY = pos[1];
        
            if (Dom.getStyle(obj.overlay.element, "visibility") == 
                "hidden" && currentX < x) {

                obj.overlay._setDomVisibility(true);

            }
        
            obj.overlay.cfg.setProperty("xy", [currentX, currentY], true);
            obj.overlay.cfg.refireEvent("iframe");
        };
        
        slide.handleCompleteAnimateIn = function (type, args, obj) {
            obj.overlay.cfg.setProperty("xy", [x, y], true);
            obj.startX = x;
            obj.startY = y;
            obj.overlay.cfg.refireEvent("iframe");
            obj.animateInCompleteEvent.fire();
        };

        slide.handleStartAnimateOut = function (type, args, obj) {
    
            var vw = Dom.getViewportWidth(),
                pos = Dom.getXY(obj.overlay.element),
                yso = pos[1];
    
            obj.animOut.attributes.points.to = [(vw + 25), yso];
        };
        
        slide.handleTweenAnimateOut = function (type, args, obj) {
    
            var pos = Dom.getXY(obj.overlay.element),
                xto = pos[0],
                yto = pos[1];
        
            obj.overlay.cfg.setProperty("xy", [xto, yto], true);
            obj.overlay.cfg.refireEvent("iframe");
        };
        
        slide.handleCompleteAnimateOut = function (type, args, obj) {
            obj.overlay._setDomVisibility(false);

            obj.overlay.cfg.setProperty("xy", [x, y]);
            obj.animateOutCompleteEvent.fire();
        };

        slide.init();
        return slide;
    };

    ContainerEffect.prototype = {

        /**
        * Initializes the animation classes and events.
        * @method init
        */
        init: function () {

            this.beforeAnimateInEvent = this.createEvent("beforeAnimateIn");
            this.beforeAnimateInEvent.signature = CustomEvent.LIST;
            
            this.beforeAnimateOutEvent = this.createEvent("beforeAnimateOut");
            this.beforeAnimateOutEvent.signature = CustomEvent.LIST;
        
            this.animateInCompleteEvent = this.createEvent("animateInComplete");
            this.animateInCompleteEvent.signature = CustomEvent.LIST;
        
            this.animateOutCompleteEvent = this.createEvent("animateOutComplete");
            this.animateOutCompleteEvent.signature = CustomEvent.LIST;

            this.animIn = new this.animClass(
                this.targetElement, 
                this.attrIn.attributes, 
                this.attrIn.duration, 
                this.attrIn.method);

            this.animIn.onStart.subscribe(this.handleStartAnimateIn, this);
            this.animIn.onTween.subscribe(this.handleTweenAnimateIn, this);
            this.animIn.onComplete.subscribe(this.handleCompleteAnimateIn,this);
        
            this.animOut = new this.animClass(
                this.targetElement, 
                this.attrOut.attributes, 
                this.attrOut.duration, 
                this.attrOut.method);

            this.animOut.onStart.subscribe(this.handleStartAnimateOut, this);
            this.animOut.onTween.subscribe(this.handleTweenAnimateOut, this);
            this.animOut.onComplete.subscribe(this.handleCompleteAnimateOut, this);

        },

        /**
        * Triggers the in-animation.
        * @method animateIn
        */
        animateIn: function () {
            this._stopAnims(this.lastFrameOnStop);
            this.beforeAnimateInEvent.fire();
            this.animIn.animate();
        },

        /**
        * Triggers the out-animation.
        * @method animateOut
        */
        animateOut: function () {
            this._stopAnims(this.lastFrameOnStop);
            this.beforeAnimateOutEvent.fire();
            this.animOut.animate();
        },
        
        /**
         * Flag to define whether Anim should jump to the last frame,
         * when animateIn or animateOut is stopped.
         *
         * @property lastFrameOnStop
         * @default true
         * @type boolean
         */
        lastFrameOnStop : true,

        /**
         * Stops both animIn and animOut instances, if in progress.
         *
         * @method _stopAnims
         * @param {boolean} finish If true, animation will jump to final frame.
         * @protected
         */
        _stopAnims : function(finish) {
            if (this.animOut && this.animOut.isAnimated()) {
                this.animOut.stop(finish);
            }

            if (this.animIn && this.animIn.isAnimated()) {
                this.animIn.stop(finish);
            }
        },

        /**
        * The default onStart handler for the in-animation.
        * @method handleStartAnimateIn
        * @param {String} type The CustomEvent type
        * @param {Object[]} args The CustomEvent arguments
        * @param {Object} obj The scope object
        */
        handleStartAnimateIn: function (type, args, obj) { },

        /**
        * The default onTween handler for the in-animation.
        * @method handleTweenAnimateIn
        * @param {String} type The CustomEvent type
        * @param {Object[]} args The CustomEvent arguments
        * @param {Object} obj The scope object
        */
        handleTweenAnimateIn: function (type, args, obj) { },

        /**
        * The default onComplete handler for the in-animation.
        * @method handleCompleteAnimateIn
        * @param {String} type The CustomEvent type
        * @param {Object[]} args The CustomEvent arguments
        * @param {Object} obj The scope object
        */
        handleCompleteAnimateIn: function (type, args, obj) { },

        /**
        * The default onStart handler for the out-animation.
        * @method handleStartAnimateOut
        * @param {String} type The CustomEvent type
        * @param {Object[]} args The CustomEvent arguments
        * @param {Object} obj The scope object
        */
        handleStartAnimateOut: function (type, args, obj) { },

        /**
        * The default onTween handler for the out-animation.
        * @method handleTweenAnimateOut
        * @param {String} type The CustomEvent type
        * @param {Object[]} args The CustomEvent arguments
        * @param {Object} obj The scope object
        */
        handleTweenAnimateOut: function (type, args, obj) { },

        /**
        * The default onComplete handler for the out-animation.
        * @method handleCompleteAnimateOut
        * @param {String} type The CustomEvent type
        * @param {Object[]} args The CustomEvent arguments
        * @param {Object} obj The scope object
        */
        handleCompleteAnimateOut: function (type, args, obj) { },
        
        /**
        * Returns a string representation of the object.
        * @method toString
        * @return {String} The string representation of the ContainerEffect
        */
        toString: function () {
            var output = "ContainerEffect";
            if (this.overlay) {
                output += " [" + this.overlay.toString() + "]";
            }
            return output;
        }
    };

    YAHOO.lang.augmentProto(ContainerEffect, YAHOO.util.EventProvider);

})();
YAHOO.register("containercore", YAHOO.widget.Module, {version: "2.9.0", build: "2800"});
