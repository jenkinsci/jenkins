/*
Copyright (c) 2007, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.net/yui/license.txt
version: 2.3.1
*/
 /**
 * The AutoComplete control provides the front-end logic for text-entry suggestion and
 * completion functionality.
 *
 * @module autocomplete
 * @requires yahoo, dom, event, datasource
 * @optional animation, connection
 * @namespace YAHOO.widget
 * @title AutoComplete Widget
 */

/****************************************************************************/
/****************************************************************************/
/****************************************************************************/

/**
 * The AutoComplete class provides the customizable functionality of a plug-and-play DHTML
 * auto completion widget.  Some key features:
 * <ul>
 * <li>Navigate with up/down arrow keys and/or mouse to pick a selection</li>
 * <li>The drop down container can "roll down" or "fly out" via configurable
 * animation</li>
 * <li>UI look-and-feel customizable through CSS, including container
 * attributes, borders, position, fonts, etc</li>
 * </ul>
 *
 * @class AutoComplete
 * @constructor
 * @param elInput {HTMLElement} DOM element reference of an input field.
 * @param elInput {String} String ID of an input field.
 * @param elContainer {HTMLElement} DOM element reference of an existing DIV.
 * @param elContainer {String} String ID of an existing DIV.
 * @param oDataSource {YAHOO.widget.DataSource} DataSource instance.
 * @param oConfigs {Object} (optional) Object literal of configuration params.
 */
YAHOO.widget.AutoComplete = function(elInput,elContainer,oDataSource,oConfigs) {
    if(elInput && elContainer && oDataSource) {
        // Validate DataSource
        if(oDataSource instanceof YAHOO.widget.DataSource) {
            this.dataSource = oDataSource;
        }
        else {
            YAHOO.log("Could not instantiate AutoComplete due to an invalid DataSource", "error", this.toString());
            return;
        }

        // Validate input element
        if(YAHOO.util.Dom.inDocument(elInput)) {
            if(YAHOO.lang.isString(elInput)) {
                    this._sName = "instance" + YAHOO.widget.AutoComplete._nIndex + " " + elInput;
                    this._oTextbox = document.getElementById(elInput);
            }
            else {
                this._sName = (elInput.id) ?
                    "instance" + YAHOO.widget.AutoComplete._nIndex + " " + elInput.id:
                    "instance" + YAHOO.widget.AutoComplete._nIndex;
                this._oTextbox = elInput;
            }
            YAHOO.util.Dom.addClass(this._oTextbox, "yui-ac-input");
        }
        else {
            YAHOO.log("Could not instantiate AutoComplete due to an invalid input element", "error", this.toString());
            return;
        }

        // Validate container element
        if(YAHOO.util.Dom.inDocument(elContainer)) {
            if(YAHOO.lang.isString(elContainer)) {
                    this._oContainer = document.getElementById(elContainer);
            }
            else {
                this._oContainer = elContainer;
            }
            if(this._oContainer.style.display == "none") {
                YAHOO.log("The container may not display properly if display is set to \"none\" in CSS", "warn", this.toString());
            }
            
            // For skinning
            var elParent = this._oContainer.parentNode;
            var elTag = elParent.tagName.toLowerCase();
            if(elTag == "div") {
                YAHOO.util.Dom.addClass(elParent, "yui-ac");
            }
            else {
                YAHOO.log("Could not find the wrapper element for skinning", "warn", this.toString());
            }
        }
        else {
            YAHOO.log("Could not instantiate AutoComplete due to an invalid container element", "error", this.toString());
            return;
        }

        // Set any config params passed in to override defaults
        if(oConfigs && (oConfigs.constructor == Object)) {
            for(var sConfig in oConfigs) {
                if(sConfig) {
                    this[sConfig] = oConfigs[sConfig];
                }
            }
        }

        // Initialization sequence
        this._initContainer();
        this._initProps();
        this._initList();
        this._initContainerHelpers();

        // Set up events
        var oSelf = this;
        var oTextbox = this._oTextbox;
        // Events are actually for the content module within the container
        var oContent = this._oContainer._oContent;

        // Dom events
        YAHOO.util.Event.addListener(oTextbox,"keyup",oSelf._onTextboxKeyUp,oSelf);
        YAHOO.util.Event.addListener(oTextbox,"keydown",oSelf._onTextboxKeyDown,oSelf);
        YAHOO.util.Event.addListener(oTextbox,"focus",oSelf._onTextboxFocus,oSelf);
        YAHOO.util.Event.addListener(oTextbox,"blur",oSelf._onTextboxBlur,oSelf);
        YAHOO.util.Event.addListener(oContent,"mouseover",oSelf._onContainerMouseover,oSelf);
        YAHOO.util.Event.addListener(oContent,"mouseout",oSelf._onContainerMouseout,oSelf);
        YAHOO.util.Event.addListener(oContent,"scroll",oSelf._onContainerScroll,oSelf);
        YAHOO.util.Event.addListener(oContent,"resize",oSelf._onContainerResize,oSelf);
        if(oTextbox.form) {
            YAHOO.util.Event.addListener(oTextbox.form,"submit",oSelf._onFormSubmit,oSelf);
        }
        YAHOO.util.Event.addListener(oTextbox,"keypress",oSelf._onTextboxKeyPress,oSelf);

        // Custom events
        this.textboxFocusEvent = new YAHOO.util.CustomEvent("textboxFocus", this);
        this.textboxKeyEvent = new YAHOO.util.CustomEvent("textboxKey", this);
        this.dataRequestEvent = new YAHOO.util.CustomEvent("dataRequest", this);
        this.dataReturnEvent = new YAHOO.util.CustomEvent("dataReturn", this);
        this.dataErrorEvent = new YAHOO.util.CustomEvent("dataError", this);
        this.containerExpandEvent = new YAHOO.util.CustomEvent("containerExpand", this);
        this.typeAheadEvent = new YAHOO.util.CustomEvent("typeAhead", this);
        this.itemMouseOverEvent = new YAHOO.util.CustomEvent("itemMouseOver", this);
        this.itemMouseOutEvent = new YAHOO.util.CustomEvent("itemMouseOut", this);
        this.itemArrowToEvent = new YAHOO.util.CustomEvent("itemArrowTo", this);
        this.itemArrowFromEvent = new YAHOO.util.CustomEvent("itemArrowFrom", this);
        this.itemSelectEvent = new YAHOO.util.CustomEvent("itemSelect", this);
        this.unmatchedItemSelectEvent = new YAHOO.util.CustomEvent("unmatchedItemSelect", this);
        this.selectionEnforceEvent = new YAHOO.util.CustomEvent("selectionEnforce", this);
        this.containerCollapseEvent = new YAHOO.util.CustomEvent("containerCollapse", this);
        this.textboxBlurEvent = new YAHOO.util.CustomEvent("textboxBlur", this);
        
        // Finish up
        oTextbox.setAttribute("autocomplete","off");
        YAHOO.widget.AutoComplete._nIndex++;
        YAHOO.log("AutoComplete initialized","info",this.toString());
    }
    // Required arguments were not found
    else {
        YAHOO.log("Could not instantiate AutoComplete due invalid arguments", "error", this.toString());
    }
};

/////////////////////////////////////////////////////////////////////////////
//
// Public member variables
//
/////////////////////////////////////////////////////////////////////////////

/**
 * The DataSource object that encapsulates the data used for auto completion.
 * This object should be an inherited object from YAHOO.widget.DataSource.
 *
 * @property dataSource
 * @type YAHOO.widget.DataSource
 */
YAHOO.widget.AutoComplete.prototype.dataSource = null;

/**
 * Number of characters that must be entered before querying for results. A negative value
 * effectively turns off the widget. A value of 0 allows queries of null or empty string
 * values.
 *
 * @property minQueryLength
 * @type Number
 * @default 1
 */
YAHOO.widget.AutoComplete.prototype.minQueryLength = 1;

/**
 * Maximum number of results to display in results container.
 *
 * @property maxResultsDisplayed
 * @type Number
 * @default 10
 */
YAHOO.widget.AutoComplete.prototype.maxResultsDisplayed = 10;

/**
 * Number of seconds to delay before submitting a query request.  If a query
 * request is received before a previous one has completed its delay, the
 * previous request is cancelled and the new request is set to the delay.
 * Implementers should take care when setting this value very low (i.e., less
 * than 0.2) with low latency DataSources and the typeAhead feature enabled, as
 * fast typers may see unexpected behavior.
 *
 * @property queryDelay
 * @type Number
 * @default 0.2
 */
YAHOO.widget.AutoComplete.prototype.queryDelay = 0.2;

/**
 * Class name of a highlighted item within results container.
 *
 * @property highlightClassName
 * @type String
 * @default "yui-ac-highlight"
 */
YAHOO.widget.AutoComplete.prototype.highlightClassName = "yui-ac-highlight";

/**
 * Class name of a pre-highlighted item within results container.
 *
 * @property prehighlightClassName
 * @type String
 */
YAHOO.widget.AutoComplete.prototype.prehighlightClassName = null;

/**
 * Query delimiter. A single character separator for multiple delimited
 * selections. Multiple delimiter characteres may be defined as an array of
 * strings. A null value or empty string indicates that query results cannot
 * be delimited. This feature is not recommended if you need forceSelection to
 * be true.
 *
 * @property delimChar
 * @type String | String[]
 */
YAHOO.widget.AutoComplete.prototype.delimChar = null;

/**
 * Whether or not the first item in results container should be automatically highlighted
 * on expand.
 *
 * @property autoHighlight
 * @type Boolean
 * @default true
 */
YAHOO.widget.AutoComplete.prototype.autoHighlight = true;

/**
 * Whether or not the input field should be automatically updated
 * with the first query result as the user types, auto-selecting the substring
 * that the user has not typed.
 *
 * @property typeAhead
 * @type Boolean
 * @default false
 */
YAHOO.widget.AutoComplete.prototype.typeAhead = false;

/**
 * Whether or not to animate the expansion/collapse of the results container in the
 * horizontal direction.
 *
 * @property animHoriz
 * @type Boolean
 * @default false
 */
YAHOO.widget.AutoComplete.prototype.animHoriz = false;

/**
 * Whether or not to animate the expansion/collapse of the results container in the
 * vertical direction.
 *
 * @property animVert
 * @type Boolean
 * @default true
 */
YAHOO.widget.AutoComplete.prototype.animVert = true;

/**
 * Speed of container expand/collapse animation, in seconds..
 *
 * @property animSpeed
 * @type Number
 * @default 0.3
 */
YAHOO.widget.AutoComplete.prototype.animSpeed = 0.3;

/**
 * Whether or not to force the user's selection to match one of the query
 * results. Enabling this feature essentially transforms the input field into a
 * &lt;select&gt; field. This feature is not recommended with delimiter character(s)
 * defined.
 *
 * @property forceSelection
 * @type Boolean
 * @default false
 */
YAHOO.widget.AutoComplete.prototype.forceSelection = false;

/**
 * Whether or not to allow browsers to cache user-typed input in the input
 * field. Disabling this feature will prevent the widget from setting the
 * autocomplete="off" on the input field. When autocomplete="off"
 * and users click the back button after form submission, user-typed input can
 * be prefilled by the browser from its cache. This caching of user input may
 * not be desired for sensitive data, such as credit card numbers, in which
 * case, implementers should consider setting allowBrowserAutocomplete to false.
 *
 * @property allowBrowserAutocomplete
 * @type Boolean
 * @default true
 */
YAHOO.widget.AutoComplete.prototype.allowBrowserAutocomplete = true;

/**
 * Whether or not the results container should always be displayed.
 * Enabling this feature displays the container when the widget is instantiated
 * and prevents the toggling of the container to a collapsed state.
 *
 * @property alwaysShowContainer
 * @type Boolean
 * @default false
 */
YAHOO.widget.AutoComplete.prototype.alwaysShowContainer = false;

/**
 * Whether or not to use an iFrame to layer over Windows form elements in
 * IE. Set to true only when the results container will be on top of a
 * &lt;select&gt; field in IE and thus exposed to the IE z-index bug (i.e.,
 * 5.5 < IE < 7).
 *
 * @property useIFrame
 * @type Boolean
 * @default false
 */
YAHOO.widget.AutoComplete.prototype.useIFrame = false;

/**
 * Whether or not the results container should have a shadow.
 *
 * @property useShadow
 * @type Boolean
 * @default false
 */
YAHOO.widget.AutoComplete.prototype.useShadow = false;

/////////////////////////////////////////////////////////////////////////////
//
// Public methods
//
/////////////////////////////////////////////////////////////////////////////

 /**
 * Public accessor to the unique name of the AutoComplete instance.
 *
 * @method toString
 * @return {String} Unique name of the AutoComplete instance.
 */
