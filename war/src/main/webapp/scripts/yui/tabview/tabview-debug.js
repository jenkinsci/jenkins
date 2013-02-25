/*
Copyright (c) 2011, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.com/yui/license.html
version: 2.9.0
*/
(function() {

    /**
     * The tabview module provides a widget for managing content bound to tabs.
     * @module tabview
     * @requires yahoo, dom, event, element
     *
     */

    var Y = YAHOO.util,
        Dom = Y.Dom,
        Event = Y.Event,
        document = window.document,
    
        // STRING CONSTANTS
        ACTIVE = 'active',
        ACTIVE_INDEX = 'activeIndex',
        ACTIVE_TAB = 'activeTab',
        DISABLED = 'disabled',
        CONTENT_EL = 'contentEl',
        ELEMENT = 'element',
    
    /**
     * A widget to control tabbed views.
     * @namespace YAHOO.widget
     * @class TabView
     * @extends YAHOO.util.Element
     * @constructor
     * @param {HTMLElement | String | Object} el(optional) The html 
     * element that represents the TabView, or the attribute object to use. 
     * An element will be created if none provided.
     * @param {Object} attr (optional) A key map of the tabView's 
     * initial attributes.  Ignored if first arg is attributes object.
     */
    TabView = function(el, attr) {
        attr = attr || {};
        if (arguments.length == 1 && !YAHOO.lang.isString(el) && !el.nodeName) {
            attr = el; // treat first arg as attr object
            el = attr.element || null;
        }
        
        if (!el && !attr.element) { // create if we dont have one
            el = this._createTabViewElement(attr);
        }
        TabView.superclass.constructor.call(this, el, attr); 
    };

    YAHOO.extend(TabView, Y.Element, {
        /**
         * The className to add when building from scratch. 
         * @property CLASSNAME
         * @default "navset"
         */
        CLASSNAME: 'yui-navset',
        
        /**
         * The className of the HTMLElement containing the TabView's tab elements
         * to look for when building from existing markup, or to add when building
         * from scratch. 
         * All childNodes of the tab container are treated as Tabs when building
         * from existing markup.
         * @property TAB_PARENT_CLASSNAME
         * @default "nav"
         */
        TAB_PARENT_CLASSNAME: 'yui-nav',
        
        /**
         * The className of the HTMLElement containing the TabView's label elements
         * to look for when building from existing markup, or to add when building
         * from scratch. 
         * All childNodes of the content container are treated as content elements when
         * building from existing markup.
         * @property CONTENT_PARENT_CLASSNAME
         * @default "nav-content"
         */
        CONTENT_PARENT_CLASSNAME: 'yui-content',
        
        _tabParent: null,
        _contentParent: null,
        
        /**
         * Adds a Tab to the TabView instance.  
         * If no index is specified, the tab is added to the end of the tab list.
         * @method addTab
         * @param {YAHOO.widget.Tab} tab A Tab instance to add.
         * @param {Integer} index The position to add the tab. 
         * @return void
         */
        addTab: function(tab, index) {
            var tabs = this.get('tabs'),
                tabParent = this._tabParent,
                contentParent = this._contentParent,
                tabElement = tab.get(ELEMENT),
                contentEl = tab.get(CONTENT_EL),
                activeIndex = this.get(ACTIVE_INDEX),
                before;

            if (!tabs) { // not ready yet
                this._queue[this._queue.length] = ['addTab', arguments];
                return false;
            }
            
            before = this.getTab(index);
            index = (index === undefined) ? tabs.length : index;
            
            tabs.splice(index, 0, tab);

            if (before) {
                tabParent.insertBefore(tabElement, before.get(ELEMENT));
                if (contentEl) {
                    contentParent.appendChild(contentEl);
                }
            } else {
                tabParent.appendChild(tabElement);
                if (contentEl) {
                    contentParent.appendChild(contentEl);
                }
            }

            if ( !tab.get(ACTIVE) ) {
                tab.set('contentVisible', false, true); /* hide if not active */
                if (index <= activeIndex) {
                    this.set(ACTIVE_INDEX, activeIndex + 1, true);
                }  
            } else {
                this.set(ACTIVE_TAB, tab, true);
                this.set('activeIndex', index, true);
            }

            this._initTabEvents(tab);
        },

        _initTabEvents: function(tab) {
            tab.addListener( tab.get('activationEvent'), tab._onActivate, this, tab);
            tab.addListener('activationEventChange', tab._onActivationEventChange, this, tab);
        },

        _removeTabEvents: function(tab) {
            tab.removeListener(tab.get('activationEvent'), tab._onActivate, this, tab);
            tab.removeListener('activationEventChange', tab._onActivationEventChange, this, tab);
        },

        /**
         * Routes childNode events.
         * @method DOMEventHandler
         * @param {event} e The Dom event that is being handled.
         * @return void
         */
        DOMEventHandler: function(e) {
            var target = Event.getTarget(e),
                tabParent = this._tabParent,
                tabs = this.get('tabs'),
                tab,
                tabEl,
                contentEl;

            
            if (Dom.isAncestor(tabParent, target) ) {
                for (var i = 0, len = tabs.length; i < len; i++) {
                    tabEl = tabs[i].get(ELEMENT);
                    contentEl = tabs[i].get(CONTENT_EL);

                    if ( target == tabEl || Dom.isAncestor(tabEl, target) ) {
                        tab = tabs[i];
                        break; // note break
                    }
                } 
                
                if (tab) {
                    tab.fireEvent(e.type, e);
                }
            }
        },
        
        /**
         * Returns the Tab instance at the specified index.
         * @method getTab
         * @param {Integer} index The position of the Tab.
         * @return YAHOO.widget.Tab
         */
        getTab: function(index) {
            return this.get('tabs')[index];
        },
        
        /**
         * Returns the index of given tab.
         * @method getTabIndex
         * @param {YAHOO.widget.Tab} tab The tab whose index will be returned.
         * @return int
         */
        getTabIndex: function(tab) {
            var index = null,
                tabs = this.get('tabs');
            for (var i = 0, len = tabs.length; i < len; ++i) {
                if (tab == tabs[i]) {
                    index = i;
                    break;
                }
            }
            
            return index;
        },
        
        /**
         * Removes the specified Tab from the TabView.
         * @method removeTab
         * @param {YAHOO.widget.Tab} item The Tab instance to be removed.
         * @return void
         */
        removeTab: function(tab) {
            var tabCount = this.get('tabs').length,
                activeIndex = this.get(ACTIVE_INDEX),
                index = this.getTabIndex(tab);

            if ( tab === this.get(ACTIVE_TAB) ) { 
                if (tabCount > 1) { // select another tab
                    if (index + 1 === tabCount) { // if last, activate previous
                        this.set(ACTIVE_INDEX, index - 1);
                    } else { // activate next tab
                        this.set(ACTIVE_INDEX, index + 1);
                    }
                } else { // no more tabs
                    this.set(ACTIVE_TAB, null);
                }
            } else if (index < activeIndex) {
                this.set(ACTIVE_INDEX, activeIndex - 1, true);
            }
            
            this._removeTabEvents(tab);
            this._tabParent.removeChild( tab.get(ELEMENT) );
            this._contentParent.removeChild( tab.get(CONTENT_EL) );
            this._configs.tabs.value.splice(index, 1);

            tab.fireEvent('remove', { type: 'remove', tabview: this });
        },
        
        /**
         * Provides a readable name for the TabView instance.
         * @method toString
         * @return String
         */
        toString: function() {
            var name = this.get('id') || this.get('tagName');
            return "TabView " + name; 
        },
        
        /**
         * The transiton to use when switching between tabs.
         * @method contentTransition
         */
        contentTransition: function(newTab, oldTab) {
            if (newTab) {
                newTab.set('contentVisible', true);
            }
            if (oldTab) {
                oldTab.set('contentVisible', false);
            }
        },
        
        /**
         * setAttributeConfigs TabView specific properties.
         * @method initAttributes
         * @param {Object} attr Hash of initial attributes
         */
        initAttributes: function(attr) {
            TabView.superclass.initAttributes.call(this, attr);
            
            if (!attr.orientation) {
                attr.orientation = 'top';
            }
            
            var el = this.get(ELEMENT);

            if (!this.hasClass(this.CLASSNAME)) {
                this.addClass(this.CLASSNAME);        
            }
            
            /**
             * The Tabs belonging to the TabView instance.
             * @attribute tabs
             * @type Array
             */
            this.setAttributeConfig('tabs', {
                value: [],
                readOnly: true
            });

            /**
             * The container of the tabView's label elements.
             * @property _tabParent
             * @private
             * @type HTMLElement
             */
            this._tabParent = 
                    this.getElementsByClassName(this.TAB_PARENT_CLASSNAME,
                            'ul' )[0] || this._createTabParent();
                
            /**
             * The container of the tabView's content elements.
             * @property _contentParent
             * @type HTMLElement
             * @private
             */
            this._contentParent = 
                    this.getElementsByClassName(this.CONTENT_PARENT_CLASSNAME,
                            'div')[0] ||  this._createContentParent();
            
            /**
             * How the Tabs should be oriented relative to the TabView.
             * Valid orientations are "top", "left", "bottom", and "right"
             * @attribute orientation
             * @type String
             * @default "top"
             */
            this.setAttributeConfig('orientation', {
                value: attr.orientation,
                method: function(value) {
                    var current = this.get('orientation');
                    this.addClass('yui-navset-' + value);
                    
                    if (current != value) {
                        this.removeClass('yui-navset-' + current);
                    }
                    
                    if (value === 'bottom') {
                        this.appendChild(this._tabParent);
                    }
                }
            });
            
            /**
             * The index of the tab currently active.
             * @attribute activeIndex
             * @type Int
             */
            this.setAttributeConfig(ACTIVE_INDEX, {
                value: attr.activeIndex,
                validator: function(value) {
                    var ret = true,
                        tab;
                    if (value) { // cannot activate if disabled
                        tab = this.getTab(value);
                        if (tab && tab.get(DISABLED)) {
                            ret = false;
                        }
                    }
                    return ret;
                }
            });
            
            /**
             * The tab currently active.
             * @attribute activeTab
             * @type YAHOO.widget.Tab
             */
            this.setAttributeConfig(ACTIVE_TAB, {
                value: attr[ACTIVE_TAB],
                method: function(tab) {
                    var activeTab = this.get(ACTIVE_TAB);
                    
                    if (tab) {
                        tab.set(ACTIVE, true);
                    }
                    
                    if (activeTab && activeTab !== tab) {
                        activeTab.set(ACTIVE, false);
                    }
                    
                    if (activeTab && tab !== activeTab) { // no transition if only 1
                        this.contentTransition(tab, activeTab);
                    } else if (tab) {
                        tab.set('contentVisible', true);
                    }
                },
                validator: function(value) {
                    var ret = true;
                    if (value && value.get(DISABLED)) { // cannot activate if disabled
                        ret = false;
                    }
                    return ret;
                }
            });

            this.on('activeTabChange', this._onActiveTabChange);
            this.on('activeIndexChange', this._onActiveIndexChange);

            YAHOO.log('attributes initialized', 'info', 'TabView');
            if ( this._tabParent ) {
                this._initTabs();
            }
            
            // Due to delegation we add all DOM_EVENTS to the TabView container
            // but IE will leak when unsupported events are added, so remove these
            this.DOM_EVENTS.submit = false;
            this.DOM_EVENTS.focus = false;
            this.DOM_EVENTS.blur = false;
            this.DOM_EVENTS.change = false;

            for (var type in this.DOM_EVENTS) {
                if ( YAHOO.lang.hasOwnProperty(this.DOM_EVENTS, type) ) {
                    this.addListener.call(this, type, this.DOMEventHandler);
                }
            }
        },

        /**
         * Removes selected state from the given tab if it is the activeTab
         * @method deselectTab
         * @param {Int} index The tab index to deselect 
         */
        deselectTab: function(index) {
            if (this.getTab(index) === this.get(ACTIVE_TAB)) {
                this.set(ACTIVE_TAB, null);
            }
        },

        /**
         * Makes the tab at the given index the active tab
         * @method selectTab
         * @param {Int} index The tab index to be made active
         */
        selectTab: function(index) {
            this.set(ACTIVE_TAB, this.getTab(index));
        },

        _onActiveTabChange: function(e) {
            var activeIndex = this.get(ACTIVE_INDEX),
                newIndex = this.getTabIndex(e.newValue);

            if (activeIndex !== newIndex) {
                if (!(this.set(ACTIVE_INDEX, newIndex)) ) { // NOTE: setting
                     // revert if activeIndex update fails (cancelled via beforeChange) 
                    this.set(ACTIVE_TAB, e.prevValue);
                }
            }
        },
        
        _onActiveIndexChange: function(e) {
            // no set if called from ActiveTabChange event
            if (e.newValue !== this.getTabIndex(this.get(ACTIVE_TAB))) {
                if (!(this.set(ACTIVE_TAB, this.getTab(e.newValue))) ) { // NOTE: setting
                     // revert if activeTab update fails (cancelled via beforeChange) 
                    this.set(ACTIVE_INDEX, e.prevValue);
                }
            }
        },

        /**
         * Creates Tab instances from a collection of HTMLElements.
         * @method _initTabs
         * @private
         * @return void
         */
        _initTabs: function() {
            var tabs = Dom.getChildren(this._tabParent),
                contentElements = Dom.getChildren(this._contentParent),
                activeIndex = this.get(ACTIVE_INDEX),
                tab,
                attr,
                active;

            for (var i = 0, len = tabs.length; i < len; ++i) {
                attr = {};
                
                if (contentElements[i]) {
                    attr.contentEl = contentElements[i];
                }

                tab = new YAHOO.widget.Tab(tabs[i], attr);
                this.addTab(tab);
                
                if (tab.hasClass(tab.ACTIVE_CLASSNAME) ) {
                    active = tab;
                }
            }
            if (activeIndex != undefined) { // not null or undefined
                this.set(ACTIVE_TAB, this.getTab(activeIndex));
            } else {
                this._configs[ACTIVE_TAB].value = active; // dont invoke method
                this._configs[ACTIVE_INDEX].value = this.getTabIndex(active);
            }
        },

        _createTabViewElement: function(attr) {
            var el = document.createElement('div');

            if ( this.CLASSNAME ) {
                el.className = this.CLASSNAME;
            }
            
            YAHOO.log('TabView Dom created', 'info', 'TabView');
            return el;
        },

        _createTabParent: function(attr) {
            var el = document.createElement('ul');

            if ( this.TAB_PARENT_CLASSNAME ) {
                el.className = this.TAB_PARENT_CLASSNAME;
            }
            
            this.get(ELEMENT).appendChild(el);
            
            return el;
        },
        
        _createContentParent: function(attr) {
            var el = document.createElement('div');

            if ( this.CONTENT_PARENT_CLASSNAME ) {
                el.className = this.CONTENT_PARENT_CLASSNAME;
            }
            
            this.get(ELEMENT).appendChild(el);
            
            return el;
        }
    });
    
    
    YAHOO.widget.TabView = TabView;
})();

