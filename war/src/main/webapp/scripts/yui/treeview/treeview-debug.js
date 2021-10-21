/*
Copyright (c) 2011, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.com/yui/license.html
version: 2.9.0
*/
(function () {
    var Dom = YAHOO.util.Dom,
        Event = YAHOO.util.Event,
        Lang = YAHOO.lang,
        Widget = YAHOO.widget;



/**
 * The treeview widget is a generic tree building tool.
 * @module treeview
 * @title TreeView Widget
 * @requires yahoo, dom, event
 * @optional animation, json, calendar
 * @namespace YAHOO.widget
 */

/**
 * Contains the tree view state data and the root node.
 *
 * @class TreeView
 * @uses YAHOO.util.EventProvider
 * @constructor
 * @param {string|HTMLElement} id The id of the element, or the element itself that the tree will be inserted into.
 *        Existing markup in this element, if valid, will be used to build the tree
 * @param {Array|Object|String}  oConfig (optional)  If present, it will be used to build the tree via method <a href="#method_buildTreeFromObject">buildTreeFromObject</a>
 *
 */
YAHOO.widget.TreeView = function(id, oConfig) {
    if (id) { this.init(id); }
    if (oConfig) {
        this.buildTreeFromObject(oConfig);
    } else if (Lang.trim(this._el.innerHTML)) {
        this.buildTreeFromMarkup(id);
    }
};

var TV = Widget.TreeView;

TV.prototype = {

    /**
     * The id of tree container element
     * @property id
     * @type String
     */
    id: null,

    /**
     * The host element for this tree
     * @property _el
     * @private
     * @type HTMLelement
     */
    _el: null,

     /**
     * Flat collection of all nodes in this tree.  This is a sparse
     * array, so the length property can't be relied upon for a
     * node count for the tree.
     * @property _nodes
     * @type Node[]
     * @private
     */
    _nodes: null,

    /**
     * We lock the tree control while waiting for the dynamic loader to return
     * @property locked
     * @type boolean
     */
    locked: false,

    /**
     * The animation to use for expanding children, if any
     * @property _expandAnim
     * @type string
     * @private
     */
    _expandAnim: null,

    /**
     * The animation to use for collapsing children, if any
     * @property _collapseAnim
     * @type string
     * @private
     */
    _collapseAnim: null,

    /**
     * The current number of animations that are executing
     * @property _animCount
     * @type int
     * @private
     */
    _animCount: 0,

    /**
     * The maximum number of animations to run at one time.
     * @property maxAnim
     * @type int
     */
    maxAnim: 2,

    /**
     * Whether there is any subscriber to dblClickEvent
     * @property _hasDblClickSubscriber
     * @type boolean
     * @private
     */
    _hasDblClickSubscriber: false,

    /**
     * Stores the timer used to check for double clicks
     * @property _dblClickTimer
     * @type window.timer object
     * @private
     */
    _dblClickTimer: null,

  /**
     * A reference to the Node currently having the focus or null if none.
     * @property currentFocus
     * @type YAHOO.widget.Node
     */
    currentFocus: null,

    /**
    * If true, only one Node can be highlighted at a time
    * @property singleNodeHighlight
    * @type boolean
    * @default false
    */

    singleNodeHighlight: false,

    /**
    * A reference to the Node that is currently highlighted.
    * It is only meaningful if singleNodeHighlight is enabled
    * @property _currentlyHighlighted
    * @type YAHOO.widget.Node
    * @default null
    * @private
    */

    _currentlyHighlighted: null,

    /**
     * Sets up the animation for expanding children
     * @method setExpandAnim
     * @param {string} type the type of animation (acceptable values defined
     * in YAHOO.widget.TVAnim)
     */
    setExpandAnim: function(type) {
        this._expandAnim = (Widget.TVAnim.isValid(type)) ? type : null;
    },

    /**
     * Sets up the animation for collapsing children
     * @method setCollapseAnim
     * @param {string} type of animation (acceptable values defined in
     * YAHOO.widget.TVAnim)
     */
    setCollapseAnim: function(type) {
        this._collapseAnim = (Widget.TVAnim.isValid(type)) ? type : null;
    },

    /**
     * Perform the expand animation if configured, or just show the
     * element if not configured or too many animations are in progress
     * @method animateExpand
     * @param el {HTMLElement} the element to animate
     * @param node {YAHOO.util.Node} the node that was expanded
     * @return {boolean} true if animation could be invoked, false otherwise
     */
    animateExpand: function(el, node) {
        this.logger.log("animating expand");

        if (this._expandAnim && this._animCount < this.maxAnim) {
            // this.locked = true;
            var tree = this;
            var a = Widget.TVAnim.getAnim(this._expandAnim, el,
                            function() { tree.expandComplete(node); });
            if (a) {
                ++this._animCount;
                this.fireEvent("animStart", {
                        "node": node,
                        "type": "expand"
                    });
                a.animate();
            }

            return true;
        }

        return false;
    },

    /**
     * Perform the collapse animation if configured, or just show the
     * element if not configured or too many animations are in progress
     * @method animateCollapse
     * @param el {HTMLElement} the element to animate
     * @param node {YAHOO.util.Node} the node that was expanded
     * @return {boolean} true if animation could be invoked, false otherwise
     */
    animateCollapse: function(el, node) {
        this.logger.log("animating collapse");

        if (this._collapseAnim && this._animCount < this.maxAnim) {
            // this.locked = true;
            var tree = this;
            var a = Widget.TVAnim.getAnim(this._collapseAnim, el,
                            function() { tree.collapseComplete(node); });
            if (a) {
                ++this._animCount;
                this.fireEvent("animStart", {
                        "node": node,
                        "type": "collapse"
                    });
                a.animate();
            }

            return true;
        }

        return false;
    },

    /**
     * Function executed when the expand animation completes
     * @method expandComplete
     */
    expandComplete: function(node) {
        this.logger.log("expand complete: " + this.id);
        --this._animCount;
        this.fireEvent("animComplete", {
                "node": node,
                "type": "expand"
            });
        // this.locked = false;
    },

    /**
     * Function executed when the collapse animation completes
     * @method collapseComplete
     */
    collapseComplete: function(node) {
        this.logger.log("collapse complete: " + this.id);
        --this._animCount;
        this.fireEvent("animComplete", {
                "node": node,
                "type": "collapse"
            });
        // this.locked = false;
    },

    /**
     * Initializes the tree
     * @method init
     * @parm {string|HTMLElement} id the id of the element that will hold the tree
     * @private
     */
    init: function(id) {
        this._el = Dom.get(id);
        this.id = Dom.generateId(this._el,"yui-tv-auto-id-");

    /**
         * When animation is enabled, this event fires when the animation
         * starts
         * @event animStart
         * @type CustomEvent
         * @param {YAHOO.widget.Node} oArgs.node the node that is expanding/collapsing
         * @param {String} oArgs.type the type of animation ("expand" or "collapse")
         */
        this.createEvent("animStart", this);

        /**
         * When animation is enabled, this event fires when the animation
         * completes
         * @event animComplete
         * @type CustomEvent
         * @param {YAHOO.widget.Node} oArgs.node the node that is expanding/collapsing
         * @param {String} oArgs.type the type of animation ("expand" or "collapse")
         */
        this.createEvent("animComplete", this);

        /**
         * Fires when a node is going to be collapsed.  Return false to stop
         * the collapse.
         * @event collapse
         * @type CustomEvent
         * @param {YAHOO.widget.Node} node the node that is collapsing
         */
        this.createEvent("collapse", this);

        /**
         * Fires after a node is successfully collapsed.  This event will not fire
         * if the "collapse" event was cancelled.
         * @event collapseComplete
         * @type CustomEvent
         * @param {YAHOO.widget.Node} node the node that was collapsed
         */
        this.createEvent("collapseComplete", this);

        /**
         * Fires when a node is going to be expanded.  Return false to stop
         * the collapse.
         * @event expand
         * @type CustomEvent
         * @param {YAHOO.widget.Node} node the node that is expanding
         */
        this.createEvent("expand", this);

        /**
         * Fires after a node is successfully expanded.  This event will not fire
         * if the "expand" event was cancelled.
         * @event expandComplete
         * @type CustomEvent
         * @param {YAHOO.widget.Node} node the node that was expanded
         */
        this.createEvent("expandComplete", this);

    /**
         * Fires when the Enter key is pressed on a node that has the focus
         * @event enterKeyPressed
         * @type CustomEvent
         * @param {YAHOO.widget.Node} node the node that has the focus
         */
        this.createEvent("enterKeyPressed", this);

    /**
         * Fires when the label in a TextNode or MenuNode or content in an HTMLNode receives a Click.
    * The listener may return false to cancel toggling and focusing on the node.
         * @event clickEvent
         * @type CustomEvent
         * @param oArgs.event  {HTMLEvent} The event object
         * @param oArgs.node {YAHOO.widget.Node} node the node that was clicked
         */
        this.createEvent("clickEvent", this);

    /**
         * Fires when the focus receives the focus, when it changes from a Node
    * to another Node or when it is completely lost (blurred)
         * @event focusChanged
         * @type CustomEvent
         * @param oArgs.oldNode  {YAHOO.widget.Node} Node that had the focus or null if none
         * @param oArgs.newNode {YAHOO.widget.Node} Node that receives the focus or null if none
         */

        this.createEvent('focusChanged',this);

    /**
         * Fires when the label in a TextNode or MenuNode or content in an HTMLNode receives a double Click
         * @event dblClickEvent
         * @type CustomEvent
         * @param oArgs.event  {HTMLEvent} The event object
         * @param oArgs.node {YAHOO.widget.Node} node the node that was clicked
         */
        var self = this;
        this.createEvent("dblClickEvent", {
            scope:this,
            onSubscribeCallback: function() {
                self._hasDblClickSubscriber = true;
            }
        });

    /**
         * Custom event that is fired when the text node label is clicked.
         *  The node clicked is  provided as an argument
         *
         * @event labelClick
         * @type CustomEvent
         * @param {YAHOO.widget.Node} node the node clicked
    * @deprecated use clickEvent or dblClickEvent
         */
        this.createEvent("labelClick", this);

    /**
     * Custom event fired when the highlight of a node changes.
     * The node that triggered the change is provided as an argument:
     * The status of the highlight can be checked in
     * <a href="YAHOO.widget.Node.html#property_highlightState">nodeRef.highlightState</a>.
     * Depending on <a href="YAHOO.widget.Node.html#property_propagateHighlight">nodeRef.propagateHighlight</a>, other nodes might have changed
     * @event highlightEvent
     * @type CustomEvent
     * @param node {YAHOO.widget.Node} the node that started the change in highlighting state
    */
        this.createEvent("highlightEvent",this);


        this._nodes = [];

        // store a global reference
        TV.trees[this.id] = this;

        // Set up the root node
        this.root = new Widget.RootNode(this);

        var LW = Widget.LogWriter;

        this.logger = (LW) ? new LW(this.toString()) : YAHOO;

        this.logger.log("tree init: " + this.id);

        if (this._initEditor) {
            this._initEditor();
        }

        // YAHOO.util.Event.onContentReady(this.id, this.handleAvailable, this, true);
        // YAHOO.util.Event.on(this.id, "click", this.handleClick, this, true);
    },

    //handleAvailable: function() {
        //var Event = YAHOO.util.Event;
        //Event.on(this.id,
    //},
 /**
     * Builds the TreeView from an object.
     * This is the method called by the constructor to build the tree when it has a second argument.
     *  A tree can be described by an array of objects, each object corresponding to a node.
     *  Node descriptions may contain values for any property of a node plus the following extra properties: <ul>
     * <li>type:  can be one of the following:<ul>
     *    <li> A shortname for a node type (<code>'text','menu','html'</code>) </li>
     *    <li>The name of a Node class under YAHOO.widget (<code>'TextNode', 'MenuNode', 'DateNode'</code>, etc) </li>
     *    <li>a reference to an actual class: <code>YAHOO.widget.DateNode</code></li>
     * </ul></li>
     * <li>children: an array containing further node definitions</li></ul>
     * A string instead of an object will produce a node of type 'text' with the given string as its label.
     * @method buildTreeFromObject
     * @param  oConfig {Array|Object|String}  array containing a full description of the tree.
     *        An object or a string will be turned into an array with the given object or string as its only element.
     *
     */
    buildTreeFromObject: function (oConfig) {
        var logger = this.logger;
        logger.log('Building tree from object');
        var build = function (parent, oConfig) {
            var i, item, node, children, type, NodeType, ThisType;
            for (i = 0; i < oConfig.length; i++) {
                item = oConfig[i];
                if (Lang.isString(item)) {
                    node = new Widget.TextNode(item, parent);
                } else if (Lang.isObject(item)) {
                    children = item.children;
                    delete item.children;
                    type = item.type || 'text';
                    delete item.type;
                    switch (Lang.isString(type) && type.toLowerCase()) {
                        case 'text':
                            node = new Widget.TextNode(item, parent);
                            break;
                        case 'menu':
                            node = new Widget.MenuNode(item, parent);
                            break;
                        case 'html':
                            node = new Widget.HTMLNode(item, parent);
                            break;
                        default:
                            if (Lang.isString(type)) {
                                NodeType = Widget[type];
                            } else {
                                NodeType = type;
                            }
                            if (Lang.isObject(NodeType)) {
                                for (ThisType = NodeType; ThisType && ThisType !== Widget.Node; ThisType = ThisType.superclass.constructor) {}
                                if (ThisType) {
                                    node = new NodeType(item, parent);
                                } else {
                                    logger.log('Invalid type in node definition: ' + type,'error');
                                }
                            } else {
                                logger.log('Invalid type in node definition: ' + type,'error');
                            }
                    }
                    if (children) {
                        build(node,children);
                    }
                } else {
                    logger.log('Invalid node definition','error');
                }
            }
        };
        if (!Lang.isArray(oConfig)) {
            oConfig = [oConfig];
        }


        build(this.root,oConfig);
    },
/**
     * Builds the TreeView from existing markup.   Markup should consist of &lt;UL&gt; or &lt;OL&gt; elements containing &lt;LI&gt; elements.
     * Each &lt;LI&gt; can have one element used as label and a second optional element which is to be a &lt;UL&gt; or &lt;OL&gt;
     * containing nested nodes.
     * Depending on what the first element of the &lt;LI&gt; element is, the following Nodes will be created: <ul>
     *           <li>plain text:  a regular TextNode</li>
     *           <li>anchor &lt;A&gt;: a TextNode with its <code>href</code> and <code>target</code> taken from the anchor</li>
     *           <li>anything else: an HTMLNode</li></ul>
     * Only the first  outermost (un-)ordered list in the markup and its children will be parsed.
     * Nodes will be collapsed unless  an  &lt;LI&gt;  tag has a className called 'expanded'.
     * All other className attributes will be copied over to the Node className property.
     * If the &lt;LI&gt; element contains an attribute called <code>yuiConfig</code>, its contents should be a JSON-encoded object
     * as the one used in method <a href="#method_buildTreeFromObject">buildTreeFromObject</a>.
     * @method buildTreeFromMarkup
     * @param  id {string|HTMLElement} The id of the element that contains the markup or a reference to it.
     */
    buildTreeFromMarkup: function (id) {
        this.logger.log('Building tree from existing markup');
        var build = function (markup) {
            var el, child, branch = [], config = {}, label, yuiConfig;
            // Dom's getFirstChild and getNextSibling skip over text elements
            for (el = Dom.getFirstChild(markup); el; el = Dom.getNextSibling(el)) {
                switch (el.tagName.toUpperCase()) {
                    case 'LI':
                        label = '';
                        config = {
                            expanded: Dom.hasClass(el,'expanded'),
                            title: el.title || el.alt || null,
                            className: Lang.trim(el.className.replace(/\bexpanded\b/,'')) || null
                        };
                        // I cannot skip over text elements here because I want them for labels
                        child = el.firstChild;
                        if (child.nodeType == 3) {
                            // nodes with only whitespace, tabs and new lines don't count, they are probably just formatting.
                            label = Lang.trim(child.nodeValue.replace(/[\n\t\r]*/g,''));
                            if (label) {
                                config.type = 'text';
                                config.label = label;
                            } else {
                                child = Dom.getNextSibling(child);
                            }
                        }
                        if (!label) {
                            if (child.tagName.toUpperCase() == 'A') {
                                config.type = 'text';
                                config.label = child.innerHTML;
                                config.href = child.href;
                                config.target = child.target;
                                config.title = child.title || child.alt || config.title;
                            } else {
                                config.type = 'html';
                                var d = document.createElement('div');
                                d.appendChild(child.cloneNode(true));
                                config.html = d.innerHTML;
                                config.hasIcon = true;
                            }
                        }
                        // see if after the label it has a further list which will become children of this node.
                        child = Dom.getNextSibling(child);
                        switch (child && child.tagName.toUpperCase()) {
                            case 'UL':
                            case 'OL':
                                config.children = build(child);
                                break;
                        }
                        // if there are further elements or text, it will be ignored.

                        if (YAHOO.lang.JSON) {
                            yuiConfig = el.getAttribute('yuiConfig');
                            if (yuiConfig) {
                                yuiConfig = YAHOO.lang.JSON.parse(yuiConfig);
                                config = YAHOO.lang.merge(config,yuiConfig);
                            }
                        }

                        branch.push(config);
                        break;
                    case 'UL':
                    case 'OL':
                        this.logger.log('ULs or OLs can only contain LI elements, not other UL or OL.  This will not work in some browsers','error');
                        config = {
                            type: 'text',
                            label: '',
                            children: build(child)
                        };
                        branch.push(config);
                        break;
                }
            }
            return branch;
        };

        var markup = Dom.getChildrenBy(Dom.get(id),function (el) {
            var tag = el.tagName.toUpperCase();
            return  tag == 'UL' || tag == 'OL';
        });
        if (markup.length) {
            this.buildTreeFromObject(build(markup[0]));
        } else {
            this.logger.log('Markup contains no UL or OL elements','warn');
        }
    },
  /**
     * Returns the TD element where the event has occurred
     * @method _getEventTargetTdEl
     * @private
     */
    _getEventTargetTdEl: function (ev) {
        var target = Event.getTarget(ev);
        // go up looking for a TD with a className with a ygtv prefix
        while (target && !(target.tagName.toUpperCase() == 'TD' && Dom.hasClass(target.parentNode,'ygtvrow'))) {
            target = Dom.getAncestorByTagName(target,'td');
        }
        if (Lang.isNull(target)) { return null; }
        // If it is a spacer cell, do nothing
        if (/\bygtv(blank)?depthcell/.test(target.className)) { return null;}
        // If it has an id, search for the node number and see if it belongs to a node in this tree.
        if (target.id) {
            var m = target.id.match(/\bygtv([^\d]*)(.*)/);
            if (m && m[2] && this._nodes[m[2]]) {
                return target;
            }
        }
        return null;
    },
  /**
     * Event listener for click events
     * @method _onClickEvent
     * @private
     */
    _onClickEvent: function (ev) {
        var self = this,
            td = this._getEventTargetTdEl(ev),
            node,
            target,
            toggle = function (force) {
                node.focus();
                if (force || !node.href) {
                    node.toggle();
                    try {
                        Event.preventDefault(ev);
                    } catch (e) {
                        // @TODO
                        // For some reason IE8 is providing an event object with
                        // most of the fields missing, but only when clicking on
                        // the node's label, and only when working with inline
                        // editing.  This generates a "Member not found" error
                        // in that browser.  Determine if this is a browser
                        // bug, or a problem with this code.  Already checked to
                        // see if the problem has to do with access the event
                        // in the outer scope, and that isn't the problem.
                        // Maybe the markup for inline editing is broken.
                    }
                }
            };

        if (!td) {
            return;
        }

        node = this.getNodeByElement(td);
        if (!node) {
            return;
        }

        // exception to handle deprecated event labelClick
        // @TODO take another look at this deprecation.  It is common for people to
        // only be interested in the label click, so why make them have to test
        // the node type to figure out whether the click was on the label?
        target = Event.getTarget(ev);
        if (Dom.hasClass(target, node.labelStyle) || Dom.getAncestorByClassName(target,node.labelStyle)) {
            this.logger.log("onLabelClick " + node.label);
            this.fireEvent('labelClick',node);
        }
        // http://yuilibrary.com/projects/yui2/ticket/2528946
        // Ensures that any open editor is closed.
        // Since the editor is in a separate source which might not be included,
        // we first need to ensure we have the _closeEditor method available
        if (this._closeEditor) { this._closeEditor(false); }

        //  If it is a toggle cell, toggle
        if (/\bygtv[tl][mp]h?h?/.test(td.className)) {
            toggle(true);
        } else {
            if (this._dblClickTimer) {
                window.clearTimeout(this._dblClickTimer);
                this._dblClickTimer = null;
            } else {
                if (this._hasDblClickSubscriber) {
                    this._dblClickTimer = window.setTimeout(function () {
                        self._dblClickTimer = null;
                        if (self.fireEvent('clickEvent', {event:ev,node:node}) !== false) {
                            toggle();
                        }
                    }, 200);
                } else {
                    if (self.fireEvent('clickEvent', {event:ev,node:node}) !== false) {
                        toggle();
                    }
                }
            }
        }
    },

  /**
     * Event listener for double-click events
     * @method _onDblClickEvent
     * @private
     */
    _onDblClickEvent: function (ev) {
        if (!this._hasDblClickSubscriber) { return; }
        var td = this._getEventTargetTdEl(ev);
        if (!td) {return;}

        if (!(/\bygtv[tl][mp]h?h?/.test(td.className))) {
            this.fireEvent('dblClickEvent', {event:ev, node:this.getNodeByElement(td)});
            if (this._dblClickTimer) {
                window.clearTimeout(this._dblClickTimer);
                this._dblClickTimer = null;
            }
        }
    },
  /**
     * Event listener for mouse over events
     * @method _onMouseOverEvent
     * @private
     */
    _onMouseOverEvent:function (ev) {
        var target;
        if ((target = this._getEventTargetTdEl(ev)) && (target = this.getNodeByElement(target)) && (target = target.getToggleEl())) {
            target.className = target.className.replace(/\bygtv([lt])([mp])\b/gi,'ygtv$1$2h');
        }
    },
  /**
     * Event listener for mouse out events
     * @method _onMouseOutEvent
     * @private
     */
    _onMouseOutEvent: function (ev) {
        var target;
        if ((target = this._getEventTargetTdEl(ev)) && (target = this.getNodeByElement(target)) && (target = target.getToggleEl())) {
            target.className = target.className.replace(/\bygtv([lt])([mp])h\b/gi,'ygtv$1$2');
        }
    },
  /**
     * Event listener for key down events
     * @method _onKeyDownEvent
     * @private
     */
    _onKeyDownEvent: function (ev) {
        var target = Event.getTarget(ev),
            node = this.getNodeByElement(target),
            newNode = node,
            KEY = YAHOO.util.KeyListener.KEY;

        switch(ev.keyCode) {
            case KEY.UP:
                this.logger.log('UP');
                do {
                    if (newNode.previousSibling) {
                        newNode = newNode.previousSibling;
                    } else {
                        newNode = newNode.parent;
                    }
                } while (newNode && !newNode._canHaveFocus());
                if (newNode) { newNode.focus(); }
                Event.preventDefault(ev);
                break;
            case KEY.DOWN:
                this.logger.log('DOWN');
                do {
                    if (newNode.nextSibling) {
                        newNode = newNode.nextSibling;
                    } else {
                        newNode.expand();
                        newNode = (newNode.children.length || null) && newNode.children[0];
                    }
                } while (newNode && !newNode._canHaveFocus);
                if (newNode) { newNode.focus();}
                Event.preventDefault(ev);
                break;
            case KEY.LEFT:
                this.logger.log('LEFT');
                do {
                    if (newNode.parent) {
                        newNode = newNode.parent;
                    } else {
                        newNode = newNode.previousSibling;
                    }
                } while (newNode && !newNode._canHaveFocus());
                if (newNode) { newNode.focus();}
                Event.preventDefault(ev);
                break;
            case KEY.RIGHT:
                this.logger.log('RIGHT');
                var self = this,
                    moveFocusRight,
                    focusOnExpand = function (newNode) {
                        self.unsubscribe('expandComplete',focusOnExpand);
                        moveFocusRight(newNode);
                    };
                moveFocusRight = function (newNode) {
                    do {
                        if (newNode.isDynamic() && !newNode.childrenRendered) {
                            self.subscribe('expandComplete',focusOnExpand);
                            newNode.expand();
                            newNode = null;
                            break;
                        } else {
                            newNode.expand();
                            if (newNode.children.length) {
                                newNode = newNode.children[0];
                            } else {
                                newNode = newNode.nextSibling;
                            }
                        }
                    } while (newNode && !newNode._canHaveFocus());
                    if (newNode) { newNode.focus();}
                };

                moveFocusRight(newNode);
                Event.preventDefault(ev);
                break;
            case KEY.ENTER:
                this.logger.log('ENTER: ' + newNode.href);
                if (node.href) {
                    if (node.target) {
                        window.open(node.href,node.target);
                    } else {
                        window.location(node.href);
                    }
                } else {
                    node.toggle();
                }
                this.fireEvent('enterKeyPressed',node);
                Event.preventDefault(ev);
                break;
            case KEY.HOME:
                this.logger.log('HOME');
                newNode = this.getRoot();
                if (newNode.children.length) {newNode = newNode.children[0];}
                if (newNode._canHaveFocus()) { newNode.focus(); }
                Event.preventDefault(ev);
                break;
            case KEY.END:
                this.logger.log('END');
                newNode = newNode.parent.children;
                newNode = newNode[newNode.length -1];
                if (newNode._canHaveFocus()) { newNode.focus(); }
                Event.preventDefault(ev);
                break;
            // case KEY.PAGE_UP:
                // this.logger.log('PAGE_UP');
                // break;
            // case KEY.PAGE_DOWN:
                // this.logger.log('PAGE_DOWN');
                // break;
            case 107:  // plus key
            case 187:  // plus key
                if (ev.shiftKey) {
                    this.logger.log('Shift-PLUS');
                    node.parent.expandAll();
                } else {
                    this.logger.log('PLUS');
                    node.expand();
                }
                break;
            case 109: // minus key
            case 189: // minus key
                if (ev.shiftKey) {
                    this.logger.log('Shift-MINUS');
                    node.parent.collapseAll();
                } else {
                    this.logger.log('MINUS');
                    node.collapse();
                }
                break;
            default:
                break;
        }
    },
    /**
     * Renders the tree boilerplate and visible nodes
     * @method render
     */
    render: function() {
        var html = this.root.getHtml(),
            el = this.getEl();
        el.innerHTML = html;
        if (!this._hasEvents) {
            Event.on(el, 'click', this._onClickEvent, this, true);
            Event.on(el, 'dblclick', this._onDblClickEvent, this, true);
            Event.on(el, 'mouseover', this._onMouseOverEvent, this, true);
            Event.on(el, 'mouseout', this._onMouseOutEvent, this, true);
            Event.on(el, 'keydown', this._onKeyDownEvent, this, true);
        }
        this._hasEvents = true;
    },

  /**
     * Returns the tree's host element
     * @method getEl
     * @return {HTMLElement} the host element
     */
    getEl: function() {
        if (! this._el) {
            this._el = Dom.get(this.id);
        }
        return this._el;
    },

    /**
     * Nodes register themselves with the tree instance when they are created.
     * @method regNode
     * @param node {Node} the node to register
     * @private
     */
    regNode: function(node) {
        this._nodes[node.index] = node;
    },

    /**
     * Returns the root node of this tree
     * @method getRoot
     * @return {Node} the root node
     */
    getRoot: function() {
        return this.root;
    },

    /**
     * Configures this tree to dynamically load all child data
     * @method setDynamicLoad
     * @param {function} fnDataLoader the function that will be called to get the data
     * @param iconMode {int} configures the icon that is displayed when a dynamic
     * load node is expanded the first time without children.  By default, the
     * "collapse" icon will be used.  If set to 1, the leaf node icon will be
     * displayed.
     */
    setDynamicLoad: function(fnDataLoader, iconMode) {
        this.root.setDynamicLoad(fnDataLoader, iconMode);
    },

    /**
     * Expands all child nodes.  Note: this conflicts with the "multiExpand"
     * node property.  If expand all is called in a tree with nodes that
     * do not allow multiple siblings to be displayed, only the last sibling
     * will be expanded.
     * @method expandAll
     */
    expandAll: function() {
        if (!this.locked) {
            this.root.expandAll();
        }
    },

    /**
     * Collapses all expanded child nodes in the entire tree.
     * @method collapseAll
     */
    collapseAll: function() {
        if (!this.locked) {
            this.root.collapseAll();
        }
    },

    /**
     * Returns a node in the tree that has the specified index (this index
     * is created internally, so this function probably will only be used
     * in html generated for a given node.)
     * @method getNodeByIndex
     * @param {int} nodeIndex the index of the node wanted
     * @return {Node} the node with index=nodeIndex, null if no match
     */
    getNodeByIndex: function(nodeIndex) {
        var n = this._nodes[nodeIndex];
        return (n) ? n : null;
    },

    /**
     * Returns a node that has a matching property and value in the data
     * object that was passed into its constructor.
     * @method getNodeByProperty
     * @param {object} property the property to search (usually a string)
     * @param {object} value the value we want to find (usuall an int or string)
     * @return {Node} the matching node, null if no match
     */
    getNodeByProperty: function(property, value) {
        for (var i in this._nodes) {
            if (this._nodes.hasOwnProperty(i)) {
                var n = this._nodes[i];
                if ((property in n && n[property] == value) || (n.data && value == n.data[property])) {
                    return n;
                }
            }
        }

        return null;
    },

    /**
     * Returns a collection of nodes that have a matching property
     * and value in the data object that was passed into its constructor.
     * @method getNodesByProperty
     * @param {object} property the property to search (usually a string)
     * @param {object} value the value we want to find (usuall an int or string)
     * @return {Array} the matching collection of nodes, null if no match
     */
    getNodesByProperty: function(property, value) {
        var values = [];
        for (var i in this._nodes) {
            if (this._nodes.hasOwnProperty(i)) {
                var n = this._nodes[i];
                if ((property in n && n[property] == value) || (n.data && value == n.data[property])) {
                    values.push(n);
                }
            }
        }

        return (values.length) ? values : null;
    },


    /**
     * Returns a collection of nodes that have passed the test function
     * passed as its only argument.
     * The function will receive a reference to each node to be tested.
     * @method getNodesBy
     * @param {function} a boolean function that receives a Node instance and returns true to add the node to the results list
     * @return {Array} the matching collection of nodes, null if no match
     */
    getNodesBy: function(fn) {
        var values = [];
        for (var i in this._nodes) {
            if (this._nodes.hasOwnProperty(i)) {
                var n = this._nodes[i];
                if (fn(n)) {
                    values.push(n);
                }
            }
        }
        return (values.length) ? values : null;
    },
    /**
     * Returns the treeview node reference for an ancestor element
     * of the node, or null if it is not contained within any node
     * in this tree.
     * @method getNodeByElement
     * @param el {HTMLElement} the element to test
     * @return {YAHOO.widget.Node} a node reference or null
     */
    getNodeByElement: function(el) {

        var p=el, m, re=/ygtv([^\d]*)(.*)/;

        do {

            if (p && p.id) {
                m = p.id.match(re);
                if (m && m[2]) {
                    return this.getNodeByIndex(m[2]);
                }
            }

            p = p.parentNode;

            if (!p || !p.tagName) {
                break;
            }

        }
        while (p.id !== this.id && p.tagName.toLowerCase() !== "body");

        return null;
    },

    /**
     * When in singleNodeHighlight it returns the node highlighted
     * or null if none.  Returns null if singleNodeHighlight is false.
     * @method getHighlightedNode
     * @return {YAHOO.widget.Node} a node reference or null
     */
    getHighlightedNode: function() {
        return this._currentlyHighlighted;
    },


    /**
     * Removes the node and its children, and optionally refreshes the
     * branch of the tree that was affected.
     * @method removeNode
     * @param {Node} node to remove
     * @param {boolean} autoRefresh automatically refreshes branch if true
     * @return {boolean} False is there was a problem, true otherwise.
     */
    removeNode: function(node, autoRefresh) {

        // Don't delete the root node
        if (node.isRoot()) {
            return false;
        }

        // Get the branch that we may need to refresh
        var p = node.parent;
        if (p.parent) {
            p = p.parent;
        }

        // Delete the node and its children
        this._deleteNode(node);

        // Refresh the parent of the parent
        if (autoRefresh && p && p.childrenRendered) {
            p.refresh();
        }

        return true;
    },

    /**
     * wait until the animation is complete before deleting
     * to avoid javascript errors
     * @method _removeChildren_animComplete
     * @param o the custom event payload
     * @private
     */
    _removeChildren_animComplete: function(o) {
        this.unsubscribe(this._removeChildren_animComplete);
        this.removeChildren(o.node);
    },

    /**
     * Deletes this nodes child collection, recursively.  Also collapses
     * the node, and resets the dynamic load flag.  The primary use for
     * this method is to purge a node and allow it to fetch its data
     * dynamically again.
     * @method removeChildren
     * @param {Node} node the node to purge
     */
    removeChildren: function(node) {

        if (node.expanded) {
            // wait until the animation is complete before deleting to
            // avoid javascript errors
            if (this._collapseAnim) {
                this.subscribe("animComplete",
                        this._removeChildren_animComplete, this, true);
                Widget.Node.prototype.collapse.call(node);
                return;
            }

            node.collapse();
        }

        this.logger.log("Removing children for " + node);
        while (node.children.length) {
            this._deleteNode(node.children[0]);
        }

        if (node.isRoot()) {
            Widget.Node.prototype.expand.call(node);
        }

        node.childrenRendered = false;
        node.dynamicLoadComplete = false;

        node.updateIcon();
    },

    /**
     * Deletes the node and recurses children
     * @method _deleteNode
     * @private
     */
    _deleteNode: function(node) {
        // Remove all the child nodes first
        this.removeChildren(node);

        // Remove the node from the tree
        this.popNode(node);
    },

    /**
     * Removes the node from the tree, preserving the child collection
     * to make it possible to insert the branch into another part of the
     * tree, or another tree.
     * @method popNode
     * @param {Node} node to remove
     */
    popNode: function(node) {
        var p = node.parent;

        // Update the parent's collection of children
        var a = [];

        for (var i=0, len=p.children.length;i<len;++i) {
            if (p.children[i] != node) {
                a[a.length] = p.children[i];
            }
        }

        p.children = a;

        // reset the childrenRendered flag for the parent
        p.childrenRendered = false;

         // Update the sibling relationship
        if (node.previousSibling) {
            node.previousSibling.nextSibling = node.nextSibling;
        }

        if (node.nextSibling) {
            node.nextSibling.previousSibling = node.previousSibling;
        }

        if (this.currentFocus == node) {
            this.currentFocus = null;
        }
        if (this._currentlyHighlighted == node) {
            this._currentlyHighlighted = null;
        }

        node.parent = null;
        node.previousSibling = null;
        node.nextSibling = null;
        node.tree = null;

        // Update the tree's node collection
        delete this._nodes[node.index];
    },

    /**
    * Nulls out the entire TreeView instance and related objects, removes attached
    * event listeners, and clears out DOM elements inside the container. After
    * calling this method, the instance reference should be expliclitly nulled by
    * implementer, as in myDataTable = null. Use with caution!
    *
    * @method destroy
    */
    destroy : function() {
        // Since the label editor can be separated from the main TreeView control
        // the destroy method for it might not be there.
        if (this._destroyEditor) { this._destroyEditor(); }
        var el = this.getEl();
        Event.removeListener(el,'click');
        Event.removeListener(el,'dblclick');
        Event.removeListener(el,'mouseover');
        Event.removeListener(el,'mouseout');
        Event.removeListener(el,'keydown');
        for (var i = 0 ; i < this._nodes.length; i++) {
            var node = this._nodes[i];
            if (node && node.destroy) {node.destroy(); }
        }
        el.innerHTML = '';
        this._hasEvents = false;
    },




    /**
     * TreeView instance toString
     * @method toString
     * @return {string} string representation of the tree
     */
    toString: function() {
        return "TreeView " + this.id;
    },

    /**
     * Count of nodes in tree
     * @method getNodeCount
     * @return {int} number of nodes in the tree
     */
    getNodeCount: function() {
        return this.getRoot().getNodeCount();
    },

    /**
     * Returns an object which could be used to rebuild the tree.
     * It can be passed to the tree constructor to reproduce the same tree.
     * It will return false if any node loads dynamically, regardless of whether it is loaded or not.
     * @method getTreeDefinition
     * @return {Object | false}  definition of the tree or false if any node is defined as dynamic
     */
    getTreeDefinition: function() {
        return this.getRoot().getNodeDefinition();
    },

    /**
     * Abstract method that is executed when a node is expanded
     * @method onExpand
     * @param node {Node} the node that was expanded
     * @deprecated use treeobj.subscribe("expand") instead
     */
    onExpand: function(node) { },

    /**
     * Abstract method that is executed when a node is collapsed.
     * @method onCollapse
     * @param node {Node} the node that was collapsed.
     * @deprecated use treeobj.subscribe("collapse") instead
     */
    onCollapse: function(node) { },

    /**
    * Sets the value of a property for all loaded nodes in the tree.
    * @method setNodesProperty
    * @param name {string} Name of the property to be set
    * @param value {any} value to be set
    * @param refresh {boolean} if present and true, it does a refresh
    */
    setNodesProperty: function(name, value, refresh) {
        this.root.setNodesProperty(name,value);
        if (refresh) {
            this.root.refresh();
        }
    },
    /**
    * Event listener to toggle node highlight.
    * Can be assigned as listener to clickEvent, dblClickEvent and enterKeyPressed.
    * It returns false to prevent the default action.
    * @method onEventToggleHighlight
    * @param oArgs {any} it takes the arguments of any of the events mentioned above
    * @return {false} Always cancels the default action for the event
    */
    onEventToggleHighlight: function (oArgs) {
        var node;
        if ('node' in oArgs && oArgs.node instanceof Widget.Node) {
            node = oArgs.node;
        } else if (oArgs instanceof Widget.Node) {
            node = oArgs;
        } else {
            return false;
        }
        node.toggleHighlight();
        return false;
    }


};

/* Backwards compatibility aliases */
var PROT = TV.prototype;
 /**
     * Renders the tree boilerplate and visible nodes.
     *  Alias for render
     * @method draw
     * @deprecated Use render instead
     */
PROT.draw = PROT.render;

/* end backwards compatibility aliases */

YAHOO.augment(TV, YAHOO.util.EventProvider);

/**
 * Running count of all nodes created in all trees.  This is
 * used to provide unique identifies for all nodes.  Deleting
 * nodes does not change the nodeCount.
 * @property YAHOO.widget.TreeView.nodeCount
 * @type int
 * @static
 */
TV.nodeCount = 0;

/**
 * Global cache of tree instances
 * @property YAHOO.widget.TreeView.trees
 * @type Array
 * @static
 * @private
 */
TV.trees = [];

/**
 * Global method for getting a tree by its id.  Used in the generated
 * tree html.
 * @method YAHOO.widget.TreeView.getTree
 * @param treeId {String} the id of the tree instance
 * @return {TreeView} the tree instance requested, null if not found.
 * @static
 */
TV.getTree = function(treeId) {
    var t = TV.trees[treeId];
    return (t) ? t : null;
};


/**
 * Global method for getting a node by its id.  Used in the generated
 * tree html.
 * @method YAHOO.widget.TreeView.getNode
 * @param treeId {String} the id of the tree instance
 * @param nodeIndex {String} the index of the node to return
 * @return {Node} the node instance requested, null if not found
 * @static
 */
TV.getNode = function(treeId, nodeIndex) {
    var t = TV.getTree(treeId);
    return (t) ? t.getNodeByIndex(nodeIndex) : null;
};


/**
     * Class name assigned to elements that have the focus
     *
     * @property TreeView.FOCUS_CLASS_NAME
     * @type String
     * @static
     * @final
     * @default "ygtvfocus"

    */
TV.FOCUS_CLASS_NAME = 'ygtvfocus';



})();
(function () {
    var Dom = YAHOO.util.Dom,
        Lang = YAHOO.lang,
        Event = YAHOO.util.Event;
/**
 * The base class for all tree nodes.  The node's presentation and behavior in
 * response to mouse events is handled in Node subclasses.
 * @namespace YAHOO.widget
 * @class Node
 * @uses YAHOO.util.EventProvider
 * @param oData {object} a string or object containing the data that will
 * be used to render this node, and any custom attributes that should be
 * stored with the node (which is available in noderef.data).
 * All values in oData will be used to set equally named properties in the node
 * as long as the node does have such properties, they are not undefined, private or functions,
 * the rest of the values will be stored in noderef.data
 * @param oParent {Node} this node's parent node
 * @param expanded {boolean} the initial expanded/collapsed state (deprecated, use oData.expanded)
 * @constructor
 */
YAHOO.widget.Node = function(oData, oParent, expanded) {
    if (oData) { this.init(oData, oParent, expanded); }
};

YAHOO.widget.Node.prototype = {

    /**
     * The index for this instance obtained from global counter in YAHOO.widget.TreeView.
     * @property index
     * @type int
     */
    index: 0,

    /**
     * This node's child node collection.
     * @property children
     * @type Node[]
     */
    children: null,

    /**
     * Tree instance this node is part of
     * @property tree
     * @type TreeView
     */
    tree: null,

    /**
     * The data linked to this node.  This can be any object or primitive
     * value, and the data can be used in getNodeHtml().
     * @property data
     * @type object
     */
    data: null,

    /**
     * Parent node
     * @property parent
     * @type Node
     */
    parent: null,

    /**
     * The depth of this node.  We start at -1 for the root node.
     * @property depth
     * @type int
     */
    depth: -1,

    /**
     * The node's expanded/collapsed state
     * @property expanded
     * @type boolean
     */
    expanded: false,

    /**
     * Can multiple children be expanded at once?
     * @property multiExpand
     * @type boolean
     */
    multiExpand: true,

    /**
     * Should we render children for a collapsed node?  It is possible that the
     * implementer will want to render the hidden data...  @todo verify that we
     * need this, and implement it if we do.
     * @property renderHidden
     * @type boolean
     */
    renderHidden: false,

    /**
     * This flag is set to true when the html is generated for this node's
     * children, and set to false when new children are added.
     * @property childrenRendered
     * @type boolean
     */
    childrenRendered: false,

    /**
     * Dynamically loaded nodes only fetch the data the first time they are
     * expanded.  This flag is set to true once the data has been fetched.
     * @property dynamicLoadComplete
     * @type boolean
     */
    dynamicLoadComplete: false,

    /**
     * This node's previous sibling
     * @property previousSibling
     * @type Node
     */
    previousSibling: null,

    /**
     * This node's next sibling
     * @property nextSibling
     * @type Node
     */
    nextSibling: null,

    /**
     * We can set the node up to call an external method to get the child
     * data dynamically.
     * @property _dynLoad
     * @type boolean
     * @private
     */
    _dynLoad: false,

    /**
     * Function to execute when we need to get this node's child data.
     * @property dataLoader
     * @type function
     */
    dataLoader: null,

    /**
     * This is true for dynamically loading nodes while waiting for the
     * callback to return.
     * @property isLoading
     * @type boolean
     */
    isLoading: false,

    /**
     * The toggle/branch icon will not show if this is set to false.  This
     * could be useful if the implementer wants to have the child contain
     * extra info about the parent, rather than an actual node.
     * @property hasIcon
     * @type boolean
     */
    hasIcon: true,

    /**
     * Used to configure what happens when a dynamic load node is expanded
     * and we discover that it does not have children.  By default, it is
     * treated as if it still could have children (plus/minus icon).  Set
     * iconMode to have it display like a leaf node instead.
     * @property iconMode
     * @type int
     */
    iconMode: 0,

    /**
     * Specifies whether or not the content area of the node should be allowed
     * to wrap.
     * @property nowrap
     * @type boolean
     * @default false
     */
    nowrap: false,

 /**
     * If true, the node will always be rendered as a leaf node.  This can be
     * used to override the presentation when dynamically loading the entire
     * tree.  Setting this to true also disables the dynamic load call for the
     * node.
     * @property isLeaf
     * @type boolean
     * @default false
     */
    isLeaf: false,

/**
     * The CSS class for the html content container.  Defaults to ygtvhtml, but
     * can be overridden to provide a custom presentation for a specific node.
     * @property contentStyle
     * @type string
     */
    contentStyle: "",


    /**
     * The generated id that will contain the data passed in by the implementer.
     * @property contentElId
     * @type string
     */
    contentElId: null,

/**
 * Enables node highlighting.  If true, the node can be highlighted and/or propagate highlighting
 * @property enableHighlight
 * @type boolean
 * @default true
 */
    enableHighlight: true,

/**
 * Stores the highlight state.  Can be any of:
 * <ul>
 * <li>0 - not highlighted</li>
 * <li>1 - highlighted</li>
 * <li>2 - some children highlighted</li>
 * </ul>
 * @property highlightState
 * @type integer
 * @default 0
 */

 highlightState: 0,

 /**
 * Tells whether highlighting will be propagated up to the parents of the clicked node
 * @property propagateHighlightUp
 * @type boolean
 * @default false
 */

 propagateHighlightUp: false,

 /**
 * Tells whether highlighting will be propagated down to the children of the clicked node
 * @property propagateHighlightDown
 * @type boolean
 * @default false
 */

 propagateHighlightDown: false,

 /**
  * User-defined className to be added to the Node
  * @property className
  * @type string
  * @default null
  */

 className: null,

 /**
     * The node type
     * @property _type
     * @private
     * @type string
     * @default "Node"
*/
    _type: "Node",

    /*
    spacerPath: "http://l.yimg.com/a/i/space.gif",
    expandedText: "Expanded",
    collapsedText: "Collapsed",
    loadingText: "Loading",
    */

    /**
     * Initializes this node, gets some of the properties from the parent
     * @method init
     * @param oData {object} a string or object containing the data that will
     * be used to render this node
     * @param oParent {Node} this node's parent node
     * @param expanded {boolean} the initial expanded/collapsed state
     */
    init: function(oData, oParent, expanded) {

        this.data = {};
        this.children   = [];
        this.index      = YAHOO.widget.TreeView.nodeCount;
        ++YAHOO.widget.TreeView.nodeCount;
        this.contentElId = "ygtvcontentel" + this.index;

        if (Lang.isObject(oData)) {
            for (var property in oData) {
                if (oData.hasOwnProperty(property)) {
                    if (property.charAt(0) != '_'  && !Lang.isUndefined(this[property]) && !Lang.isFunction(this[property]) ) {
                        this[property] = oData[property];
                    } else {
                        this.data[property] = oData[property];
                    }
                }
            }
        }
        if (!Lang.isUndefined(expanded) ) { this.expanded  = expanded;  }

        this.logger     = new YAHOO.widget.LogWriter(this.toString());

        /**
         * The parentChange event is fired when a parent element is applied
         * to the node.  This is useful if you need to apply tree-level
         * properties to a tree that need to happen if a node is moved from
         * one tree to another.
         *
         * @event parentChange
         * @type CustomEvent
         */
        this.createEvent("parentChange", this);

        // oParent should never be null except when we create the root node.
        if (oParent) {
            oParent.appendChild(this);
        }
    },

    /**
     * Certain properties for the node cannot be set until the parent
     * is known. This is called after the node is inserted into a tree.
     * the parent is also applied to this node's children in order to
     * make it possible to move a branch from one tree to another.
     * @method applyParent
     * @param {Node} parentNode this node's parent node
     * @return {boolean} true if the application was successful
     */
    applyParent: function(parentNode) {
        if (!parentNode) {
            return false;
        }

        this.tree   = parentNode.tree;
        this.parent = parentNode;
        this.depth  = parentNode.depth + 1;

        // @todo why was this put here.  This causes new nodes added at the
        // root level to lose the menu behavior.
        // if (! this.multiExpand) {
            // this.multiExpand = parentNode.multiExpand;
        // }

        this.tree.regNode(this);
        parentNode.childrenRendered = false;

        // cascade update existing children
        for (var i=0, len=this.children.length;i<len;++i) {
            this.children[i].applyParent(this);
        }

        this.fireEvent("parentChange");

        return true;
    },

    /**
     * Appends a node to the child collection.
     * @method appendChild
     * @param childNode {Node} the new node
     * @return {Node} the child node
     * @private
     */
    appendChild: function(childNode) {
        if (this.hasChildren()) {
            var sib = this.children[this.children.length - 1];
            sib.nextSibling = childNode;
            childNode.previousSibling = sib;
        }
        this.children[this.children.length] = childNode;
        childNode.applyParent(this);

        // part of the IE display issue workaround. If child nodes
        // are added after the initial render, and the node was
        // instantiated with expanded = true, we need to show the
        // children div now that the node has a child.
        if (this.childrenRendered && this.expanded) {
            this.getChildrenEl().style.display = "";
        }

        return childNode;
    },

    /**
     * Appends this node to the supplied node's child collection
     * @method appendTo
     * @param parentNode {Node} the node to append to.
     * @return {Node} The appended node
     */
    appendTo: function(parentNode) {
        return parentNode.appendChild(this);
    },

    /**
    * Inserts this node before this supplied node
    * @method insertBefore
    * @param node {Node} the node to insert this node before
    * @return {Node} the inserted node
    */
    insertBefore: function(node) {
        this.logger.log("insertBefore: " + node);
        var p = node.parent;
        if (p) {

            if (this.tree) {
                this.tree.popNode(this);
            }

            var refIndex = node.isChildOf(p);
            //this.logger.log(refIndex);
            p.children.splice(refIndex, 0, this);
            if (node.previousSibling) {
                node.previousSibling.nextSibling = this;
            }
            this.previousSibling = node.previousSibling;
            this.nextSibling = node;
            node.previousSibling = this;

            this.applyParent(p);
        }

        return this;
    },

    /**
    * Inserts this node after the supplied node
    * @method insertAfter
    * @param node {Node} the node to insert after
    * @return {Node} the inserted node
    */
    insertAfter: function(node) {
        this.logger.log("insertAfter: " + node);
        var p = node.parent;
        if (p) {

            if (this.tree) {
                this.tree.popNode(this);
            }

            var refIndex = node.isChildOf(p);
            this.logger.log(refIndex);

            if (!node.nextSibling) {
                this.nextSibling = null;
                return this.appendTo(p);
            }

            p.children.splice(refIndex + 1, 0, this);

            node.nextSibling.previousSibling = this;
            this.previousSibling = node;
            this.nextSibling = node.nextSibling;
            node.nextSibling = this;

            this.applyParent(p);
        }

        return this;
    },

    /**
    * Returns true if the Node is a child of supplied Node
    * @method isChildOf
    * @param parentNode {Node} the Node to check
    * @return {boolean} The node index if this Node is a child of
    *                   supplied Node, else -1.
    * @private
    */
    isChildOf: function(parentNode) {
        if (parentNode && parentNode.children) {
            for (var i=0, len=parentNode.children.length; i<len ; ++i) {
                if (parentNode.children[i] === this) {
                    return i;
                }
            }
        }

        return -1;
    },

    /**
     * Returns a node array of this node's siblings, null if none.
     * @method getSiblings
     * @return Node[]
     */
    getSiblings: function() {
        var sib =  this.parent.children.slice(0);
        for (var i=0;i < sib.length && sib[i] != this;i++) {}
        sib.splice(i,1);
        if (sib.length) { return sib; }
        return null;
    },

    /**
     * Shows this node's children
     * @method showChildren
     */
    showChildren: function() {
        if (!this.tree.animateExpand(this.getChildrenEl(), this)) {
            if (this.hasChildren()) {
                this.getChildrenEl().style.display = "";
            }
        }
    },

    /**
     * Hides this node's children
     * @method hideChildren
     */
    hideChildren: function() {
        this.logger.log("hiding " + this.index);

        if (!this.tree.animateCollapse(this.getChildrenEl(), this)) {
            this.getChildrenEl().style.display = "none";
        }
    },

    /**
     * Returns the id for this node's container div
     * @method getElId
     * @return {string} the element id
     */
    getElId: function() {
        return "ygtv" + this.index;
    },

    /**
     * Returns the id for this node's children div
     * @method getChildrenElId
     * @return {string} the element id for this node's children div
     */
    getChildrenElId: function() {
        return "ygtvc" + this.index;
    },

    /**
     * Returns the id for this node's toggle element
     * @method getToggleElId
     * @return {string} the toggel element id
     */
    getToggleElId: function() {
        return "ygtvt" + this.index;
    },


    /*
     * Returns the id for this node's spacer image.  The spacer is positioned
     * over the toggle and provides feedback for screen readers.
     * @method getSpacerId
     * @return {string} the id for the spacer image
     */
    /*
    getSpacerId: function() {
        return "ygtvspacer" + this.index;
    },
    */

    /**
     * Returns this node's container html element
     * @method getEl
     * @return {HTMLElement} the container html element
     */
    getEl: function() {
        return Dom.get(this.getElId());
    },

    /**
     * Returns the div that was generated for this node's children
     * @method getChildrenEl
     * @return {HTMLElement} this node's children div
     */
    getChildrenEl: function() {
        return Dom.get(this.getChildrenElId());
    },

    /**
     * Returns the element that is being used for this node's toggle.
     * @method getToggleEl
     * @return {HTMLElement} this node's toggle html element
     */
    getToggleEl: function() {
        return Dom.get(this.getToggleElId());
    },
    /**
    * Returns the outer html element for this node's content
    * @method getContentEl
    * @return {HTMLElement} the element
    */
    getContentEl: function() {
        return Dom.get(this.contentElId);
    },


    /*
     * Returns the element that is being used for this node's spacer.
     * @method getSpacer
     * @return {HTMLElement} this node's spacer html element
     */
    /*
    getSpacer: function() {
        return document.getElementById( this.getSpacerId() ) || {};
    },
    */

    /*
    getStateText: function() {
        if (this.isLoading) {
            return this.loadingText;
        } else if (this.hasChildren(true)) {
            if (this.expanded) {
                return this.expandedText;
            } else {
                return this.collapsedText;
            }
        } else {
            return "";
        }
    },
    */

  /**
     * Hides this nodes children (creating them if necessary), changes the toggle style.
     * @method collapse
     */
    collapse: function() {
        // Only collapse if currently expanded
        if (!this.expanded) { return; }

        // fire the collapse event handler
        var ret = this.tree.onCollapse(this);

        if (false === ret) {
            this.logger.log("Collapse was stopped by the abstract onCollapse");
            return;
        }

        ret = this.tree.fireEvent("collapse", this);

        if (false === ret) {
            this.logger.log("Collapse was stopped by a custom event handler");
            return;
        }


        if (!this.getEl()) {
            this.expanded = false;
        } else {
            // hide the child div
            this.hideChildren();
            this.expanded = false;

            this.updateIcon();
        }

        // this.getSpacer().title = this.getStateText();

        ret = this.tree.fireEvent("collapseComplete", this);

    },

    /**
     * Shows this nodes children (creating them if necessary), changes the
     * toggle style, and collapses its siblings if multiExpand is not set.
     * @method expand
     */
    expand: function(lazySource) {
        // Only expand if currently collapsed.
        if (this.isLoading || (this.expanded && !lazySource)) {
            return;
        }

        var ret = true;

        // When returning from the lazy load handler, expand is called again
        // in order to render the new children.  The "expand" event already
        // fired before fething the new data, so we need to skip it now.
        if (!lazySource) {
            // fire the expand event handler
            ret = this.tree.onExpand(this);

            if (false === ret) {
                this.logger.log("Expand was stopped by the abstract onExpand");
                return;
            }

            ret = this.tree.fireEvent("expand", this);
        }

        if (false === ret) {
            this.logger.log("Expand was stopped by the custom event handler");
            return;
        }

        if (!this.getEl()) {
            this.expanded = true;
            return;
        }

        if (!this.childrenRendered) {
            this.logger.log("children not rendered yet");
            this.getChildrenEl().innerHTML = this.renderChildren();
        } else {
            this.logger.log("children already rendered");
        }

        this.expanded = true;

        this.updateIcon();

        // this.getSpacer().title = this.getStateText();

        // We do an extra check for children here because the lazy
        // load feature can expose nodes that have no children.

        // if (!this.hasChildren()) {
        if (this.isLoading) {
            this.expanded = false;
            return;
        }

        if (! this.multiExpand) {
            var sibs = this.getSiblings();
            for (var i=0; sibs && i<sibs.length; ++i) {
                if (sibs[i] != this && sibs[i].expanded) {
                    sibs[i].collapse();
                }
            }
        }

        this.showChildren();

        ret = this.tree.fireEvent("expandComplete", this);
    },

    updateIcon: function() {
        if (this.hasIcon) {
            var el = this.getToggleEl();
            if (el) {
                el.className = el.className.replace(/\bygtv(([tl][pmn]h?)|(loading))\b/gi,this.getStyle());
            }
        }
        el = Dom.get('ygtvtableel' + this.index);
        if (el) {
            if (this.expanded) {
                Dom.replaceClass(el,'ygtv-collapsed','ygtv-expanded');
            } else {
                Dom.replaceClass(el,'ygtv-expanded','ygtv-collapsed');
            }
        }
    },

    /**
     * Returns the css style name for the toggle
     * @method getStyle
     * @return {string} the css class for this node's toggle
     */
    getStyle: function() {
        // this.logger.log("No children, " + " isDyanmic: " + this.isDynamic() + " expanded: " + this.expanded);
        if (this.isLoading) {
            this.logger.log("returning the loading icon");
            return "ygtvloading";
        } else {
            // location top or bottom, middle nodes also get the top style
            var loc = (this.nextSibling) ? "t" : "l";

            // type p=plus(expand), m=minus(collapase), n=none(no children)
            var type = "n";
            if (this.hasChildren(true) || (this.isDynamic() && !this.getIconMode())) {
            // if (this.hasChildren(true)) {
                type = (this.expanded) ? "m" : "p";
            }

            // this.logger.log("ygtv" + loc + type);
            return "ygtv" + loc + type;
        }
    },

    /**
     * Returns the hover style for the icon
     * @return {string} the css class hover state
     * @method getHoverStyle
     */
    getHoverStyle: function() {
        var s = this.getStyle();
        if (this.hasChildren(true) && !this.isLoading) {
            s += "h";
        }
        return s;
    },

    /**
     * Recursively expands all of this node's children.
     * @method expandAll
     */
    expandAll: function() {
        var l = this.children.length;
        for (var i=0;i<l;++i) {
            var c = this.children[i];
            if (c.isDynamic()) {
                this.logger.log("Not supported (lazy load + expand all)");
                break;
            } else if (! c.multiExpand) {
                this.logger.log("Not supported (no multi-expand + expand all)");
                break;
            } else {
                c.expand();
                c.expandAll();
            }
        }
    },

    /**
     * Recursively collapses all of this node's children.
     * @method collapseAll
     */
    collapseAll: function() {
        for (var i=0;i<this.children.length;++i) {
            this.children[i].collapse();
            this.children[i].collapseAll();
        }
    },

    /**
     * Configures this node for dynamically obtaining the child data
     * when the node is first expanded.  Calling it without the callback
     * will turn off dynamic load for the node.
     * @method setDynamicLoad
     * @param fmDataLoader {function} the function that will be used to get the data.
     * @param iconMode {int} configures the icon that is displayed when a dynamic
     * load node is expanded the first time without children.  By default, the
     * "collapse" icon will be used.  If set to 1, the leaf node icon will be
     * displayed.
     */
    setDynamicLoad: function(fnDataLoader, iconMode) {
        if (fnDataLoader) {
            this.dataLoader = fnDataLoader;
            this._dynLoad = true;
        } else {
            this.dataLoader = null;
            this._dynLoad = false;
        }

        if (iconMode) {
            this.iconMode = iconMode;
        }
    },

    /**
     * Evaluates if this node is the root node of the tree
     * @method isRoot
     * @return {boolean} true if this is the root node
     */
    isRoot: function() {
        return (this == this.tree.root);
    },

    /**
     * Evaluates if this node's children should be loaded dynamically.  Looks for
     * the property both in this instance and the root node.  If the tree is
     * defined to load all children dynamically, the data callback function is
     * defined in the root node
     * @method isDynamic
     * @return {boolean} true if this node's children are to be loaded dynamically
     */
    isDynamic: function() {
        if (this.isLeaf) {
            return false;
        } else {
            return (!this.isRoot() && (this._dynLoad || this.tree.root._dynLoad));
            // this.logger.log("isDynamic: " + lazy);
            // return lazy;
        }
    },

    /**
     * Returns the current icon mode.  This refers to the way childless dynamic
     * load nodes appear (this comes into play only after the initial dynamic
     * load request produced no children).
     * @method getIconMode
     * @return {int} 0 for collapse style, 1 for leaf node style
     */
    getIconMode: function() {
        return (this.iconMode || this.tree.root.iconMode);
    },

    /**
     * Checks if this node has children.  If this node is lazy-loading and the
     * children have not been rendered, we do not know whether or not there
     * are actual children.  In most cases, we need to assume that there are
     * children (for instance, the toggle needs to show the expandable
     * presentation state).  In other times we want to know if there are rendered
     * children.  For the latter, "checkForLazyLoad" should be false.
     * @method hasChildren
     * @param checkForLazyLoad {boolean} should we check for unloaded children?
     * @return {boolean} true if this has children or if it might and we are
     * checking for this condition.
     */
    hasChildren: function(checkForLazyLoad) {
        if (this.isLeaf) {
            return false;
        } else {
            return ( this.children.length > 0 ||
                (checkForLazyLoad && this.isDynamic() && !this.dynamicLoadComplete)
            );
        }
    },

    /**
     * Expands if node is collapsed, collapses otherwise.
     * @method toggle
     */
    toggle: function() {
        if (!this.tree.locked && ( this.hasChildren(true) || this.isDynamic()) ) {
            if (this.expanded) { this.collapse(); } else { this.expand(); }
        }
    },

    /**
     * Returns the markup for this node and its children.
     * @method getHtml
     * @return {string} the markup for this node and its expanded children.
     */
    getHtml: function() {

        this.childrenRendered = false;

        return ['<div class="ygtvitem" id="' , this.getElId() , '">' ,this.getNodeHtml() , this.getChildrenHtml() ,'</div>'].join("");
    },

    /**
     * Called when first rendering the tree.  We always build the div that will
     * contain this nodes children, but we don't render the children themselves
     * unless this node is expanded.
     * @method getChildrenHtml
     * @return {string} the children container div html and any expanded children
     * @private
     */
    getChildrenHtml: function() {


        var sb = [];
        sb[sb.length] = '<div class="ygtvchildren" id="' + this.getChildrenElId() + '"';

        // This is a workaround for an IE rendering issue, the child div has layout
        // in IE, creating extra space if a leaf node is created with the expanded
        // property set to true.
        if (!this.expanded || !this.hasChildren()) {
            sb[sb.length] = ' style="display:none;"';
        }
        sb[sb.length] = '>';

        // this.logger.log(["index", this.index,
                         // "hasChildren", this.hasChildren(true),
                         // "expanded", this.expanded,
                         // "renderHidden", this.renderHidden,
                         // "isDynamic", this.isDynamic()]);

        // Don't render the actual child node HTML unless this node is expanded.
        if ( (this.hasChildren(true) && this.expanded) ||
                (this.renderHidden && !this.isDynamic()) ) {
            sb[sb.length] = this.renderChildren();
        }

        sb[sb.length] = '</div>';

        return sb.join("");
    },

    /**
     * Generates the markup for the child nodes.  This is not done until the node
     * is expanded.
     * @method renderChildren
     * @return {string} the html for this node's children
     * @private
     */
    renderChildren: function() {

        this.logger.log("rendering children for " + this.index);

        var node = this;

        if (this.isDynamic() && !this.dynamicLoadComplete) {
            this.isLoading = true;
            this.tree.locked = true;

            if (this.dataLoader) {
                this.logger.log("Using dynamic loader defined for this node");

                setTimeout(
                    function() {
                        node.dataLoader(node,
                            function() {
                                node.loadComplete();
                            });
                    }, 10);

            } else if (this.tree.root.dataLoader) {
                this.logger.log("Using the tree-level dynamic loader");

                setTimeout(
                    function() {
                        node.tree.root.dataLoader(node,
                            function() {
                                node.loadComplete();
                            });
                    }, 10);

            } else {
                this.logger.log("no loader found");
                return "Error: data loader not found or not specified.";
            }

            return "";

        } else {
            return this.completeRender();
        }
    },

    /**
     * Called when we know we have all the child data.
     * @method completeRender
     * @return {string} children html
     */
    completeRender: function() {
        this.logger.log("completeRender: " + this.index + ", # of children: " + this.children.length);
        var sb = [];

        for (var i=0; i < this.children.length; ++i) {
            // this.children[i].childrenRendered = false;
            sb[sb.length] = this.children[i].getHtml();
        }

        this.childrenRendered = true;

        return sb.join("");
    },

    /**
     * Load complete is the callback function we pass to the data provider
     * in dynamic load situations.
     * @method loadComplete
     */
    loadComplete: function() {
        this.logger.log(this.index + " loadComplete, children: " + this.children.length);
        this.getChildrenEl().innerHTML = this.completeRender();
        if (this.propagateHighlightDown) {
            if (this.highlightState === 1 && !this.tree.singleNodeHighlight) {
                for (var i = 0; i < this.children.length; i++) {
                this.children[i].highlight(true);
            }
            } else if (this.highlightState === 0 || this.tree.singleNodeHighlight) {
                for (i = 0; i < this.children.length; i++) {
                    this.children[i].unhighlight(true);
                }
            } // if (highlighState == 2) leave child nodes with whichever highlight state they are set
        }

        this.dynamicLoadComplete = true;
        this.isLoading = false;
        this.expand(true);
        this.tree.locked = false;
    },

    /**
     * Returns this node's ancestor at the specified depth.
     * @method getAncestor
     * @param {int} depth the depth of the ancestor.
     * @return {Node} the ancestor
     */
    getAncestor: function(depth) {
        if (depth >= this.depth || depth < 0)  {
            this.logger.log("illegal getAncestor depth: " + depth);
            return null;
        }

        var p = this.parent;

        while (p.depth > depth) {
            p = p.parent;
        }

        return p;
    },

    /**
     * Returns the css class for the spacer at the specified depth for
     * this node.  If this node's ancestor at the specified depth
     * has a next sibling the presentation is different than if it
     * does not have a next sibling
     * @method getDepthStyle
     * @param {int} depth the depth of the ancestor.
     * @return {string} the css class for the spacer
     */
    getDepthStyle: function(depth) {
        return (this.getAncestor(depth).nextSibling) ?
            "ygtvdepthcell" : "ygtvblankdepthcell";
    },

    /**
     * Get the markup for the node.  This may be overrided so that we can
     * support different types of nodes.
     * @method getNodeHtml
     * @return {string} The HTML that will render this node.
     */
    getNodeHtml: function() {
        this.logger.log("Generating html");
        var sb = [];

        sb[sb.length] = '<table id="ygtvtableel' + this.index + '" border="0" cellpadding="0" cellspacing="0" class="ygtvtable ygtvdepth' + this.depth;
        sb[sb.length] = ' ygtv-' + (this.expanded?'expanded':'collapsed');
        if (this.enableHighlight) {
            sb[sb.length] = ' ygtv-highlight' + this.highlightState;
        }
        if (this.className) {
            sb[sb.length] = ' ' + this.className;
        }
        sb[sb.length] = '"><tr class="ygtvrow">';

        for (var i=0;i<this.depth;++i) {
            sb[sb.length] = '<td class="ygtvcell ' + this.getDepthStyle(i) + '"><div class="ygtvspacer"></div></td>';
        }

        if (this.hasIcon) {
            sb[sb.length] = '<td id="' + this.getToggleElId();
            sb[sb.length] = '" class="ygtvcell ';
            sb[sb.length] = this.getStyle() ;
            sb[sb.length] = '"><a href="#" class="ygtvspacer">&#160;</a></td>';
        }

        sb[sb.length] = '<td id="' + this.contentElId;
        sb[sb.length] = '" class="ygtvcell ';
        sb[sb.length] = this.contentStyle  + ' ygtvcontent" ';
        sb[sb.length] = (this.nowrap) ? ' nowrap="nowrap" ' : '';
        sb[sb.length] = ' >';
        sb[sb.length] = this.getContentHtml();
        sb[sb.length] = '</td></tr></table>';

        return sb.join("");

    },
    /**
     * Get the markup for the contents of the node.  This is designed to be overrided so that we can
     * support different types of nodes.
     * @method getContentHtml
     * @return {string} The HTML that will render the content of this node.
     */
    getContentHtml: function () {
        return "";
    },

    /**
     * Regenerates the html for this node and its children.  To be used when the
     * node is expanded and new children have been added.
     * @method refresh
     */
    refresh: function() {
        // this.loadComplete();
        this.getChildrenEl().innerHTML = this.completeRender();

        if (this.hasIcon) {
            var el = this.getToggleEl();
            if (el) {
                el.className = el.className.replace(/\bygtv[lt][nmp]h*\b/gi,this.getStyle());
            }
        }
    },

    /**
     * Node toString
     * @method toString
     * @return {string} string representation of the node
     */
    toString: function() {
        return this._type + " (" + this.index + ")";
    },
    /**
    * array of items that had the focus set on them
    * so that they can be cleaned when focus is lost
    * @property _focusHighlightedItems
    * @type Array of DOM elements
    * @private
    */
    _focusHighlightedItems: [],
    /**
    * DOM element that actually got the browser focus
    * @property _focusedItem
    * @type DOM element
    * @private
    */
    _focusedItem: null,

    /**
    * Returns true if there are any elements in the node that can
    * accept the real actual browser focus
    * @method _canHaveFocus
    * @return {boolean} success
    * @private
    */
    _canHaveFocus: function() {
        return this.getEl().getElementsByTagName('a').length > 0;
    },
    /**
    * Removes the focus of previously selected Node
    * @method _removeFocus
    * @private
    */
    _removeFocus:function () {
        if (this._focusedItem) {
            Event.removeListener(this._focusedItem,'blur');
            this._focusedItem = null;
        }
        var el;
        while ((el = this._focusHighlightedItems.shift())) {  // yes, it is meant as an assignment, really
            Dom.removeClass(el,YAHOO.widget.TreeView.FOCUS_CLASS_NAME );
        }
    },
    /**
    * Sets the focus on the node element.
    * It will only be able to set the focus on nodes that have anchor elements in it.
    * Toggle or branch icons have anchors and can be focused on.
    * If will fail in nodes that have no anchor
    * @method focus
    * @return {boolean} success
    */
    focus: function () {
        var focused = false, self = this;

        if (this.tree.currentFocus) {
            this.tree.currentFocus._removeFocus();
        }

        var  expandParent = function (node) {
            if (node.parent) {
                expandParent(node.parent);
                node.parent.expand();
            }
        };
        expandParent(this);

        Dom.getElementsBy  (
            function (el) {
                return (/ygtv(([tl][pmn]h?)|(content))/).test(el.className);
            } ,
            'td' ,
            self.getEl().firstChild ,
            function (el) {
                Dom.addClass(el, YAHOO.widget.TreeView.FOCUS_CLASS_NAME );
                if (!focused) {
                    var aEl = el.getElementsByTagName('a');
                    if (aEl.length) {
                        aEl = aEl[0];
                        aEl.focus();
                        self._focusedItem = aEl;
                        Event.on(aEl,'blur',function () {
                            self.tree.fireEvent('focusChanged',{oldNode:self.tree.currentFocus,newNode:null});
                            self.tree.currentFocus = null;
                            self._removeFocus();
                        });
                        focused = true;
                    }
                }
                self._focusHighlightedItems.push(el);
            }
        );
        if (focused) {
            this.tree.fireEvent('focusChanged',{oldNode:this.tree.currentFocus,newNode:this});
            this.tree.currentFocus = this;
        } else {
            this.tree.fireEvent('focusChanged',{oldNode:self.tree.currentFocus,newNode:null});
            this.tree.currentFocus = null;
            this._removeFocus();
        }
        return focused;
    },

  /**
     * Count of nodes in a branch
     * @method getNodeCount
     * @return {int} number of nodes in the branch
     */
    getNodeCount: function() {
        for (var i = 0, count = 0;i< this.children.length;i++) {
            count += this.children[i].getNodeCount();
        }
        return count + 1;
    },

      /**
     * Returns an object which could be used to build a tree out of this node and its children.
     * It can be passed to the tree constructor to reproduce this node as a tree.
     * It will return false if the node or any children loads dynamically, regardless of whether it is loaded or not.
     * @method getNodeDefinition
     * @return {Object | false}  definition of the tree or false if the node or any children is defined as dynamic
     */
    getNodeDefinition: function() {

        if (this.isDynamic()) { return false; }

        var def, defs = Lang.merge(this.data), children = [];



        if (this.expanded) {defs.expanded = this.expanded; }
        if (!this.multiExpand) { defs.multiExpand = this.multiExpand; }
        if (this.renderHidden) { defs.renderHidden = this.renderHidden; }
        if (!this.hasIcon) { defs.hasIcon = this.hasIcon; }
        if (this.nowrap) { defs.nowrap = this.nowrap; }
        if (this.className) { defs.className = this.className; }
        if (this.editable) { defs.editable = this.editable; }
        if (!this.enableHighlight) { defs.enableHighlight = this.enableHighlight; }
        if (this.highlightState) { defs.highlightState = this.highlightState; }
        if (this.propagateHighlightUp) { defs.propagateHighlightUp = this.propagateHighlightUp; }
        if (this.propagateHighlightDown) { defs.propagateHighlightDown = this.propagateHighlightDown; }
        defs.type = this._type;



        for (var i = 0; i < this.children.length;i++) {
            def = this.children[i].getNodeDefinition();
            if (def === false) { return false;}
            children.push(def);
        }
        if (children.length) { defs.children = children; }
        return defs;
    },


    /**
     * Generates the link that will invoke this node's toggle method
     * @method getToggleLink
     * @return {string} the javascript url for toggling this node
     */
    getToggleLink: function() {
        return 'return false;';
    },

    /**
    * Sets the value of property for this node and all loaded descendants.
    * Only public and defined properties can be set, not methods.
    * Values for unknown properties will be assigned to the refNode.data object
    * @method setNodesProperty
    * @param name {string} Name of the property to be set
    * @param value {any} value to be set
    * @param refresh {boolean} if present and true, it does a refresh
    */
    setNodesProperty: function(name, value, refresh) {
        if (name.charAt(0) != '_'  && !Lang.isUndefined(this[name]) && !Lang.isFunction(this[name]) ) {
            this[name] = value;
        } else {
            this.data[name] = value;
        }
        for (var i = 0; i < this.children.length;i++) {
            this.children[i].setNodesProperty(name,value);
        }
        if (refresh) {
            this.refresh();
        }
    },
    /**
    * Toggles the highlighted state of a Node
    * @method toggleHighlight
    */
    toggleHighlight: function() {
        if (this.enableHighlight) {
            // unhighlights only if fully highligthed.  For not or partially highlighted it will highlight
            if (this.highlightState == 1) {
                this.unhighlight();
            } else {
                this.highlight();
            }
        }
    },

    /**
    * Turns highlighting on node.
    * @method highlight
    * @param _silent {boolean} optional, don't fire the highlightEvent
    */
    highlight: function(_silent) {
        if (this.enableHighlight) {
            if (this.tree.singleNodeHighlight) {
                if (this.tree._currentlyHighlighted) {
                    this.tree._currentlyHighlighted.unhighlight(_silent);
                }
                this.tree._currentlyHighlighted = this;
            }
            this.highlightState = 1;
            this._setHighlightClassName();
            if (!this.tree.singleNodeHighlight) {
                if (this.propagateHighlightDown) {
                    for (var i = 0;i < this.children.length;i++) {
                        this.children[i].highlight(true);
                    }
                }
                if (this.propagateHighlightUp) {
                    if (this.parent) {
                        this.parent._childrenHighlighted();
                    }
                }
            }
            if (!_silent) {
                this.tree.fireEvent('highlightEvent',this);
            }
        }
    },
    /**
    * Turns highlighting off a node.
    * @method unhighlight
    * @param _silent {boolean} optional, don't fire the highlightEvent
    */
    unhighlight: function(_silent) {
        if (this.enableHighlight) {
            // might have checked singleNodeHighlight but it wouldn't really matter either way
            this.tree._currentlyHighlighted = null;
            this.highlightState = 0;
            this._setHighlightClassName();
            if (!this.tree.singleNodeHighlight) {
                if (this.propagateHighlightDown) {
                    for (var i = 0;i < this.children.length;i++) {
                        this.children[i].unhighlight(true);
                    }
                }
                if (this.propagateHighlightUp) {
                    if (this.parent) {
                        this.parent._childrenHighlighted();
                    }
                }
            }
            if (!_silent) {
                this.tree.fireEvent('highlightEvent',this);
            }
        }
    },
    /**
    * Checks whether all or part of the children of a node are highlighted and
    * sets the node highlight to full, none or partial highlight.
    * If set to propagate it will further call the parent
    * @method _childrenHighlighted
    * @private
    */
    _childrenHighlighted: function() {
        var yes = false, no = false;
        if (this.enableHighlight) {
            for (var i = 0;i < this.children.length;i++) {
                switch(this.children[i].highlightState) {
                    case 0:
                        no = true;
                        break;
                    case 1:
                        yes = true;
                        break;
                    case 2:
                        yes = no = true;
                        break;
                }
            }
            if (yes && no) {
                this.highlightState = 2;
            } else if (yes) {
                this.highlightState = 1;
            } else {
                this.highlightState = 0;
            }
            this._setHighlightClassName();
            if (this.propagateHighlightUp) {
                if (this.parent) {
                    this.parent._childrenHighlighted();
                }
            }
        }
    },

    /**
    * Changes the classNames on the toggle and content containers to reflect the current highlighting
    * @method _setHighlightClassName
    * @private
    */
    _setHighlightClassName: function() {
        var el = Dom.get('ygtvtableel' + this.index);
        if (el) {
            el.className = el.className.replace(/\bygtv-highlight\d\b/gi,'ygtv-highlight' + this.highlightState);
        }
    }

};

YAHOO.augment(YAHOO.widget.Node, YAHOO.util.EventProvider);
})();
/**
 * A custom YAHOO.widget.Node that handles the unique nature of
 * the virtual, presentationless root node.
 * @namespace YAHOO.widget
 * @class RootNode
 * @extends YAHOO.widget.Node
 * @param oTree {YAHOO.widget.TreeView} The tree instance this node belongs to
 * @constructor
 */