YAHOO.widget.AutoComplete.prototype.toString = function() {
    return "AutoComplete " + this._sName;
};

 /**
 * Returns true if container is in an expanded state, false otherwise.
 *
 * @method isContainerOpen
 * @return {Boolean} Returns true if container is in an expanded state, false otherwise.
 */
YAHOO.widget.AutoComplete.prototype.isContainerOpen = function() {
    return this._bContainerOpen;
};

/**
 * Public accessor to the internal array of DOM &lt;li&gt; elements that
 * display query results within the results container.
 *
 * @method getListItems
 * @return {HTMLElement[]} Array of &lt;li&gt; elements within the results container.
 */
YAHOO.widget.AutoComplete.prototype.getListItems = function() {
    return this._aListItems;
};

/**
 * Public accessor to the data held in an &lt;li&gt; element of the
 * results container.
 *
 * @method getListItemData
 * @return {Object | Object[]} Object or array of result data or null
 */
YAHOO.widget.AutoComplete.prototype.getListItemData = function(oListItem) {
    if(oListItem._oResultData) {
        return oListItem._oResultData;
    }
    else {
        return false;
    }
};

/**
 * Sets HTML markup for the results container header. This markup will be
 * inserted within a &lt;div&gt; tag with a class of "yui-ac-hd".
 *
 * @method setHeader
 * @param sHeader {String} HTML markup for results container header.
 */
YAHOO.widget.AutoComplete.prototype.setHeader = function(sHeader) {
    if(sHeader) {
        if(this._oContainer._oContent._oHeader) {
            this._oContainer._oContent._oHeader.innerHTML = sHeader;
            this._oContainer._oContent._oHeader.style.display = "block";
        }
    }
    else {
        this._oContainer._oContent._oHeader.innerHTML = "";
        this._oContainer._oContent._oHeader.style.display = "none";
    }
};

/**
 * Sets HTML markup for the results container footer. This markup will be
 * inserted within a &lt;div&gt; tag with a class of "yui-ac-ft".
 *
 * @method setFooter
 * @param sFooter {String} HTML markup for results container footer.
 */
YAHOO.widget.AutoComplete.prototype.setFooter = function(sFooter) {
    if(sFooter) {
        if(this._oContainer._oContent._oFooter) {
            this._oContainer._oContent._oFooter.innerHTML = sFooter;
            this._oContainer._oContent._oFooter.style.display = "block";
        }
    }
    else {
        this._oContainer._oContent._oFooter.innerHTML = "";
        this._oContainer._oContent._oFooter.style.display = "none";
    }
};

/**
 * Sets HTML markup for the results container body. This markup will be
 * inserted within a &lt;div&gt; tag with a class of "yui-ac-bd".
 *
 * @method setBody
 * @param sBody {String} HTML markup for results container body.
 */
YAHOO.widget.AutoComplete.prototype.setBody = function(sBody) {
    if(sBody) {
        if(this._oContainer._oContent._oBody) {
            this._oContainer._oContent._oBody.innerHTML = sBody;
            this._oContainer._oContent._oBody.style.display = "block";
            this._oContainer._oContent.style.display = "block";
        }
    }
    else {
        this._oContainer._oContent._oBody.innerHTML = "";
        this._oContainer._oContent.style.display = "none";
    }
    this._maxResultsDisplayed = 0;
};

/**
 * Overridable method that converts a result item object into HTML markup
 * for display. Return data values are accessible via the oResultItem object,
 * and the key return value will always be oResultItem[0]. Markup will be
 * displayed within &lt;li&gt; element tags in the container.
 *
 * @method formatResult
 * @param oResultItem {Object} Result item representing one query result. Data is held in an array.
 * @param sQuery {String} The current query string.
 * @return {String} HTML markup of formatted result data.
 */
YAHOO.widget.AutoComplete.prototype.formatResult = function(oResultItem, sQuery) {
    var sResult = oResultItem[0];
    if(sResult) {
        return sResult;
    }
    else {
        return "";
    }
};

/**
 * Overridable method called before container expands allows implementers to access data
 * and DOM elements.
 *
 * @method doBeforeExpandContainer
 * @param oTextbox {HTMLElement} The text input box.
 * @param oContainer {HTMLElement} The container element.
 * @param sQuery {String} The query string.
 * @param aResults {Object[]}  An array of query results.
 * @return {Boolean} Return true to continue expanding container, false to cancel the expand.
 */
YAHOO.widget.AutoComplete.prototype.doBeforeExpandContainer = function(oTextbox, oContainer, sQuery, aResults) {
    return true;
};

/**
 * Makes query request to the DataSource.
 *
 * @method sendQuery
 * @param sQuery {String} Query string.
 */
YAHOO.widget.AutoComplete.prototype.sendQuery = function(sQuery) {
    this._sendQuery(sQuery);
};

/**
 * Overridable method gives implementers access to the query before it gets sent.
 *
 * @method doBeforeSendQuery
 * @param sQuery {String} Query string.
 * @return {String} Query string.
 */
YAHOO.widget.AutoComplete.prototype.doBeforeSendQuery = function(sQuery) {
    return sQuery;
};

/**
 * Nulls out the entire AutoComplete instance and related objects, removes attached
 * event listeners, and clears out DOM elements inside the container. After
 * calling this method, the instance reference should be expliclitly nulled by
 * implementer, as in myDataTable = null. Use with caution!
 *
 * @method destroy
 */
YAHOO.widget.AutoComplete.prototype.destroy = function() {
    var instanceName = this.toString();
    var elInput = this._oTextbox;
    var elContainer = this._oContainer;

    // Unhook custom events
    this.textboxFocusEvent.unsubscribe();
    this.textboxKeyEvent.unsubscribe();
    this.dataRequestEvent.unsubscribe();
    this.dataReturnEvent.unsubscribe();
    this.dataErrorEvent.unsubscribe();
    this.containerExpandEvent.unsubscribe();
    this.typeAheadEvent.unsubscribe();
    this.itemMouseOverEvent.unsubscribe();
    this.itemMouseOutEvent.unsubscribe();
    this.itemArrowToEvent.unsubscribe();
    this.itemArrowFromEvent.unsubscribe();
    this.itemSelectEvent.unsubscribe();
    this.unmatchedItemSelectEvent.unsubscribe();
    this.selectionEnforceEvent.unsubscribe();
    this.containerCollapseEvent.unsubscribe();
    this.textboxBlurEvent.unsubscribe();

    // Unhook DOM events
    YAHOO.util.Event.purgeElement(elInput, true);
    YAHOO.util.Event.purgeElement(elContainer, true);

    // Remove DOM elements
    elContainer.innerHTML = "";

    // Null out objects
    for(var key in this) {
        if(YAHOO.lang.hasOwnProperty(this, key)) {
            this[key] = null;
        }
    }

    YAHOO.log("AutoComplete instance destroyed: " + instanceName);
};

/////////////////////////////////////////////////////////////////////////////
//
// Public events
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Fired when the input field receives focus.
 *
 * @event textboxFocusEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 */
YAHOO.widget.AutoComplete.prototype.textboxFocusEvent = null;

/**
 * Fired when the input field receives key input.
 *
 * @event textboxKeyEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param nKeycode {Number} The keycode number.
 */
YAHOO.widget.AutoComplete.prototype.textboxKeyEvent = null;

/**
 * Fired when the AutoComplete instance makes a query to the DataSource.
 * 
 * @event dataRequestEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param sQuery {String} The query string.
 */
YAHOO.widget.AutoComplete.prototype.dataRequestEvent = null;

/**
 * Fired when the AutoComplete instance receives query results from the data
 * source.
 *
 * @event dataReturnEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param sQuery {String} The query string.
 * @param aResults {Object[]} Results array.
 */
YAHOO.widget.AutoComplete.prototype.dataReturnEvent = null;

/**
 * Fired when the AutoComplete instance does not receive query results from the
 * DataSource due to an error.
 *
 * @event dataErrorEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param sQuery {String} The query string.
 */
YAHOO.widget.AutoComplete.prototype.dataErrorEvent = null;

/**
 * Fired when the results container is expanded.
 *
 * @event containerExpandEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 */
YAHOO.widget.AutoComplete.prototype.containerExpandEvent = null;

/**
 * Fired when the input field has been prefilled by the type-ahead
 * feature. 
 *
 * @event typeAheadEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param sQuery {String} The query string.
 * @param sPrefill {String} The prefill string.
 */
YAHOO.widget.AutoComplete.prototype.typeAheadEvent = null;

/**
 * Fired when result item has been moused over.
 *
 * @event itemMouseOverEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param elItem {HTMLElement} The &lt;li&gt element item moused to.
 */
YAHOO.widget.AutoComplete.prototype.itemMouseOverEvent = null;

/**
 * Fired when result item has been moused out.
 *
 * @event itemMouseOutEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param elItem {HTMLElement} The &lt;li&gt; element item moused from.
 */
YAHOO.widget.AutoComplete.prototype.itemMouseOutEvent = null;

/**
 * Fired when result item has been arrowed to. 
 *
 * @event itemArrowToEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param elItem {HTMLElement} The &lt;li&gt; element item arrowed to.
 */
YAHOO.widget.AutoComplete.prototype.itemArrowToEvent = null;

/**
 * Fired when result item has been arrowed away from.
 *
 * @event itemArrowFromEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param elItem {HTMLElement} The &lt;li&gt; element item arrowed from.
 */
YAHOO.widget.AutoComplete.prototype.itemArrowFromEvent = null;

/**
 * Fired when an item is selected via mouse click, ENTER key, or TAB key.
 *
 * @event itemSelectEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param elItem {HTMLElement} The selected &lt;li&gt; element item.
 * @param oData {Object} The data returned for the item, either as an object,
 * or mapped from the schema into an array.
 */
YAHOO.widget.AutoComplete.prototype.itemSelectEvent = null;

/**
 * Fired when a user selection does not match any of the displayed result items.
 *
 * @event unmatchedItemSelectEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 */
YAHOO.widget.AutoComplete.prototype.unmatchedItemSelectEvent = null;

/**
 * Fired if forceSelection is enabled and the user's input has been cleared
 * because it did not match one of the returned query results.
 *
 * @event selectionEnforceEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 */
YAHOO.widget.AutoComplete.prototype.selectionEnforceEvent = null;

/**
 * Fired when the results container is collapsed.
 *
 * @event containerCollapseEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 */
YAHOO.widget.AutoComplete.prototype.containerCollapseEvent = null;

/**
 * Fired when the input field loses focus.
 *
 * @event textboxBlurEvent
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 */
YAHOO.widget.AutoComplete.prototype.textboxBlurEvent = null;

/////////////////////////////////////////////////////////////////////////////
//
// Private member variables
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Internal class variable to index multiple AutoComplete instances.
 *
 * @property _nIndex
 * @type Number
 * @default 0
 * @private
 */
YAHOO.widget.AutoComplete._nIndex = 0;

/**
 * Name of AutoComplete instance.
 *
 * @property _sName
 * @type String
 * @private
 */
YAHOO.widget.AutoComplete.prototype._sName = null;

/**
 * Text input field DOM element.
 *
 * @property _oTextbox
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._oTextbox = null;

/**
 * Whether or not the input field is currently in focus. If query results come back
 * but the user has already moved on, do not proceed with auto complete behavior.
 *
 * @property _bFocused
 * @type Boolean
 * @private
 */
YAHOO.widget.AutoComplete.prototype._bFocused = true;

/**
 * Animation instance for container expand/collapse.
 *
 * @property _oAnim
 * @type Boolean
 * @private
 */
YAHOO.widget.AutoComplete.prototype._oAnim = null;

/**
 * Container DOM element.
 *
 * @property _oContainer
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._oContainer = null;

/**
 * Whether or not the results container is currently open.
 *
 * @property _bContainerOpen
 * @type Boolean
 * @private
 */
YAHOO.widget.AutoComplete.prototype._bContainerOpen = false;

/**
 * Whether or not the mouse is currently over the results
 * container. This is necessary in order to prevent clicks on container items
 * from being text input field blur events.
 *
 * @property _bOverContainer
 * @type Boolean
 * @private
 */
YAHOO.widget.AutoComplete.prototype._bOverContainer = false;

/**
 * Array of &lt;li&gt; elements references that contain query results within the
 * results container.
 *
 * @property _aListItems
 * @type HTMLElement[]
 * @private
 */
YAHOO.widget.AutoComplete.prototype._aListItems = null;

/**
 * Number of &lt;li&gt; elements currently displayed in results container.
 *
 * @property _nDisplayedItems
 * @type Number
 * @private
 */
YAHOO.widget.AutoComplete.prototype._nDisplayedItems = 0;

