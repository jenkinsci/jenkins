/*
Copyright (c) 2011, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.com/yui/license.html
version: 2.9.0
*/
/**
 * Provides Attribute configurations.
 * @namespace YAHOO.util
 * @class Attribute
 * @constructor
 * @param hash {Object} The initial Attribute.
 * @param {YAHOO.util.AttributeProvider} The owner of the Attribute instance.
 */

YAHOO.util.Attribute = function(hash, owner) {
    if (owner) { 
        this.owner = owner;
        this.configure(hash, true);
    }
};

YAHOO.util.Attribute.INVALID_VALUE = {};

YAHOO.util.Attribute.prototype = {
    /**
     * The name of the attribute.
     * @property name
     * @type String
     */
    name: undefined,
    
    /**
     * The value of the attribute.
     * @property value
     * @type String
     */
    value: null,
    
    /**
     * The owner of the attribute.
     * @property owner
     * @type YAHOO.util.AttributeProvider
     */
    owner: null,
    
    /**
     * Whether or not the attribute is read only.
     * @property readOnly
     * @type Boolean
     */
    readOnly: false,
    
    /**
     * Whether or not the attribute can only be written once.
     * @property writeOnce
     * @type Boolean
     */
    writeOnce: false,

    /**
     * The attribute's initial configuration.
     * @private
     * @property _initialConfig
     * @type Object
     */
    _initialConfig: null,
    
    /**
     * Whether or not the attribute's value has been set.
     * @private
     * @property _written
     * @type Boolean
     */
    _written: false,
    
    /**
     * A function to call when setting the attribute's value.
     * The method receives the new value as the first arg and the attribute name as the 2nd
     * @property method
     * @type Function
     */
    method: null,
    
    /**
     * The function to use when setting the attribute's value.
     * The setter receives the new value as the first arg and the attribute name as the 2nd
     * The return value of the setter replaces the value passed to set(). 
     * @property setter
     * @type Function
     */
    setter: null,
    
    /**
     * The function to use when getting the attribute's value.
     * The getter receives the new value as the first arg and the attribute name as the 2nd
     * The return value of the getter will be used as the return from get().
     * @property getter
     * @type Function
     */
    getter: null,

    /**
     * The validator to use when setting the attribute's value.
     * @property validator
     * @type Function
     * @return Boolean
     */
    validator: null,
    
    /**
     * Retrieves the current value of the attribute.
     * @method getValue
     * @return {any} The current value of the attribute.
     */
    getValue: function() {
        var val = this.value;

        if (this.getter) {
            val = this.getter.call(this.owner, this.name, val);
        }

        return val;
    },
    
    /**
     * Sets the value of the attribute and fires beforeChange and change events.
     * @method setValue
     * @param {Any} value The value to apply to the attribute.
     * @param {Boolean} silent If true the change events will not be fired.
     * @return {Boolean} Whether or not the value was set.
     */
    setValue: function(value, silent) {
        var beforeRetVal,
            owner = this.owner,
            name = this.name,
            invalidValue = YAHOO.util.Attribute.INVALID_VALUE,
        
            event = {
                type: name, 
                prevValue: this.getValue(),
                newValue: value
        };
        
        if (this.readOnly || ( this.writeOnce && this._written) ) {
            YAHOO.log( 'setValue ' + name + ', ' +  value +
                    ' failed: read only', 'error', 'Attribute');
            return false; // write not allowed
        }
        
        if (this.validator && !this.validator.call(owner, value) ) {
            YAHOO.log( 'setValue ' + name + ', ' + value +
                    ' validation failed', 'error', 'Attribute');
            return false; // invalid value
        }

        if (!silent) {
            beforeRetVal = owner.fireBeforeChangeEvent(event);
            if (beforeRetVal === false) {
                YAHOO.log('setValue ' + name + 
                        ' cancelled by beforeChange event', 'info', 'Attribute');
                return false;
            }
        }

        if (this.setter) {
            value = this.setter.call(owner, value, this.name);
            if (value === undefined) {
                YAHOO.log('setter for ' + this.name + ' returned undefined', 'warn', 'Attribute');
            }

            if (value === invalidValue) {
                return false;
            }
        }
        
        if (this.method) {
            if (this.method.call(owner, value, this.name) === invalidValue) {
                return false; 
            }
        }
        
        this.value = value; // TODO: set before calling setter/method?
        this._written = true;
        
        event.type = name;
        
        if (!silent) {
            this.owner.fireChangeEvent(event);
        }
        
        return true;
    },
    
    /**
     * Allows for configuring the Attribute's properties.
     * @method configure
     * @param {Object} map A key-value map of Attribute properties.
     * @param {Boolean} init Whether or not this should become the initial config.
     */
    configure: function(map, init) {
        map = map || {};

        if (init) {
            this._written = false; // reset writeOnce
        }

        this._initialConfig = this._initialConfig || {};
        
        for (var key in map) {
            if ( map.hasOwnProperty(key) ) {
                this[key] = map[key];
                if (init) {
                    this._initialConfig[key] = map[key];
                }
            }
        }
    },
    
    /**
     * Resets the value to the initial config value.
     * @method resetValue
     * @return {Boolean} Whether or not the value was set.
     */
    resetValue: function() {
        return this.setValue(this._initialConfig.value);
    },
    
    /**
     * Resets the attribute config to the initial config state.
     * @method resetConfig
     */
    resetConfig: function() {
        this.configure(this._initialConfig, true);
    },
    
    /**
     * Resets the value to the current value.
     * Useful when values may have gotten out of sync with actual properties.
     * @method refresh
     * @return {Boolean} Whether or not the value was set.
     */
    refresh: function(silent) {
        this.setValue(this.value, silent);
    }
};