YAHOO.widget.RootNode = function(oTree) {
    // Initialize the node with null params.  The root node is a
    // special case where the node has no presentation.  So we have
    // to alter the standard properties a bit.
    this.init(null, null, true);

    /*
     * For the root node, we get the tree reference from as a param
     * to the constructor instead of from the parent element.
     */
    this.tree = oTree;
};

YAHOO.extend(YAHOO.widget.RootNode, YAHOO.widget.Node, {

   /**
     * The node type
     * @property _type
      * @type string
     * @private
     * @default "RootNode"
     */
    _type: "RootNode",

    // overrides YAHOO.widget.Node
    getNodeHtml: function() {
        return "";
    },

    toString: function() {
        return this._type;
    },

    loadComplete: function() {
        this.tree.draw();
    },

   /**
     * Count of nodes in tree.
    * It overrides Nodes.getNodeCount because the root node should not be counted.
     * @method getNodeCount
     * @return {int} number of nodes in the tree
     */
    getNodeCount: function() {
        for (var i = 0, count = 0;i< this.children.length;i++) {
            count += this.children[i].getNodeCount();
        }
        return count;
    },

  /**
     * Returns an object which could be used to build a tree out of this node and its children.
     * It can be passed to the tree constructor to reproduce this node as a tree.
     * Since the RootNode is automatically created by treeView,
     * its own definition is excluded from the returned node definition
     * which only contains its children.
     * @method getNodeDefinition
     * @return {Object | false}  definition of the tree or false if any child node is defined as dynamic
     */
    getNodeDefinition: function() {

        for (var def, defs = [], i = 0; i < this.children.length;i++) {
            def = this.children[i].getNodeDefinition();
            if (def === false) { return false;}
            defs.push(def);
        }
        return defs;
    },

    collapse: function() {},
    expand: function() {},
    getSiblings: function() { return null; },
    focus: function () {}

});
(function () {
    var Dom = YAHOO.util.Dom,
        Lang = YAHOO.lang,
        Event = YAHOO.util.Event;
/**
 * The default node presentation.  The first parameter should be
 * either a string that will be used as the node's label, or an object
 * that has at least a string property called label.  By default,  clicking the
 * label will toggle the expanded/collapsed state of the node.  By
 * setting the href property of the instance, this behavior can be
 * changed so that the label will go to the specified href.
 * @namespace YAHOO.widget
 * @class TextNode
 * @extends YAHOO.widget.Node
 * @constructor
 * @param oData {object} a string or object containing the data that will
 * be used to render this node.
 * Providing a string is the same as providing an object with a single property named label.
 * All values in the oData will be used to set equally named properties in the node
 * as long as the node does have such properties, they are not undefined, private or functions.
 * All attributes are made available in noderef.data, which
 * can be used to store custom attributes.  TreeView.getNode(s)ByProperty
 * can be used to retrieve a node by one of the attributes.
 * @param oParent {YAHOO.widget.Node} this node's parent node
 * @param expanded {boolean} the initial expanded/collapsed state (deprecated; use oData.expanded)
 */
YAHOO.widget.TextNode = function(oData, oParent, expanded) {

    if (oData) {
        if (Lang.isString(oData)) {
            oData = { label: oData };
        }
        this.init(oData, oParent, expanded);
        this.setUpLabel(oData);
    }

    this.logger     = new YAHOO.widget.LogWriter(this.toString());
};

YAHOO.extend(YAHOO.widget.TextNode, YAHOO.widget.Node, {

    /**
     * The CSS class for the label href.  Defaults to ygtvlabel, but can be
     * overridden to provide a custom presentation for a specific node.
     * @property labelStyle
     * @type string
     */
    labelStyle: "ygtvlabel",

    /**
     * The derived element id of the label for this node
     * @property labelElId
     * @type string
     */
    labelElId: null,

    /**
     * The text for the label.  It is assumed that the oData parameter will
     * either be a string that will be used as the label, or an object that
     * has a property called "label" that we will use.
     * @property label
     * @type string
     */
    label: null,

    /**
     * The text for the title (tooltip) for the label element
     * @property title
     * @type string
     */
    title: null,

    /**
     * The href for the node's label.  If one is not specified, the href will
     * be set so that it toggles the node.
     * @property href
     * @type string
     */
    href: null,

    /**
     * The label href target, defaults to current window
     * @property target
     * @type string
     */
    target: "_self",

    /**
     * The node type
     * @property _type
     * @private
     * @type string
     * @default "TextNode"
     */
    _type: "TextNode",


    /**
     * Sets up the node label
     * @method setUpLabel
     * @param oData string containing the label, or an object with a label property
     */
    setUpLabel: function(oData) {

        if (Lang.isString(oData)) {
            oData = {
                label: oData
            };
        } else {
            if (oData.style) {
                this.labelStyle = oData.style;
            }
        }

        this.label = oData.label;

        this.labelElId = "ygtvlabelel" + this.index;

    },

    /**
     * Returns the label element
     * @for YAHOO.widget.TextNode
     * @method getLabelEl
     * @return {object} the element
     */
    getLabelEl: function() {
        return Dom.get(this.labelElId);
    },

    // overrides YAHOO.widget.Node
    getContentHtml: function() {
        var sb = [];
        sb[sb.length] = this.href ? '<a' : '<span';
        sb[sb.length] = ' id="' + Lang.escapeHTML(this.labelElId) + '"';
        sb[sb.length] = ' class="' + Lang.escapeHTML(this.labelStyle)  + '"';
        if (this.href) {
            sb[sb.length] = ' href="' + Lang.escapeHTML(this.href) + '"';
            sb[sb.length] = ' target="' + Lang.escapeHTML(this.target) + '"';
        }
        if (this.title) {
            sb[sb.length] = ' title="' + Lang.escapeHTML(this.title) + '"';
        }
        sb[sb.length] = ' >';
        sb[sb.length] = Lang.escapeHTML(this.label);
        sb[sb.length] = this.href?'</a>':'</span>';
        return sb.join("");
    },



  /**
     * Returns an object which could be used to build a tree out of this node and its children.
     * It can be passed to the tree constructor to reproduce this node as a tree.
     * It will return false if the node or any descendant loads dynamically, regardless of whether it is loaded or not.
     * @method getNodeDefinition
     * @return {Object | false}  definition of the tree or false if this node or any descendant is defined as dynamic
     */
    getNodeDefinition: function() {
        var def = YAHOO.widget.TextNode.superclass.getNodeDefinition.call(this);
        if (def === false) { return false; }

        // Node specific properties
        def.label = this.label;
        if (this.labelStyle != 'ygtvlabel') { def.style = this.labelStyle; }
        if (this.title) { def.title = this.title; }
        if (this.href) { def.href = this.href; }
        if (this.target != '_self') { def.target = this.target; }

        return def;

    },

    toString: function() {
        return YAHOO.widget.TextNode.superclass.toString.call(this) + ": " + this.label;
    },

    // deprecated
    onLabelClick: function() {
        return false;
    },
    refresh: function() {
        YAHOO.widget.TextNode.superclass.refresh.call(this);
        var label = this.getLabelEl();
        label.innerHTML = this.label;
        if (label.tagName.toUpperCase() == 'A') {
            label.href = this.href;
            label.target = this.target;
        }
    }




});
})();
/**
 * A menu-specific implementation that differs from TextNode in that only
 * one sibling can be expanded at a time.
 * @namespace YAHOO.widget
 * @class MenuNode
 * @extends YAHOO.widget.TextNode
 * @param oData {object} a string or object containing the data that will
 * be used to render this node.
 * Providing a string is the same as providing an object with a single property named label.
 * All values in the oData will be used to set equally named properties in the node
 * as long as the node does have such properties, they are not undefined, private or functions.
 * All attributes are made available in noderef.data, which
 * can be used to store custom attributes.  TreeView.getNode(s)ByProperty
 * can be used to retrieve a node by one of the attributes.
 * @param oParent {YAHOO.widget.Node} this node's parent node
 * @param expanded {boolean} the initial expanded/collapsed state (deprecated; use oData.expanded)
 * @constructor
 */