/**
 * Internal count of &lt;li&gt; elements displayed and hidden in results container.
 *
 * @property _maxResultsDisplayed
 * @type Number
 * @private
 */
YAHOO.widget.AutoComplete.prototype._maxResultsDisplayed = 0;

/**
 * Current query string
 *
 * @property _sCurQuery
 * @type String
 * @private
 */
YAHOO.widget.AutoComplete.prototype._sCurQuery = null;

/**
 * Past queries this session (for saving delimited queries).
 *
 * @property _sSavedQuery
 * @type String
 * @private
 */
YAHOO.widget.AutoComplete.prototype._sSavedQuery = null;

/**
 * Pointer to the currently highlighted &lt;li&gt; element in the container.
 *
 * @property _oCurItem
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._oCurItem = null;

/**
 * Whether or not an item has been selected since the container was populated
 * with results. Reset to false by _populateList, and set to true when item is
 * selected.
 *
 * @property _bItemSelected
 * @type Boolean
 * @private
 */
YAHOO.widget.AutoComplete.prototype._bItemSelected = false;

/**
 * Key code of the last key pressed in textbox.
 *
 * @property _nKeyCode
 * @type Number
 * @private
 */
YAHOO.widget.AutoComplete.prototype._nKeyCode = null;

/**
 * Delay timeout ID.
 *
 * @property _nDelayID
 * @type Number
 * @private
 */
YAHOO.widget.AutoComplete.prototype._nDelayID = -1;

/**
 * Src to iFrame used when useIFrame = true. Supports implementations over SSL
 * as well.
 *
 * @property _iFrameSrc
 * @type String
 * @private
 */
YAHOO.widget.AutoComplete.prototype._iFrameSrc = "javascript:false;";

/**
 * For users typing via certain IMEs, queries must be triggered by intervals,
 * since key events yet supported across all browsers for all IMEs.
 *
 * @property _queryInterval
 * @type Object
 * @private
 */
YAHOO.widget.AutoComplete.prototype._queryInterval = null;

/**
 * Internal tracker to last known textbox value, used to determine whether or not
 * to trigger a query via interval for certain IME users.
 *
 * @event _sLastTextboxValue
 * @type String
 * @private
 */
YAHOO.widget.AutoComplete.prototype._sLastTextboxValue = null;

/////////////////////////////////////////////////////////////////////////////
//
// Private methods
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Updates and validates latest public config properties.
 *
 * @method __initProps
 * @private
 */
YAHOO.widget.AutoComplete.prototype._initProps = function() {
    // Correct any invalid values
    var minQueryLength = this.minQueryLength;
    if(!YAHOO.lang.isNumber(minQueryLength)) {
        this.minQueryLength = 1;
    }
    var maxResultsDisplayed = this.maxResultsDisplayed;
    if(!YAHOO.lang.isNumber(maxResultsDisplayed) || (maxResultsDisplayed < 1)) {
        this.maxResultsDisplayed = 10;
    }
    var queryDelay = this.queryDelay;
    if(!YAHOO.lang.isNumber(queryDelay) || (queryDelay < 0)) {
        this.queryDelay = 0.2;
    }
    var delimChar = this.delimChar;
    if(YAHOO.lang.isString(delimChar) && (delimChar.length > 0)) {
        this.delimChar = [delimChar];
    }
    else if(!YAHOO.lang.isArray(delimChar)) {
        this.delimChar = null;
    }
    var animSpeed = this.animSpeed;
    if((this.animHoriz || this.animVert) && YAHOO.util.Anim) {
        if(!YAHOO.lang.isNumber(animSpeed) || (animSpeed < 0)) {
            this.animSpeed = 0.3;
        }
        if(!this._oAnim ) {
            this._oAnim = new YAHOO.util.Anim(this._oContainer._oContent, {}, this.animSpeed);
        }
        else {
            this._oAnim.duration = this.animSpeed;
        }
    }
    if(this.forceSelection && delimChar) {
        YAHOO.log("The forceSelection feature has been enabled with delimChar defined.","warn", this.toString());
    }
};

/**
 * Initializes the results container helpers if they are enabled and do
 * not exist
 *
 * @method _initContainerHelpers
 * @private
 */
YAHOO.widget.AutoComplete.prototype._initContainerHelpers = function() {
    if(this.useShadow && !this._oContainer._oShadow) {
        var oShadow = document.createElement("div");
        oShadow.className = "yui-ac-shadow";
        this._oContainer._oShadow = this._oContainer.appendChild(oShadow);
    }
    if(this.useIFrame && !this._oContainer._oIFrame) {
        var oIFrame = document.createElement("iframe");
        oIFrame.src = this._iFrameSrc;
        oIFrame.frameBorder = 0;
        oIFrame.scrolling = "no";
        oIFrame.style.position = "absolute";
        oIFrame.style.width = "100%";
        oIFrame.style.height = "100%";
        oIFrame.tabIndex = -1;
        this._oContainer._oIFrame = this._oContainer.appendChild(oIFrame);
    }
};

/**
 * Initializes the results container once at object creation
 *
 * @method _initContainer
 * @private
 */
YAHOO.widget.AutoComplete.prototype._initContainer = function() {
    YAHOO.util.Dom.addClass(this._oContainer, "yui-ac-container");
    
    if(!this._oContainer._oContent) {
        // The oContent div helps size the iframe and shadow properly
        var oContent = document.createElement("div");
        oContent.className = "yui-ac-content";
        oContent.style.display = "none";
        this._oContainer._oContent = this._oContainer.appendChild(oContent);

        var oHeader = document.createElement("div");
        oHeader.className = "yui-ac-hd";
        oHeader.style.display = "none";
        this._oContainer._oContent._oHeader = this._oContainer._oContent.appendChild(oHeader);

        var oBody = document.createElement("div");
        oBody.className = "yui-ac-bd";
        this._oContainer._oContent._oBody = this._oContainer._oContent.appendChild(oBody);

        var oFooter = document.createElement("div");
        oFooter.className = "yui-ac-ft";
        oFooter.style.display = "none";
        this._oContainer._oContent._oFooter = this._oContainer._oContent.appendChild(oFooter);
    }
    else {
        YAHOO.log("Could not initialize the container","warn",this.toString());
    }
};

/**
 * Clears out contents of container body and creates up to
 * YAHOO.widget.AutoComplete#maxResultsDisplayed &lt;li&gt; elements in an
 * &lt;ul&gt; element.
 *
 * @method _initList
 * @private
 */
YAHOO.widget.AutoComplete.prototype._initList = function() {
    this._aListItems = [];
    while(this._oContainer._oContent._oBody.hasChildNodes()) {
        var oldListItems = this.getListItems();
        if(oldListItems) {
            for(var oldi = oldListItems.length-1; oldi >= 0; oldi--) {
                oldListItems[oldi] = null;
            }
        }
        this._oContainer._oContent._oBody.innerHTML = "";
    }

    var oList = document.createElement("ul");
    oList = this._oContainer._oContent._oBody.appendChild(oList);
    for(var i=0; i<this.maxResultsDisplayed; i++) {
        var oItem = document.createElement("li");
        oItem = oList.appendChild(oItem);
        this._aListItems[i] = oItem;
        this._initListItem(oItem, i);
    }
    this._maxResultsDisplayed = this.maxResultsDisplayed;
};

/**
 * Initializes each &lt;li&gt; element in the container list.
 *
 * @method _initListItem
 * @param oItem {HTMLElement} The &lt;li&gt; DOM element.
 * @param nItemIndex {Number} The index of the element.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._initListItem = function(oItem, nItemIndex) {
    var oSelf = this;
    oItem.style.display = "none";
    oItem._nItemIndex = nItemIndex;

    oItem.mouseover = oItem.mouseout = oItem.onclick = null;
    YAHOO.util.Event.addListener(oItem,"mouseover",oSelf._onItemMouseover,oSelf);
    YAHOO.util.Event.addListener(oItem,"mouseout",oSelf._onItemMouseout,oSelf);
    YAHOO.util.Event.addListener(oItem,"click",oSelf._onItemMouseclick,oSelf);
};

/**
 * Enables interval detection for  Korean IME support.
 *
 * @method _onIMEDetected
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onIMEDetected = function(oSelf) {
    oSelf._enableIntervalDetection();
};

/**
 * Enables query triggers based on text input detection by intervals (rather
 * than by key events).
 *
 * @method _enableIntervalDetection
 * @private
 */
YAHOO.widget.AutoComplete.prototype._enableIntervalDetection = function() {
    var currValue = this._oTextbox.value;
    var lastValue = this._sLastTextboxValue;
    if(currValue != lastValue) {
        this._sLastTextboxValue = currValue;
        this._sendQuery(currValue);
    }
};


/**
 * Cancels text input detection by intervals.
 *
 * @method _cancelIntervalDetection
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._cancelIntervalDetection = function(oSelf) {
    if(oSelf._queryInterval) {
        clearInterval(oSelf._queryInterval);
    }
};


/**
 * Whether or not key is functional or should be ignored. Note that the right
 * arrow key is NOT an ignored key since it triggers queries for certain intl
 * charsets.
 *
 * @method _isIgnoreKey
 * @param nKeycode {Number} Code of key pressed.
 * @return {Boolean} True if key should be ignored, false otherwise.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._isIgnoreKey = function(nKeyCode) {
    if((nKeyCode == 9) || (nKeyCode == 13)  || // tab, enter
            (nKeyCode == 16) || (nKeyCode == 17) || // shift, ctl
            (nKeyCode >= 18 && nKeyCode <= 20) || // alt,pause/break,caps lock
            (nKeyCode == 27) || // esc
            (nKeyCode >= 33 && nKeyCode <= 35) || // page up,page down,end
            /*(nKeyCode >= 36 && nKeyCode <= 38) || // home,left,up
            (nKeyCode == 40) || // down*/
            (nKeyCode >= 36 && nKeyCode <= 40) || // home,left,up, right, down
            (nKeyCode >= 44 && nKeyCode <= 45)) { // print screen,insert
        return true;
    }
    return false;
};