(function() {
    var Lang = YAHOO.util.Lang;

    /*
    Copyright (c) 2006, Yahoo! Inc. All rights reserved.
    Code licensed under the BSD License:
    http://developer.yahoo.net/yui/license.txt
    */
    
    /**
     * Provides and manages YAHOO.util.Attribute instances
     * @namespace YAHOO.util
     * @class AttributeProvider
     * @uses YAHOO.util.EventProvider
     */
    YAHOO.util.AttributeProvider = function() {};

    YAHOO.util.AttributeProvider.prototype = {
        
        /**
         * A key-value map of Attribute configurations
         * @property _configs
         * @protected (may be used by subclasses and augmentors)
         * @private
         * @type {Object}
         */
        _configs: null,
        /**
         * Returns the current value of the attribute.
         * @method get
         * @param {String} key The attribute whose value will be returned.
         * @return {Any} The current value of the attribute.
         */
        get: function(key){
            this._configs = this._configs || {};
            var config = this._configs[key];
            
            if (!config || !this._configs.hasOwnProperty(key)) {
                YAHOO.log(key + ' not found', 'error', 'AttributeProvider');
                return null;
            }
            
            return config.getValue();
        },
        
        /**
         * Sets the value of a config.
         * @method set
         * @param {String} key The name of the attribute
         * @param {Any} value The value to apply to the attribute
         * @param {Boolean} silent Whether or not to suppress change events
         * @return {Boolean} Whether or not the value was set.
         */
        set: function(key, value, silent){
            this._configs = this._configs || {};
            var config = this._configs[key];
            
            if (!config) {
                YAHOO.log('set failed: ' + key + ' not found',
                        'error', 'AttributeProvider');
                return false;
            }
            
            return config.setValue(value, silent);
        },
    
        /**
         * Returns an array of attribute names.
         * @method getAttributeKeys
         * @return {Array} An array of attribute names.
         */
        getAttributeKeys: function(){
            this._configs = this._configs;
            var keys = [], key;

            for (key in this._configs) {
                if ( Lang.hasOwnProperty(this._configs, key) && 
                        !Lang.isUndefined(this._configs[key]) ) {
                    keys[keys.length] = key;
                }
            }
            
            return keys;
        },
        
        /**
         * Sets multiple attribute values.
         * @method setAttributes
         * @param {Object} map  A key-value map of attributes
         * @param {Boolean} silent Whether or not to suppress change events
         */
        setAttributes: function(map, silent){
            for (var key in map) {
                if ( Lang.hasOwnProperty(map, key) ) {
                    this.set(key, map[key], silent);
                }
            }
        },
    
        /**
         * Resets the specified attribute's value to its initial value.
         * @method resetValue
         * @param {String} key The name of the attribute
         * @param {Boolean} silent Whether or not to suppress change events
         * @return {Boolean} Whether or not the value was set
         */
        resetValue: function(key, silent){
            this._configs = this._configs || {};
            if (this._configs[key]) {
                this.set(key, this._configs[key]._initialConfig.value, silent);
                return true;
            }
            return false;
        },
    
        /**
         * Sets the attribute's value to its current value.
         * @method refresh
         * @param {String | Array} key The attribute(s) to refresh
         * @param {Boolean} silent Whether or not to suppress change events
         */
        refresh: function(key, silent) {
            this._configs = this._configs || {};
            var configs = this._configs;
            
            key = ( ( Lang.isString(key) ) ? [key] : key ) || 
                    this.getAttributeKeys();
            
            for (var i = 0, len = key.length; i < len; ++i) { 
                if (configs.hasOwnProperty(key[i])) {
                    this._configs[key[i]].refresh(silent);
                }
            }
        },
    
        /**
         * Adds an Attribute to the AttributeProvider instance. 
         * @method register
         * @param {String} key The attribute's name
         * @param {Object} map A key-value map containing the
         * attribute's properties.
         * @deprecated Use setAttributeConfig
         */
        register: function(key, map) {
            this.setAttributeConfig(key, map);
        },
        
        
        /**
         * Returns the attribute's properties.
         * @method getAttributeConfig
         * @param {String} key The attribute's name
         * @private
         * @return {object} A key-value map containing all of the
         * attribute's properties.
         */
        getAttributeConfig: function(key) {
            this._configs = this._configs || {};
            var config = this._configs[key] || {};
            var map = {}; // returning a copy to prevent overrides
            
            for (key in config) {
                if ( Lang.hasOwnProperty(config, key) ) {
                    map[key] = config[key];
                }
            }
    
            return map;
        },
        
        /**
         * Sets or updates an Attribute instance's properties. 
         * @method setAttributeConfig
         * @param {String} key The attribute's name.
         * @param {Object} map A key-value map of attribute properties
         * @param {Boolean} init Whether or not this should become the initial config.
         */
        setAttributeConfig: function(key, map, init) {
            this._configs = this._configs || {};
            map = map || {};
            if (!this._configs[key]) {
                map.name = key;
                this._configs[key] = this.createAttribute(map);
            } else {
                this._configs[key].configure(map, init);
            }
        },
        
        /**
         * Sets or updates an Attribute instance's properties. 
         * @method configureAttribute
         * @param {String} key The attribute's name.
         * @param {Object} map A key-value map of attribute properties
         * @param {Boolean} init Whether or not this should become the initial config.
         * @deprecated Use setAttributeConfig
         */
        configureAttribute: function(key, map, init) {
            this.setAttributeConfig(key, map, init);
        },
        
        /**
         * Resets an attribute to its initial configuration. 
         * @method resetAttributeConfig
         * @param {String} key The attribute's name.
         * @private
         */
        resetAttributeConfig: function(key){
            this._configs = this._configs || {};
            this._configs[key].resetConfig();
        },
        
        // wrapper for EventProvider.subscribe
        // to create events on the fly
        subscribe: function(type, callback) {
            this._events = this._events || {};

            if ( !(type in this._events) ) {
                this._events[type] = this.createEvent(type);
            }

            YAHOO.util.EventProvider.prototype.subscribe.apply(this, arguments);
        },

        on: function() {
            this.subscribe.apply(this, arguments);
        },

        addListener: function() {
            this.subscribe.apply(this, arguments);
        },

        /**
         * Fires the attribute's beforeChange event. 
         * @method fireBeforeChangeEvent
         * @param {String} key The attribute's name.
         * @param {Obj} e The event object to pass to handlers.
         */
        fireBeforeChangeEvent: function(e) {
            var type = 'before';
            type += e.type.charAt(0).toUpperCase() + e.type.substr(1) + 'Change';
            e.type = type;
            return this.fireEvent(e.type, e);
        },
        
        /**
         * Fires the attribute's change event. 
         * @method fireChangeEvent
         * @param {String} key The attribute's name.
         * @param {Obj} e The event object to pass to the handlers.
         */
        fireChangeEvent: function(e) {
            e.type += 'Change';
            return this.fireEvent(e.type, e);
        },

        createAttribute: function(map) {
            return new YAHOO.util.Attribute(map, this);
        }
    };
    
    YAHOO.augment(YAHOO.util.AttributeProvider, YAHOO.util.EventProvider);
})();