YAHOO.widget.MenuNode = function(oData, oParent, expanded) {
    YAHOO.widget.MenuNode.superclass.constructor.call(this,oData,oParent,expanded);

   /*
     * Menus usually allow only one branch to be open at a time.
     */
    this.multiExpand = false;

};

YAHOO.extend(YAHOO.widget.MenuNode, YAHOO.widget.TextNode, {

    /**
     * The node type
     * @property _type
     * @private
    * @default "MenuNode"
     */
    _type: "MenuNode"

});
(function () {
    var Dom = YAHOO.util.Dom,
        Lang = YAHOO.lang,
        Event = YAHOO.util.Event;

/**
 * This implementation takes either a string or object for the
 * oData argument.  If is it a string, it will use it for the display
 * of this node (and it can contain any html code).  If the parameter
 * is an object,it looks for a parameter called "html" that will be
 * used for this node's display.
 * @namespace YAHOO.widget
 * @class HTMLNode
 * @extends YAHOO.widget.Node
 * @constructor
 * @param oData {object} a string or object containing the data that will
 * be used to render this node.
 * Providing a string is the same as providing an object with a single property named html.
 * All values in the oData will be used to set equally named properties in the node
 * as long as the node does have such properties, they are not undefined, private or functions.
 * All other attributes are made available in noderef.data, which
 * can be used to store custom attributes.  TreeView.getNode(s)ByProperty
 * can be used to retrieve a node by one of the attributes.
 * @param oParent {YAHOO.widget.Node} this node's parent node
 * @param expanded {boolean} the initial expanded/collapsed state (deprecated; use oData.expanded)
 * @param hasIcon {boolean} specifies whether or not leaf nodes should
 * be rendered with or without a horizontal line and/or toggle icon. If the icon
 * is not displayed, the content fills the space it would have occupied.
 * This option operates independently of the leaf node presentation logic
 * for dynamic nodes.
 * (deprecated; use oData.hasIcon)
 */
var HN =  function(oData, oParent, expanded, hasIcon) {
    if (oData) {
        this.init(oData, oParent, expanded);
        this.initContent(oData, hasIcon);
    }
};


YAHOO.widget.HTMLNode = HN;
YAHOO.extend(HN, YAHOO.widget.Node, {

    /**
     * The CSS class for the html content container.  Defaults to ygtvhtml, but
     * can be overridden to provide a custom presentation for a specific node.
     * @property contentStyle
     * @type string
     */
    contentStyle: "ygtvhtml",


    /**
     * The HTML content to use for this node's display
     * @property html
     * @type string
     */
    html: null,

/**
     * The node type
     * @property _type
     * @private
     * @type string
     * @default "HTMLNode"
     */
    _type: "HTMLNode",

    /**
     * Sets up the node label
     * @method initContent
     * @param oData {object} An html string or object containing an html property
     * @param hasIcon {boolean} determines if the node will be rendered with an
     * icon or not
     */
    initContent: function(oData, hasIcon) {
        this.setHtml(oData);
        this.contentElId = "ygtvcontentel" + this.index;
        if (!Lang.isUndefined(hasIcon)) { this.hasIcon  = hasIcon; }

        this.logger = new YAHOO.widget.LogWriter(this.toString());
    },

    /**
     * Synchronizes the node.html, and the node's content
     * @method setHtml
     * @param o {object |string | HTMLElement } An html string, an object containing an html property or an HTML element
     */
    setHtml: function(o) {
        this.html = (Lang.isObject(o) && 'html' in o) ? o.html : o;

        var el = this.getContentEl();
        if (el) {
            if (o.nodeType && o.nodeType == 1 && o.tagName) {
                el.innerHTML = "";
            } else {
                el.innerHTML = this.html;
            }
        }

    },

    // overrides YAHOO.widget.Node
    // If property html is a string, it sets the innerHTML for the node
    // If it is an HTMLElement, it defers appending it to the tree until the HTML basic structure is built
    getContentHtml: function() {
        if (typeof this.html === "string") {
            return this.html;
        } else {

            HN._deferredNodes.push(this);
            if (!HN._timer) {
                HN._timer = window.setTimeout(function () {
                    var n;
                    while((n = HN._deferredNodes.pop())) {
                        n.getContentEl().appendChild(n.html);
                    }
                    HN._timer = null;
                },0);
            }
            return "";
        }
    },

      /**
     * Returns an object which could be used to build a tree out of this node and its children.
     * It can be passed to the tree constructor to reproduce this node as a tree.
     * It will return false if any node loads dynamically, regardless of whether it is loaded or not.
     * @method getNodeDefinition
     * @return {Object | false}  definition of the tree or false if any node is defined as dynamic
     */
    getNodeDefinition: function() {
        var def = HN.superclass.getNodeDefinition.call(this);
        if (def === false) { return false; }
        def.html = this.html;
        return def;

    }
});

    /**
    * An array of HTMLNodes created with HTML Elements that had their rendering
    * deferred until the basic tree structure is rendered.
    * @property _deferredNodes
    * @type YAHOO.widget.HTMLNode[]
    * @default []
    * @private
    * @static
    */
HN._deferredNodes = [];
    /**
    * A system timer value used to mark whether a deferred operation is pending.
    * @property _timer
    * @type System Timer
    * @default null
    * @private
    * @static
    */
HN._timer = null;
})();
(function () {
    var Dom = YAHOO.util.Dom,
        Lang = YAHOO.lang,
        Event = YAHOO.util.Event,
        Calendar = YAHOO.widget.Calendar;

/**
 * A Date-specific implementation that differs from TextNode in that it uses
 * YAHOO.widget.Calendar as an in-line editor, if available
 * If Calendar is not available, it behaves as a plain TextNode.
 * @namespace YAHOO.widget
 * @class DateNode
 * @extends YAHOO.widget.TextNode
 * @param oData {object} a string or object containing the data that will
 * be used to render this node.
 * Providing a string is the same as providing an object with a single property named label.
 * All values in the oData will be used to set equally named properties in the node
 * as long as the node does have such properties, they are not undefined, private nor functions.
 * All attributes are made available in noderef.data, which
 * can be used to store custom attributes.  TreeView.getNode(s)ByProperty
 * can be used to retrieve a node by one of the attributes.
 * @param oParent {YAHOO.widget.Node} this node's parent node
 * @param expanded {boolean} the initial expanded/collapsed state (deprecated; use oData.expanded)
 * @constructor
 */
YAHOO.widget.DateNode = function(oData, oParent, expanded) {
    YAHOO.widget.DateNode.superclass.constructor.call(this,oData, oParent, expanded);
};

YAHOO.extend(YAHOO.widget.DateNode, YAHOO.widget.TextNode, {

    /**
     * The node type
     * @property _type
     * @type string
     * @private
     * @default  "DateNode"
     */
    _type: "DateNode",

    /**
    * Configuration object for the Calendar editor, if used.
    * See <a href="http://developer.yahoo.com/yui/calendar/#internationalization">http://developer.yahoo.com/yui/calendar/#internationalization</a>
    * @property calendarConfig
    */
    calendarConfig: null,



    /**
     *  If YAHOO.widget.Calendar is available, it will pop up a Calendar to enter a new date.  Otherwise, it falls back to a plain &lt;input&gt;  textbox
     * @method fillEditorContainer
     * @param editorData {YAHOO.widget.TreeView.editorData}  a shortcut to the static object holding editing information
     * @return void
     */
    fillEditorContainer: function (editorData) {

        var cal, container = editorData.inputContainer;

        if (Lang.isUndefined(Calendar)) {
            Dom.replaceClass(editorData.editorPanel,'ygtv-edit-DateNode','ygtv-edit-TextNode');
            YAHOO.widget.DateNode.superclass.fillEditorContainer.call(this, editorData);
            return;
        }

        if (editorData.nodeType != this._type) {
            editorData.nodeType = this._type;
            editorData.saveOnEnter = false;

            editorData.node.destroyEditorContents(editorData);

            editorData.inputObject = cal = new Calendar(container.appendChild(document.createElement('div')));
            if (this.calendarConfig) {
                cal.cfg.applyConfig(this.calendarConfig,true);
                cal.cfg.fireQueue();
            }
            cal.selectEvent.subscribe(function () {
                this.tree._closeEditor(true);
            },this,true);
        } else {
            cal = editorData.inputObject;
        }

        editorData.oldValue = this.label;
        cal.cfg.setProperty("selected",this.label, false);

        var delim = cal.cfg.getProperty('DATE_FIELD_DELIMITER');
        var pageDate = this.label.split(delim);
        cal.cfg.setProperty('pagedate',pageDate[cal.cfg.getProperty('MDY_MONTH_POSITION') -1] + delim + pageDate[cal.cfg.getProperty('MDY_YEAR_POSITION') -1]);
        cal.cfg.fireQueue();

        cal.render();
        cal.oDomContainer.focus();
    },
     /**
    * Returns the value from the input element.
    * Overrides Node.getEditorValue.
    * @method getEditorValue
     * @param editorData {YAHOO.widget.TreeView.editorData}  a shortcut to the static object holding editing information
     * @return {string} date entered
     */

    getEditorValue: function (editorData) {
        if (Lang.isUndefined(Calendar)) {
            return editorData.inputElement.value;
        } else {
            var cal = editorData.inputObject,
                date = cal.getSelectedDates()[0],
                dd = [];

            dd[cal.cfg.getProperty('MDY_DAY_POSITION') -1] = date.getDate();
            dd[cal.cfg.getProperty('MDY_MONTH_POSITION') -1] = date.getMonth() + 1;
            dd[cal.cfg.getProperty('MDY_YEAR_POSITION') -1] = date.getFullYear();
            return dd.join(cal.cfg.getProperty('DATE_FIELD_DELIMITER'));
        }
    },

    /**
     * Finally displays the newly entered date in the tree.
     * Overrides Node.displayEditedValue.
     * @method displayEditedValue
     * @param value {HTML} date to be displayed and stored in the node.
     * This data is added to the node unescaped via the innerHTML property.
     * @param editorData {YAHOO.widget.TreeView.editorData}  a shortcut to the static object holding editing information
     */
    displayEditedValue: function (value,editorData) {
        var node = editorData.node;
        node.label = value;
        node.getLabelEl().innerHTML = value;
    },

   /**
     * Returns an object which could be used to build a tree out of this node and its children.
     * It can be passed to the tree constructor to reproduce this node as a tree.
     * It will return false if the node or any descendant loads dynamically, regardless of whether it is loaded or not.
     * @method getNodeDefinition
     * @return {Object | false}  definition of the node or false if this node or any descendant is defined as dynamic
     */
    getNodeDefinition: function() {
        var def = YAHOO.widget.DateNode.superclass.getNodeDefinition.call(this);
        if (def === false) { return false; }
        if (this.calendarConfig) { def.calendarConfig = this.calendarConfig; }
        return def;
    }


});
})();
(function () {
    var Dom = YAHOO.util.Dom,
        Lang = YAHOO.lang,
        Event = YAHOO.util.Event,
        TV = YAHOO.widget.TreeView,
        TVproto = TV.prototype;

    /**
     * An object to store information used for in-line editing
     * for all Nodes of all TreeViews. It contains:
     * <ul>
    * <li>active {boolean}, whether there is an active cell editor </li>
    * <li>whoHasIt {YAHOO.widget.TreeView} TreeView instance that is currently using the editor</li>
    * <li>nodeType {string} value of static Node._type property, allows reuse of input element if node is of the same type.</li>
    * <li>editorPanel {HTMLelement (&lt;div&gt;)} element holding the in-line editor</li>
    * <li>inputContainer {HTMLelement (&lt;div&gt;)} element which will hold the type-specific input element(s) to be filled by the fillEditorContainer method</li>
    * <li>buttonsContainer {HTMLelement (&lt;div&gt;)} element which holds the &lt;button&gt; elements for Ok/Cancel.  If you don't want any of the buttons, hide it via CSS styles, don't destroy it</li>
    * <li>node {YAHOO.widget.Node} reference to the Node being edited</li>
    * <li>saveOnEnter {boolean}, whether the Enter key should be accepted as a Save command (Esc. is always taken as Cancel), disable for multi-line input elements </li>
    * <li>oldValue {any}  value before editing</li>
    * </ul>
    *  Editors are free to use this object to store additional data.
     * @property editorData
     * @static
     * @for YAHOO.widget.TreeView
     */
    TV.editorData = {
        active:false,
        whoHasIt:null, // which TreeView has it
        nodeType:null,
        editorPanel:null,
        inputContainer:null,
        buttonsContainer:null,
        node:null, // which Node is being edited
        saveOnEnter:true,
        oldValue:undefined
        // Each node type is free to add its own properties to this as it sees fit.
    };

    /**
    * Validator function for edited data, called from the TreeView instance scope,
    * receives the arguments (newValue, oldValue, nodeInstance)
    * and returns either the validated (or type-converted) value or undefined.
    * An undefined return will prevent the editor from closing
    * @property validator
    * @type function
    * @default null
     * @for YAHOO.widget.TreeView
     */
    TVproto.validator = null;

    /**
    * Entry point for initializing the editing plug-in.
    * TreeView will call this method on initializing if it exists
    * @method _initEditor
     * @for YAHOO.widget.TreeView
     * @private
    */

    TVproto._initEditor = function () {
        /**
        * Fires when the user clicks on the ok button of a node editor
        * @event editorSaveEvent
        * @type CustomEvent
        * @param oArgs.newValue {mixed} the new value just entered
        * @param oArgs.oldValue {mixed} the value originally in the tree
        * @param oArgs.node {YAHOO.widget.Node} the node that has the focus
            * @for YAHOO.widget.TreeView
        */
        this.createEvent("editorSaveEvent", this);

        /**
        * Fires when the user clicks on the cancel button of a node editor
        * @event editorCancelEvent
        * @type CustomEvent
        * @param {YAHOO.widget.Node} node the node that has the focus
            * @for YAHOO.widget.TreeView
        */
        this.createEvent("editorCancelEvent", this);

    };

    /**
    * Entry point of the editing plug-in.
    * TreeView will call this method if it exists when a node label is clicked
    * @method _nodeEditing
    * @param node {YAHOO.widget.Node} the node to be edited
    * @return {Boolean} true to indicate that the node is editable and prevent any further bubbling of the click.
     * @for YAHOO.widget.TreeView
     * @private
    */



    TVproto._nodeEditing = function (node) {
        if (node.fillEditorContainer && node.editable) {
            var ed, topLeft, buttons, button, editorData = TV.editorData;
            editorData.active = true;
            editorData.whoHasIt = this;
            if (!editorData.nodeType) {
                // Fixes: http://yuilibrary.com/projects/yui2/ticket/2528945
                editorData.editorPanel = ed = this.getEl().appendChild(document.createElement('div'));
                Dom.addClass(ed,'ygtv-label-editor');
                ed.tabIndex = 0;

                buttons = editorData.buttonsContainer = ed.appendChild(document.createElement('div'));
                Dom.addClass(buttons,'ygtv-button-container');
                button = buttons.appendChild(document.createElement('button'));
                Dom.addClass(button,'ygtvok');
                button.innerHTML = ' ';
                button = buttons.appendChild(document.createElement('button'));
                Dom.addClass(button,'ygtvcancel');
                button.innerHTML = ' ';
                Event.on(buttons, 'click', function (ev) {
                    var target = Event.getTarget(ev),
                        editorData = TV.editorData,
                        node = editorData.node,
                        self = editorData.whoHasIt;
                    self.logger.log('click on editor');
                    if (Dom.hasClass(target,'ygtvok')) {
                        node.logger.log('ygtvok');
                        Event.stopEvent(ev);
                        self._closeEditor(true);
                    }
                    if (Dom.hasClass(target,'ygtvcancel')) {
                        node.logger.log('ygtvcancel');
                        Event.stopEvent(ev);
                        self._closeEditor(false);
                    }
                });

                editorData.inputContainer = ed.appendChild(document.createElement('div'));
                Dom.addClass(editorData.inputContainer,'ygtv-input');

                Event.on(ed,'keydown',function (ev) {
                    var editorData = TV.editorData,
                        KEY = YAHOO.util.KeyListener.KEY,
                        self = editorData.whoHasIt;
                    switch (ev.keyCode) {
                        case KEY.ENTER:
                            self.logger.log('ENTER');
                            Event.stopEvent(ev);
                            if (editorData.saveOnEnter) {
                                self._closeEditor(true);
                            }
                            break;
                        case KEY.ESCAPE:
                            self.logger.log('ESC');
                            Event.stopEvent(ev);
                            self._closeEditor(false);
                            break;
                    }
                });



            } else {
                ed = editorData.editorPanel;
            }
            editorData.node = node;
            if (editorData.nodeType) {
                Dom.removeClass(ed,'ygtv-edit-' + editorData.nodeType);
            }
            Dom.addClass(ed,' ygtv-edit-' + node._type);
            // Fixes: http://yuilibrary.com/projects/yui2/ticket/2528945
            Dom.setStyle(ed,'display','block');
            Dom.setXY(ed,Dom.getXY(node.getContentEl()));
            // up to here
            ed.focus();
            node.fillEditorContainer(editorData);

            return true;  // If inline editor available, don't do anything else.
        }
    };

    /**
    * Method to be associated with an event (clickEvent, dblClickEvent or enterKeyPressed) to pop up the contents editor
    *  It calls the corresponding node editNode method.
    * @method onEventEditNode
    * @param oArgs {object} Object passed as arguments to TreeView event listeners
    * @for YAHOO.widget.TreeView
    */

    TVproto.onEventEditNode = function (oArgs) {
        if (oArgs instanceof YAHOO.widget.Node) {
            oArgs.editNode();
        } else if (oArgs.node instanceof YAHOO.widget.Node) {
            oArgs.node.editNode();
        }
        return false;
    };

    /**
    * Method to be called when the inline editing is finished and the editor is to be closed
    * @method _closeEditor
    * @param save {Boolean} true if the edited value is to be saved, false if discarded
    * @private
     * @for YAHOO.widget.TreeView
    */

    TVproto._closeEditor = function (save) {
        var ed = TV.editorData,
            node = ed.node,
            close = true;
        // http://yuilibrary.com/projects/yui2/ticket/2528946
        // _closeEditor might now be called at any time, even when there is no label editor open
        // so we need to ensure there is one.
        if (!node || !ed.active) { return; }
        if (save) {
            close = ed.node.saveEditorValue(ed) !== false;
        } else {
            this.fireEvent( 'editorCancelEvent', node);
        }

        if (close) {
            Dom.setStyle(ed.editorPanel,'display','none');
            ed.active = false;
            node.focus();
        }
    };

    /**
    *  Entry point for TreeView's destroy method to destroy whatever the editing plug-in has created
    * @method _destroyEditor
    * @private
     * @for YAHOO.widget.TreeView
    */
    TVproto._destroyEditor = function() {
        var ed = TV.editorData;
        if (ed && ed.nodeType && (!ed.active || ed.whoHasIt === this)) {
            Event.removeListener(ed.editorPanel,'keydown');
            Event.removeListener(ed.buttonContainer,'click');
            ed.node.destroyEditorContents(ed);
            document.body.removeChild(ed.editorPanel);
            ed.nodeType = ed.editorPanel = ed.inputContainer = ed.buttonsContainer = ed.whoHasIt = ed.node = null;
            ed.active = false;
        }
    };

    var Nproto = YAHOO.widget.Node.prototype;

    /**
    * Signals if the label is editable.  (Ignored on TextNodes with href set.)
    * @property editable
    * @type boolean
         * @for YAHOO.widget.Node
    */
    Nproto.editable = false;

    /**
    * pops up the contents editor, if there is one and the node is declared editable
    * @method editNode
     * @for YAHOO.widget.Node
    */

    Nproto.editNode = function () {
        this.tree._nodeEditing(this);
    };


    /** Placeholder for a function that should provide the inline node label editor.
     *   Leaving it set to null will indicate that this node type is not editable.
     * It should be overridden by nodes that provide inline editing.
     *  The Node-specific editing element (input box, textarea or whatever) should be inserted into editorData.inputContainer.
     * @method fillEditorContainer
     * @param editorData {YAHOO.widget.TreeView.editorData}  a shortcut to the static object holding editing information
     * @return void
     * @for YAHOO.widget.Node
     */
    Nproto.fillEditorContainer = null;


    /**
    * Node-specific destroy function to empty the contents of the inline editor panel.
    * This function is the worst case alternative that will purge all possible events and remove the editor contents.
    * Method Event.purgeElement is somewhat costly so if it can be replaced by specifc Event.removeListeners, it is better to do so.
    * @method destroyEditorContents
     * @param editorData {YAHOO.widget.TreeView.editorData}  a shortcut to the static object holding editing information
     * @for YAHOO.widget.Node
     */
    Nproto.destroyEditorContents = function (editorData) {
        // In the worst case, if the input editor (such as the Calendar) has no destroy method
        // we can only try to remove all possible events on it.
        Event.purgeElement(editorData.inputContainer,true);
        editorData.inputContainer.innerHTML = '';
    };

    /**
    * Saves the value entered into the editor.
    * @method saveEditorValue
     * @param editorData {YAHOO.widget.TreeView.editorData}  a shortcut to the static object holding editing information
     * @return {false or none} a return of exactly false will prevent the editor from closing
     * @for YAHOO.widget.Node
     */
    Nproto.saveEditorValue = function (editorData) {
        var node = editorData.node,
            value,
            validator = node.tree.validator;

        value = this.getEditorValue(editorData);

        if (Lang.isFunction(validator)) {
            value = validator(value,editorData.oldValue,node);
            if (Lang.isUndefined(value)) {
                return false;
            }
        }

        if (this.tree.fireEvent( 'editorSaveEvent', {
            newValue:value,
            oldValue:editorData.oldValue,
            node:node
        }) !== false) {
            this.displayEditedValue(value,editorData);
        }
    };


    /**
     * Returns the value(s) from the input element(s) .
     * Should be overridden by each node type.
     * @method getEditorValue
     * @param editorData {YAHOO.widget.TreeView.editorData}  a shortcut to the static object holding editing information
     * @return {any} value entered
     * @for YAHOO.widget.Node
     */

     Nproto.getEditorValue = function (editorData) {
    };

    /**
     * Finally displays the newly edited value(s) in the tree.
     * Should be overridden by each node type.
     * @method displayEditedValue
     * @param value {HTML} value to be displayed and stored in the node
     * This data is added to the node unescaped via the innerHTML property.
     * @param editorData {YAHOO.widget.TreeView.editorData}  a shortcut to the static object holding editing information
     * @for YAHOO.widget.Node
     */
    Nproto.displayEditedValue = function (value,editorData) {
    };

    var TNproto = YAHOO.widget.TextNode.prototype;



    /**
     *  Places an &lt;input&gt;  textbox in the input container and loads the label text into it.
     * @method fillEditorContainer
     * @param editorData {YAHOO.widget.TreeView.editorData}  a shortcut to the static object holding editing information
     * @return void
     * @for YAHOO.widget.TextNode
     */
    TNproto.fillEditorContainer = function (editorData) {

        var input;
        // If last node edited is not of the same type as this one, delete it and fill it with our editor
        if (editorData.nodeType != this._type) {
            editorData.nodeType = this._type;
            editorData.saveOnEnter = true;
            editorData.node.destroyEditorContents(editorData);

            editorData.inputElement = input = editorData.inputContainer.appendChild(document.createElement('input'));

        } else {
            // if the last node edited was of the same time, reuse the input element.
            input = editorData.inputElement;
        }
        editorData.oldValue = this.label;
        input.value = this.label;
        input.focus();
        input.select();
    };

    /**
     * Returns the value from the input element.
     * Overrides Node.getEditorValue.
     * @method getEditorValue
     * @param editorData {YAHOO.widget.TreeView.editorData}  a shortcut to the static object holding editing information
     * @return {string} value entered
     * @for YAHOO.widget.TextNode
     */

    TNproto.getEditorValue = function (editorData) {
        return editorData.inputElement.value;
    };

    /**
     * Finally displays the newly edited value in the tree.
     * Overrides Node.displayEditedValue.
     * @method displayEditedValue
     * @param value {string} value to be displayed and stored in the node
     * @param editorData {YAHOO.widget.TreeView.editorData}  a shortcut to the static object holding editing information
     * @for YAHOO.widget.TextNode
     */
    TNproto.displayEditedValue = function (value,editorData) {
        var node = editorData.node;
        node.label = value;
        node.getLabelEl().innerHTML = value;
    };

    /**
    * Destroys the contents of the inline editor panel.
    * Overrides Node.destroyEditorContent.
    * Since we didn't set any event listeners on this inline editor, it is more efficient to avoid the generic method in Node.
    * @method destroyEditorContents
     * @param editorData {YAHOO.widget.TreeView.editorData}  a shortcut to the static object holding editing information
     * @for YAHOO.widget.TextNode
     */
    TNproto.destroyEditorContents = function (editorData) {
        editorData.inputContainer.innerHTML = '';
    };
})();
/**
 * A static factory class for tree view expand/collapse animations
 * @class TVAnim
 * @static
 */