/**
 * Makes query request to the DataSource.
 *
 * @method _sendQuery
 * @param sQuery {String} Query string.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._sendQuery = function(sQuery) {
    // Widget has been effectively turned off
    if(this.minQueryLength == -1) {
        this._toggleContainer(false);
        YAHOO.log("Property minQueryLength is set to -1", "info", this.toString());
        return;
    }
    // Delimiter has been enabled
    var aDelimChar = (this.delimChar) ? this.delimChar : null;
    if(aDelimChar) {
        // Loop through all possible delimiters and find the latest one
        // A " " may be a false positive if they are defined as delimiters AND
        // are used to separate delimited queries
        var nDelimIndex = -1;
        for(var i = aDelimChar.length-1; i >= 0; i--) {
            var nNewIndex = sQuery.lastIndexOf(aDelimChar[i]);
            if(nNewIndex > nDelimIndex) {
                nDelimIndex = nNewIndex;
            }
        }
        // If we think the last delimiter is a space (" "), make sure it is NOT
        // a false positive by also checking the char directly before it
        if(aDelimChar[i] == " ") {
            for (var j = aDelimChar.length-1; j >= 0; j--) {
                if(sQuery[nDelimIndex - 1] == aDelimChar[j]) {
                    nDelimIndex--;
                    break;
                }
            }
        }
        // A delimiter has been found so extract the latest query
        if(nDelimIndex > -1) {
            var nQueryStart = nDelimIndex + 1;
            // Trim any white space from the beginning...
            while(sQuery.charAt(nQueryStart) == " ") {
                nQueryStart += 1;
            }
            // ...and save the rest of the string for later
            this._sSavedQuery = sQuery.substring(0,nQueryStart);
            // Here is the query itself
            sQuery = sQuery.substr(nQueryStart);
        }
        else if(sQuery.indexOf(this._sSavedQuery) < 0){
            this._sSavedQuery = null;
        }
    }

    // Don't search queries that are too short
    if((sQuery && (sQuery.length < this.minQueryLength)) || (!sQuery && this.minQueryLength > 0)) {
        if(this._nDelayID != -1) {
            clearTimeout(this._nDelayID);
        }
        this._toggleContainer(false);
        YAHOO.log("Query \"" + sQuery + "\" is too short", "info", this.toString());
        return;
    }

    sQuery = encodeURIComponent(sQuery);
    this._nDelayID = -1;    // Reset timeout ID because request has been made
    sQuery = this.doBeforeSendQuery(sQuery);
    this.dataRequestEvent.fire(this, sQuery);
    YAHOO.log("Sending query \"" + sQuery + "\"", "info", this.toString());
    this.dataSource.getResults(this._populateList, sQuery, this);
};

/**
 * Populates the array of &lt;li&gt; elements in the container with query
 * results. This method is passed to YAHOO.widget.DataSource#getResults as a
 * callback function so results from the DataSource instance are returned to the
 * AutoComplete instance.
 *
 * @method _populateList
 * @param sQuery {String} The query string.
 * @param aResults {Object[]} An array of query result objects from the DataSource.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._populateList = function(sQuery, aResults, oSelf) {
    if(aResults === null) {
        oSelf.dataErrorEvent.fire(oSelf, sQuery);
    }
    if(!oSelf._bFocused || !aResults) {
        YAHOO.log("Could not populate list", "info", oSelf.toString());
        return;
    }

    var isOpera = (navigator.userAgent.toLowerCase().indexOf("opera") != -1);
    var contentStyle = oSelf._oContainer._oContent.style;
    contentStyle.width = (!isOpera) ? null : "";
    contentStyle.height = (!isOpera) ? null : "";

    var sCurQuery = decodeURIComponent(sQuery);
    oSelf._sCurQuery = sCurQuery;
    oSelf._bItemSelected = false;

    if(oSelf._maxResultsDisplayed != oSelf.maxResultsDisplayed) {
        oSelf._initList();
    }

    var nItems = Math.min(aResults.length,oSelf.maxResultsDisplayed);
    oSelf._nDisplayedItems = nItems;
    if(nItems > 0) {
        oSelf._initContainerHelpers();
        var aItems = oSelf._aListItems;

        // Fill items with data
        for(var i = nItems-1; i >= 0; i--) {
            var oItemi = aItems[i];
            var oResultItemi = aResults[i];
            oItemi.innerHTML = oSelf.formatResult(oResultItemi, sCurQuery);
            oItemi.style.display = "list-item";
            oItemi._sResultKey = oResultItemi[0];
            oItemi._oResultData = oResultItemi;

        }

        // Empty out remaining items if any
        for(var j = aItems.length-1; j >= nItems ; j--) {
            var oItemj = aItems[j];
            oItemj.innerHTML = null;
            oItemj.style.display = "none";
            oItemj._sResultKey = null;
            oItemj._oResultData = null;
        }

        // Expand the container
        var ok = oSelf.doBeforeExpandContainer(oSelf._oTextbox, oSelf._oContainer, sQuery, aResults);
        oSelf._toggleContainer(ok);
        
        if(oSelf.autoHighlight) {
            // Go to the first item
            var oFirstItem = aItems[0];
            oSelf._toggleHighlight(oFirstItem,"to");
            oSelf.itemArrowToEvent.fire(oSelf, oFirstItem);
            YAHOO.log("Arrowed to first item", "info", oSelf.toString());
            oSelf._typeAhead(oFirstItem,sQuery);
        }
        else {
            oSelf._oCurItem = null;
        }
    }
    else {
        oSelf._toggleContainer(false);
    }
    oSelf.dataReturnEvent.fire(oSelf, sQuery, aResults);
    YAHOO.log("Container populated with list items", "info", oSelf.toString());
    
};

/**
 * When forceSelection is true and the user attempts
 * leave the text input box without selecting an item from the query results,
 * the user selection is cleared.
 *
 * @method _clearSelection
 * @private
 */
YAHOO.widget.AutoComplete.prototype._clearSelection = function() {
    var sValue = this._oTextbox.value;
    var sChar = (this.delimChar) ? this.delimChar[0] : null;
    var nIndex = (sChar) ? sValue.lastIndexOf(sChar, sValue.length-2) : -1;
    if(nIndex > -1) {
        this._oTextbox.value = sValue.substring(0,nIndex);
    }
    else {
         this._oTextbox.value = "";
    }
    this._sSavedQuery = this._oTextbox.value;

    // Fire custom event
    this.selectionEnforceEvent.fire(this);
    YAHOO.log("Selection enforced", "info", this.toString());
};

/**
 * Whether or not user-typed value in the text input box matches any of the
 * query results.
 *
 * @method _textMatchesOption
 * @return {HTMLElement} Matching list item element if user-input text matches
 * a result, null otherwise.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._textMatchesOption = function() {
    var foundMatch = null;

    for(var i = this._nDisplayedItems-1; i >= 0 ; i--) {
        var oItem = this._aListItems[i];
        var sMatch = oItem._sResultKey.toLowerCase();
        if(sMatch == this._sCurQuery.toLowerCase()) {
            foundMatch = oItem;
            break;
        }
    }
    return(foundMatch);
};

/**
 * Updates in the text input box with the first query result as the user types,
 * selecting the substring that the user has not typed.
 *
 * @method _typeAhead
 * @param oItem {HTMLElement} The &lt;li&gt; element item whose data populates the input field.
 * @param sQuery {String} Query string.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._typeAhead = function(oItem, sQuery) {
    // Don't update if turned off
    if(!this.typeAhead || (this._nKeyCode == 8)) {
        return;
    }

    var oTextbox = this._oTextbox;
    var sValue = this._oTextbox.value; // any saved queries plus what user has typed

    // Don't update with type-ahead if text selection is not supported
    if(!oTextbox.setSelectionRange && !oTextbox.createTextRange) {
        return;
    }

    // Select the portion of text that the user has not typed
    var nStart = sValue.length;
    this._updateValue(oItem);
    var nEnd = oTextbox.value.length;
    this._selectText(oTextbox,nStart,nEnd);
    var sPrefill = oTextbox.value.substr(nStart,nEnd);
    this.typeAheadEvent.fire(this,sQuery,sPrefill);
    YAHOO.log("Typeahead occured with prefill string \"" + sPrefill + "\"", "info", this.toString());
};

/**
 * Selects text in the input field.
 *
 * @method _selectText
 * @param oTextbox {HTMLElement} Text input box element in which to select text.
 * @param nStart {Number} Starting index of text string to select.
 * @param nEnd {Number} Ending index of text selection.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._selectText = function(oTextbox, nStart, nEnd) {
    if(oTextbox.setSelectionRange) { // For Mozilla
        oTextbox.setSelectionRange(nStart,nEnd);
    }
    else if(oTextbox.createTextRange) { // For IE
        var oTextRange = oTextbox.createTextRange();
        oTextRange.moveStart("character", nStart);
        oTextRange.moveEnd("character", nEnd-oTextbox.value.length);
        oTextRange.select();
    }
    else {
        oTextbox.select();
    }
};

/**
 * Syncs results container with its helpers.
 *
 * @method _toggleContainerHelpers
 * @param bShow {Boolean} True if container is expanded, false if collapsed
 * @private
 */
YAHOO.widget.AutoComplete.prototype._toggleContainerHelpers = function(bShow) {
    var bFireEvent = false;
    var width = this._oContainer._oContent.offsetWidth + "px";
    var height = this._oContainer._oContent.offsetHeight + "px";

    if(this.useIFrame && this._oContainer._oIFrame) {
        bFireEvent = true;
        if(bShow) {
            this._oContainer._oIFrame.style.width = width;
            this._oContainer._oIFrame.style.height = height;
        }
        else {
            this._oContainer._oIFrame.style.width = 0;
            this._oContainer._oIFrame.style.height = 0;
        }
    }
    if(this.useShadow && this._oContainer._oShadow) {
        bFireEvent = true;
        if(bShow) {
            this._oContainer._oShadow.style.width = width;
            this._oContainer._oShadow.style.height = height;
        }
        else {
           this._oContainer._oShadow.style.width = 0;
            this._oContainer._oShadow.style.height = 0;
        }
    }
};

/**
 * Animates expansion or collapse of the container.
 *
 * @method _toggleContainer
 * @param bShow {Boolean} True if container should be expanded, false if container should be collapsed
 * @private
 */
YAHOO.widget.AutoComplete.prototype._toggleContainer = function(bShow) {
    var oContainer = this._oContainer;

    // Implementer has container always open so don't mess with it
    if(this.alwaysShowContainer && this._bContainerOpen) {
        return;
    }
    
    // Clear contents of container
    if(!bShow) {
        this._oContainer._oContent.scrollTop = 0;
        var aItems = this._aListItems;

        if(aItems && (aItems.length > 0)) {
            for(var i = aItems.length-1; i >= 0 ; i--) {
                aItems[i].style.display = "none";
            }
        }

        if(this._oCurItem) {
            this._toggleHighlight(this._oCurItem,"from");
        }

        this._oCurItem = null;
        this._nDisplayedItems = 0;
        this._sCurQuery = null;
    }

    // Container is already closed
    if(!bShow && !this._bContainerOpen) {
        oContainer._oContent.style.display = "none";
        return;
    }

    // If animation is enabled...
    var oAnim = this._oAnim;
    if(oAnim && oAnim.getEl() && (this.animHoriz || this.animVert)) {
        // If helpers need to be collapsed, do it right away...
        // but if helpers need to be expanded, wait until after the container expands
        if(!bShow) {
            this._toggleContainerHelpers(bShow);
        }

        if(oAnim.isAnimated()) {
            oAnim.stop();
        }

        // Clone container to grab current size offscreen
        var oClone = oContainer._oContent.cloneNode(true);
        oContainer.appendChild(oClone);
        oClone.style.top = "-9000px";
        oClone.style.display = "block";

        // Current size of the container is the EXPANDED size
        var wExp = oClone.offsetWidth;
        var hExp = oClone.offsetHeight;

        // Calculate COLLAPSED sizes based on horiz and vert anim
        var wColl = (this.animHoriz) ? 0 : wExp;
        var hColl = (this.animVert) ? 0 : hExp;

        // Set animation sizes
        oAnim.attributes = (bShow) ?
            {width: { to: wExp }, height: { to: hExp }} :
            {width: { to: wColl}, height: { to: hColl }};

        // If opening anew, set to a collapsed size...
        if(bShow && !this._bContainerOpen) {
            oContainer._oContent.style.width = wColl+"px";
            oContainer._oContent.style.height = hColl+"px";
        }
        // Else, set it to its last known size.
        else {
            oContainer._oContent.style.width = wExp+"px";
            oContainer._oContent.style.height = hExp+"px";
        }

        oContainer.removeChild(oClone);
        oClone = null;

    	var oSelf = this;
    	var onAnimComplete = function() {
            // Finish the collapse
    		oAnim.onComplete.unsubscribeAll();

            if(bShow) {
                oSelf.containerExpandEvent.fire(oSelf);
                YAHOO.log("Container expanded", "info", oSelf.toString());
            }
            else {
                oContainer._oContent.style.display = "none";
                oSelf.containerCollapseEvent.fire(oSelf);
                YAHOO.log("Container collapsed", "info", oSelf.toString());
            }
            oSelf._toggleContainerHelpers(bShow);
     	};

        // Display container and animate it
        oContainer._oContent.style.display = "block";
        oAnim.onComplete.subscribe(onAnimComplete);
        oAnim.animate();
        this._bContainerOpen = bShow;
    }
    // Else don't animate, just show or hide
    else {
        if(bShow) {
            oContainer._oContent.style.display = "block";
            this.containerExpandEvent.fire(this);
            YAHOO.log("Container expanded", "info", this.toString());
        }
        else {
            oContainer._oContent.style.display = "none";
            this.containerCollapseEvent.fire(this);
            YAHOO.log("Container collapsed", "info", this.toString());
        }
        this._toggleContainerHelpers(bShow);
        this._bContainerOpen = bShow;
   }

};

