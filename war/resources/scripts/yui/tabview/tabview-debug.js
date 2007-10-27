/*
Copyright (c) 2007, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.net/yui/license.txt
version: 2.3.1
*/
(function() {

    /**
     * The tabview module provides a widget for managing content bound to tabs.
     * @module tabview
     * @requires yahoo, dom, event, element
     *
     */
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
    YAHOO.widget.TabView = function(el, attr) {
        attr = attr || {};
        if (arguments.length == 1 && !YAHOO.lang.isString(el) && !el.nodeName) {
            attr = el; // treat first arg as attr object
            el = attr.element || null;
        }
        
        if (!el && !attr.element) { // create if we dont have one
            el = _createTabViewElement.call(this, attr);
        }
    	YAHOO.widget.TabView.superclass.constructor.call(this, el, attr); 
    };

    YAHOO.extend(YAHOO.widget.TabView, YAHOO.util.Element);
    
    var proto = YAHOO.widget.TabView.prototype;
    var Dom = YAHOO.util.Dom;
    var Event = YAHOO.util.Event;
    var Tab = YAHOO.widget.Tab;
    
    
    /**
     * The className to add when building from scratch. 
     * @property CLASSNAME
     * @default "navset"
     */
    proto.CLASSNAME = 'yui-navset';
    
    /**
     * The className of the HTMLElement containing the TabView's tab elements
     * to look for when building from existing markup, or to add when building
     * from scratch. 
     * All childNodes of the tab container are treated as Tabs when building
     * from existing markup.
     * @property TAB_PARENT_CLASSNAME
     * @default "nav"
     */
    proto.TAB_PARENT_CLASSNAME = 'yui-nav';
    
    /**
     * The className of the HTMLElement containing the TabView's label elements
     * to look for when building from existing markup, or to add when building
     * from scratch. 
     * All childNodes of the content container are treated as content elements when
     * building from existing markup.
     * @property CONTENT_PARENT_CLASSNAME
     * @default "nav-content"
     */
    proto.CONTENT_PARENT_CLASSNAME = 'yui-content';
    
    proto._tabParent = null;
    proto._contentParent = null; 
    
    /**
     * Adds a Tab to the TabView instance.  
     * If no index is specified, the tab is added to the end of the tab list.
     * @method addTab
     * @param {YAHOO.widget.Tab} tab A Tab instance to add.
     * @param {Integer} index The position to add the tab. 
     * @return void
     */
    proto.addTab = function(tab, index) {
        var tabs = this.get('tabs');
        if (!tabs) { // not ready yet
            this._queue[this._queue.length] = ['addTab', arguments];
            return false;
        }
        
        index = (index === undefined) ? tabs.length : index;
        
        var before = this.getTab(index);
        
        var self = this;
        var el = this.get('element');
        var tabParent = this._tabParent;
        var contentParent = this._contentParent;

        var tabElement = tab.get('element');
        var contentEl = tab.get('contentEl');

        if ( before ) {
            tabParent.insertBefore(tabElement, before.get('element'));
        } else {
            tabParent.appendChild(tabElement);
        }

        if ( contentEl && !Dom.isAncestor(contentParent, contentEl) ) {
            contentParent.appendChild(contentEl);
        }
        
        if ( !tab.get('active') ) {
            tab.set('contentVisible', false, true); /* hide if not active */
        } else {
            this.set('activeTab', tab, true);
            
        }

        var activate = function(e) {
            YAHOO.util.Event.preventDefault(e);
            var silent = false;

            if (this == self.get('activeTab')) {
                silent = true; // dont fire activeTabChange if already active
            }
            self.set('activeTab', this, silent);
        };
        
        tab.addListener( tab.get('activationEvent'), activate);
        
        tab.addListener('activationEventChange', function(e) {
            if (e.prevValue != e.newValue) {
                tab.removeListener(e.prevValue, activate);
                tab.addListener(e.newValue, activate);
            }
        });
        
        tabs.splice(index, 0, tab);
    };

    /**
     * Routes childNode events.
     * @method DOMEventHandler
     * @param {event} e The Dom event that is being handled.
     * @return void
     */
    proto.DOMEventHandler = function(e) {
        var el = this.get('element');
        var target = YAHOO.util.Event.getTarget(e);
        var tabParent = this._tabParent;
        
        if (Dom.isAncestor(tabParent, target) ) {
            var tabEl;
            var tab = null;
            var contentEl;
            var tabs = this.get('tabs');

            for (var i = 0, len = tabs.length; i < len; i++) {
                tabEl = tabs[i].get('element');
                contentEl = tabs[i].get('contentEl');

                if ( target == tabEl || Dom.isAncestor(tabEl, target) ) {
                    tab = tabs[i];
                    break; // note break
                }
            } 
            
            if (tab) {
                tab.fireEvent(e.type, e);
            }
        }
    };
    
    /**
     * Returns the Tab instance at the specified index.
     * @method getTab
     * @param {Integer} index The position of the Tab.
     * @return YAHOO.widget.Tab
     */
    proto.getTab = function(index) {
    	return this.get('tabs')[index];
    };
    
    /**
     * Returns the index of given tab.
     * @method getTabIndex
     * @param {YAHOO.widget.Tab} tab The tab whose index will be returned.
     * @return int
     */
    proto.getTabIndex = function(tab) {
        var index = null;
        var tabs = this.get('tabs');
    	for (var i = 0, len = tabs.length; i < len; ++i) {
            if (tab == tabs[i]) {
                index = i;
                break;
            }
        }
        
        return index;
    };
    
    /**
     * Removes the specified Tab from the TabView.
     * @method removeTab
     * @param {YAHOO.widget.Tab} item The Tab instance to be removed.
     * @return void
     */
    proto.removeTab = function(tab) {
        var tabCount = this.get('tabs').length;

        var index = this.getTabIndex(tab);
        var nextIndex = index + 1;
        if ( tab == this.get('activeTab') ) { // select next tab
            if (tabCount > 1) {
                if (index + 1 == tabCount) {
                    this.set('activeIndex', index - 1);
                } else {
                    this.set('activeIndex', index + 1);
                }
            }
        }
        
        this._tabParent.removeChild( tab.get('element') );
        this._contentParent.removeChild( tab.get('contentEl') );
        this._configs.tabs.value.splice(index, 1);
    	
    };
    
    /**
     * Provides a readable name for the TabView instance.
     * @method toString
     * @return String
     */
    proto.toString = function() {
        var name = this.get('id') || this.get('tagName');
        return "TabView " + name; 
    };
    
    /**
     * The transiton to use when switching between tabs.
     * @method contentTransition
     */
    proto.contentTransition = function(newTab, oldTab) {
        newTab.set('contentVisible', true);
        oldTab.set('contentVisible', false);
    };
    
    /**
     * setAttributeConfigs TabView specific properties.
     * @method initAttributes
     * @param {Object} attr Hash of initial attributes
     */
    proto.initAttributes = function(attr) {
        YAHOO.widget.TabView.superclass.initAttributes.call(this, attr);
        
        if (!attr.orientation) {
            attr.orientation = 'top';
        }
        
        var el = this.get('element');

        if (!YAHOO.util.Dom.hasClass(el, this.CLASSNAME)) {
            YAHOO.util.Dom.addClass(el, this.CLASSNAME);        
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
                        'ul' )[0] || _createTabParent.call(this);
            
        /**
         * The container of the tabView's content elements.
         * @property _contentParent
         * @type HTMLElement
         * @private
         */
        this._contentParent = 
                this.getElementsByClassName(this.CONTENT_PARENT_CLASSNAME,
                        'div')[0] ||  _createContentParent.call(this);
        
        /**
         * How the Tabs should be oriented relative to the TabView.
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
                
                switch(value) {
                    case 'bottom':
                    this.appendChild(this._tabParent);
                    break;
                }
            }
        });
        
        /**
         * The index of the tab currently active.
         * @attribute activeIndex
         * @type Int
         */
        this.setAttributeConfig('activeIndex', {
            value: attr.activeIndex,
            method: function(value) {
                this.set('activeTab', this.getTab(value));
            },
            validator: function(value) {
                return !this.getTab(value).get('disabled'); // cannot activate if disabled
            }
        });
        
        /**
         * The tab currently active.
         * @attribute activeTab
         * @type YAHOO.widget.Tab
         */
        this.setAttributeConfig('activeTab', {
            value: attr.activeTab,
            method: function(tab) {
                var activeTab = this.get('activeTab');
                
                if (tab) {  
                    tab.set('active', true);
                    this._configs['activeIndex'].value = this.getTabIndex(tab); // keep in sync
                }
                
                if (activeTab && activeTab != tab) {
                    activeTab.set('active', false);
                }
                
                if (activeTab && tab != activeTab) { // no transition if only 1
                    this.contentTransition(tab, activeTab);
                } else if (tab) {
                    tab.set('contentVisible', true);
                }
            },
            validator: function(value) {
                return !value.get('disabled'); // cannot activate if disabled
            }
        });

        if ( this._tabParent ) {
            _initTabs.call(this);
        }
        
        // Due to delegation we add all DOM_EVENTS to the TabView container
        // but IE will leak when unsupported events are added, so remove these
        this.DOM_EVENTS.submit = false;
        this.DOM_EVENTS.focus = false;
        this.DOM_EVENTS.blur = false;

        for (var type in this.DOM_EVENTS) {
            if ( YAHOO.lang.hasOwnProperty(this.DOM_EVENTS, type) ) {
                this.addListener.call(this, type, this.DOMEventHandler);
            }
        }
    };
    
    /**
     * Creates Tab instances from a collection of HTMLElements.
     * @method initTabs
     * @private
     * @return void
     */
    var _initTabs = function() {
        var tab,
            attr,
            contentEl;
            
        var el = this.get('element');   
        var tabs = _getChildNodes(this._tabParent);
        var contentElements = _getChildNodes(this._contentParent);

        for (var i = 0, len = tabs.length; i < len; ++i) {
            attr = {};
            
            if (contentElements[i]) {
                attr.contentEl = contentElements[i];
            }

            tab = new YAHOO.widget.Tab(tabs[i], attr);
            this.addTab(tab);
            
            if (tab.hasClass(tab.ACTIVE_CLASSNAME) ) {
                this._configs.activeTab.value = tab; // dont invoke method
                this._configs.activeIndex.value = this.getTabIndex(tab);
            }
        }
    };
    
    var _createTabViewElement = function(attr) {
        var el = document.createElement('div');

        if ( this.CLASSNAME ) {
            el.className = this.CLASSNAME;
        }
        
        return el;
    };
    
    var _createTabParent = function(attr) {
        var el = document.createElement('ul');

        if ( this.TAB_PARENT_CLASSNAME ) {
            el.className = this.TAB_PARENT_CLASSNAME;
        }
        
        this.get('element').appendChild(el);
        
        return el;
    };
    
    var _createContentParent = function(attr) {
        var el = document.createElement('div');

        if ( this.CONTENT_PARENT_CLASSNAME ) {
            el.className = this.CONTENT_PARENT_CLASSNAME;
        }
        
        this.get('element').appendChild(el);
        
        return el;
    };
    
    var _getChildNodes = function(el) {
        var nodes = [];
        var childNodes = el.childNodes;
        
        for (var i = 0, len = childNodes.length; i < len; ++i) {
            if (childNodes[i].nodeType == 1) {
                nodes[nodes.length] = childNodes[i];
            }
        }
        
        return nodes;
    };
})();