YAHOO.widget.TVAnim = function() {
    return {
        /**
         * Constant for the fade in animation
         * @property FADE_IN
         * @type string
         * @static
         */
        FADE_IN: "TVFadeIn",

        /**
         * Constant for the fade out animation
         * @property FADE_OUT
         * @type string
         * @static
         */
        FADE_OUT: "TVFadeOut",

        /**
         * Returns a ygAnim instance of the given type
         * @method getAnim
         * @param type {string} the type of animation
         * @param el {HTMLElement} the element to element (probably the children div)
         * @param callback {function} function to invoke when the animation is done.
         * @return {YAHOO.util.Animation} the animation instance
         * @static
         */
        getAnim: function(type, el, callback) {
            if (YAHOO.widget[type]) {
                return new YAHOO.widget[type](el, callback);
            } else {
                return null;
            }
        },

        /**
         * Returns true if the specified animation class is available
         * @method isValid
         * @param type {string} the type of animation
         * @return {boolean} true if valid, false if not
         * @static
         */
        isValid: function(type) {
            return (YAHOO.widget[type]);
        }
    };
} ();
/**
 * A 1/2 second fade-in animation.
 * @class TVFadeIn
 * @constructor
 * @param el {HTMLElement} the element to animate
 * @param callback {function} function to invoke when the animation is finished
 */