/**
 * Toggles the highlight on or off for an item in the container, and also cleans
 * up highlighting of any previous item.
 *
 * @method _toggleHighlight
 * @param oNewItem {HTMLElement} The &lt;li&gt; element item to receive highlight behavior.
 * @param sType {String} Type "mouseover" will toggle highlight on, and "mouseout" will toggle highlight off.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._toggleHighlight = function(oNewItem, sType) {
    var sHighlight = this.highlightClassName;
    if(this._oCurItem) {
        // Remove highlight from old item
        YAHOO.util.Dom.removeClass(this._oCurItem, sHighlight);
    }

    if((sType == "to") && sHighlight) {
        // Apply highlight to new item
        YAHOO.util.Dom.addClass(oNewItem, sHighlight);
        this._oCurItem = oNewItem;
    }
};

/**
 * Toggles the pre-highlight on or off for an item in the container.
 *
 * @method _togglePrehighlight
 * @param oNewItem {HTMLElement} The &lt;li&gt; element item to receive highlight behavior.
 * @param sType {String} Type "mouseover" will toggle highlight on, and "mouseout" will toggle highlight off.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._togglePrehighlight = function(oNewItem, sType) {
    if(oNewItem == this._oCurItem) {
        return;
    }

    var sPrehighlight = this.prehighlightClassName;
    if((sType == "mouseover") && sPrehighlight) {
        // Apply prehighlight to new item
        YAHOO.util.Dom.addClass(oNewItem, sPrehighlight);
    }
    else {
        // Remove prehighlight from old item
        YAHOO.util.Dom.removeClass(oNewItem, sPrehighlight);
    }
};

/**
 * Updates the text input box value with selected query result. If a delimiter
 * has been defined, then the value gets appended with the delimiter.
 *
 * @method _updateValue
 * @param oItem {HTMLElement} The &lt;li&gt; element item with which to update the value.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._updateValue = function(oItem) {
    var oTextbox = this._oTextbox;
    var sDelimChar = (this.delimChar) ? (this.delimChar[0] || this.delimChar) : null;
    var sSavedQuery = this._sSavedQuery;
    var sResultKey = oItem._sResultKey;
    oTextbox.focus();

    // First clear text field
    oTextbox.value = "";
    // Grab data to put into text field
    if(sDelimChar) {
        if(sSavedQuery) {
            oTextbox.value = sSavedQuery;
        }
        oTextbox.value += sResultKey + sDelimChar;
        if(sDelimChar != " ") {
            oTextbox.value += " ";
        }
    }
    else { oTextbox.value = sResultKey; }

    // scroll to bottom of textarea if necessary
    if(oTextbox.type == "textarea") {
        oTextbox.scrollTop = oTextbox.scrollHeight;
    }

    // move cursor to end
    var end = oTextbox.value.length;
    this._selectText(oTextbox,end,end);

    this._oCurItem = oItem;
};

/**
 * Selects a result item from the container
 *
 * @method _selectItem
 * @param oItem {HTMLElement} The selected &lt;li&gt; element item.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._selectItem = function(oItem) {
    this._bItemSelected = true;
    this._updateValue(oItem);
    this._cancelIntervalDetection(this);
    this.itemSelectEvent.fire(this, oItem, oItem._oResultData);
    YAHOO.log("Item selected: " + YAHOO.lang.dump(oItem._oResultData), "info", this.toString());
    this._toggleContainer(false);
};

/**
 * If an item is highlighted in the container, the right arrow key jumps to the
 * end of the textbox and selects the highlighted item, otherwise the container
 * is closed.
 *
 * @method _jumpSelection
 * @private
 */
YAHOO.widget.AutoComplete.prototype._jumpSelection = function() {
    if(this._oCurItem) {
        this._selectItem(this._oCurItem);
    }
    else {
        this._toggleContainer(false);
    }
};

/**
 * Triggered by up and down arrow keys, changes the current highlighted
 * &lt;li&gt; element item. Scrolls container if necessary.
 *
 * @method _moveSelection
 * @param nKeyCode {Number} Code of key pressed.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._moveSelection = function(nKeyCode) {
    if(this._bContainerOpen) {
        // Determine current item's id number
        var oCurItem = this._oCurItem;
        var nCurItemIndex = -1;

        if(oCurItem) {
            nCurItemIndex = oCurItem._nItemIndex;
        }

        var nNewItemIndex = (nKeyCode == 40) ?
                (nCurItemIndex + 1) : (nCurItemIndex - 1);

        // Out of bounds
        if(nNewItemIndex < -2 || nNewItemIndex >= this._nDisplayedItems) {
            return;
        }

        if(oCurItem) {
            // Unhighlight current item
            this._toggleHighlight(oCurItem, "from");
            this.itemArrowFromEvent.fire(this, oCurItem);
            YAHOO.log("Item arrowed from", "info", this.toString());
        }
        if(nNewItemIndex == -1) {
           // Go back to query (remove type-ahead string)
            if(this.delimChar && this._sSavedQuery) {
                if(!this._textMatchesOption()) {
                    this._oTextbox.value = this._sSavedQuery;
                }
                else {
                    this._oTextbox.value = this._sSavedQuery + this._sCurQuery;
                }
            }
            else {
                this._oTextbox.value = this._sCurQuery;
            }
            this._oCurItem = null;
            return;
        }
        if(nNewItemIndex == -2) {
            // Close container
            this._toggleContainer(false);
            return;
        }

        var oNewItem = this._aListItems[nNewItemIndex];

        // Scroll the container if necessary
        var oContent = this._oContainer._oContent;
        var scrollOn = ((YAHOO.util.Dom.getStyle(oContent,"overflow") == "auto") ||
            (YAHOO.util.Dom.getStyle(oContent,"overflowY") == "auto"));
        if(scrollOn && (nNewItemIndex > -1) &&
        (nNewItemIndex < this._nDisplayedItems)) {
            // User is keying down
            if(nKeyCode == 40) {
                // Bottom of selected item is below scroll area...
                if((oNewItem.offsetTop+oNewItem.offsetHeight) > (oContent.scrollTop + oContent.offsetHeight)) {
                    // Set bottom of scroll area to bottom of selected item
                    oContent.scrollTop = (oNewItem.offsetTop+oNewItem.offsetHeight) - oContent.offsetHeight;
                }
                // Bottom of selected item is above scroll area...
                else if((oNewItem.offsetTop+oNewItem.offsetHeight) < oContent.scrollTop) {
                    // Set top of selected item to top of scroll area
                    oContent.scrollTop = oNewItem.offsetTop;

                }
            }
            // User is keying up
            else {
                // Top of selected item is above scroll area
                if(oNewItem.offsetTop < oContent.scrollTop) {
                    // Set top of scroll area to top of selected item
                    this._oContainer._oContent.scrollTop = oNewItem.offsetTop;
                }
                // Top of selected item is below scroll area
                else if(oNewItem.offsetTop > (oContent.scrollTop + oContent.offsetHeight)) {
                    // Set bottom of selected item to bottom of scroll area
                    this._oContainer._oContent.scrollTop = (oNewItem.offsetTop+oNewItem.offsetHeight) - oContent.offsetHeight;
                }
            }
        }

        this._toggleHighlight(oNewItem, "to");
        this.itemArrowToEvent.fire(this, oNewItem);
        YAHOO.log("Item arrowed to", "info", this.toString());
        if(this.typeAhead) {
            this._updateValue(oNewItem);
        }
    }
};

/////////////////////////////////////////////////////////////////////////////
//
// Private event handlers
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Handles &lt;li&gt; element mouseover events in the container.
 *
 * @method _onItemMouseover
 * @param v {HTMLEvent} The mouseover event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onItemMouseover = function(v,oSelf) {
    if(oSelf.prehighlightClassName) {
        oSelf._togglePrehighlight(this,"mouseover");
    }
    else {
        oSelf._toggleHighlight(this,"to");
    }

    oSelf.itemMouseOverEvent.fire(oSelf, this);
    YAHOO.log("Item moused over", "info", oSelf.toString());
};

/**
 * Handles &lt;li&gt; element mouseout events in the container.
 *
 * @method _onItemMouseout
 * @param v {HTMLEvent} The mouseout event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onItemMouseout = function(v,oSelf) {
    if(oSelf.prehighlightClassName) {
        oSelf._togglePrehighlight(this,"mouseout");
    }
    else {
        oSelf._toggleHighlight(this,"from");
    }

    oSelf.itemMouseOutEvent.fire(oSelf, this);
    YAHOO.log("Item moused out", "info", oSelf.toString());
};

/**
 * Handles &lt;li&gt; element click events in the container.
 *
 * @method _onItemMouseclick
 * @param v {HTMLEvent} The click event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onItemMouseclick = function(v,oSelf) {
    // In case item has not been moused over
    oSelf._toggleHighlight(this,"to");
    oSelf._selectItem(this);
};

/**
 * Handles container mouseover events.
 *
 * @method _onContainerMouseover
 * @param v {HTMLEvent} The mouseover event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onContainerMouseover = function(v,oSelf) {
    oSelf._bOverContainer = true;
};

/**
 * Handles container mouseout events.
 *
 * @method _onContainerMouseout
 * @param v {HTMLEvent} The mouseout event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onContainerMouseout = function(v,oSelf) {
    oSelf._bOverContainer = false;
    // If container is still active
    if(oSelf._oCurItem) {
        oSelf._toggleHighlight(oSelf._oCurItem,"to");
    }
};

/**
 * Handles container scroll events.
 *
 * @method _onContainerScroll
 * @param v {HTMLEvent} The scroll event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onContainerScroll = function(v,oSelf) {
    oSelf._oTextbox.focus();
};

/**
 * Handles container resize events.
 *
 * @method _onContainerResize
 * @param v {HTMLEvent} The resize event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onContainerResize = function(v,oSelf) {
    oSelf._toggleContainerHelpers(oSelf._bContainerOpen);
};


/**
 * Handles textbox keydown events of functional keys, mainly for UI behavior.
 *
 * @method _onTextboxKeyDown
 * @param v {HTMLEvent} The keydown event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onTextboxKeyDown = function(v,oSelf) {
    var nKeyCode = v.keyCode;

    switch (nKeyCode) {
        case 9: // tab
            // select an item or clear out
            if(oSelf._oCurItem) {
                if(oSelf.delimChar && (oSelf._nKeyCode != nKeyCode)) {
                    if(oSelf._bContainerOpen) {
                        YAHOO.util.Event.stopEvent(v);
                    }
                }
                oSelf._selectItem(oSelf._oCurItem);
            }
            else {
                oSelf._toggleContainer(false);
            }
            break;
        case 13: // enter
            if(oSelf._oCurItem) {
                if(oSelf._nKeyCode != nKeyCode) {
                    if(oSelf._bContainerOpen) {
                        YAHOO.util.Event.stopEvent(v);
                    }
                }
                oSelf._selectItem(oSelf._oCurItem);
            }
            else {
                oSelf._toggleContainer(false);
            }
            break;
        case 27: // esc
            oSelf._toggleContainer(false);
            return;
        case 39: // right
            oSelf._jumpSelection();
            break;
        case 38: // up
            YAHOO.util.Event.stopEvent(v);
            oSelf._moveSelection(nKeyCode);
            break;
        case 40: // down
            YAHOO.util.Event.stopEvent(v);
            oSelf._moveSelection(nKeyCode);
            break;
        default:
            break;
    }
};

/**
 * Handles textbox keypress events.
 * @method _onTextboxKeyPress
 * @param v {HTMLEvent} The keypress event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onTextboxKeyPress = function(v,oSelf) {
    var nKeyCode = v.keyCode;

        //Expose only to Mac browsers, where stopEvent is ineffective on keydown events (bug 790337)
        var isMac = (navigator.userAgent.toLowerCase().indexOf("mac") != -1);
        if(isMac) {
            switch (nKeyCode) {
            case 9: // tab
                if(oSelf._oCurItem) {
                    if(oSelf.delimChar && (oSelf._nKeyCode != nKeyCode)) {
                        YAHOO.util.Event.stopEvent(v);
                    }
                }
                break;
            case 13: // enter
                if(oSelf._oCurItem) {
                    if(oSelf._nKeyCode != nKeyCode) {
                        if(oSelf._bContainerOpen) {
                            YAHOO.util.Event.stopEvent(v);
                        }
                    }
                }
                break;
            case 38: // up
            case 40: // down
                YAHOO.util.Event.stopEvent(v);
                break;
            default:
                break;
            }
        }

        //TODO: (?) limit only to non-IE, non-Mac-FF for Korean IME support (bug 811948)
        // Korean IME detected
        else if(nKeyCode == 229) {
            oSelf._queryInterval = setInterval(function() { oSelf._onIMEDetected(oSelf); },500);
        }
};

/**
 * Handles textbox keyup events that trigger queries.
 *
 * @method _onTextboxKeyUp
 * @param v {HTMLEvent} The keyup event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onTextboxKeyUp = function(v,oSelf) {
    // Check to see if any of the public properties have been updated
    oSelf._initProps();

    var nKeyCode = v.keyCode;
    oSelf._nKeyCode = nKeyCode;
    var sText = this.value; //string in textbox

    // Filter out chars that don't trigger queries
    if(oSelf._isIgnoreKey(nKeyCode) || (sText.toLowerCase() == oSelf._sCurQuery)) {
        return;
    }
    else {
        oSelf._bItemSelected = false;
        YAHOO.util.Dom.removeClass(oSelf._oCurItem,  oSelf.highlightClassName);
        oSelf._oCurItem = null;

        oSelf.textboxKeyEvent.fire(oSelf, nKeyCode);
        YAHOO.log("Textbox keyed", "info", oSelf.toString());
    }

    // Set timeout on the request
    if(oSelf.queryDelay > 0) {
        var nDelayID =
            setTimeout(function(){oSelf._sendQuery(sText);},(oSelf.queryDelay * 1000));

        if(oSelf._nDelayID != -1) {
            clearTimeout(oSelf._nDelayID);
        }

        oSelf._nDelayID = nDelayID;
    }
    else {
        // No delay so send request immediately
        oSelf._sendQuery(sText);
    }
};

/**
 * Handles text input box receiving focus.
 *
 * @method _onTextboxFocus
 * @param v {HTMLEvent} The focus event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onTextboxFocus = function (v,oSelf) {
    oSelf._oTextbox.setAttribute("autocomplete","off");
    oSelf._bFocused = true;
    if(!oSelf._bItemSelected) {
        oSelf.textboxFocusEvent.fire(oSelf);
        YAHOO.log("Textbox focused", "info", oSelf.toString());
    }
};

/**
 * Handles text input box losing focus.
 *
 * @method _onTextboxBlur
 * @param v {HTMLEvent} The focus event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onTextboxBlur = function (v,oSelf) {
    // Don't treat as a blur if it was a selection via mouse click
    if(!oSelf._bOverContainer || (oSelf._nKeyCode == 9)) {
        // Current query needs to be validated as a selection
        if(!oSelf._bItemSelected) {
            var oMatch = oSelf._textMatchesOption();
            // Container is closed or current query doesn't match any result
            if(!oSelf._bContainerOpen || (oSelf._bContainerOpen && (oMatch === null))) {
                // Force selection is enabled so clear the current query
                if(oSelf.forceSelection) {
                    oSelf._clearSelection();
                }
                // Treat current query as a valid selection
                else {
                    oSelf.unmatchedItemSelectEvent.fire(oSelf);
                    YAHOO.log("Unmatched item selected", "info", oSelf.toString());
                }
            }
            // Container is open and current query matches a result
            else {
                // Force a selection when textbox is blurred with a match
                if(oSelf.forceSelection) {
                    oSelf._selectItem(oMatch);
                }
            }
        }

        if(oSelf._bContainerOpen) {
            oSelf._toggleContainer(false);
        }
        oSelf._cancelIntervalDetection(oSelf);
        oSelf._bFocused = false;
        oSelf.textboxBlurEvent.fire(oSelf);
        YAHOO.log("Textbox blurred", "info", oSelf.toString());
    }
};

/**
 * Handles form submission event.
 *
 * @method _onFormSubmit
 * @param v {HTMLEvent} The submit event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onFormSubmit = function(v,oSelf) {
    if(oSelf.allowBrowserAutocomplete) {
        oSelf._oTextbox.setAttribute("autocomplete","on");
    }
    else {
        oSelf._oTextbox.setAttribute("autocomplete","off");
    }
};

/****************************************************************************/
/****************************************************************************/
/****************************************************************************/