(function() {
// internal shorthand
var Dom = YAHOO.util.Dom,
    AttributeProvider = YAHOO.util.AttributeProvider,
	specialTypes = {
		mouseenter: true,
		mouseleave: true
	};

/**
 * Element provides an wrapper object to simplify adding
 * event listeners, using dom methods, and managing attributes. 
 * @module element
 * @namespace YAHOO.util
 * @requires yahoo, dom, event
 */

/**
 * Element provides an wrapper object to simplify adding
 * event listeners, using dom methods, and managing attributes. 
 * @class Element
 * @uses YAHOO.util.AttributeProvider
 * @constructor
 * @param el {HTMLElement | String} The html element that 
 * represents the Element.
 * @param {Object} map A key-value map of initial config names and values
 */
var Element = function(el, map) {
    this.init.apply(this, arguments);
};

Element.DOM_EVENTS = {
    'click': true,
    'dblclick': true,
    'keydown': true,
    'keypress': true,
    'keyup': true,
    'mousedown': true,
    'mousemove': true,
    'mouseout': true, 
    'mouseover': true, 
    'mouseup': true,
    'mouseenter': true, 
    'mouseleave': true,
    'focus': true,
    'blur': true,
    'submit': true,
    'change': true
};

Element.prototype = {
    /**
     * Dom events supported by the Element instance.
     * @property DOM_EVENTS
     * @type Object
     */
    DOM_EVENTS: null,

    DEFAULT_HTML_SETTER: function(value, key) {
        var el = this.get('element');
        
        if (el) {
            el[key] = value;
        }

		return value;

    },

    DEFAULT_HTML_GETTER: function(key) {
        var el = this.get('element'),
            val;

        if (el) {
            val = el[key];
        }

        return val;
    },

    /**
     * Wrapper for HTMLElement method.
     * @method appendChild
     * @param {YAHOO.util.Element || HTMLElement} child The element to append. 
     * @return {HTMLElement} The appended DOM element. 
     */
    appendChild: function(child) {
        child = child.get ? child.get('element') : child;
        return this.get('element').appendChild(child);
    },
    
    /**
     * Wrapper for HTMLElement method.
     * @method getElementsByTagName
     * @param {String} tag The tagName to collect
     * @return {HTMLCollection} A collection of DOM elements. 
     */
    getElementsByTagName: function(tag) {
        return this.get('element').getElementsByTagName(tag);
    },
    
    /**
     * Wrapper for HTMLElement method.
     * @method hasChildNodes
     * @return {Boolean} Whether or not the element has childNodes
     */
    hasChildNodes: function() {
        return this.get('element').hasChildNodes();
    },
    
    /**
     * Wrapper for HTMLElement method.
     * @method insertBefore
     * @param {HTMLElement} element The HTMLElement to insert
     * @param {HTMLElement} before The HTMLElement to insert
     * the element before.
     * @return {HTMLElement} The inserted DOM element. 
     */
    insertBefore: function(element, before) {
        element = element.get ? element.get('element') : element;
        before = (before && before.get) ? before.get('element') : before;
        
        return this.get('element').insertBefore(element, before);
    },
    
    /**
     * Wrapper for HTMLElement method.
     * @method removeChild
     * @param {HTMLElement} child The HTMLElement to remove
     * @return {HTMLElement} The removed DOM element. 
     */
    removeChild: function(child) {
        child = child.get ? child.get('element') : child;
        return this.get('element').removeChild(child);
    },
    
    /**
     * Wrapper for HTMLElement method.
     * @method replaceChild
     * @param {HTMLElement} newNode The HTMLElement to insert
     * @param {HTMLElement} oldNode The HTMLElement to replace
     * @return {HTMLElement} The replaced DOM element. 
     */
    replaceChild: function(newNode, oldNode) {
        newNode = newNode.get ? newNode.get('element') : newNode;
        oldNode = oldNode.get ? oldNode.get('element') : oldNode;
        return this.get('element').replaceChild(newNode, oldNode);
    },

    
    /**
     * Registers Element specific attributes.
     * @method initAttributes
     * @param {Object} map A key-value map of initial attribute configs
     */
    initAttributes: function(map) {
    },

    /**
     * Adds a listener for the given event.  These may be DOM or 
     * customEvent listeners.  Any event that is fired via fireEvent
     * can be listened for.  All handlers receive an event object. 
     * @method addListener
     * @param {String} type The name of the event to listen for
     * @param {Function} fn The handler to call when the event fires
     * @param {Any} obj A variable to pass to the handler
     * @param {Object} scope The object to use for the scope of the handler 
     */
    addListener: function(type, fn, obj, scope) {

        scope = scope || this;

        var Event = YAHOO.util.Event,
			el = this.get('element') || this.get('id'),
        	self = this;


		if (specialTypes[type] && !Event._createMouseDelegate) {
	        YAHOO.log("Using a " + type + " event requires the event-mouseenter module", "error", "Event");
	        return false;	
		}


        if (!this._events[type]) { // create on the fly

            if (el && this.DOM_EVENTS[type]) {
				Event.on(el, type, function(e, matchedEl) {

					// Supplement IE with target, currentTarget relatedTarget

	                if (e.srcElement && !e.target) { 
	                    e.target = e.srcElement;
	                }

					if ((e.toElement && !e.relatedTarget) || (e.fromElement && !e.relatedTarget)) {
						e.relatedTarget = Event.getRelatedTarget(e);
					}
					
					if (!e.currentTarget) {
						e.currentTarget = el;
					}

					//	Note: matchedEl el is passed back for delegated listeners
		            self.fireEvent(type, e, matchedEl);

		        }, obj, scope);
            }
            this.createEvent(type, {scope: this});
        }
        
        return YAHOO.util.EventProvider.prototype.subscribe.apply(this, arguments); // notify via customEvent
    },


    /**
     * Alias for addListener
     * @method on
     * @param {String} type The name of the event to listen for
     * @param {Function} fn The function call when the event fires
     * @param {Any} obj A variable to pass to the handler
     * @param {Object} scope The object to use for the scope of the handler 
     */
    on: function() {
        return this.addListener.apply(this, arguments);
    },
    
    /**
     * Alias for addListener
     * @method subscribe
     * @param {String} type The name of the event to listen for
     * @param {Function} fn The function call when the event fires
     * @param {Any} obj A variable to pass to the handler
     * @param {Object} scope The object to use for the scope of the handler 
     */
    subscribe: function() {
        return this.addListener.apply(this, arguments);
    },
    
    /**
     * Remove an event listener
     * @method removeListener
     * @param {String} type The name of the event to listen for
     * @param {Function} fn The function call when the event fires
     */
    removeListener: function(type, fn) {
        return this.unsubscribe.apply(this, arguments);
    },
    
    /**
     * Wrapper for Dom method.
     * @method addClass
     * @param {String} className The className to add
     */
    addClass: function(className) {
        Dom.addClass(this.get('element'), className);
    },
    
    /**
     * Wrapper for Dom method.
     * @method getElementsByClassName
     * @param {String} className The className to collect
     * @param {String} tag (optional) The tag to use in
     * conjunction with class name
     * @return {Array} Array of HTMLElements
     */
    getElementsByClassName: function(className, tag) {
        return Dom.getElementsByClassName(className, tag,
                this.get('element') );
    },
    
    /**
     * Wrapper for Dom method.
     * @method hasClass
     * @param {String} className The className to add
     * @return {Boolean} Whether or not the element has the class name
     */
    hasClass: function(className) {
        return Dom.hasClass(this.get('element'), className); 
    },
    
    /**
     * Wrapper for Dom method.
     * @method removeClass
     * @param {String} className The className to remove
     */
    removeClass: function(className) {
        return Dom.removeClass(this.get('element'), className);
    },
    
    /**
     * Wrapper for Dom method.
     * @method replaceClass
     * @param {String} oldClassName The className to replace
     * @param {String} newClassName The className to add
     */
    replaceClass: function(oldClassName, newClassName) {
        return Dom.replaceClass(this.get('element'), 
                oldClassName, newClassName);
    },
    
    /**
     * Wrapper for Dom method.
     * @method setStyle
     * @param {String} property The style property to set
     * @param {String} value The value to apply to the style property
     */
    setStyle: function(property, value) {
        return Dom.setStyle(this.get('element'),  property, value); // TODO: always queuing?
    },
    
    /**
     * Wrapper for Dom method.
     * @method getStyle
     * @param {String} property The style property to retrieve
     * @return {String} The current value of the property
     */
    getStyle: function(property) {
        return Dom.getStyle(this.get('element'),  property);
    },
    
    /**
     * Apply any queued set calls.
     * @method fireQueue
     */
    fireQueue: function() {
        var queue = this._queue;
        for (var i = 0, len = queue.length; i < len; ++i) {
            this[queue[i][0]].apply(this, queue[i][1]);
        }
    },
    
    /**
     * Appends the HTMLElement into either the supplied parentNode.
     * @method appendTo
     * @param {HTMLElement | Element} parentNode The node to append to
     * @param {HTMLElement | Element} before An optional node to insert before
     * @return {HTMLElement} The appended DOM element. 
     */
    appendTo: function(parent, before) {
        parent = (parent.get) ?  parent.get('element') : Dom.get(parent);
        
        this.fireEvent('beforeAppendTo', {
            type: 'beforeAppendTo',
            target: parent
        });
        
        
        before = (before && before.get) ? 
                before.get('element') : Dom.get(before);
        var element = this.get('element');
        
        if (!element) {
            YAHOO.log('appendTo failed: element not available',
                    'error', 'Element');
            return false;
        }
        
        if (!parent) {
            YAHOO.log('appendTo failed: parent not available',
                    'error', 'Element');
            return false;
        }
        
        if (element.parent != parent) {
            if (before) {
                parent.insertBefore(element, before);
            } else {
                parent.appendChild(element);
            }
        }
        
        YAHOO.log(element + 'appended to ' + parent);
        
        this.fireEvent('appendTo', {
            type: 'appendTo',
            target: parent
        });

        return element;
    },
    
    get: function(key) {
        var configs = this._configs || {},
            el = configs.element; // avoid loop due to 'element'

        if (el && !configs[key] && !YAHOO.lang.isUndefined(el.value[key]) ) {
            this._setHTMLAttrConfig(key);
        }

        return AttributeProvider.prototype.get.call(this, key);
    },

    setAttributes: function(map, silent) {
        // set based on configOrder
        var done = {},
            configOrder = this._configOrder;

        // set based on configOrder
        for (var i = 0, len = configOrder.length; i < len; ++i) {
            if (map[configOrder[i]] !== undefined) {
                done[configOrder[i]] = true;
                this.set(configOrder[i], map[configOrder[i]], silent);
            }
        }

        // unconfigured (e.g. Dom attributes)
        for (var att in map) {
            if (map.hasOwnProperty(att) && !done[att]) {
                this.set(att, map[att], silent);
            }
        }
    },

    set: function(key, value, silent) {
        var el = this.get('element');
        if (!el) {
            this._queue[this._queue.length] = ['set', arguments];
            if (this._configs[key]) {
                this._configs[key].value = value; // so "get" works while queueing
            
            }
            return;
        }
        
        // set it on the element if not configured and is an HTML attribute
        if ( !this._configs[key] && !YAHOO.lang.isUndefined(el[key]) ) {
            this._setHTMLAttrConfig(key);
        }

        return AttributeProvider.prototype.set.apply(this, arguments);
    },
    
    setAttributeConfig: function(key, map, init) {
        this._configOrder.push(key);
        AttributeProvider.prototype.setAttributeConfig.apply(this, arguments);
    },

    createEvent: function(type, config) {
        this._events[type] = true;
        return AttributeProvider.prototype.createEvent.apply(this, arguments);
    },
    
    init: function(el, attr) {
        this._initElement(el, attr); 
    },

    destroy: function() {
        var el = this.get('element');
        YAHOO.util.Event.purgeElement(el, true); // purge DOM listeners recursively
        this.unsubscribeAll(); // unsubscribe all custom events

        if (el && el.parentNode) {
            el.parentNode.removeChild(el); // pull from the DOM
        }

        // revert initial configs
        this._queue = [];
        this._events = {};
        this._configs = {};
        this._configOrder = []; 
    },

    _initElement: function(el, attr) {
        this._queue = this._queue || [];
        this._events = this._events || {};
        this._configs = this._configs || {};
        this._configOrder = []; 
        attr = attr || {};
        attr.element = attr.element || el || null;

        var isReady = false;  // to determine when to init HTMLElement and content

        var DOM_EVENTS = Element.DOM_EVENTS;
        this.DOM_EVENTS = this.DOM_EVENTS || {};

        for (var event in DOM_EVENTS) {
            if (DOM_EVENTS.hasOwnProperty(event)) {
                this.DOM_EVENTS[event] = DOM_EVENTS[event];
            }
        }

        if (typeof attr.element === 'string') { // register ID for get() access
            this._setHTMLAttrConfig('id', { value: attr.element });
        }

        if (Dom.get(attr.element)) {
            isReady = true;
            this._initHTMLElement(attr);
            this._initContent(attr);
        }

        YAHOO.util.Event.onAvailable(attr.element, function() {
            if (!isReady) { // otherwise already done
                this._initHTMLElement(attr);
            }

            this.fireEvent('available', { type: 'available', target: Dom.get(attr.element) });  
        }, this, true);
        
        YAHOO.util.Event.onContentReady(attr.element, function() {
            if (!isReady) { // otherwise already done
                this._initContent(attr);
            }
            this.fireEvent('contentReady', { type: 'contentReady', target: Dom.get(attr.element) });  
        }, this, true);
    },

    _initHTMLElement: function(attr) {
        /**
         * The HTMLElement the Element instance refers to.
         * @attribute element
         * @type HTMLElement
         */
        this.setAttributeConfig('element', {
            value: Dom.get(attr.element),
            readOnly: true
         });
    },

    _initContent: function(attr) {
        this.initAttributes(attr);
        this.setAttributes(attr, true);
        this.fireQueue();

    },

    /**
     * Sets the value of the property and fires beforeChange and change events.
     * @private
     * @method _setHTMLAttrConfig
     * @param {YAHOO.util.Element} element The Element instance to
     * register the config to.
     * @param {String} key The name of the config to register
     * @param {Object} map A key-value map of the config's params
     */
    _setHTMLAttrConfig: function(key, map) {
        var el = this.get('element');
        map = map || {};
        map.name = key;

        map.setter = map.setter || this.DEFAULT_HTML_SETTER;
        map.getter = map.getter || this.DEFAULT_HTML_GETTER;

        map.value = map.value || el[key];
        this._configs[key] = new YAHOO.util.Attribute(map, this);
    }
};

/**
 * Fires when the Element's HTMLElement can be retrieved by Id.
 * <p>See: <a href="#addListener">Element.addListener</a></p>
 * <p><strong>Event fields:</strong><br>
 * <code>&lt;String&gt; type</code> available<br>
 * <code>&lt;HTMLElement&gt;
 * target</code> the HTMLElement bound to this Element instance<br>
 * <p><strong>Usage:</strong><br>
 * <code>var handler = function(e) {var target = e.target};<br>
 * myTabs.addListener('available', handler);</code></p>
 * @event available
 */
 
/**
 * Fires when the Element's HTMLElement subtree is rendered.
 * <p>See: <a href="#addListener">Element.addListener</a></p>
 * <p><strong>Event fields:</strong><br>
 * <code>&lt;String&gt; type</code> contentReady<br>
 * <code>&lt;HTMLElement&gt;
 * target</code> the HTMLElement bound to this Element instance<br>
 * <p><strong>Usage:</strong><br>
 * <code>var handler = function(e) {var target = e.target};<br>
 * myTabs.addListener('contentReady', handler);</code></p>
 * @event contentReady
 */

/**
 * Fires before the Element is appended to another Element.
 * <p>See: <a href="#addListener">Element.addListener</a></p>
 * <p><strong>Event fields:</strong><br>
 * <code>&lt;String&gt; type</code> beforeAppendTo<br>
 * <code>&lt;HTMLElement/Element&gt;
 * target</code> the HTMLElement/Element being appended to 
 * <p><strong>Usage:</strong><br>
 * <code>var handler = function(e) {var target = e.target};<br>
 * myTabs.addListener('beforeAppendTo', handler);</code></p>
 * @event beforeAppendTo
 */

/**
 * Fires after the Element is appended to another Element.
 * <p>See: <a href="#addListener">Element.addListener</a></p>
 * <p><strong>Event fields:</strong><br>
 * <code>&lt;String&gt; type</code> appendTo<br>
 * <code>&lt;HTMLElement/Element&gt;
 * target</code> the HTMLElement/Element being appended to 
 * <p><strong>Usage:</strong><br>
 * <code>var handler = function(e) {var target = e.target};<br>
 * myTabs.addListener('appendTo', handler);</code></p>
 * @event appendTo
 */

YAHOO.augment(Element, AttributeProvider);
YAHOO.util.Element = Element;
})();

YAHOO.register("element", YAHOO.util.Element, {version: "2.9.0", build: "2800"});