(function() {
    var Dom = YAHOO.util.Dom,
        Event = YAHOO.util.Event;
    
    /**
     * A representation of a Tab's label and content.
     * @namespace YAHOO.widget
     * @class Tab
     * @extends YAHOO.util.Element
     * @constructor
     * @param element {HTMLElement | String} (optional) The html element that 
     * represents the TabView. An element will be created if none provided.
     * @param {Object} properties A key map of initial properties
     */
    var Tab = function(el, attr) {
        attr = attr || {};
        if (arguments.length == 1 && !YAHOO.lang.isString(el) && !el.nodeName) {
            attr = el;
            el = attr.element;
        }

        if (!el && !attr.element) {
            el = _createTabElement.call(this, attr);
        }

        this.loadHandler =  {
            success: function(o) {
                this.set('content', o.responseText);
            },
            failure: function(o) {
                YAHOO.log('loading failed: ' + o.statusText,
                        'error', 'Tab');
            }
        };
        
        Tab.superclass.constructor.call(this, el, attr);
        
        this.DOM_EVENTS = {}; // delegating to tabView
    };

    YAHOO.extend(Tab, YAHOO.util.Element);
    var proto = Tab.prototype;
    
    /**
     * The default tag name for a Tab's inner element.
     * @property LABEL_INNER_TAGNAME
     * @type String
     * @default "em"
     */
    proto.LABEL_TAGNAME = 'em';
    
    /**
     * The class name applied to active tabs.
     * @property ACTIVE_CLASSNAME
     * @type String
     * @default "selected"
     */
    proto.ACTIVE_CLASSNAME = 'selected';
    
    /**
     * The class name applied to disabled tabs.
     * @property DISABLED_CLASSNAME
     * @type String
     * @default "disabled"
     */
    proto.DISABLED_CLASSNAME = 'disabled';
    
    /**
     * The class name applied to dynamic tabs while loading.
     * @property LOADING_CLASSNAME
     * @type String
     * @default "disabled"
     */
    proto.LOADING_CLASSNAME = 'loading';

    /**
     * Provides a reference to the connection request object when data is
     * loaded dynamically.
     * @property dataConnection
     * @type Object
     */
    proto.dataConnection = null;
    
    /**
     * Object containing success and failure callbacks for loading data.
     * @property loadHandler
     * @type object
     */
    proto.loadHandler = null;

    proto._loading = false;
    
    /**
     * Provides a readable name for the tab.
     * @method toString
     * @return String
     */
    proto.toString = function() {
        var el = this.get('element');
        var id = el.id || el.tagName;
        return "Tab " + id; 
    };
    
    /**
     * setAttributeConfigs TabView specific properties.
     * @method initAttributes
     * @param {Object} attr Hash of initial attributes
     */
    proto.initAttributes = function(attr) {
        attr = attr || {};
        Tab.superclass.initAttributes.call(this, attr);
        
        var el = this.get('element');
        
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
        this.setAttributeConfig('labelEl', {
            value: attr.labelEl || _getlabelEl.call(this),
            method: function(value) {
                var current = this.get('labelEl');

                if (current) {
                    if (current == value) {
                        return false; // already set
                    }
                    
                    this.replaceChild(value, current);
                } else if (el.firstChild) { // ensure label is firstChild by default
                    this.insertBefore(value, el.firstChild);
                } else {
                    this.appendChild(value);
                }  
            } 
        });

        /**
         * The tab's label text (or innerHTML).
         * @attribute label
         * @type String
         */
        this.setAttributeConfig('label', {
            value: attr.label || _getLabel.call(this),
            method: function(value) {
                var labelEl = this.get('labelEl');
                if (!labelEl) { // create if needed
                    this.set('labelEl', _createlabelEl.call(this));
                }
                
                _setLabel.call(this, value);
            }
        });
        
        /**
         * The HTMLElement that contains the tab's content.
         * @attribute contentEl
         * @type HTMLElement
         */
        this.setAttributeConfig('contentEl', {
            value: attr.contentEl || document.createElement('div'),
            method: function(value) {
                var current = this.get('contentEl');

                if (current) {
                    if (current == value) {
                        return false; // already set
                    }
                    this.replaceChild(value, current);
                }
            }
        });
        
        /**
         * The tab's content.
         * @attribute content
         * @type String
         */
        this.setAttributeConfig('content', {
            value: attr.content,
            method: function(value) {
                this.get('contentEl').innerHTML = value;
            }
        });

        var _dataLoaded = false;
        
        /**
         * The tab's data source, used for loading content dynamically.
         * @attribute dataSrc
         * @type String
         */
        this.setAttributeConfig('dataSrc', {
            value: attr.dataSrc
        });
        
        /**
         * Whether or not content should be reloaded for every view.
         * @attribute cacheData
         * @type Boolean
         * @default false
         */
        this.setAttributeConfig('cacheData', {
            value: attr.cacheData || false,
            validator: YAHOO.lang.isBoolean
        });
        
        /**
         * The method to use for the data request.
         * @attribute loadMethod
         * @type String
         * @default "GET"
         */
        this.setAttributeConfig('loadMethod', {
            value: attr.loadMethod || 'GET',
            validator: YAHOO.lang.isString
        });

        /**
         * Whether or not any data has been loaded from the server.
         * @attribute dataLoaded
         * @type Boolean
         */        
        this.setAttributeConfig('dataLoaded', {
            value: false,
            validator: YAHOO.lang.isBoolean,
            writeOnce: true
        });
        
        /**
         * Number if milliseconds before aborting and calling failure handler.
         * @attribute dataTimeout
         * @type Number
         * @default null
         */
        this.setAttributeConfig('dataTimeout', {
            value: attr.dataTimeout || null,
            validator: YAHOO.lang.isNumber
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
                    this.set('title', 'active');
                } else {
                    this.removeClass(this.ACTIVE_CLASSNAME);
                    this.set('title', '');
                }
            },
            validator: function(value) {
                return YAHOO.lang.isBoolean(value) && !this.get('disabled') ;
            }
        });
        
        /**
         * Whether or not the tab is disabled.
         * @attribute disabled
         * @type Boolean
         */
        this.setAttributeConfig('disabled', {
            value: attr.disabled || this.hasClass(this.DISABLED_CLASSNAME),
            method: function(value) {
                if (value === true) {
                    Dom.addClass(this.get('element'), this.DISABLED_CLASSNAME);
                } else {
                    Dom.removeClass(this.get('element'), this.DISABLED_CLASSNAME);
                }
            },
            validator: YAHOO.lang.isBoolean
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
            validator: YAHOO.lang.isString
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
                    this.get('contentEl').style.display = 'block';
                    
                    if ( this.get('dataSrc') ) {
                     // load dynamic content unless already loading or loaded and caching
                        if ( !this._loading && !(this.get('dataLoaded') && this.get('cacheData')) ) {
                            _dataConnect.call(this);
                        }
                    }
                } else {
                    this.get('contentEl').style.display = 'none';
                }
            },
            validator: YAHOO.lang.isBoolean
        });
    };
    
    var _createTabElement = function(attr) {
        var el = document.createElement('li');
        var a = document.createElement('a');
        
        a.href = attr.href || '#';
        
        el.appendChild(a);
        
        var label = attr.label || null;
        var labelEl = attr.labelEl || null;
        
        if (labelEl) { // user supplied labelEl
            if (!label) { // user supplied label
                label = _getLabel.call(this, labelEl);
            }
        } else {
            labelEl = _createlabelEl.call(this);
        }
        
        a.appendChild(labelEl);
        
        return el;
    };
    
    var _getlabelEl = function() {
        return this.getElementsByTagName(this.LABEL_TAGNAME)[0];
    };
    
    var _createlabelEl = function() {
        var el = document.createElement(this.LABEL_TAGNAME);
        return el;
    };
    
    var _setLabel = function(label) {
        var el = this.get('labelEl');
        el.innerHTML = label;
    };
    
    var _getLabel = function() {
        var label,
            el = this.get('labelEl');
            
            if (!el) {
                return undefined;
            }
        
        return el.innerHTML;
    };
    
    var _dataConnect = function() {
        if (!YAHOO.util.Connect) {
            YAHOO.log('YAHOO.util.Connect dependency not met',
                    'error', 'Tab');
            return false;
        }

        Dom.addClass(this.get('contentEl').parentNode, this.LOADING_CLASSNAME);
        this._loading = true; 
        this.dataConnection = YAHOO.util.Connect.asyncRequest(
            this.get('loadMethod'),
            this.get('dataSrc'), 
            {
                success: function(o) {
                    this.loadHandler.success.call(this, o);
                    this.set('dataLoaded', true);
                    this.dataConnection = null;
                    Dom.removeClass(this.get('contentEl').parentNode,
                            this.LOADING_CLASSNAME);
                    this._loading = false;
                },
                failure: function(o) {
                    this.loadHandler.failure.call(this, o);
                    this.dataConnection = null;
                    Dom.removeClass(this.get('contentEl').parentNode,
                            this.LOADING_CLASSNAME);
                    this._loading = false;
                },
                scope: this,
                timeout: this.get('dataTimeout')
            }
        );
    };
    
    YAHOO.widget.Tab = Tab;
})();

YAHOO.register("tabview", YAHOO.widget.TabView, {version: "2.3.1", build: "541"});