/**
 * The DataSource classes manages sending a request and returning response from a live
 * database. Supported data include local JavaScript arrays and objects and databases
 * accessible via XHR connections. Supported response formats include JavaScript arrays,
 * JSON, XML, and flat-file textual data.
 *  
 * @class DataSource
 * @constructor
 */
YAHOO.widget.DataSource = function() { 
    /* abstract class */
};


/////////////////////////////////////////////////////////////////////////////
//
// Public constants
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Error message for null data responses.
 *
 * @property ERROR_DATANULL
 * @type String
 * @static
 * @final
 */
YAHOO.widget.DataSource.ERROR_DATANULL = "Response data was null";

/**
 * Error message for data responses with parsing errors.
 *
 * @property ERROR_DATAPARSE
 * @type String
 * @static
 * @final
 */
YAHOO.widget.DataSource.ERROR_DATAPARSE = "Response data could not be parsed";


/////////////////////////////////////////////////////////////////////////////
//
// Public member variables
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Max size of the local cache.  Set to 0 to turn off caching.  Caching is
 * useful to reduce the number of server connections.  Recommended only for data
 * sources that return comprehensive results for queries or when stale data is
 * not an issue.
 *
 * @property maxCacheEntries
 * @type Number
 * @default 15
 */
YAHOO.widget.DataSource.prototype.maxCacheEntries = 15;

/**
 * Use this to fine-tune the matching algorithm used against JS Array types of
 * DataSource and DataSource caches. If queryMatchContains is true, then the JS
 * Array or cache returns results that "contain" the query string. By default,
 * queryMatchContains is set to false, so that only results that "start with"
 * the query string are returned.
 *
 * @property queryMatchContains
 * @type Boolean
 * @default false
 */
YAHOO.widget.DataSource.prototype.queryMatchContains = false;

/**
 * Enables query subset matching. If caching is on and queryMatchSubset is
 * true, substrings of queries will return matching cached results. For
 * instance, if the first query is for "abc" susequent queries that start with
 * "abc", like "abcd", will be queried against the cache, and not the live data
 * source. Recommended only for DataSources that return comprehensive results
 * for queries with very few characters.
 *
 * @property queryMatchSubset
 * @type Boolean
 * @default false
 *
 */
YAHOO.widget.DataSource.prototype.queryMatchSubset = false;

/**
 * Enables case-sensitivity in the matching algorithm used against JS Array
 * types of DataSources and DataSource caches. If queryMatchCase is true, only
 * case-sensitive matches will return.
 *
 * @property queryMatchCase
 * @type Boolean
 * @default false
 */
YAHOO.widget.DataSource.prototype.queryMatchCase = false;


/////////////////////////////////////////////////////////////////////////////
//
// Public methods
//
/////////////////////////////////////////////////////////////////////////////

 /**
 * Public accessor to the unique name of the DataSource instance.
 *
 * @method toString
 * @return {String} Unique name of the DataSource instance
 */
YAHOO.widget.DataSource.prototype.toString = function() {
    return "DataSource " + this._sName;
};

/**
 * Retrieves query results, first checking the local cache, then making the
 * query request to the live data source as defined by the function doQuery.
 *
 * @method getResults
 * @param oCallbackFn {HTMLFunction} Callback function defined by oParent object to which to return results.
 * @param sQuery {String} Query string.
 * @param oParent {Object} The object instance that has requested data.
 */
YAHOO.widget.DataSource.prototype.getResults = function(oCallbackFn, sQuery, oParent) {
    
    // First look in cache
    var aResults = this._doQueryCache(oCallbackFn,sQuery,oParent);
    // Not in cache, so get results from server
    if(aResults.length === 0) {
        this.queryEvent.fire(this, oParent, sQuery);
        YAHOO.log("Query received \"" + sQuery, "info", this.toString());
        this.doQuery(oCallbackFn, sQuery, oParent);
    }
};

/**
 * Abstract method implemented by subclasses to make a query to the live data
 * source. Must call the callback function with the response returned from the
 * query. Populates cache (if enabled).
 *
 * @method doQuery
 * @param oCallbackFn {HTMLFunction} Callback function implemented by oParent to which to return results.
 * @param sQuery {String} Query string.
 * @param oParent {Object} The object instance that has requested data.
 */
YAHOO.widget.DataSource.prototype.doQuery = function(oCallbackFn, sQuery, oParent) {
    /* override this */ 
};

/**
 * Flushes cache.
 *
 * @method flushCache
 */
YAHOO.widget.DataSource.prototype.flushCache = function() {
    if(this._aCache) {
        this._aCache = [];
    }
    if(this._aCacheHelper) {
        this._aCacheHelper = [];
    }
    this.cacheFlushEvent.fire(this);
    YAHOO.log("Cache flushed", "info", this.toString());

};

/////////////////////////////////////////////////////////////////////////////
//
// Public events
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Fired when a query is made to the live data source.
 *
 * @event queryEvent
 * @param oSelf {Object} The DataSource instance.
 * @param oParent {Object} The requesting object.
 * @param sQuery {String} The query string.
 */
YAHOO.widget.DataSource.prototype.queryEvent = null;

/**
 * Fired when a query is made to the local cache.
 *
 * @event cacheQueryEvent
 * @param oSelf {Object} The DataSource instance.
 * @param oParent {Object} The requesting object.
 * @param sQuery {String} The query string.
 */
YAHOO.widget.DataSource.prototype.cacheQueryEvent = null;

/**
 * Fired when data is retrieved from the live data source.
 *
 * @event getResultsEvent
 * @param oSelf {Object} The DataSource instance.
 * @param oParent {Object} The requesting object.
 * @param sQuery {String} The query string.
 * @param aResults {Object[]} Array of result objects.
 */
YAHOO.widget.DataSource.prototype.getResultsEvent = null;
    
/**
 * Fired when data is retrieved from the local cache.
 *
 * @event getCachedResultsEvent
 * @param oSelf {Object} The DataSource instance.
 * @param oParent {Object} The requesting object.
 * @param sQuery {String} The query string.
 * @param aResults {Object[]} Array of result objects.
 */
YAHOO.widget.DataSource.prototype.getCachedResultsEvent = null;

/**
 * Fired when an error is encountered with the live data source.
 *
 * @event dataErrorEvent
 * @param oSelf {Object} The DataSource instance.
 * @param oParent {Object} The requesting object.
 * @param sQuery {String} The query string.
 * @param sMsg {String} Error message string
 */
YAHOO.widget.DataSource.prototype.dataErrorEvent = null;

/**
 * Fired when the local cache is flushed.
 *
 * @event cacheFlushEvent
 * @param oSelf {Object} The DataSource instance
 */
YAHOO.widget.DataSource.prototype.cacheFlushEvent = null;

/////////////////////////////////////////////////////////////////////////////
//
// Private member variables
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Internal class variable to index multiple DataSource instances.
 *
 * @property _nIndex
 * @type Number
 * @private
 * @static
 */
YAHOO.widget.DataSource._nIndex = 0;

/**
 * Name of DataSource instance.
 *
 * @property _sName
 * @type String
 * @private
 */
YAHOO.widget.DataSource.prototype._sName = null;

/**
 * Local cache of data result objects indexed chronologically.
 *
 * @property _aCache
 * @type Object[]
 * @private
 */
YAHOO.widget.DataSource.prototype._aCache = null;


/////////////////////////////////////////////////////////////////////////////
//
// Private methods
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Initializes DataSource instance.
 *  
 * @method _init
 * @private
 */
YAHOO.widget.DataSource.prototype._init = function() {
    // Validate and initialize public configs
    var maxCacheEntries = this.maxCacheEntries;
    if(!YAHOO.lang.isNumber(maxCacheEntries) || (maxCacheEntries < 0)) {
        maxCacheEntries = 0;
    }
    // Initialize local cache
    if(maxCacheEntries > 0 && !this._aCache) {
        this._aCache = [];
    }
    
    this._sName = "instance" + YAHOO.widget.DataSource._nIndex;
    YAHOO.widget.DataSource._nIndex++;
    
    this.queryEvent = new YAHOO.util.CustomEvent("query", this);
    this.cacheQueryEvent = new YAHOO.util.CustomEvent("cacheQuery", this);
    this.getResultsEvent = new YAHOO.util.CustomEvent("getResults", this);
    this.getCachedResultsEvent = new YAHOO.util.CustomEvent("getCachedResults", this);
    this.dataErrorEvent = new YAHOO.util.CustomEvent("dataError", this);
    this.cacheFlushEvent = new YAHOO.util.CustomEvent("cacheFlush", this);
};

/**
 * Adds a result object to the local cache, evicting the oldest element if the 
 * cache is full. Newer items will have higher indexes, the oldest item will have
 * index of 0. 
 *
 * @method _addCacheElem
 * @param oResult {Object} Data result object, including array of results.
 * @private
 */
YAHOO.widget.DataSource.prototype._addCacheElem = function(oResult) {
    var aCache = this._aCache;
    // Don't add if anything important is missing.
    if(!aCache || !oResult || !oResult.query || !oResult.results) {
        return;
    }
    
    // If the cache is full, make room by removing from index=0
    if(aCache.length >= this.maxCacheEntries) {
        aCache.shift();
    }
        
    // Add to cache, at the end of the array
    aCache.push(oResult);
};

/**
 * Queries the local cache for results. If query has been cached, the callback
 * function is called with the results, and the cached is refreshed so that it
 * is now the newest element.  
 *
 * @method _doQueryCache
 * @param oCallbackFn {HTMLFunction} Callback function defined by oParent object to which to return results.
 * @param sQuery {String} Query string.
 * @param oParent {Object} The object instance that has requested data.
 * @return aResults {Object[]} Array of results from local cache if found, otherwise null.
 * @private 
 */