YAHOO.widget.TVFadeIn = function(el, callback) {
    /**
     * The element to animate
     * @property el
     * @type HTMLElement
     */
    this.el = el;

    /**
     * the callback to invoke when the animation is complete
     * @property callback
     * @type function
     */
    this.callback = callback;

    this.logger = new YAHOO.widget.LogWriter(this.toString());
};

YAHOO.widget.TVFadeIn.prototype = {
    /**
     * Performs the animation
     * @method animate
     */
    animate: function() {
        var tvanim = this;

        var s = this.el.style;
        s.opacity = 0.1;
        s.filter = "alpha(opacity=10)";
        s.display = "";

        var dur = 0.4; 
        var a = new YAHOO.util.Anim(this.el, {opacity: {from: 0.1, to: 1, unit:""}}, dur);
        a.onComplete.subscribe( function() { tvanim.onComplete(); } );
        a.animate();
    },

    /**
     * Clean up and invoke callback
     * @method onComplete
     */
    onComplete: function() {
        this.callback();
    },

    /**
     * toString
     * @method toString
     * @return {string} the string representation of the instance
     */
    toString: function() {
        return "TVFadeIn";
    }
};
/**
 * A 1/2 second fade out animation.
 * @class TVFadeOut
 * @constructor
 * @param el {HTMLElement} the element to animate
 * @param callback {Function} function to invoke when the animation is finished
 */
YAHOO.widget.TVFadeOut = function(el, callback) {
    /**
     * The element to animate
     * @property el
     * @type HTMLElement
     */
    this.el = el;

    /**
     * the callback to invoke when the animation is complete
     * @property callback
     * @type function
     */
    this.callback = callback;

    this.logger = new YAHOO.widget.LogWriter(this.toString());
};

YAHOO.widget.TVFadeOut.prototype = {
    /**
     * Performs the animation
     * @method animate
     */
    animate: function() {
        var tvanim = this;
        var dur = 0.4;
        var a = new YAHOO.util.Anim(this.el, {opacity: {from: 1, to: 0.1, unit:""}}, dur);
        a.onComplete.subscribe( function() { tvanim.onComplete(); } );
        a.animate();
    },

    /**
     * Clean up and invoke callback
     * @method onComplete
     */
    onComplete: function() {
        var s = this.el.style;
        s.display = "none";
        s.opacity = 1;
        s.filter = "alpha(opacity=100)";
        this.callback();
    },

    /**
     * toString
     * @method toString
     * @return {string} the string representation of the instance
     */
    toString: function() {
        return "TVFadeOut";
    }
};
YAHOO.register("treeview", YAHOO.widget.TreeView, {version: "2.9.0", build: "2800"});