(function() {
    var Y = YAHOO.util, 
        Dom = Y.Dom,
        Lang = YAHOO.lang,
    

    // STRING CONSTANTS
        ACTIVE_TAB = 'activeTab',
        LABEL = 'label',
        LABEL_EL = 'labelEl',
        CONTENT = 'content',
        CONTENT_EL = 'contentEl',
        ELEMENT = 'element',
        CACHE_DATA = 'cacheData',
        DATA_SRC = 'dataSrc',
        DATA_LOADED = 'dataLoaded',
        DATA_TIMEOUT = 'dataTimeout',
        LOAD_METHOD = 'loadMethod',
        POST_DATA = 'postData',
        DISABLED = 'disabled',
    
    /**
     * A representation of a Tab's label and content.
     * @namespace YAHOO.widget
     * @class Tab
     * @extends YAHOO.util.Element
     * @constructor
     * @param element {HTMLElement | String} (optional) The html element that 
     * represents the Tab. An element will be created if none provided.
     * @param {Object} properties A key map of initial properties
     */
    Tab = function(el, attr) {
        attr = attr || {};
        if (arguments.length == 1 && !Lang.isString(el) && !el.nodeName) {
            attr = el;
            el = attr.element;
        }

        if (!el && !attr.element) {
            el = this._createTabElement(attr);
        }

        this.loadHandler =  {
            success: function(o) {
                this.set(CONTENT, o.responseText);
            },
            failure: function(o) {
            }
        };
        
        Tab.superclass.constructor.call(this, el, attr);
        
        this.DOM_EVENTS = {}; // delegating to tabView
    };

    YAHOO.extend(Tab, YAHOO.util.Element, {
        /**
         * The default tag name for a Tab's inner element.
         * @property LABEL_INNER_TAGNAME
         * @type String
         * @default "em"
         */
        LABEL_TAGNAME: 'em',
        
        /**
         * The class name applied to active tabs.
         * @property ACTIVE_CLASSNAME
         * @type String
         * @default "selected"
         */
        ACTIVE_CLASSNAME: 'selected',
        
        /**
         * The class name applied to active tabs.
         * @property HIDDEN_CLASSNAME
         * @type String
         * @default "yui-hidden"
         */
        HIDDEN_CLASSNAME: 'yui-hidden',
        
        /**
         * The title applied to active tabs.
         * @property ACTIVE_TITLE
         * @type String
         * @default "active"
         */
        ACTIVE_TITLE: 'active',

        /**
         * The class name applied to disabled tabs.
         * @property DISABLED_CLASSNAME
         * @type String
         * @default "disabled"
         */
        DISABLED_CLASSNAME: DISABLED,
        
        /**
         * The class name applied to dynamic tabs while loading.
         * @property LOADING_CLASSNAME
         * @type String
         * @default "disabled"
         */
        LOADING_CLASSNAME: 'loading',

        /**
         * Provides a reference to the connection request object when data is
         * loaded dynamically.
         * @property dataConnection
         * @type Object
         */
        dataConnection: null,
        
        /**
         * Object containing success and failure callbacks for loading data.
         * @property loadHandler
         * @type object
         */
        loadHandler: null,

        _loading: false,
        
        /**
         * Provides a readable name for the tab.
         * @method toString
         * @return String
         */
        toString: function() {
            var el = this.get(ELEMENT),
                id = el.id || el.tagName;
            return "Tab " + id; 
        },
        
        /**
         * setAttributeConfigs Tab specific properties.
         * @method initAttributes
         * @param {Object} attr Hash of initial attributes
         */
        initAttributes: function(attr) {
            attr = attr || {};
            Tab.superclass.initAttributes.call(this, attr);
            
            /**
             * The event that triggers the tab's activation.
             * @attribute activationEvent
             * @type String
             */
            this.setAttributeConfig('activationEvent', {
                value: attr.activationEvent || 'click'
            });        

            /**
             * The element that contains the tab's label.
             * @attribute labelEl
             * @type HTMLElement
             */
            this.setAttributeConfig(LABEL_EL, {
                value: attr[LABEL_EL] || this._getLabelEl(),
                method: function(value) {
                    value = Dom.get(value);
                    var current = this.get(LABEL_EL);

                    if (current) {
                        if (current == value) {
                            return false; // already set
                        }
                        
                        current.parentNode.replaceChild(value, current);
                        this.set(LABEL, value.innerHTML);
                    }
                } 
            });

            /**
             * The tab's label text (or innerHTML).
             * @attribute label
             * @type String
             */
            this.setAttributeConfig(LABEL, {
                value: attr.label || this._getLabel(),
                method: function(value) {
                    var labelEl = this.get(LABEL_EL);
                    if (!labelEl) { // create if needed
                        this.set(LABEL_EL, this._createLabelEl());
                    }
                    
                    labelEl.innerHTML = value;
                }
            });
            
            /**
             * The HTMLElement that contains the tab's content.
             * @attribute contentEl
             * @type HTMLElement
             */
            this.setAttributeConfig(CONTENT_EL, {
                value: attr[CONTENT_EL] || document.createElement('div'),
                method: function(value) {
                    value = Dom.get(value);
                    var current = this.get(CONTENT_EL);

                    if (current) {
                        if (current === value) {
                            return false; // already set
                        }
                        if (!this.get('selected')) {
                            Dom.addClass(value, this.HIDDEN_CLASSNAME);
                        }
                        current.parentNode.replaceChild(value, current);
                        this.set(CONTENT, value.innerHTML);
                    }
                }
            });
            
            /**
             * The tab's content.
             * @attribute content
             * @type String
             */
            this.setAttributeConfig(CONTENT, {
                value: attr[CONTENT] || this.get(CONTENT_EL).innerHTML,
                method: function(value) {
                    this.get(CONTENT_EL).innerHTML = value;
                }
            });

            /**
             * The tab's data source, used for loading content dynamically.
             * @attribute dataSrc
             * @type String
             */
            this.setAttributeConfig(DATA_SRC, {
                value: attr.dataSrc
            });
            
            /**
             * Whether or not content should be reloaded for every view.
             * @attribute cacheData
             * @type Boolean
             * @default false
             */
            this.setAttributeConfig(CACHE_DATA, {
                value: attr.cacheData || false,
                validator: Lang.isBoolean
            });
            
            /**
             * The method to use for the data request.
             * @attribute loadMethod
             * @type String
             * @default "GET"
             */
            this.setAttributeConfig(LOAD_METHOD, {
                value: attr.loadMethod || 'GET',
                validator: Lang.isString
            });

            /**
             * Whether or not any data has been loaded from the server.
             * @attribute dataLoaded
             * @type Boolean
             */        
            this.setAttributeConfig(DATA_LOADED, {
                value: false,
                validator: Lang.isBoolean,
                writeOnce: true
            });
            
            /**
             * Number if milliseconds before aborting and calling failure handler.
             * @attribute dataTimeout
             * @type Number
             * @default null
             */
            this.setAttributeConfig(DATA_TIMEOUT, {
                value: attr.dataTimeout || null,
                validator: Lang.isNumber
            });
            
            /**
             * Arguments to pass when POST method is used 
             * @attribute postData
             * @default null
             */
            this.setAttributeConfig(POST_DATA, {
                value: attr.postData || null
            });

            /**
             * Whether or not the tab is currently active.
             * If a dataSrc is set for the tab, the content will be loaded from
             * the given source.
             * @attribute active
             * @type Boolean
             */
            this.setAttributeConfig('active', {
                value: attr.active || this.hasClass(this.ACTIVE_CLASSNAME),
                method: function(value) {
                    if (value === true) {
                        this.addClass(this.ACTIVE_CLASSNAME);
                        this.set('title', this.ACTIVE_TITLE);
                    } else {
                        this.removeClass(this.ACTIVE_CLASSNAME);
                        this.set('title', '');
                    }
                },
                validator: function(value) {
                    return Lang.isBoolean(value) && !this.get(DISABLED) ;
                }
            });
            
            /**
             * Whether or not the tab is disabled.
             * @attribute disabled
             * @type Boolean
             */
            this.setAttributeConfig(DISABLED, {
                value: attr.disabled || this.hasClass(this.DISABLED_CLASSNAME),
                method: function(value) {
                    if (value === true) {
                        this.addClass(this.DISABLED_CLASSNAME);
                    } else {
                        this.removeClass(this.DISABLED_CLASSNAME);
                    }
                },
                validator: Lang.isBoolean
            });
            
            /**
             * The href of the tab's anchor element.
             * @attribute href
             * @type String
             * @default '#'
             */
            this.setAttributeConfig('href', {
                value: attr.href ||
                        this.getElementsByTagName('a')[0].getAttribute('href', 2) || '#',
                method: function(value) {
                    this.getElementsByTagName('a')[0].href = value;
                },
                validator: Lang.isString
            });
            
            /**
             * The Whether or not the tab's content is visible.
             * @attribute contentVisible
             * @type Boolean
             * @default false
             */
            this.setAttributeConfig('contentVisible', {
                value: attr.contentVisible,
                method: function(value) {
                    if (value) {
                        Dom.removeClass(this.get(CONTENT_EL), this.HIDDEN_CLASSNAME);
                        
                        if ( this.get(DATA_SRC) ) {
                         // load dynamic content unless already loading or loaded and caching
                            if ( !this._loading && !(this.get(DATA_LOADED) && this.get(CACHE_DATA)) ) {
                                this._dataConnect();
                            }
                        }
                    } else {
                        Dom.addClass(this.get(CONTENT_EL), this.HIDDEN_CLASSNAME);
                    }
                },
                validator: Lang.isBoolean
            });
            YAHOO.log('attributes initialized', 'info', 'Tab');
        },
        
        _dataConnect: function() {
            if (!Y.Connect) {
                YAHOO.log('YAHOO.util.Connect dependency not met',
                        'error', 'Tab');
                return false;
            }

            Dom.addClass(this.get(CONTENT_EL).parentNode, this.LOADING_CLASSNAME);
            this._loading = true; 
            this.dataConnection = Y.Connect.asyncRequest(
                this.get(LOAD_METHOD),
                this.get(DATA_SRC), 
                {
                    success: function(o) {
                        YAHOO.log('content loaded successfully', 'info', 'Tab');
                        this.loadHandler.success.call(this, o);
                        this.set(DATA_LOADED, true);
                        this.dataConnection = null;
                        Dom.removeClass(this.get(CONTENT_EL).parentNode,
                                this.LOADING_CLASSNAME);
                        this._loading = false;
                    },
                    failure: function(o) {
                        YAHOO.log('loading failed: ' + o.statusText, 'error', 'Tab');
                        this.loadHandler.failure.call(this, o);
                        this.dataConnection = null;
                        Dom.removeClass(this.get(CONTENT_EL).parentNode,
                                this.LOADING_CLASSNAME);
                        this._loading = false;
                    },
                    scope: this,
                    timeout: this.get(DATA_TIMEOUT)
                },

                this.get(POST_DATA)
            );
        },
        _createTabElement: function(attr) {
            var el = document.createElement('li'),
                a = document.createElement('a'),
                label = attr.label || null,
                labelEl = attr.labelEl || null;
            
            a.href = attr.href || '#'; // TODO: Use Dom.setAttribute?
            el.appendChild(a);
            
            if (labelEl) { // user supplied labelEl
                if (!label) { // user supplied label
                    label = this._getLabel();
                }
            } else {
                labelEl = this._createLabelEl();
            }
            
            a.appendChild(labelEl);
            
            YAHOO.log('creating Tab Dom', 'info', 'Tab');
            return el;
        },

        _getLabelEl: function() {
            return this.getElementsByTagName(this.LABEL_TAGNAME)[0];
        },

        _createLabelEl: function() {
            var el = document.createElement(this.LABEL_TAGNAME);
            return el;
        },
    
        
        _getLabel: function() {
            var el = this.get(LABEL_EL);
                
                if (!el) {
                    return undefined;
                }
            
            return el.innerHTML;
        },

        _onActivate: function(e, tabview) {
            var tab = this,
                silent = false;

            Y.Event.preventDefault(e);
            if (tab === tabview.get(ACTIVE_TAB)) {
                silent = true; // dont fire activeTabChange if already active
            }
            tabview.set(ACTIVE_TAB, tab, silent);
        },

        _onActivationEventChange: function(e) {
            var tab = this;

            if (e.prevValue != e.newValue) {
                tab.removeListener(e.prevValue, tab._onActivate);
                tab.addListener(e.newValue, tab._onActivate, this, tab);
            }
        }
    });
    
    
    /**
     * Fires when a tab is removed from the tabview
     * @event remove
     * @type CustomEvent
     * @param {Event} An event object with fields for "type" ("remove")
     * and "tabview" (the tabview instance it was removed from) 
     */
    
    YAHOO.widget.Tab = Tab;
})();

YAHOO.register("tabview", YAHOO.widget.TabView, {version: "2.9.0", build: "2800"});