YAHOO.widget.DataSource.prototype._doQueryCache = function(oCallbackFn, sQuery, oParent) {
    var aResults = [];
    var bMatchFound = false;
    var aCache = this._aCache;
    var nCacheLength = (aCache) ? aCache.length : 0;
    var bMatchContains = this.queryMatchContains;
    var sOrigQuery;
    
    // If cache is enabled...
    if((this.maxCacheEntries > 0) && aCache && (nCacheLength > 0)) {
        this.cacheQueryEvent.fire(this, oParent, sQuery);
        YAHOO.log("Querying cache: \"" + sQuery + "\"", "info", this.toString());
        // If case is unimportant, normalize query now instead of in loops
        if(!this.queryMatchCase) {
            sOrigQuery = sQuery;
            sQuery = sQuery.toLowerCase();
        }

        // Loop through each cached element's query property...
        for(var i = nCacheLength-1; i >= 0; i--) {
            var resultObj = aCache[i];
            var aAllResultItems = resultObj.results;
            // If case is unimportant, normalize match key for comparison
            var matchKey = (!this.queryMatchCase) ?
                encodeURIComponent(resultObj.query).toLowerCase():
                encodeURIComponent(resultObj.query);
            
            // If a cached match key exactly matches the query...
            if(matchKey == sQuery) {
                    // Stash all result objects into aResult[] and stop looping through the cache.
                    bMatchFound = true;
                    aResults = aAllResultItems;
                    
                    // The matching cache element was not the most recent,
                    // so now we need to refresh the cache.
                    if(i != nCacheLength-1) {                        
                        // Remove element from its original location
                        aCache.splice(i,1);
                        // Add element as newest
                        this._addCacheElem(resultObj);
                    }
                    break;
            }
            // Else if this query is not an exact match and subset matching is enabled...
            else if(this.queryMatchSubset) {
                // Loop through substrings of each cached element's query property...
                for(var j = sQuery.length-1; j >= 0 ; j--) {
                    var subQuery = sQuery.substr(0,j);
                    
                    // If a substring of a cached sQuery exactly matches the query...
                    if(matchKey == subQuery) {                    
                        bMatchFound = true;
                        
                        // Go through each cached result object to match against the query...
                        for(var k = aAllResultItems.length-1; k >= 0; k--) {
                            var aRecord = aAllResultItems[k];
                            var sKeyIndex = (this.queryMatchCase) ?
                                encodeURIComponent(aRecord[0]).indexOf(sQuery):
                                encodeURIComponent(aRecord[0]).toLowerCase().indexOf(sQuery);
                            
                            // A STARTSWITH match is when the query is found at the beginning of the key string...
                            if((!bMatchContains && (sKeyIndex === 0)) ||
                            // A CONTAINS match is when the query is found anywhere within the key string...
                            (bMatchContains && (sKeyIndex > -1))) {
                                // Stash a match into aResults[].
                                aResults.unshift(aRecord);
                            }
                        }
                        
                        // Add the subset match result set object as the newest element to cache,
                        // and stop looping through the cache.
                        resultObj = {};
                        resultObj.query = sQuery;
                        resultObj.results = aResults;
                        this._addCacheElem(resultObj);
                        break;
                    }
                }
                if(bMatchFound) {
                    break;
                }
            }
        }
        
        // If there was a match, send along the results.
        if(bMatchFound) {
            this.getCachedResultsEvent.fire(this, oParent, sOrigQuery, aResults);
            YAHOO.log("Cached results found for query \"" + sQuery + "\": " +
                    YAHOO.lang.dump(aResults), "info", this.toString());
            oCallbackFn(sOrigQuery, aResults, oParent);
        }
    }
    return aResults;
};


/****************************************************************************/
/****************************************************************************/
/****************************************************************************/

/**
 * Implementation of YAHOO.widget.DataSource using XML HTTP requests that return
 * query results.
 *  
 * @class DS_XHR
 * @extends YAHOO.widget.DataSource
 * @requires connection
 * @constructor
 * @param sScriptURI {String} Absolute or relative URI to script that returns query
 * results as JSON, XML, or delimited flat-file data.
 * @param aSchema {String[]} Data schema definition of results.
 * @param oConfigs {Object} (optional) Object literal of config params.
 */
YAHOO.widget.DS_XHR = function(sScriptURI, aSchema, oConfigs) {
    // Set any config params passed in to override defaults
    if(oConfigs && (oConfigs.constructor == Object)) {
        for(var sConfig in oConfigs) {
            this[sConfig] = oConfigs[sConfig];
        }
    }

    // Initialization sequence
    if(!YAHOO.lang.isArray(aSchema) || !YAHOO.lang.isString(sScriptURI)) {
        YAHOO.log("Could not instantiate XHR DataSource due to invalid arguments", "error", this.toString());
        return;
    }

    this.schema = aSchema;
    this.scriptURI = sScriptURI;
    
    this._init();
    YAHOO.log("XHR DataSource initialized","info",this.toString());
};

YAHOO.widget.DS_XHR.prototype = new YAHOO.widget.DataSource();

/////////////////////////////////////////////////////////////////////////////
//
// Public constants
//
/////////////////////////////////////////////////////////////////////////////

/**
 * JSON data type.
 *
 * @property TYPE_JSON
 * @type Number
 * @static
 * @final
 */
YAHOO.widget.DS_XHR.TYPE_JSON = 0;

/**
 * XML data type.
 *
 * @property TYPE_XML
 * @type Number
 * @static
 * @final
 */
YAHOO.widget.DS_XHR.TYPE_XML = 1;

/**
 * Flat-file data type.
 *
 * @property TYPE_FLAT
 * @type Number
 * @static
 * @final
 */
YAHOO.widget.DS_XHR.TYPE_FLAT = 2;

/**
 * Error message for XHR failure.
 *
 * @property ERROR_DATAXHR
 * @type String
 * @static
 * @final
 */
YAHOO.widget.DS_XHR.ERROR_DATAXHR = "XHR response failed";

/////////////////////////////////////////////////////////////////////////////
//
// Public member variables
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Alias to YUI Connection Manager. Allows implementers to specify their own
 * subclasses of the YUI Connection Manager utility.
 *
 * @property connMgr
 * @type Object
 * @default YAHOO.util.Connect
 */
YAHOO.widget.DS_XHR.prototype.connMgr = YAHOO.util.Connect;

/**
 * Number of milliseconds the XHR connection will wait for a server response. A
 * a value of zero indicates the XHR connection will wait forever. Any value
 * greater than zero will use the Connection utility's Auto-Abort feature.
 *
 * @property connTimeout
 * @type Number
 * @default 0
 */
YAHOO.widget.DS_XHR.prototype.connTimeout = 0;

/**
 * Absolute or relative URI to script that returns query results. For instance,
 * queries will be sent to &#60;scriptURI&#62;?&#60;scriptQueryParam&#62;=userinput
 *
 * @property scriptURI
 * @type String
 */
YAHOO.widget.DS_XHR.prototype.scriptURI = null;

/**
 * Query string parameter name sent to scriptURI. For instance, queries will be
 * sent to &#60;scriptURI&#62;?&#60;scriptQueryParam&#62;=userinput
 *
 * @property scriptQueryParam
 * @type String
 * @default "query"
 */
YAHOO.widget.DS_XHR.prototype.scriptQueryParam = "query";

/**
 * String of key/value pairs to append to requests made to scriptURI. Define
 * this string when you want to send additional query parameters to your script.
 * When defined, queries will be sent to
 * &#60;scriptURI&#62;?&#60;scriptQueryParam&#62;=userinput&#38;&#60;scriptQueryAppend&#62;
 *
 * @property scriptQueryAppend
 * @type String
 * @default ""
 */
YAHOO.widget.DS_XHR.prototype.scriptQueryAppend = "";

/**
 * XHR response data type. Other types that may be defined are YAHOO.widget.DS_XHR.TYPE_XML
 * and YAHOO.widget.DS_XHR.TYPE_FLAT.
 *
 * @property responseType
 * @type String
 * @default YAHOO.widget.DS_XHR.TYPE_JSON
 */
YAHOO.widget.DS_XHR.prototype.responseType = YAHOO.widget.DS_XHR.TYPE_JSON;

/**
 * String after which to strip results. If the results from the XHR are sent
 * back as HTML, the gzip HTML comment appears at the end of the data and should
 * be ignored.
 *
 * @property responseStripAfter
 * @type String
 * @default "\n&#60;!-"
 */
YAHOO.widget.DS_XHR.prototype.responseStripAfter = "\n<!-";

/////////////////////////////////////////////////////////////////////////////
//
// Public methods
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Queries the live data source defined by scriptURI for results. Results are
 * passed back to a callback function.
 *  
 * @method doQuery
 * @param oCallbackFn {HTMLFunction} Callback function defined by oParent object to which to return results.
 * @param sQuery {String} Query string.
 * @param oParent {Object} The object instance that has requested data.
 */
YAHOO.widget.DS_XHR.prototype.doQuery = function(oCallbackFn, sQuery, oParent) {
    var isXML = (this.responseType == YAHOO.widget.DS_XHR.TYPE_XML);
    var sUri = this.scriptURI+"?"+this.scriptQueryParam+"="+sQuery;
    if(this.scriptQueryAppend.length > 0) {
        sUri += "&" + this.scriptQueryAppend;
    }
    YAHOO.log("DataSource is querying URL " + sUri, "info", this.toString());
    var oResponse = null;
    
    var oSelf = this;
    /*
     * Sets up ajax request callback
     *
     * @param {object} oReq          HTTPXMLRequest object
     * @private
     */
    var responseSuccess = function(oResp) {
        // Response ID does not match last made request ID.
        if(!oSelf._oConn || (oResp.tId != oSelf._oConn.tId)) {
            oSelf.dataErrorEvent.fire(oSelf, oParent, sQuery, YAHOO.widget.DataSource.ERROR_DATANULL);
            YAHOO.log(YAHOO.widget.DataSource.ERROR_DATANULL, "error", oSelf.toString());
            return;
        }
//DEBUG
/*YAHOO.log(oResp.responseXML.getElementsByTagName("Result"),'warn');
for(var foo in oResp) {
    YAHOO.log(foo + ": "+oResp[foo],'warn');
}
YAHOO.log('responseXML.xml: '+oResp.responseXML.xml,'warn');*/
        if(!isXML) {
            oResp = oResp.responseText;
        }
        else { 
            oResp = oResp.responseXML;
        }
        if(oResp === null) {
            oSelf.dataErrorEvent.fire(oSelf, oParent, sQuery, YAHOO.widget.DataSource.ERROR_DATANULL);
            YAHOO.log(YAHOO.widget.DataSource.ERROR_DATANULL, "error", oSelf.toString());
            return;
        }

        var aResults = oSelf.parseResponse(sQuery, oResp, oParent);
        var resultObj = {};
        resultObj.query = decodeURIComponent(sQuery);
        resultObj.results = aResults;
        if(aResults === null) {
            oSelf.dataErrorEvent.fire(oSelf, oParent, sQuery, YAHOO.widget.DataSource.ERROR_DATAPARSE);
            YAHOO.log(YAHOO.widget.DataSource.ERROR_DATAPARSE, "error", oSelf.toString());
            aResults = [];
        }
        else {
            oSelf.getResultsEvent.fire(oSelf, oParent, sQuery, aResults);
            YAHOO.log("Results returned for query \"" + sQuery + "\": " +
                    YAHOO.lang.dump(aResults), "info", oSelf.toString());
            oSelf._addCacheElem(resultObj);
        }
        oCallbackFn(sQuery, aResults, oParent);
    };

    var responseFailure = function(oResp) {
        oSelf.dataErrorEvent.fire(oSelf, oParent, sQuery, YAHOO.widget.DS_XHR.ERROR_DATAXHR);
        YAHOO.log(YAHOO.widget.DS_XHR.ERROR_DATAXHR + ": " + oResp.statusText, "error", oSelf.toString());
        return;
    };
    
    var oCallback = {
        success:responseSuccess,
        failure:responseFailure
    };
    
    if(YAHOO.lang.isNumber(this.connTimeout) && (this.connTimeout > 0)) {
        oCallback.timeout = this.connTimeout;
    }
    
    if(this._oConn) {
        this.connMgr.abort(this._oConn);
    }
    
    oSelf._oConn = this.connMgr.asyncRequest("GET", sUri, oCallback, null);
};

/**
 * Parses raw response data into an array of result objects. The result data key
 * is always stashed in the [0] element of each result object. 
 *
 * @method parseResponse
 * @param sQuery {String} Query string.
 * @param oResponse {Object} The raw response data to parse.
 * @param oParent {Object} The object instance that has requested data.
 * @returns {Object[]} Array of result objects.
 */
