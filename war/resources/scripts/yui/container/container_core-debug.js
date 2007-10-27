/*
Copyright (c) 2007, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.net/yui/license.txt
version: 2.3.1
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
    
        if (!owner) { 
        
            YAHOO.log("No owner specified for Config object", "error"); 
    
        }
    
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
            YAHOO.log("Firing Config event: " + key + "=" + value, "info");
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
            YAHOO.log("Added property: " + key, "info");
        
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
                prop,
                property;
                
            for (prop in this.config) {
                property = this.config[prop];
                if (property && property.event) {
                    cfg[prop] = property.value;
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
    
                if (this.initialConfig[key] && 
                    !Lang.isUndefined(this.initialConfig[key])) {
    
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
            YAHOO.log("setProperty: " + key + "=" + value, "info");
        
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
            YAHOO.log("queueProperty: " + key + "=" + value, "info");
        
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

                YAHOO.log("Config event queue: " + this.outputEventQueue(), "info");
        
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
                oValue,
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
                this.refireEvent(prop);
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
        * @param {Boolean} override Optional. If true, will override "this"  
        * within the handler to map to the scope Object passed into the method.
        * @return {Boolean} True, if the subscription was successful, 
        * otherwise false.
        */ 
        subscribeToConfigEvent: function (key, handler, obj, override) {
    
            var property = this.config[key.toLowerCase()];
    
            if (property && property.event) {
    
                if (!Config.alreadySubscribed(property.event, handler, obj)) {
    
                    property.event.subscribe(handler, obj, override);
    
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
            "DESTORY": "destroy",
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
    * Constant representing the module header
    * @property YAHOO.widget.Module.CSS_HEADER
    * @static
    * @final
    * @type String
    */
    Module.CSS_HEADER = "hd";
    
    /**
    * Constant representing the module body
    * @property YAHOO.widget.Module.CSS_BODY
    * @static
    * @final
    * @type String
    */
    Module.CSS_BODY = "bd";
    
    /**
    * Constant representing the module footer
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
    * Singleton CustomEvent fired when the font size is changed in the browser.
    * Opera's "zoom" functionality currently does not support text 
    * size detection.
    * @event YAHOO.widget.Module.textResizeEvent
    */
    Module.textResizeEvent = new CustomEvent("textResize");

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
            this.destroyEvent = this.createEvent(EVENT_TYPES.DESTORY);
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
        * String representing the current user-agent platform
        * @property platform
        * @type String
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
        * @type String
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
            * Object or array of objects representing the ContainerEffect 
            * classes that are active for animating the container.
            * @config effect
            * @type Object
            * @default null
            */
            this.cfg.addProperty(DEFAULT_CONFIG.EFFECT.key, {
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
        * @method init
        * @param {String} el The element ID representing the Module <em>OR</em>
        * @param {HTMLElement} el The element representing the Module
        * @param {Object} userConfig The configuration Object literal 
        * containing the configuration that should be set for this module. 
        * See configuration documentation for more details.
        */
        init: function (el, userConfig) {

            var elId, i, child;

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

            this.element = el;

            if (el.id) {
                this.id = el.id;
            }

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
        * Initialized an empty IFRAME that is placed out of the visible area 
        * that can be used to detect text resize.
        * @method initResizeMonitor
        */
        initResizeMonitor: function () {

            var oDoc, 
                oIFrame, 
                sHTML;

            function fireTextResize() {
                Module.textResizeEvent.fire();
            }

            if (!YAHOO.env.ua.opera) {
                oIFrame = Dom.get("_yuiResizeMonitor");

                if (!oIFrame) {
                    oIFrame = document.createElement("iframe");

                    if (this.isSecure && Module.RESIZE_MONITOR_SECURE_URL && YAHOO.env.ua.ie) {
                        oIFrame.src = Module.RESIZE_MONITOR_SECURE_URL;
                    }

                    /*
                        Need to set "src" attribute of the iframe to 
                        prevent the browser from reporting duplicate 
                        cookies. (See SourceForge bug #1721755)
                    */
                    if (YAHOO.env.ua.gecko) {
                        sHTML = "<html><head><script " +
                                "type=\"text/javascript\">" + 
                                "window.onresize=function(){window.parent." +
                                "YAHOO.widget.Module.textResizeEvent." +
                                "fire();};window.parent.YAHOO.widget.Module." +
                                "textResizeEvent.fire();</script></head>" + 
                                "<body></body></html>";

                        oIFrame.src = "data:text/html;charset=utf-8," + 
                            encodeURIComponent(sHTML);
                    }

                    oIFrame.id = "_yuiResizeMonitor";
                    /*
                        Need to set "position" property before inserting the 
                        iframe into the document or Safari's status bar will 
                        forever indicate the iframe is loading 
                        (See SourceForge bug #1723064)
                    */
                    oIFrame.style.position = "absolute";
                    oIFrame.style.visibility = "hidden";

                    var fc = document.body.firstChild;
                    if (fc) {
                        document.body.insertBefore(oIFrame, fc);
                    } else {
                        document.body.appendChild(oIFrame);
                    }

                    oIFrame.style.width = "10em";
                    oIFrame.style.height = "10em";
                    oIFrame.style.top = (-1 * oIFrame.offsetHeight) + "px";
                    oIFrame.style.left = (-1 * oIFrame.offsetWidth) + "px";
                    oIFrame.style.borderWidth = "0";
                    oIFrame.style.visibility = "visible";

                    if (YAHOO.env.ua.webkit) {
                        oDoc = oIFrame.contentWindow.document;
                        oDoc.open();
                        oDoc.close();
                    }
                }

                if (oIFrame && oIFrame.contentWindow) {
                    Module.textResizeEvent.subscribe(this.onDomResize, this, true);

                    if (!Module.textResizeInitialized) {
                        if (!Event.on(oIFrame.contentWindow, "resize", fireTextResize)) {
                            /*
                                 This will fail in IE if document.domain has 
                                 changed, so we must change the listener to 
                                 use the oIFrame element instead
                            */
                            Event.on(oIFrame, "resize", fireTextResize);
                        }
                        Module.textResizeInitialized = true;
                    }
                    this.resizeMonitor = oIFrame;
                }
            }
        },

        /**
        * Event handler fired when the resize monitor element is resized.
        * @method onDomResize
        * @param {DOMEvent} e The DOM resize event
        * @param {Object} obj The scope object passed to the handler
        */
        onDomResize: function (e, obj) {
        
            var nLeft = -1 * this.resizeMonitor.offsetWidth,
                nTop = -1 * this.resizeMonitor.offsetHeight;
        
            this.resizeMonitor.style.top = nTop + "px";
            this.resizeMonitor.style.left =  nLeft + "px";
        
        },
        
        /**
        * Sets the Module's header content to the HTML specified, or appends 
        * the passed element to the header. If no header is present, one will 
        * be automatically created.
        * @method setHeader
        * @param {String} headerContent The HTML used to set the header 
        * <em>OR</em>
        * @param {HTMLElement} headerContent The HTMLElement to append to 
        * the header
        */
        setHeader: function (headerContent) {

            var oHeader = this.header || (this.header = createHeader());
        
            if (typeof headerContent == "string") {

                oHeader.innerHTML = headerContent;

            } else {

                oHeader.innerHTML = "";
                oHeader.appendChild(headerContent);

            }
        
            this.changeHeaderEvent.fire(headerContent);
            this.changeContentEvent.fire();

        },
        
        /**
        * Appends the passed element to the header. If no header is present, 
        * one will be automatically created.
        * @method appendToHeader
        * @param {HTMLElement} element The element to append to the header
        */
        appendToHeader: function (element) {

            var oHeader = this.header || (this.header = createHeader());
        
            oHeader.appendChild(element);

            this.changeHeaderEvent.fire(element);
            this.changeContentEvent.fire();

        },
        
        /**
        * Sets the Module's body content to the HTML specified, or appends the
        * passed element to the body. If no body is present, one will be 
        * automatically created.
        * @method setBody
        * @param {String} bodyContent The HTML used to set the body <em>OR</em>
        * @param {HTMLElement} bodyContent The HTMLElement to append to the body
        */
        setBody: function (bodyContent) {

            var oBody = this.body || (this.body = createBody());
        
            if (typeof bodyContent == "string") {

                oBody.innerHTML = bodyContent;

            } else {

                oBody.innerHTML = "";
                oBody.appendChild(bodyContent);

            }
        
            this.changeBodyEvent.fire(bodyContent);
            this.changeContentEvent.fire();

        },
        
        /**
        * Appends the passed element to the body. If no body is present, one 
        * will be automatically created.
        * @method appendToBody
        * @param {HTMLElement} element The element to append to the body
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
        * be automatically created.
        * @method setFooter
        * @param {String} footerContent The HTML used to set the footer 
        * <em>OR</em>
        * @param {HTMLElement} footerContent The HTMLElement to append to 
        * the footer
        */
        setFooter: function (footerContent) {

            var oFooter = this.footer || (this.footer = createFooter());
        
            if (typeof footerContent == "string") {

                oFooter.innerHTML = footerContent;

            } else {

                oFooter.innerHTML = "";
                oFooter.appendChild(footerContent);

            }
        
            this.changeFooterEvent.fire(footerContent);
            this.changeContentEvent.fire();

        },
        
        /**
        * Appends the passed element to the footer. If no footer is present, 
        * one will be automatically created.
        * @method appendToFooter
        * @param {HTMLElement} element The element to append to the footer
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

            var me = this,
                firstChild;

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

            // Need to get everything into the DOM if it isn't already
            if (this.header && ! Dom.inDocument(this.header)) {
                // There is a header, but it's not in the DOM yet. Need to add it.
                firstChild = moduleElement.firstChild;
                if (firstChild) {
                    moduleElement.insertBefore(this.header, firstChild);
                } else {
                    moduleElement.appendChild(this.header);
                }
            }

            if (this.body && ! Dom.inDocument(this.body)) {
                // There is a body, but it's not in the DOM yet. Need to add it.		
                if (this.footer && Dom.isAncestor(this.moduleElement, this.footer)) {
                    moduleElement.insertBefore(this.body, this.footer);
                } else {
                    moduleElement.appendChild(this.body);
                }
            }

            if (this.footer && ! Dom.inDocument(this.footer)) {
                // There is a footer, but it's not in the DOM yet. Need to add it.
                moduleElement.appendChild(this.footer);
            }

            this.renderEvent.fire();
            return true;
        },

        /**
        * Removes the Module element from the DOM and sets all child elements 
        * to null.
        * @method destroy
        */
        destroy: function () {

            var parent,
                e;

            if (this.element) {
                Event.purgeElement(this.element, true);
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
        
            for (e in this) {
                if (e instanceof CustomEvent) {
                    e.unsubscribeAll();
                }
            }

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
                this.beforeShowEvent.fire();
                Dom.setStyle(this.element, "display", "block");
                this.showEvent.fire();
            } else {
                this.beforeHideEvent.fire();
                Dom.setStyle(this.element, "display", "none");
                this.hideEvent.fire();
            }
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
         * This method is a private helper, used when constructing the DOM structure for the module 
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
        Overlay = YAHOO.widget.Overlay,

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
                validator: Lang.isBoolean, 
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
                value: (YAHOO.env.ua.ie == 6 ? true : false), 
                validator: Lang.isBoolean, 
                supercedes: ["zindex"] 
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
    * side of an Overlay instance.
    * @property YAHOO.widget.Overlay.IFRAME_SRC
    * @default 3
    * @static
    * @final
    * @type Number
    */
    Overlay.IFRAME_OFFSET = 3;
    
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
    
    /**
    * Constant representing the default CSS class used for an Overlay
    * @property YAHOO.widget.Overlay.CSS_OVERLAY
    * @static
    * @final
    * @type String
    */
    Overlay.CSS_OVERLAY = "yui-overlay";
    
    
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
    
        if (YAHOO.env.ua.ie) {

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
    };

    /**
    * The DOM event handler used to fire the CustomEvent for window resize
    * @method YAHOO.widget.Overlay.windowResizeHandler
    * @static
    * @param {DOMEvent} e The DOM resize event
    */
    Overlay.windowResizeHandler = function (e) {

        if (YAHOO.env.ua.ie) {
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

    YAHOO.extend(Overlay, Module, {
    
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

            if (this.platform == "mac" && YAHOO.env.ua.gecko) {

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
            
            
            // Add overlay config properties //
            
            /**
            * The absolute x-coordinate position of the Overlay
            * @config x
            * @type Number
            * @default null
            */
            this.cfg.addProperty(DEFAULT_CONFIG.X.key, { 
    
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
            this.cfg.addProperty(DEFAULT_CONFIG.Y.key, {
    
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
            this.cfg.addProperty(DEFAULT_CONFIG.XY.key, {
            
                handler: this.configXY, 
                suppressEvent: DEFAULT_CONFIG.XY.suppressEvent, 
                supercedes: DEFAULT_CONFIG.XY.supercedes
            
            });
    
            /**
            * The array of context arguments for context-sensitive positioning.  
            * The format is: [id or element, element corner, context corner]. 
            * For example, setting this property to ["img1", "tl", "bl"] would 
            * align the Overlay's top left corner to the context element's 
            * bottom left corner.
            * @config context
            * @type Array
            * @default null
            */
            this.cfg.addProperty(DEFAULT_CONFIG.CONTEXT.key, {
            
                handler: this.configContext, 
                suppressEvent: DEFAULT_CONFIG.CONTEXT.suppressEvent, 
                supercedes: DEFAULT_CONFIG.CONTEXT.supercedes
            
            });
    
            /**
            * True if the Overlay should be anchored to the center of 
            * the viewport.
            * @config fixedcenter
            * @type Boolean
            * @default false
            */
            this.cfg.addProperty(DEFAULT_CONFIG.FIXED_CENTER.key, {
            
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
            this.cfg.addProperty(DEFAULT_CONFIG.WIDTH.key, {
            
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
            this.cfg.addProperty(DEFAULT_CONFIG.HEIGHT.key, {
            
                handler: this.configHeight, 
                suppressEvent: DEFAULT_CONFIG.HEIGHT.suppressEvent, 
                supercedes: DEFAULT_CONFIG.HEIGHT.supercedes
            
            });
            
            /**
            * CSS z-index of the Overlay.
            * @config zIndex
            * @type Number
            * @default null
            */
            this.cfg.addProperty(DEFAULT_CONFIG.ZINDEX.key, {
    
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
            this.cfg.addProperty(DEFAULT_CONFIG.CONSTRAIN_TO_VIEWPORT.key, {
            
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
            this.cfg.addProperty(DEFAULT_CONFIG.IFRAME.key, {
            
                handler: this.configIframe, 
                value: DEFAULT_CONFIG.IFRAME.value, 
                validator: DEFAULT_CONFIG.IFRAME.validator, 
                supercedes: DEFAULT_CONFIG.IFRAME.supercedes

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
    
            Dom.removeClass(this.element, "show-scrollbars");
            Dom.addClass(this.element, "hide-scrollbars");
    
        },

        /**
        * Adds a CSS class ("show-scrollbars") and removes a CSS class 
        * ("hide-scrollbars") to the Overlay to fix a bug in Gecko on Mac OS X 
        * (https://bugzilla.mozilla.org/show_bug.cgi?id=187435)
        * @method showMacGeckoScrollbars
        */
        showMacGeckoScrollbars: function () {
    
            Dom.removeClass(this.element, "hide-scrollbars");
            Dom.addClass(this.element, "show-scrollbars");
    
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
                effect = this.cfg.getProperty("effect"),
                effectInstances = [],
                isMacGecko = (this.platform == "mac" && YAHOO.env.ua.gecko),
                alreadySubscribed = Config.alreadySubscribed,
                eff, ei, e, i, j, k, h,
                nEffects,
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
    
            if (effect) {
                if (effect instanceof Array) {
                    nEffects = effect.length;
    
                    for (i = 0; i < nEffects; i++) {
                        eff = effect[i];
                        effectInstances[effectInstances.length] = 
                            eff.effect(this, eff.duration);
    
                    }
                } else {
                    effectInstances[effectInstances.length] = 
                        effect.effect(this, effect.duration);
                }
            }
    
        
            if (visible) { // Show
                if (isMacGecko) {
                    this.showMacGeckoScrollbars();
                }
    
                if (effect) { // Animate in
                    if (visible) { // Animate in if not showing
                        if (currentVis != "visible" || currentVis === "") {
                            this.beforeShowEvent.fire();
                            nEffectInstances = effectInstances.length;
    
                            for (j = 0; j < nEffectInstances; j++) {
                                ei = effectInstances[j];
                                if (j === 0 && !alreadySubscribed(
                                        ei.animateInCompleteEvent, 
                                        this.showEvent.fire, this.showEvent)) {
    
                                    /*
                                         Delegate showEvent until end 
                                         of animateInComplete
                                    */
    
                                    ei.animateInCompleteEvent.subscribe(
                                     this.showEvent.fire, this.showEvent, true);
                                }
                                ei.animateIn();
                            }
                        }
                    }
                } else { // Show
                    if (currentVis != "visible" || currentVis === "") {
                        this.beforeShowEvent.fire();
    
                        Dom.setStyle(this.element, "visibility", "visible");
    
                        this.cfg.refireEvent("iframe");
                        this.showEvent.fire();
                    }
                }
            } else { // Hide
    
                if (isMacGecko) {
                    this.hideMacGeckoScrollbars();
                }
                    
                if (effect) { // Animate out if showing
                    if (currentVis == "visible") {
                        this.beforeHideEvent.fire();

                        nEffectInstances = effectInstances.length;
                        for (k = 0; k < nEffectInstances; k++) {
                            h = effectInstances[k];
    
                            if (k === 0 && !alreadySubscribed(
                                h.animateOutCompleteEvent, this.hideEvent.fire, 
                                this.hideEvent)) {
    
                                /*
                                     Delegate hideEvent until end 
                                     of animateOutComplete
                                */
    
                                h.animateOutCompleteEvent.subscribe(
                                    this.hideEvent.fire, this.hideEvent, true);
    
                            }
                            h.animateOut();
                        }
    
                    } else if (currentVis === "") {
                        Dom.setStyle(this.element, "visibility", "hidden");
                    }
    
                } else { // Simple hide
    
                    if (currentVis == "visible" || currentVis === "") {
                        this.beforeHideEvent.fire();
                        Dom.setStyle(this.element, "visibility", "hidden");
                        this.hideEvent.fire();
                    }
                }
            }
        },

        /**
        * Center event handler used for centering on scroll/resize, but only if 
        * the Overlay is visible
        * @method doCenterOnDOMEvent
        */
        doCenterOnDOMEvent: function () {
            if (this.cfg.getProperty("visible")) {
                this.center();
            }
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

                if (!alreadySubscribed(this.beforeShowEvent, this.center, this)) {
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
        stackIframe: function() {
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
                    oParent,
                    aXY;

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

                        if (YAHOO.env.ua.ie) {
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
                    }

                    oIFrame = m_oIFrameTemplate.cloneNode(false);
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
                if (! Config.alreadySubscribed(this.beforeMoveEvent, 
                    this.enforceConstraints, this)) {
    
                    this.beforeMoveEvent.subscribe(this.enforceConstraints, 
                        this, true);
    
                }
            } else {
                this.beforeMoveEvent.unsubscribe(this.enforceConstraints, this);
            }
    
        },
        
        /**
        * The default event handler fired when the "context" property 
        * is changed.
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
                contextMagnetCorner;
            
            if (contextArgs) {
            
                contextEl = contextArgs[0];
                elementMagnetCorner = contextArgs[1];
                contextMagnetCorner = contextArgs[2];
                
                if (contextEl) {
    
                    if (typeof contextEl == "string") {
    
                        this.cfg.setProperty("context", 
                            [document.getElementById(contextEl), 
                                elementMagnetCorner, contextMagnetCorner], 
                                true);
    
                    }
                    
                    if (elementMagnetCorner && contextMagnetCorner) {
    
                        this.align(elementMagnetCorner, contextMagnetCorner);
    
                    }
    
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
        */
        align: function (elementAlign, contextAlign) {
    
            var contextArgs = this.cfg.getProperty("context"),
                me = this,
                context,
                element,
                contextRegion;
    
    
            function doAlign(v, h) {
    
                switch (elementAlign) {
    
                case Overlay.TOP_LEFT:
                    me.moveTo(h, v);
                    break;
    
                case Overlay.TOP_RIGHT:
                    me.moveTo((h - element.offsetWidth), v);
                    break;
    
                case Overlay.BOTTOM_LEFT:
                    me.moveTo(h, (v - element.offsetHeight));
                    break;
    
                case Overlay.BOTTOM_RIGHT:
                    me.moveTo((h - element.offsetWidth), 
                        (v - element.offsetHeight));
                    break;
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
    
            var pos = args[0],
                x = pos[0],
                y = pos[1],
                offsetHeight = this.element.offsetHeight,
                offsetWidth = this.element.offsetWidth,
                viewPortWidth = Dom.getViewportWidth(),
                viewPortHeight = Dom.getViewportHeight(),
                scrollX = Dom.getDocumentScrollLeft(),
                scrollY = Dom.getDocumentScrollTop(),
                topConstraint = scrollY + 10,
                leftConstraint = scrollX + 10,
                bottomConstraint = scrollY + viewPortHeight - offsetHeight - 10,
                rightConstraint = scrollX + viewPortWidth - offsetWidth - 10;
        
    
            if (x < leftConstraint) {
    
                x = leftConstraint;
    
            } else if (x > rightConstraint) {
    
                x = rightConstraint;
    
            }
            
            if (y < topConstraint) {
    
                y = topConstraint;
    
            } else if (y > bottomConstraint) {
    
                y = bottomConstraint;
    
            }
            
            this.cfg.setProperty("x", x, true);
            this.cfg.setProperty("y", y, true);
            this.cfg.setProperty("xy", [x, y], true);
    
        },
        
        /**
        * Centers the container in the viewport.
        * @method center
        */
        center: function () {
    
            var scrollX = Dom.getDocumentScrollLeft(),
                scrollY = Dom.getDocumentScrollTop(),
    
                viewPortWidth = Dom.getClientWidth(),
                viewPortHeight = Dom.getClientHeight(),
                elementWidth = this.element.offsetWidth,
                elementHeight = this.element.offsetHeight,
                x = (viewPortWidth / 2) - (elementWidth / 2) + scrollX,
                y = (viewPortHeight / 2) - (elementHeight / 2) + scrollY;
            
            this.cfg.setProperty("xy", [parseInt(x, 10), parseInt(y, 10)]);
            
            this.cfg.refireEvent("iframe");
    
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
        * Places the Overlay on top of all other instances of 
        * YAHOO.widget.Overlay.
        * @method bringToTop
        */
        bringToTop: function() {
    
            var aOverlays = [],
                oElement = this.element;
    
            function compareZIndexDesc(p_oOverlay1, p_oOverlay2) {
        
                var sZIndex1 = Dom.getStyle(p_oOverlay1, "zIndex"),
        
                    sZIndex2 = Dom.getStyle(p_oOverlay2, "zIndex"),
        
                    nZIndex1 = (!sZIndex1 || isNaN(sZIndex1)) ? 
                        0 : parseInt(sZIndex1, 10),
        
                    nZIndex2 = (!sZIndex2 || isNaN(sZIndex2)) ? 
                        0 : parseInt(sZIndex2, 10);
        
                if (nZIndex1 > nZIndex2) {
        
                    return -1;
        
                } else if (nZIndex1 < nZIndex2) {
        
                    return 1;
        
                } else {
        
                    return 0;
        
                }
        
            }
        
            function isOverlayElement(p_oElement) {
        
                var oOverlay = Dom.hasClass(p_oElement, Overlay.CSS_OVERLAY),
                    Panel = YAHOO.widget.Panel;
            
                if (oOverlay && !Dom.isAncestor(oElement, oOverlay)) {
                
                    if (Panel && Dom.hasClass(p_oElement, Panel.CSS_PANEL)) {
        
                        aOverlays[aOverlays.length] = p_oElement.parentNode;
                    
                    }
                    else {
        
                        aOverlays[aOverlays.length] = p_oElement;
        
                    }
                
                }
            
            }
            
            Dom.getElementsBy(isOverlayElement, "DIV", document.body);
    
            aOverlays.sort(compareZIndexDesc);
            
            var oTopOverlay = aOverlays[0],
                nTopZIndex;
            
            if (oTopOverlay) {
    
                nTopZIndex = Dom.getStyle(oTopOverlay, "zIndex");
    
                if (!isNaN(nTopZIndex) && oTopOverlay != oElement) {
    
                    this.cfg.setProperty("zindex", 
                        (parseInt(nTopZIndex, 10) + 2));
    
                }
            
            }
        
        },
        
        /**
        * Removes the Overlay element from the DOM and sets all child 
        * elements to null.
        * @method destroy
        */
        destroy: function () {

            if (this.iframe) {
    
                this.iframe.parentNode.removeChild(this.iframe);
    
            }
        
            this.iframe = null;
        
            Overlay.windowResizeEvent.unsubscribe(
                this.doCenterOnDOMEvent, this);
    
            Overlay.windowScrollEvent.unsubscribe(
                this.doCenterOnDOMEvent, this);
        
            Overlay.superclass.destroy.call(this);
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
                    if (activeOverlay != o) {
                        if (activeOverlay) {
                            activeOverlay.blur();
                        }
                        this.bringToTop(o);

                        activeOverlay = o;

                        Dom.addClass(activeOverlay.element, 
                            OverlayManager.CSS_FOCUSED);

                        o.focusEvent.fire();
                    }
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

                    if (!bDestroyed) {
                        Event.removeListener(o.element, 
                                    this.cfg.getProperty("focusevent"), 
                                    this._onOverlayElementFocus);

                        o.cfg.setProperty("zIndex", originalZ, true);
                        o.cfg.setProperty("manager", null);
                    }

                    o.focusEvent.unsubscribeAll();
                    o.blurEvent.unsubscribeAll();

                    o.focusEvent = null;
                    o.blurEvent = null;

                    o.focus = null;
                    o.blur = null;
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
        
            this._onOverlayBlur = function (p_sType, p_aArgs) {
                activeOverlay = null;
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
        * @param {Overlay} p_oOverlay Object representing the menu that 
        * fired the event.
        */
        _onOverlayDestroy: function (p_sType, p_aArgs, p_oOverlay) {
            this.remove(p_oOverlay);
        },
        
        /**
        * Registers an Overlay or an array of Overlays with the manager. Upon 
        * registration, the Overlay receives functions for focus and blur, 
        * along with CustomEvents for each.
        * @method register
        * @param {Overlay} overlay  An Overlay to register with the manager.
        * @param {Overlay[]} overlay  An array of Overlays to register with 
        * the manager.
        * @return {Boolean} True if any Overlays are registered.
        */
        register: function (overlay) {
        
            var mgr = this,
                zIndex,
                regcount,
                i,
                nOverlays;
        
            if (overlay instanceof Overlay) {

                overlay.cfg.addProperty("manager", { value: this } );

                overlay.focusEvent = overlay.createEvent("focus");
                overlay.focusEvent.signature = CustomEvent.LIST;

                overlay.blurEvent = overlay.createEvent("blur");
                overlay.blurEvent.signature = CustomEvent.LIST;
        
                overlay.focus = function () {
                    mgr.focus(this);
                };
        
                overlay.blur = function () {
                    if (mgr.getActive() == this) {
                        Dom.removeClass(this.element, OverlayManager.CSS_FOCUSED);
                        this.blurEvent.fire();
                    }
                };
        
                overlay.blurEvent.subscribe(mgr._onOverlayBlur);
                overlay.hideEvent.subscribe(overlay.blur);
                
                overlay.destroyEvent.subscribe(this._onOverlayDestroy, overlay, this);
        
                Event.on(overlay.element, this.cfg.getProperty("focusevent"), 
                            this._onOverlayElementFocus, null, overlay);
        
                zIndex = Dom.getStyle(overlay.element, "zIndex");

                if (!isNaN(zIndex)) {
                    overlay.cfg.setProperty("zIndex", parseInt(zIndex, 10));
                } else {
                    overlay.cfg.setProperty("zIndex", 0);
                }

                this.overlays.push(overlay);
                this.bringToTop(overlay);

                return true;

            } else if (overlay instanceof Array) {

                regcount = 0;
                nOverlays = overlay.length;

                for (i = 0; i < nOverlays; i++) {
                    if (this.register(overlay[i])) {
                        regcount++;
                    }
                }

                if (regcount > 0) {
                    return true;
                }
            } else {
                return false;
            }
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
    
                    if (!isNaN(nTopZIndex) && oTopOverlay != oOverlay) {
    
                        oOverlay.cfg.setProperty("zIndex", 
                            (parseInt(nTopZIndex, 10) + 2));
    
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
        
            var aOverlays = this.overlays,
                nOverlays = aOverlays.length,
                i;

            if (nOverlays > 0) {
                i = nOverlays - 1;

                if (overlay instanceof Overlay) {
                    do {
                        if (aOverlays[i] == overlay) {
                            return aOverlays[i];
                        }
                    }
                    while(i--);

                } else if (typeof overlay == "string") {
                    do {
                        if (aOverlays[i].id == overlay) {
                            return aOverlays[i];
                        }
                    }
                    while(i--);
                }
                return null;
            }
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
        
            var aOverlays = this.overlays,
                nOverlays = aOverlays.length,
                i;

            if (nOverlays > 0) {
                i = nOverlays - 1;
                do {
                    aOverlays[i].show();
                }
                while(i--);
            }
        },
        
        /**
        * Hides all Overlays in the manager.
        * @method hideAll
        */
        hideAll: function () {
        
            var aOverlays = this.overlays,
                nOverlays = aOverlays.length,
                i;

            if (nOverlays > 0) {
                i = nOverlays - 1;
                do {
                    aOverlays[i].hide();
                }
                while(i--);
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
    YAHOO.widget.ContainerEffect = 
    
        function (overlay, attrIn, attrOut, targetElement, animClass) {
    
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
        Easing = YAHOO.util.Easing,
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
    
        var fade = new ContainerEffect(overlay, 
        
            { attributes: { opacity: { from: 0, to: 1 } }, 
                duration: dur, 
                method: Easing.easeIn }, 
            
            { attributes: { opacity: { to: 0 } },
                duration: dur, 
                method: Easing.easeOut }, 
            
            overlay.element);
        
    
        fade.handleStartAnimateIn = function (type,args,obj) {
            Dom.addClass(obj.overlay.element, "hide-select");
        
            if (! obj.overlay.underlay) {
                obj.overlay.cfg.refireEvent("underlay");
            }
        
            if (obj.overlay.underlay) {
    
                obj.initialUnderlayOpacity = 
                    Dom.getStyle(obj.overlay.underlay, "opacity");
    
                obj.overlay.underlay.style.filter = null;
    
            }
        
            Dom.setStyle(obj.overlay.element, "visibility", "visible");
            Dom.setStyle(obj.overlay.element, "opacity", 0);
        };
        
    
        fade.handleCompleteAnimateIn = function (type,args,obj) {
            Dom.removeClass(obj.overlay.element, "hide-select");
        
            if (obj.overlay.element.style.filter) {
                obj.overlay.element.style.filter = null;
            }
        
            if (obj.overlay.underlay) {
                Dom.setStyle(obj.overlay.underlay, "opacity", 
                    obj.initialUnderlayOpacity);
            }
        
            obj.overlay.cfg.refireEvent("iframe");
            obj.animateInCompleteEvent.fire();
        };
        
    
        fade.handleStartAnimateOut = function (type, args, obj) {
            Dom.addClass(obj.overlay.element, "hide-select");
        
            if (obj.overlay.underlay) {
                obj.overlay.underlay.style.filter = null;
            }
        };
        
    
        fade.handleCompleteAnimateOut =  function (type, args, obj) {
            Dom.removeClass(obj.overlay.element, "hide-select");
            if (obj.overlay.element.style.filter) {
                obj.overlay.element.style.filter = null;
            }
            Dom.setStyle(obj.overlay.element, "visibility", "hidden");
            Dom.setStyle(obj.overlay.element, "opacity", 1);
        
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
    
        var x = overlay.cfg.getProperty("x") || Dom.getX(overlay.element),
    
            y = overlay.cfg.getProperty("y") || Dom.getY(overlay.element),
    
            clientWidth = Dom.getClientWidth(),
    
            offsetWidth = overlay.element.offsetWidth,
    
            slide = new ContainerEffect(overlay, 
            
            { attributes: { points: { to: [x, y] } },
                duration: dur,
                method: Easing.easeIn },
    
            { attributes: { points: { to: [(clientWidth + 25), y] } },
                duration: dur,
                method: Easing.easeOut },
    
            overlay.element, YAHOO.util.Motion);
        
        
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

                Dom.setStyle(obj.overlay.element, "visibility", "visible");

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
                yso = pos[1],
                currentTo = obj.animOut.attributes.points.to;
    
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
            Dom.setStyle(obj.overlay.element, "visibility", "hidden");
        
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
        
            this.animateOutCompleteEvent = 
                this.createEvent("animateOutComplete");
            this.animateOutCompleteEvent.signature = CustomEvent.LIST;
        
            this.animIn = new this.animClass(this.targetElement, 
                this.attrIn.attributes, this.attrIn.duration, 
                this.attrIn.method);

            this.animIn.onStart.subscribe(this.handleStartAnimateIn, this);
            this.animIn.onTween.subscribe(this.handleTweenAnimateIn, this);

            this.animIn.onComplete.subscribe(this.handleCompleteAnimateIn, 
                this);
        
            this.animOut = new this.animClass(this.targetElement, 
                this.attrOut.attributes, this.attrOut.duration, 
                this.attrOut.method);

            this.animOut.onStart.subscribe(this.handleStartAnimateOut, this);
            this.animOut.onTween.subscribe(this.handleTweenAnimateOut, this);
            this.animOut.onComplete.subscribe(this.handleCompleteAnimateOut, 
                this);

        },
        
        /**
        * Triggers the in-animation.
        * @method animateIn
        */
        animateIn: function () {
            this.beforeAnimateInEvent.fire();
            this.animIn.animate();
        },
        
        /**
        * Triggers the out-animation.
        * @method animateOut
        */
        animateOut: function () {
            this.beforeAnimateOutEvent.fire();
            this.animOut.animate();
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

YAHOO.register("container_core", YAHOO.widget.Module, {version: "2.3.1", build: "541"});