YAHOO.widget.DS_XHR.prototype.parseResponse = function(sQuery, oResponse, oParent) {
    var aSchema = this.schema;
    var aResults = [];
    var bError = false;

    // Strip out comment at the end of results
    var nEnd = ((this.responseStripAfter !== "") && (oResponse.indexOf)) ?
        oResponse.indexOf(this.responseStripAfter) : -1;
    if(nEnd != -1) {
        oResponse = oResponse.substring(0,nEnd);
    }

    switch (this.responseType) {
        case YAHOO.widget.DS_XHR.TYPE_JSON:
            var jsonList, jsonObjParsed;
            // Check for JSON lib but divert KHTML clients
            var isNotMac = (navigator.userAgent.toLowerCase().indexOf('khtml')== -1);
            if(oResponse.parseJSON && isNotMac) {
                // Use the new JSON utility if available
                jsonObjParsed = oResponse.parseJSON();
                if(!jsonObjParsed) {
                    bError = true;
                }
                else {
                    try {
                        // eval is necessary here since aSchema[0] is of unknown depth
                        jsonList = eval("jsonObjParsed." + aSchema[0]);
                    }
                    catch(e) {
                        bError = true;
                        break;
                   }
                }
            }
            else if(window.JSON && isNotMac) {
                // Use older JSON lib if available
                jsonObjParsed = JSON.parse(oResponse);
                if(!jsonObjParsed) {
                    bError = true;
                    break;
                }
                else {
                    try {
                        // eval is necessary here since aSchema[0] is of unknown depth
                        jsonList = eval("jsonObjParsed." + aSchema[0]);
                    }
                    catch(e) {
                        bError = true;
                        break;
                   }
                }
            }
            else {
                // Parse the JSON response as a string
                try {
                    // Trim leading spaces
                    while (oResponse.substring(0,1) == " ") {
                        oResponse = oResponse.substring(1, oResponse.length);
                    }

                    // Invalid JSON response
                    if(oResponse.indexOf("{") < 0) {
                        bError = true;
                        break;
                    }

                    // Empty (but not invalid) JSON response
                    if(oResponse.indexOf("{}") === 0) {
                        break;
                    }

                    // Turn the string into an object literal...
                    // ...eval is necessary here
                    var jsonObjRaw = eval("(" + oResponse + ")");
                    if(!jsonObjRaw) {
                        bError = true;
                        break;
                    }

                    // Grab the object member that contains an array of all reponses...
                    // ...eval is necessary here since aSchema[0] is of unknown depth
                    jsonList = eval("(jsonObjRaw." + aSchema[0]+")");
                }
                catch(e) {
                    bError = true;
                    break;
               }
            }

            if(!jsonList) {
                bError = true;
                break;
            }

            if(!YAHOO.lang.isArray(jsonList)) {
                jsonList = [jsonList];
            }
            
            // Loop through the array of all responses...
            for(var i = jsonList.length-1; i >= 0 ; i--) {
                var aResultItem = [];
                var jsonResult = jsonList[i];
                // ...and loop through each data field value of each response
                for(var j = aSchema.length-1; j >= 1 ; j--) {
                    // ...and capture data into an array mapped according to the schema...
                    var dataFieldValue = jsonResult[aSchema[j]];
                    if(!dataFieldValue) {
                        dataFieldValue = "";
                    }
                    //YAHOO.log("data: " + i + " value:" +j+" = "+dataFieldValue,"debug",this.toString());
                    aResultItem.unshift(dataFieldValue);
                }
                // If schema isn't well defined, pass along the entire result object
                if(aResultItem.length == 1) {
                    aResultItem.push(jsonResult);
                }
                // Capture the array of data field values in an array of results
                aResults.unshift(aResultItem);
            }
            break;
        case YAHOO.widget.DS_XHR.TYPE_XML:
            // Get the collection of results
            var xmlList = oResponse.getElementsByTagName(aSchema[0]);
            if(!xmlList) {
                bError = true;
                break;
            }
            // Loop through each result
            for(var k = xmlList.length-1; k >= 0 ; k--) {
                var result = xmlList.item(k);
                //YAHOO.log("Result"+k+" is "+result.attributes.item(0).firstChild.nodeValue,"debug",this.toString());
                var aFieldSet = [];
                // Loop through each data field in each result using the schema
                for(var m = aSchema.length-1; m >= 1 ; m--) {
                    //YAHOO.log(aSchema[m]+" is "+result.attributes.getNamedItem(aSchema[m]).firstChild.nodeValue);
                    var sValue = null;
                    // Values may be held in an attribute...
                    var xmlAttr = result.attributes.getNamedItem(aSchema[m]);
                    if(xmlAttr) {
                        sValue = xmlAttr.value;
                        //YAHOO.log("Attr value is "+sValue,"debug",this.toString());
                    }
                    // ...or in a node
                    else{
                        var xmlNode = result.getElementsByTagName(aSchema[m]);
                        if(xmlNode && xmlNode.item(0) && xmlNode.item(0).firstChild) {
                            sValue = xmlNode.item(0).firstChild.nodeValue;
                            //YAHOO.log("Node value is "+sValue,"debug",this.toString());
                        }
                        else {
                            sValue = "";
                            //YAHOO.log("Value not found","debug",this.toString());
                        }
                    }
                    // Capture the schema-mapped data field values into an array
                    aFieldSet.unshift(sValue);
                }
                // Capture each array of values into an array of results
                aResults.unshift(aFieldSet);
            }
            break;
        case YAHOO.widget.DS_XHR.TYPE_FLAT:
            if(oResponse.length > 0) {
                // Delete the last line delimiter at the end of the data if it exists
                var newLength = oResponse.length-aSchema[0].length;
                if(oResponse.substr(newLength) == aSchema[0]) {
                    oResponse = oResponse.substr(0, newLength);
                }
                var aRecords = oResponse.split(aSchema[0]);
                for(var n = aRecords.length-1; n >= 0; n--) {
                    aResults[n] = aRecords[n].split(aSchema[1]);
                }
            }
            break;
        default:
            break;
    }
    sQuery = null;
    oResponse = null;
    oParent = null;
    if(bError) {
        return null;
    }
    else {
        return aResults;
    }
};            

/////////////////////////////////////////////////////////////////////////////
//
// Private member variables
//
/////////////////////////////////////////////////////////////////////////////

/**
 * XHR connection object.
 *
 * @property _oConn
 * @type Object
 * @private
 */
YAHOO.widget.DS_XHR.prototype._oConn = null;


/****************************************************************************/
/****************************************************************************/
/****************************************************************************/

/**
 * Implementation of YAHOO.widget.DataSource using a native Javascript function as
 * its live data source.
 *  
 * @class DS_JSFunction
 * @constructor
 * @extends YAHOO.widget.DataSource
 * @param oFunction {HTMLFunction} In-memory Javascript function that returns query results as an array of objects.
 * @param oConfigs {Object} (optional) Object literal of config params.
 */
YAHOO.widget.DS_JSFunction = function(oFunction, oConfigs) {
    // Set any config params passed in to override defaults
    if(oConfigs && (oConfigs.constructor == Object)) {
        for(var sConfig in oConfigs) {
            this[sConfig] = oConfigs[sConfig];
        }
    }

    // Initialization sequence
    if(!YAHOO.lang.isFunction(oFunction)) {
        YAHOO.log("Could not instantiate JSFunction DataSource due to invalid arguments", "error", this.toString());
        return;
    }
    else {
        this.dataFunction = oFunction;
        this._init();
        YAHOO.log("JS Function DataSource initialized","info",this.toString());
    }
};

YAHOO.widget.DS_JSFunction.prototype = new YAHOO.widget.DataSource();

/////////////////////////////////////////////////////////////////////////////
//
// Public member variables
//
/////////////////////////////////////////////////////////////////////////////

/**
 * In-memory Javascript function that returns query results.
 *
 * @property dataFunction
 * @type HTMLFunction
 */
YAHOO.widget.DS_JSFunction.prototype.dataFunction = null;

/////////////////////////////////////////////////////////////////////////////
//
// Public methods
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Queries the live data source defined by function for results. Results are
 * passed back to a callback function.
 *  
 * @method doQuery
 * @param oCallbackFn {HTMLFunction} Callback function defined by oParent object to which to return results.
 * @param sQuery {String} Query string.
 * @param oParent {Object} The object instance that has requested data.
 */
YAHOO.widget.DS_JSFunction.prototype.doQuery = function(oCallbackFn, sQuery, oParent) {
    var oFunction = this.dataFunction;
    var aResults = [];
    
    aResults = oFunction(sQuery);
    if(aResults === null) {
        this.dataErrorEvent.fire(this, oParent, sQuery, YAHOO.widget.DataSource.ERROR_DATANULL);
        YAHOO.log(YAHOO.widget.DataSource.ERROR_DATANULL, "error", this.toString());
        return;
    }
    
    var resultObj = {};
    resultObj.query = decodeURIComponent(sQuery);
    resultObj.results = aResults;
    this._addCacheElem(resultObj);
    
    this.getResultsEvent.fire(this, oParent, sQuery, aResults);
    YAHOO.log("Results returned for query \"" + sQuery +
            "\": " + YAHOO.lang.dump(aResults), "info", this.toString());
    oCallbackFn(sQuery, aResults, oParent);
    return;
};

/****************************************************************************/
/****************************************************************************/
/****************************************************************************/

/**
 * Implementation of YAHOO.widget.DataSource using a native Javascript array as
 * its live data source.
 *
 * @class DS_JSArray
 * @constructor
 * @extends YAHOO.widget.DataSource
 * @param aData {String[]} In-memory Javascript array of simple string data.
 * @param oConfigs {Object} (optional) Object literal of config params.
 */
YAHOO.widget.DS_JSArray = function(aData, oConfigs) {
    // Set any config params passed in to override defaults
    if(oConfigs && (oConfigs.constructor == Object)) {
        for(var sConfig in oConfigs) {
            this[sConfig] = oConfigs[sConfig];
        }
    }

    // Initialization sequence
    if(!YAHOO.lang.isArray(aData)) {
        YAHOO.log("Could not instantiate JSArray DataSource due to invalid arguments", "error", this.toString());
        return;
    }
    else {
        this.data = aData;
        this._init();
        YAHOO.log("JS Array DataSource initialized","info",this.toString());
    }
};

YAHOO.widget.DS_JSArray.prototype = new YAHOO.widget.DataSource();

/////////////////////////////////////////////////////////////////////////////
//
// Public member variables
//
/////////////////////////////////////////////////////////////////////////////

/**
 * In-memory Javascript array of strings.
 *
 * @property data
 * @type Array
 */
YAHOO.widget.DS_JSArray.prototype.data = null;

/////////////////////////////////////////////////////////////////////////////
//
// Public methods
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Queries the live data source defined by data for results. Results are passed
 * back to a callback function.
 *
 * @method doQuery
 * @param oCallbackFn {HTMLFunction} Callback function defined by oParent object to which to return results.
 * @param sQuery {String} Query string.
 * @param oParent {Object} The object instance that has requested data.
 */
YAHOO.widget.DS_JSArray.prototype.doQuery = function(oCallbackFn, sQuery, oParent) {
    var i;
    var aData = this.data; // the array
    var aResults = []; // container for results
    var bMatchFound = false;
    var bMatchContains = this.queryMatchContains;
    if(sQuery) {
        if(!this.queryMatchCase) {
            sQuery = sQuery.toLowerCase();
        }

        // Loop through each element of the array...
        // which can be a string or an array of strings
        for(i = aData.length-1; i >= 0; i--) {
            var aDataset = [];

            if(YAHOO.lang.isString(aData[i])) {
                aDataset[0] = aData[i];
            }
            else if(YAHOO.lang.isArray(aData[i])) {
                aDataset = aData[i];
            }

            if(YAHOO.lang.isString(aDataset[0])) {
                var sKeyIndex = (this.queryMatchCase) ?
                encodeURIComponent(aDataset[0]).indexOf(sQuery):
                encodeURIComponent(aDataset[0]).toLowerCase().indexOf(sQuery);

                // A STARTSWITH match is when the query is found at the beginning of the key string...
                if((!bMatchContains && (sKeyIndex === 0)) ||
                // A CONTAINS match is when the query is found anywhere within the key string...
                (bMatchContains && (sKeyIndex > -1))) {
                    // Stash a match into aResults[].
                    aResults.unshift(aDataset);
                }
            }
        }
    }
    else {
        for(i = aData.length-1; i >= 0; i--) {
            if(YAHOO.lang.isString(aData[i])) {
                aResults.unshift([aData[i]]);
            }
            else if(YAHOO.lang.isArray(aData[i])) {
                aResults.unshift(aData[i]);
            }
        }
    }
    
    this.getResultsEvent.fire(this, oParent, sQuery, aResults);
    YAHOO.log("Results returned for query \"" + sQuery +
            "\": " + YAHOO.lang.dump(aResults), "info", this.toString());
    oCallbackFn(sQuery, aResults, oParent);
};

YAHOO.register("autocomplete", YAHOO.widget.AutoComplete, {version: "2.3.1", build: "541"});
