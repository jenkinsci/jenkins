/*
Copyright (c) 2011, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.com/yui/license.html
version: 2.9.0
*/
/////////////////////////////////////////////////////////////////////////////
//
// YAHOO.widget.DataSource Backwards Compatibility
//
/////////////////////////////////////////////////////////////////////////////

YAHOO.widget.DS_JSArray = YAHOO.util.LocalDataSource;

YAHOO.widget.DS_JSFunction = YAHOO.util.FunctionDataSource;

YAHOO.widget.DS_XHR = function(sScriptURI, aSchema, oConfigs) {
    var DS = new YAHOO.util.XHRDataSource(sScriptURI, oConfigs);
    DS._aDeprecatedSchema = aSchema;
    return DS;
};

YAHOO.widget.DS_ScriptNode = function(sScriptURI, aSchema, oConfigs) {
    var DS = new YAHOO.util.ScriptNodeDataSource(sScriptURI, oConfigs);
    DS._aDeprecatedSchema = aSchema;
    return DS;
};

YAHOO.widget.DS_XHR.TYPE_JSON = YAHOO.util.DataSourceBase.TYPE_JSON;
YAHOO.widget.DS_XHR.TYPE_XML = YAHOO.util.DataSourceBase.TYPE_XML;
YAHOO.widget.DS_XHR.TYPE_FLAT = YAHOO.util.DataSourceBase.TYPE_TEXT;

// TODO: widget.DS_ScriptNode.scriptCallbackParam



 /**
 * The AutoComplete control provides the front-end logic for text-entry suggestion and
 * completion functionality.
 *
 * @module autocomplete
 * @requires yahoo, dom, event, datasource
 * @optional animation
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
        if(oDataSource && YAHOO.lang.isFunction(oDataSource.sendRequest)) {
            this.dataSource = oDataSource;
        }
        else {
            YAHOO.log("Could not instantiate AutoComplete due to an invalid DataSource", "error", this.toString());
            return;
        }

        // YAHOO.widget.DataSource schema backwards compatibility
        // Converted deprecated schema into supported schema
        // First assume key data is held in position 0 of results array
        this.key = 0;
        var schema = oDataSource.responseSchema;
        // An old school schema has been defined in the deprecated DataSource constructor
        if(oDataSource._aDeprecatedSchema) {
            var aDeprecatedSchema = oDataSource._aDeprecatedSchema;
            if(YAHOO.lang.isArray(aDeprecatedSchema)) {
                
                if((oDataSource.responseType === YAHOO.util.DataSourceBase.TYPE_JSON) || 
                (oDataSource.responseType === YAHOO.util.DataSourceBase.TYPE_UNKNOWN)) { // Used to default to unknown
                    // Store the resultsList
                    schema.resultsList = aDeprecatedSchema[0];
                    // Store the key
                    this.key = aDeprecatedSchema[1];
                    // Only resultsList and key are defined, so grab all the data
                    schema.fields = (aDeprecatedSchema.length < 3) ? null : aDeprecatedSchema.slice(1);
                }
                else if(oDataSource.responseType === YAHOO.util.DataSourceBase.TYPE_XML) {
                    schema.resultNode = aDeprecatedSchema[0];
                    this.key = aDeprecatedSchema[1];
                    schema.fields = aDeprecatedSchema.slice(1);
                }                
                else if(oDataSource.responseType === YAHOO.util.DataSourceBase.TYPE_TEXT) {
                    schema.recordDelim = aDeprecatedSchema[0];
                    schema.fieldDelim = aDeprecatedSchema[1];
                }                
                oDataSource.responseSchema = schema;
            }
        }
        
        // Validate input element
        if(YAHOO.util.Dom.inDocument(elInput)) {
            if(YAHOO.lang.isString(elInput)) {
                    this._sName = "instance" + YAHOO.widget.AutoComplete._nIndex + " " + elInput;
                    this._elTextbox = document.getElementById(elInput);
            }
            else {
                this._sName = (elInput.id) ?
                    "instance" + YAHOO.widget.AutoComplete._nIndex + " " + elInput.id:
                    "instance" + YAHOO.widget.AutoComplete._nIndex;
                this._elTextbox = elInput;
            }
            YAHOO.util.Dom.addClass(this._elTextbox, "yui-ac-input");
        }
        else {
            YAHOO.log("Could not instantiate AutoComplete due to an invalid input element", "error", this.toString());
            return;
        }

        // Validate container element
        if(YAHOO.util.Dom.inDocument(elContainer)) {
            if(YAHOO.lang.isString(elContainer)) {
                    this._elContainer = document.getElementById(elContainer);
            }
            else {
                this._elContainer = elContainer;
            }
            if(this._elContainer.style.display == "none") {
                YAHOO.log("The container may not display properly if display is set to \"none\" in CSS", "warn", this.toString());
            }
            
            // For skinning
            var elParent = this._elContainer.parentNode;
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

        // Default applyLocalFilter setting is to enable for local sources
        if(this.dataSource.dataType === YAHOO.util.DataSourceBase.TYPE_LOCAL) {
            this.applyLocalFilter = true;
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
        this._initContainerEl();
        this._initProps();
        this._initListEl();
        this._initContainerHelperEls();

        // Set up events
        var oSelf = this;
        var elTextbox = this._elTextbox;

        // Dom events
        YAHOO.util.Event.addListener(elTextbox,"keyup",oSelf._onTextboxKeyUp,oSelf);
        YAHOO.util.Event.addListener(elTextbox,"keydown",oSelf._onTextboxKeyDown,oSelf);
        YAHOO.util.Event.addListener(elTextbox,"focus",oSelf._onTextboxFocus,oSelf);
        YAHOO.util.Event.addListener(elTextbox,"blur",oSelf._onTextboxBlur,oSelf);
        YAHOO.util.Event.addListener(elContainer,"mouseover",oSelf._onContainerMouseover,oSelf);
        YAHOO.util.Event.addListener(elContainer,"mouseout",oSelf._onContainerMouseout,oSelf);
        YAHOO.util.Event.addListener(elContainer,"click",oSelf._onContainerClick,oSelf);
        YAHOO.util.Event.addListener(elContainer,"scroll",oSelf._onContainerScroll,oSelf);
        YAHOO.util.Event.addListener(elContainer,"resize",oSelf._onContainerResize,oSelf);
        YAHOO.util.Event.addListener(elTextbox,"keypress",oSelf._onTextboxKeyPress,oSelf);
        YAHOO.util.Event.addListener(window,"unload",oSelf._onWindowUnload,oSelf);

        // Custom events
        this.textboxFocusEvent = new YAHOO.util.CustomEvent("textboxFocus", this);
        this.textboxKeyEvent = new YAHOO.util.CustomEvent("textboxKey", this);
        this.dataRequestEvent = new YAHOO.util.CustomEvent("dataRequest", this);
        this.dataRequestCancelEvent = new YAHOO.util.CustomEvent("dataRequestCancel", this);
        this.dataReturnEvent = new YAHOO.util.CustomEvent("dataReturn", this);
        this.dataErrorEvent = new YAHOO.util.CustomEvent("dataError", this);
        this.containerPopulateEvent = new YAHOO.util.CustomEvent("containerPopulate", this);
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
        this.textboxChangeEvent = new YAHOO.util.CustomEvent("textboxChange", this);
        
        // Finish up
        elTextbox.setAttribute("autocomplete","off");
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
 * By default, results from local DataSources will pass through the filterResults
 * method to apply a client-side matching algorithm. 
 * 
 * @property applyLocalFilter
 * @type Boolean
 * @default true for local arrays and json, otherwise false
 */
YAHOO.widget.AutoComplete.prototype.applyLocalFilter = null;

/**
 * When applyLocalFilter is true, the local filtering algorthim can have case sensitivity
 * enabled. 
 * 
 * @property queryMatchCase
 * @type Boolean
 * @default false
 */
YAHOO.widget.AutoComplete.prototype.queryMatchCase = false;

/**
 * When applyLocalFilter is true, results can  be locally filtered to return
 * matching strings that "contain" the query string rather than simply "start with"
 * the query string.
 * 
 * @property queryMatchContains
 * @type Boolean
 * @default false
 */
YAHOO.widget.AutoComplete.prototype.queryMatchContains = false;

/**
 * Enables query subset matching. When the DataSource's cache is enabled and queryMatchSubset is
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
YAHOO.widget.AutoComplete.prototype.queryMatchSubset = false;

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
 * previous request is cancelled and the new request is set to the delay. If 
 * typeAhead is also enabled, this value must always be less than the typeAheadDelay
 * in order to avoid certain race conditions. 
 *
 * @property queryDelay
 * @type Number
 * @default 0.2
 */
YAHOO.widget.AutoComplete.prototype.queryDelay = 0.2;

/**
 * If typeAhead is true, number of seconds to delay before updating input with
 * typeAhead value. In order to prevent certain race conditions, this value must
 * always be greater than the queryDelay.
 *
 * @property typeAheadDelay
 * @type Number
 * @default 0.5
 */
YAHOO.widget.AutoComplete.prototype.typeAheadDelay = 0.5;

/**
 * When IME usage is detected or interval detection is explicitly enabled,
 * AutoComplete will detect the input value at the given interval and send a
 * query if the value has changed.
 *
 * @property queryInterval
 * @type Number
 * @default 500
 */
YAHOO.widget.AutoComplete.prototype.queryInterval = 500;

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
 * If autohighlight is enabled, whether or not the input field should be automatically updated
 * with the first query result as the user types, auto-selecting the substring portion
 * of the first result that the user has not yet typed.
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
 * Enabling this feature prevents the toggling of the container to a collapsed state.
 * Setting to true does not automatically trigger the opening of the container.
 * Implementers are advised to pre-load the container with an explicit "sendQuery()" call.   
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

/**
 * Whether or not the input field should be updated with selections.
 *
 * @property suppressInputUpdate
 * @type Boolean
 * @default false
 */
YAHOO.widget.AutoComplete.prototype.suppressInputUpdate = false;

/**
 * For backward compatibility to pre-2.6.0 formatResults() signatures, setting
 * resultsTypeList to true will take each object literal result returned by
 * DataSource and flatten into an array.  
 *
 * @property resultTypeList
 * @type Boolean
 * @default true
 */
YAHOO.widget.AutoComplete.prototype.resultTypeList = true;

/**
 * For XHR DataSources, AutoComplete will automatically insert a "?" between the server URI and 
 * the "query" param/value pair. To prevent this behavior, implementers should
 * set this value to false. To more fully customize the query syntax, implementers
 * should override the generateRequest() method.
 *
 * @property queryQuestionMark
 * @type Boolean
 * @default true
 */
YAHOO.widget.AutoComplete.prototype.queryQuestionMark = true;

/**
 * If true, before each time the container expands, the container element will be
 * positioned to snap to the bottom-left corner of the input element. If
 * autoSnapContainer is set to false, this positioning will not be done.  
 *
 * @property autoSnapContainer
 * @type Boolean
 * @default true
 */
YAHOO.widget.AutoComplete.prototype.autoSnapContainer = true;

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
 * Returns DOM reference to input element.
 *
 * @method getInputEl
 * @return {HTMLELement} DOM reference to input element.
 */
YAHOO.widget.AutoComplete.prototype.getInputEl = function() {
    return this._elTextbox;
};

 /**
 * Returns DOM reference to container element.
 *
 * @method getContainerEl
 * @return {HTMLELement} DOM reference to container element.
 */
YAHOO.widget.AutoComplete.prototype.getContainerEl = function() {
    return this._elContainer;
};

 /**
 * Returns true if widget instance is currently active.
 *
 * @method isFocused
 * @return {Boolean} Returns true if widget instance is currently active.
 */
YAHOO.widget.AutoComplete.prototype.isFocused = function() {
    return this._bFocused;
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
 * Public accessor to the &lt;ul&gt; element that displays query results within the results container.
 *
 * @method getListEl
 * @return {HTMLElement[]} Reference to &lt;ul&gt; element within the results container.
 */
YAHOO.widget.AutoComplete.prototype.getListEl = function() {
    return this._elList;
};

/**
 * Public accessor to the matching string associated with a given &lt;li&gt; result.
 *
 * @method getListItemMatch
 * @param elListItem {HTMLElement} Reference to &lt;LI&gt; element.
 * @return {String} Matching string.
 */
YAHOO.widget.AutoComplete.prototype.getListItemMatch = function(elListItem) {
    if(elListItem._sResultMatch) {
        return elListItem._sResultMatch;
    }
    else {
        return null;
    }
};

/**
 * Public accessor to the result data associated with a given &lt;li&gt; result.
 *
 * @method getListItemData
 * @param elListItem {HTMLElement} Reference to &lt;LI&gt; element.
 * @return {Object} Result data.
 */
YAHOO.widget.AutoComplete.prototype.getListItemData = function(elListItem) {
    if(elListItem._oResultData) {
        return elListItem._oResultData;
    }
    else {
        return null;
    }
};

/**
 * Public accessor to the index of the associated with a given &lt;li&gt; result.
 *
 * @method getListItemIndex
 * @param elListItem {HTMLElement} Reference to &lt;LI&gt; element.
 * @return {Number} Index.
 */
YAHOO.widget.AutoComplete.prototype.getListItemIndex = function(elListItem) {
    if(YAHOO.lang.isNumber(elListItem._nItemIndex)) {
        return elListItem._nItemIndex;
    }
    else {
        return null;
    }
};

/**
 * Sets HTML markup for the results container header. This markup will be
 * inserted within a &lt;div&gt; tag with a class of "yui-ac-hd".
 *
 * @method setHeader
 * @param sHeader {HTML} HTML markup for results container header.
 */
YAHOO.widget.AutoComplete.prototype.setHeader = function(sHeader) {
    if(this._elHeader) {
        var elHeader = this._elHeader;
        if(sHeader) {
            elHeader.innerHTML = sHeader;
            elHeader.style.display = "";
        }
        else {
            elHeader.innerHTML = "";
            elHeader.style.display = "none";
        }
    }
};

/**
 * Sets HTML markup for the results container footer. This markup will be
 * inserted within a &lt;div&gt; tag with a class of "yui-ac-ft".
 *
 * @method setFooter
 * @param sFooter {HTML} HTML markup for results container footer.
 */
YAHOO.widget.AutoComplete.prototype.setFooter = function(sFooter) {
    if(this._elFooter) {
        var elFooter = this._elFooter;
        if(sFooter) {
                elFooter.innerHTML = sFooter;
                elFooter.style.display = "";
        }
        else {
            elFooter.innerHTML = "";
            elFooter.style.display = "none";
        }
    }
};

/**
 * Sets HTML markup for the results container body. This markup will be
 * inserted within a &lt;div&gt; tag with a class of "yui-ac-bd".
 *
 * @method setBody
 * @param sBody {HTML} HTML markup for results container body.
 */
YAHOO.widget.AutoComplete.prototype.setBody = function(sBody) {
    if(this._elBody) {
        var elBody = this._elBody;
        YAHOO.util.Event.purgeElement(elBody, true);
        if(sBody) {
            elBody.innerHTML = sBody;
            elBody.style.display = "";
        }
        else {
            elBody.innerHTML = "";
            elBody.style.display = "none";
        }
        this._elList = null;
    }
};

/**
* A function that converts an AutoComplete query into a request value which is then
* passed to the DataSource's sendRequest method in order to retrieve data for 
* the query. By default, returns a String with the syntax: "query={query}"
* Implementers can customize this method for custom request syntaxes.
* 
* @method generateRequest
* @param sQuery {String} Query string
* @return {MIXED} Request
*/
YAHOO.widget.AutoComplete.prototype.generateRequest = function(sQuery) {
    var dataType = this.dataSource.dataType;
    
    // Transform query string in to a request for remote data
    // By default, local data doesn't need a transformation, just passes along the query as is.
    if(dataType === YAHOO.util.DataSourceBase.TYPE_XHR) {
        // By default, XHR GET requests look like "{scriptURI}?{scriptQueryParam}={sQuery}&{scriptQueryAppend}"
        if(!this.dataSource.connMethodPost) {
            sQuery = (this.queryQuestionMark ? "?" : "") + (this.dataSource.scriptQueryParam || "query") + "=" + sQuery + 
                (this.dataSource.scriptQueryAppend ? ("&" + this.dataSource.scriptQueryAppend) : "");        
        }
        // By default, XHR POST bodies are sent to the {scriptURI} like "{scriptQueryParam}={sQuery}&{scriptQueryAppend}"
        else {
            sQuery = (this.dataSource.scriptQueryParam || "query") + "=" + sQuery + 
                (this.dataSource.scriptQueryAppend ? ("&" + this.dataSource.scriptQueryAppend) : "");
        }
    }
    // By default, remote script node requests look like "{scriptURI}&{scriptCallbackParam}={callbackString}&{scriptQueryParam}={sQuery}&{scriptQueryAppend}"
    else if(dataType === YAHOO.util.DataSourceBase.TYPE_SCRIPTNODE) {
        sQuery = "&" + (this.dataSource.scriptQueryParam || "query") + "=" + sQuery + 
            (this.dataSource.scriptQueryAppend ? ("&" + this.dataSource.scriptQueryAppend) : "");    
    }
    
    return sQuery;
};

/**
 * Makes query request to the DataSource.
 *
 * @method sendQuery
 * @param sQuery {String} Query string.
 */
YAHOO.widget.AutoComplete.prototype.sendQuery = function(sQuery) {
    // Activate focus for a new interaction
    this._bFocused = true;
    
    // Adjust programatically sent queries to look like they were input by user
    // when delimiters are enabled
    var newQuery = (this.delimChar) ? this._elTextbox.value + sQuery : sQuery;
    this._sendQuery(newQuery);
};

/**
 * Snaps container to bottom-left corner of input element
 *
 * @method snapContainer
 */
YAHOO.widget.AutoComplete.prototype.snapContainer = function() {
    var oTextbox = this._elTextbox,
        pos = YAHOO.util.Dom.getXY(oTextbox);
    pos[1] += YAHOO.util.Dom.get(oTextbox).offsetHeight + 2;
    YAHOO.util.Dom.setXY(this._elContainer,pos);
};

/**
 * Expands container.
 *
 * @method expandContainer
 */
YAHOO.widget.AutoComplete.prototype.expandContainer = function() {
    this._toggleContainer(true);
};

/**
 * Collapses container.
 *
 * @method collapseContainer
 */
YAHOO.widget.AutoComplete.prototype.collapseContainer = function() {
    this._toggleContainer(false);
};

/**
 * Clears entire list of suggestions.
 *
 * @method clearList
 */
YAHOO.widget.AutoComplete.prototype.clearList = function() {
    var allItems = this._elList.childNodes,
        i=allItems.length-1;
    for(; i>-1; i--) {
          allItems[i].style.display = "none";
    }
};

/**
 * Handles subset matching for when queryMatchSubset is enabled.
 *
 * @method getSubsetMatches
 * @param sQuery {String} Query string.
 * @return {Object} oParsedResponse or null. 
 */
YAHOO.widget.AutoComplete.prototype.getSubsetMatches = function(sQuery) {
    var subQuery, oCachedResponse, subRequest;
    // Loop through substrings of each cached element's query property...
    for(var i = sQuery.length; i >= this.minQueryLength ; i--) {
        subRequest = this.generateRequest(sQuery.substr(0,i));
        this.dataRequestEvent.fire(this, subQuery, subRequest);
        YAHOO.log("Searching for query subset \"" + subQuery + "\" in cache", "info", this.toString());
        
        // If a substring of the query is found in the cache
        oCachedResponse = this.dataSource.getCachedResponse(subRequest);
        if(oCachedResponse) {
            YAHOO.log("Found match for query subset \"" + subQuery + "\": " + YAHOO.lang.dump(oCachedResponse), "info", this.toString());
            return this.filterResults.apply(this.dataSource, [sQuery, oCachedResponse, oCachedResponse, {scope:this}]);
        }
    }
    YAHOO.log("Did not find subset match for query subset \"" + sQuery + "\"" , "info", this.toString());
    return null;
};

/**
 * Executed by DataSource (within DataSource scope via doBeforeParseData()) to
 * handle responseStripAfter cleanup.
 *
 * @method preparseRawResponse
 * @param sQuery {String} Query string.
 * @return {Object} oParsedResponse or null. 
 */
YAHOO.widget.AutoComplete.prototype.preparseRawResponse = function(oRequest, oFullResponse, oCallback) {
    var nEnd = ((this.responseStripAfter !== "") && (oFullResponse.indexOf)) ?
        oFullResponse.indexOf(this.responseStripAfter) : -1;
    if(nEnd != -1) {
        oFullResponse = oFullResponse.substring(0,nEnd);
    }
    return oFullResponse;
};

/**
 * Executed by DataSource (within DataSource scope via doBeforeCallback()) to
 * filter results through a simple client-side matching algorithm. 
 *
 * @method filterResults
 * @param sQuery {String} Original request.
 * @param oFullResponse {Object} Full response object.
 * @param oParsedResponse {Object} Parsed response object.
 * @param oCallback {Object} Callback object. 
 * @return {Object} Filtered response object.
 */

YAHOO.widget.AutoComplete.prototype.filterResults = function(sQuery, oFullResponse, oParsedResponse, oCallback) {
    // If AC has passed a query string value back to itself, grab it
    if(oCallback && oCallback.argument && YAHOO.lang.isValue(oCallback.argument.query)) {
        sQuery = oCallback.argument.query;
    }

    // Only if a query string is available to match against
    if(sQuery && sQuery !== "") {
        // First make a copy of the oParseResponse
        oParsedResponse = YAHOO.widget.AutoComplete._cloneObject(oParsedResponse);
        
        var oAC = oCallback.scope,
            oDS = this,
            allResults = oParsedResponse.results, // the array of results
            filteredResults = [], // container for filtered results,
            nMax = oAC.maxResultsDisplayed, // max to find
            bMatchCase = (oDS.queryMatchCase || oAC.queryMatchCase), // backward compat
            bMatchContains = (oDS.queryMatchContains || oAC.queryMatchContains); // backward compat
            
        // Loop through each result object...
        for(var i=0, len=allResults.length; i<len; i++) {
            var oResult = allResults[i];

            // Grab the data to match against from the result object...
            var sResult = null;
            
            // Result object is a simple string already
            if(YAHOO.lang.isString(oResult)) {
                sResult = oResult;
            }
            // Result object is an array of strings
            else if(YAHOO.lang.isArray(oResult)) {
                sResult = oResult[0];
            
            }
            // Result object is an object literal of strings
            else if(this.responseSchema.fields) {
                var key = this.responseSchema.fields[0].key || this.responseSchema.fields[0];
                sResult = oResult[key];
            }
            // Backwards compatibility
            else if(this.key) {
                sResult = oResult[this.key];
            }
            
            if(YAHOO.lang.isString(sResult)) {
                
                var sKeyIndex = (bMatchCase) ?
                sResult.indexOf(decodeURIComponent(sQuery)) :
                sResult.toLowerCase().indexOf(decodeURIComponent(sQuery).toLowerCase());

                // A STARTSWITH match is when the query is found at the beginning of the key string...
                if((!bMatchContains && (sKeyIndex === 0)) ||
                // A CONTAINS match is when the query is found anywhere within the key string...
                (bMatchContains && (sKeyIndex > -1))) {
                    // Stash the match
                    filteredResults.push(oResult);
                }
            }
            
            // Filter no more if maxResultsDisplayed is reached
            if(len>nMax && filteredResults.length===nMax) {
                break;
            }
        }
        oParsedResponse.results = filteredResults;
        YAHOO.log("Filtered " + filteredResults.length + " results against query \""  + sQuery + "\": " + YAHOO.lang.dump(filteredResults), "info", this.toString());
    }
    else {
        YAHOO.log("Did not filter results against query", "info", this.toString());
    }
    
    return oParsedResponse;
};

/**
 * Handles response for display. This is the callback function method passed to
 * YAHOO.util.DataSourceBase#sendRequest so results from the DataSource are
 * returned to the AutoComplete instance.
 *
 * @method handleResponse
 * @param sQuery {String} Original request.
 * @param oResponse {Object} <a href="http://developer.yahoo.com/yui/datasource/#ds_oParsedResponse">Response object</a>.
 * @param oPayload {MIXED} (optional) Additional argument(s)
 */
YAHOO.widget.AutoComplete.prototype.handleResponse = function(sQuery, oResponse, oPayload) {
    if((this instanceof YAHOO.widget.AutoComplete) && this._sName) {
        this._populateList(sQuery, oResponse, oPayload);
    }
};

/**
 * Overridable method called before container is loaded with result data.
 *
 * @method doBeforeLoadData
 * @param sQuery {String} Original request.
 * @param oResponse {Object} <a href="http://developer.yahoo.com/yui/datasource/#ds_oParsedResponse">Response object</a>.
 * @param oPayload {MIXED} (optional) Additional argument(s)
 * @return {Boolean} Return true to continue loading data, false to cancel.
 */
YAHOO.widget.AutoComplete.prototype.doBeforeLoadData = function(sQuery, oResponse, oPayload) {
    return true;
};

/**
 * Overridable method that returns HTML markup for one result to be populated
 * as innerHTML of an &lt;LI&gt; element. 
 *
 * @method formatResult
 * @param oResultData {Object} Result data object.
 * @param sQuery {String} The corresponding query string.
 * @param sResultMatch {HTMLElement} The current query string. 
 * @return {HTML} HTML markup of formatted result data.
 */
YAHOO.widget.AutoComplete.prototype.formatResult = function(oResultData, sQuery, sResultMatch) {
    var sMarkup = (sResultMatch) ? sResultMatch : "";
    return sMarkup;
};

/**
 * An alternative to the formatResult() method, escapes the result data before
 * inserting into DOM. Implementers should point to this method when accessing
 * data from third-party sources, from user input, or from otherwise
 * untrustworthy sources:
 * myAutoComplete.formatResult = myAutoComplete.formatEscapedResult;
 *
 * @method formatEscapedResult
 * @param oResultData {Object} Result data object.
 * @param sQuery {String} The corresponding query string.
 * @param sResultMatch {HTMLElement} The current query string.
 * @return {String} Formatted result data.
 */
YAHOO.widget.AutoComplete.prototype.formatEscapedResult = function(oResultData, sQuery, sResultMatch) {
    var sResult = (sResultMatch) ? sResultMatch : "";
    return YAHOO.lang.escapeHTML(sResult);
};

/**
 * Overridable method called before container expands allows implementers to access data
 * and DOM elements.
 *
 * @method doBeforeExpandContainer
 * @param elTextbox {HTMLElement} The text input box.
 * @param elContainer {HTMLElement} The container element.
 * @param sQuery {String} The query string.
 * @param aResults {Object[]}  An array of query results.
 * @return {Boolean} Return true to continue expanding container, false to cancel the expand.
 */
YAHOO.widget.AutoComplete.prototype.doBeforeExpandContainer = function(elTextbox, elContainer, sQuery, aResults) {
    return true;
};


/**
 * Nulls out the entire AutoComplete instance and related objects, removes attached
 * event listeners, and clears out DOM elements inside the container. After
 * calling this method, the instance reference should be expliclitly nulled by
 * implementer, as in myAutoComplete = null. Use with caution!
 *
 * @method destroy
 */
YAHOO.widget.AutoComplete.prototype.destroy = function() {
    var instanceName = this.toString();
    var elInput = this._elTextbox;
    var elContainer = this._elContainer;

    // Unhook custom events
    this.textboxFocusEvent.unsubscribeAll();
    this.textboxKeyEvent.unsubscribeAll();
    this.dataRequestEvent.unsubscribeAll();
    this.dataReturnEvent.unsubscribeAll();
    this.dataErrorEvent.unsubscribeAll();
    this.containerPopulateEvent.unsubscribeAll();
    this.containerExpandEvent.unsubscribeAll();
    this.typeAheadEvent.unsubscribeAll();
    this.itemMouseOverEvent.unsubscribeAll();
    this.itemMouseOutEvent.unsubscribeAll();
    this.itemArrowToEvent.unsubscribeAll();
    this.itemArrowFromEvent.unsubscribeAll();
    this.itemSelectEvent.unsubscribeAll();
    this.unmatchedItemSelectEvent.unsubscribeAll();
    this.selectionEnforceEvent.unsubscribeAll();
    this.containerCollapseEvent.unsubscribeAll();
    this.textboxBlurEvent.unsubscribeAll();
    this.textboxChangeEvent.unsubscribeAll();

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
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 */
YAHOO.widget.AutoComplete.prototype.textboxFocusEvent = null;

/**
 * Fired when the input field receives key input.
 *
 * @event textboxKeyEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param args[1] {Number} The keycode number.
 */
YAHOO.widget.AutoComplete.prototype.textboxKeyEvent = null;

/**
 * Fired when the AutoComplete instance makes a request to the DataSource.
 * 
 * @event dataRequestEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param args[1] {String} The query string.
 * @param args[2] {Object} The request.
 */
YAHOO.widget.AutoComplete.prototype.dataRequestEvent = null;

/**
 * Fired when the AutoComplete request to the DataSource is canceled.
 *
 * @event dataRequestCancelEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param args[1] {String} The query string.
 */
YAHOO.widget.AutoComplete.prototype.dataRequestCancelEvent = null;

/**
 * Fired when the AutoComplete instance receives query results from the data
 * source.
 *
 * @event dataReturnEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param args[1] {String} The query string.
 * @param args[2] {Object[]} Results array.
 */
YAHOO.widget.AutoComplete.prototype.dataReturnEvent = null;

/**
 * Fired when the AutoComplete instance does not receive query results from the
 * DataSource due to an error.
 *
 * @event dataErrorEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param args[1] {String} The query string.
 * @param args[2] {Object} The response object, if available.
 */
YAHOO.widget.AutoComplete.prototype.dataErrorEvent = null;

/**
 * Fired when the results container is populated.
 *
 * @event containerPopulateEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 */
YAHOO.widget.AutoComplete.prototype.containerPopulateEvent = null;

/**
 * Fired when the results container is expanded.
 *
 * @event containerExpandEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 */
YAHOO.widget.AutoComplete.prototype.containerExpandEvent = null;

/**
 * Fired when the input field has been prefilled by the type-ahead
 * feature. 
 *
 * @event typeAheadEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param args[1] {String} The query string.
 * @param args[2] {String} The prefill string.
 */
YAHOO.widget.AutoComplete.prototype.typeAheadEvent = null;

/**
 * Fired when result item has been moused over.
 *
 * @event itemMouseOverEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param args[1] {HTMLElement} The &lt;li&gt element item moused to.
 */
YAHOO.widget.AutoComplete.prototype.itemMouseOverEvent = null;

/**
 * Fired when result item has been moused out.
 *
 * @event itemMouseOutEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param args[1] {HTMLElement} The &lt;li&gt; element item moused from.
 */
YAHOO.widget.AutoComplete.prototype.itemMouseOutEvent = null;

/**
 * Fired when result item has been arrowed to. 
 *
 * @event itemArrowToEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param args[1] {HTMLElement} The &lt;li&gt; element item arrowed to.
 */
YAHOO.widget.AutoComplete.prototype.itemArrowToEvent = null;

/**
 * Fired when result item has been arrowed away from.
 *
 * @event itemArrowFromEvent
 * @param type {String} Name of the event.
 * @param args[0[ {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param args[1] {HTMLElement} The &lt;li&gt; element item arrowed from.
 */
YAHOO.widget.AutoComplete.prototype.itemArrowFromEvent = null;

/**
 * Fired when an item is selected via mouse click, ENTER key, or TAB key.
 *
 * @event itemSelectEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param args[1] {HTMLElement} The selected &lt;li&gt; element item.
 * @param args[2] {Object} The data returned for the item, either as an object,
 * or mapped from the schema into an array.
 */
YAHOO.widget.AutoComplete.prototype.itemSelectEvent = null;

/**
 * Fired when a user selection does not match any of the displayed result items.
 *
 * @event unmatchedItemSelectEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param args[1] {String} The selected string.
 */
YAHOO.widget.AutoComplete.prototype.unmatchedItemSelectEvent = null;

/**
 * Fired if forceSelection is enabled and the user's input has been cleared
 * because it did not match one of the returned query results.
 *
 * @event selectionEnforceEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @param args[1] {String} The cleared value (including delimiters if applicable).
 */
YAHOO.widget.AutoComplete.prototype.selectionEnforceEvent = null;

/**
 * Fired when the results container is collapsed.
 *
 * @event containerCollapseEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 */
YAHOO.widget.AutoComplete.prototype.containerCollapseEvent = null;

/**
 * Fired when the input field loses focus.
 *
 * @event textboxBlurEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 */
YAHOO.widget.AutoComplete.prototype.textboxBlurEvent = null;

/**
 * Fired when the input field value has changed when it loses focus.
 *
 * @event textboxChangeEvent
 * @param type {String} Name of the event.
 * @param args[0] {YAHOO.widget.AutoComplete} The AutoComplete instance.
 */
YAHOO.widget.AutoComplete.prototype.textboxChangeEvent = null;

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
 * @property _elTextbox
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._elTextbox = null;

/**
 * Container DOM element.
 *
 * @property _elContainer
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._elContainer = null;

/**
 * Reference to content element within container element.
 *
 * @property _elContent
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._elContent = null;

/**
 * Reference to header element within content element.
 *
 * @property _elHeader
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._elHeader = null;

/**
 * Reference to body element within content element.
 *
 * @property _elBody
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._elBody = null;

/**
 * Reference to footer element within content element.
 *
 * @property _elFooter
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._elFooter = null;

/**
 * Reference to shadow element within container element.
 *
 * @property _elShadow
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._elShadow = null;

/**
 * Reference to iframe element within container element.
 *
 * @property _elIFrame
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._elIFrame = null;

/**
 * Whether or not the widget instance is currently active. If query results come back
 * but the user has already moved on, do not proceed with auto complete behavior.
 *
 * @property _bFocused
 * @type Boolean
 * @private
 */
YAHOO.widget.AutoComplete.prototype._bFocused = false;

/**
 * Animation instance for container expand/collapse.
 *
 * @property _oAnim
 * @type Boolean
 * @private
 */
YAHOO.widget.AutoComplete.prototype._oAnim = null;

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
 * Internal reference to &lt;ul&gt; elements that contains query results within the
 * results container.
 *
 * @property _elList
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._elList = null;

/*
 * Array of &lt;li&gt; elements references that contain query results within the
 * results container.
 *
 * @property _aListItemEls
 * @type HTMLElement[]
 * @private
 */
//YAHOO.widget.AutoComplete.prototype._aListItemEls = null;

/**
 * Number of &lt;li&gt; elements currently displayed in results container.
 *
 * @property _nDisplayedItems
 * @type Number
 * @private
 */
YAHOO.widget.AutoComplete.prototype._nDisplayedItems = 0;

/*
 * Internal count of &lt;li&gt; elements displayed and hidden in results container.
 *
 * @property _maxResultsDisplayed
 * @type Number
 * @private
 */
//YAHOO.widget.AutoComplete.prototype._maxResultsDisplayed = 0;

/**
 * Current query string
 *
 * @property _sCurQuery
 * @type String
 * @private
 */
YAHOO.widget.AutoComplete.prototype._sCurQuery = null;

/**
 * Selections from previous queries (for saving delimited queries).
 *
 * @property _sPastSelections
 * @type String
 * @default "" 
 * @private
 */
YAHOO.widget.AutoComplete.prototype._sPastSelections = "";

/**
 * Stores initial input value used to determine if textboxChangeEvent should be fired.
 *
 * @property _sInitInputValue
 * @type String
 * @private
 */
YAHOO.widget.AutoComplete.prototype._sInitInputValue = null;

/**
 * Pointer to the currently highlighted &lt;li&gt; element in the container.
 *
 * @property _elCurListItem
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._elCurListItem = null;

/**
 * Pointer to the currently pre-highlighted &lt;li&gt; element in the container.
 *
 * @property _elCurPrehighlightItem
 * @type HTMLElement
 * @private
 */
YAHOO.widget.AutoComplete.prototype._elCurPrehighlightItem = null;

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
 * TypeAhead delay timeout ID.
 *
 * @property _nTypeAheadDelayID
 * @type Number
 * @private
 */
YAHOO.widget.AutoComplete.prototype._nTypeAheadDelayID = -1;

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
    var typeAheadDelay = this.typeAheadDelay;
    if(!YAHOO.lang.isNumber(typeAheadDelay) || (typeAheadDelay < 0)) {
        this.typeAheadDelay = 0.2;
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
            this._oAnim = new YAHOO.util.Anim(this._elContent, {}, this.animSpeed);
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
 * @method _initContainerHelperEls
 * @private
 */
YAHOO.widget.AutoComplete.prototype._initContainerHelperEls = function() {
    if(this.useShadow && !this._elShadow) {
        var elShadow = document.createElement("div");
        elShadow.className = "yui-ac-shadow";
        elShadow.style.width = 0;
        elShadow.style.height = 0;
        this._elShadow = this._elContainer.appendChild(elShadow);
    }
    if(this.useIFrame && !this._elIFrame) {
        var elIFrame = document.createElement("iframe");
        elIFrame.src = this._iFrameSrc;
        elIFrame.frameBorder = 0;
        elIFrame.scrolling = "no";
        elIFrame.style.position = "absolute";
        elIFrame.style.width = 0;
        elIFrame.style.height = 0;
        elIFrame.style.padding = 0;
        elIFrame.tabIndex = -1;
        elIFrame.role = "presentation";
        elIFrame.title = "Presentational iframe shim";
        this._elIFrame = this._elContainer.appendChild(elIFrame);
    }
};

/**
 * Initializes the results container once at object creation
 *
 * @method _initContainerEl
 * @private
 */
YAHOO.widget.AutoComplete.prototype._initContainerEl = function() {
    YAHOO.util.Dom.addClass(this._elContainer, "yui-ac-container");
    
    if(!this._elContent) {
        // The elContent div is assigned DOM listeners and 
        // helps size the iframe and shadow properly
        var elContent = document.createElement("div");
        elContent.className = "yui-ac-content";
        elContent.style.display = "none";

        this._elContent = this._elContainer.appendChild(elContent);

        var elHeader = document.createElement("div");
        elHeader.className = "yui-ac-hd";
        elHeader.style.display = "none";
        this._elHeader = this._elContent.appendChild(elHeader);

        var elBody = document.createElement("div");
        elBody.className = "yui-ac-bd";
        this._elBody = this._elContent.appendChild(elBody);

        var elFooter = document.createElement("div");
        elFooter.className = "yui-ac-ft";
        elFooter.style.display = "none";
        this._elFooter = this._elContent.appendChild(elFooter);
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
 * @method _initListEl
 * @private
 */
YAHOO.widget.AutoComplete.prototype._initListEl = function() {
    var nListLength = this.maxResultsDisplayed,
        elList = this._elList || document.createElement("ul"),
        elListItem;
    
    while(elList.childNodes.length < nListLength) {
        elListItem = document.createElement("li");
        elListItem.style.display = "none";
        elListItem._nItemIndex = elList.childNodes.length;
        elList.appendChild(elListItem);
    }
    if(!this._elList) {
        var elBody = this._elBody;
        YAHOO.util.Event.purgeElement(elBody, true);
        elBody.innerHTML = "";
        this._elList = elBody.appendChild(elList);
    }
    
    this._elBody.style.display = "";
};

/**
 * Focuses input field.
 *
 * @method _focus
 * @private
 */
YAHOO.widget.AutoComplete.prototype._focus = function() {
    // http://developer.mozilla.org/en/docs/index.php?title=Key-navigable_custom_DHTML_widgets
    var oSelf = this;
    setTimeout(function() {
        try {
            oSelf._elTextbox.focus();
        }
        catch(e) {
        }
    },0);
};

/**
 * Enables interval detection for IME support.
 *
 * @method _enableIntervalDetection
 * @private
 */
YAHOO.widget.AutoComplete.prototype._enableIntervalDetection = function() {
    var oSelf = this;
    if(!oSelf._queryInterval && oSelf.queryInterval) {
        oSelf._queryInterval = setInterval(function() { oSelf._onInterval(); }, oSelf.queryInterval);
        YAHOO.log("Interval set", "info", this.toString());
    }
};

/**
 * Enables interval detection for a less performant but brute force mechanism to
 * detect input values at an interval set by queryInterval and send queries if
 * input value has changed. Needed to support right-click+paste or shift+insert
 * edge cases. Please note that intervals are cleared at the end of each interaction,
 * so enableIntervalDetection must be called for each new interaction. The
 * recommended approach is to call it in response to textboxFocusEvent.
 *
 * @method enableIntervalDetection
 */
YAHOO.widget.AutoComplete.prototype.enableIntervalDetection =
    YAHOO.widget.AutoComplete.prototype._enableIntervalDetection;

/**
 * Enables query triggers based on text input detection by intervals (rather
 * than by key events).
 *
 * @method _onInterval
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onInterval = function() {
    var currValue = this._elTextbox.value;
    var lastValue = this._sLastTextboxValue;
    if(currValue != lastValue) {
        this._sLastTextboxValue = currValue;
        this._sendQuery(currValue);
    }
};

/**
 * Cancels text input detection by intervals.
 *
 * @method _clearInterval
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._clearInterval = function() {
    if(this._queryInterval) {
        clearInterval(this._queryInterval);
        this._queryInterval = null;
        YAHOO.log("Interval cleared", "info", this.toString());
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
            (nKeyCode >= 18 && nKeyCode <= 20) || // alt, pause/break,caps lock
            (nKeyCode == 27) || // esc
            (nKeyCode >= 33 && nKeyCode <= 35) || // page up,page down,end
            /*(nKeyCode >= 36 && nKeyCode <= 38) || // home,left,up
            (nKeyCode == 40) || // down*/
            (nKeyCode >= 36 && nKeyCode <= 40) || // home,left,up, right, down
            (nKeyCode >= 44 && nKeyCode <= 45) || // print screen,insert
            (nKeyCode == 229) // Bug 2041973: Korean XP fires 2 keyup events, the key and 229
        ) { 
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
    if(this.minQueryLength < 0) {
        this._toggleContainer(false);
        YAHOO.log("Property minQueryLength is less than 0", "info", this.toString());
        return;
    }
    // Delimiter has been enabled
    if(this.delimChar) {
        var extraction = this._extractQuery(sQuery);
        // Here is the query itself
        sQuery = extraction.query;
        // ...and save the rest of the string for later
        this._sPastSelections = extraction.previous;
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
    this._nDelayID = -1;    // Reset timeout ID because request is being made
    
    // Subset matching
    if(this.dataSource.queryMatchSubset || this.queryMatchSubset) { // backward compat
        var oResponse = this.getSubsetMatches(sQuery);
        if(oResponse) {
            this.handleResponse(sQuery, oResponse, {query: sQuery});
            return;
        }
    }
    
    if(this.dataSource.responseStripAfter) {
        this.dataSource.doBeforeParseData = this.preparseRawResponse;
    }
    if(this.applyLocalFilter) {
        this.dataSource.doBeforeCallback = this.filterResults;
    }
    
    var sRequest = this.generateRequest(sQuery);
    
    if(sRequest !== undefined) {
        this.dataRequestEvent.fire(this, sQuery, sRequest);
        YAHOO.log("Sending query \"" + sRequest + "\"", "info", this.toString());

        this.dataSource.sendRequest(sRequest, {
                success : this.handleResponse,
                failure : this.handleResponse,
                scope   : this,
                argument: {
                    query: sQuery
                }
        });
    }
    else {
        this.dataRequestCancelEvent.fire(this, sQuery);
        YAHOO.log("Canceled query \"" + sQuery + "\"", "info", this.toString());
    }
};

/**
 * Populates the given &lt;li&gt; element with return value from formatResult().
 *
 * @method _populateListItem
 * @param elListItem {HTMLElement} The LI element.
 * @param oResult {Object} The result object.
 * @param sCurQuery {String} The query string.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._populateListItem = function(elListItem, oResult, sQuery) {
    elListItem.innerHTML = this.formatResult(oResult, sQuery, elListItem._sResultMatch);
};

/**
 * Populates the array of &lt;li&gt; elements in the container with query
 * results.
 *
 * @method _populateList
 * @param sQuery {String} Original request.
 * @param oResponse {Object} <a href="http://developer.yahoo.com/yui/datasource/#ds_oParsedResponse">Response object</a>.
 * @param oPayload {MIXED} (optional) Additional argument(s)
 * @private
 */
YAHOO.widget.AutoComplete.prototype._populateList = function(sQuery, oResponse, oPayload) {
    // Clear previous timeout
    if(this._nTypeAheadDelayID != -1) {
        clearTimeout(this._nTypeAheadDelayID);
    }
        
    sQuery = (oPayload && oPayload.query) ? oPayload.query : sQuery;
    
    // Pass data through abstract method for any transformations
    var ok = this.doBeforeLoadData(sQuery, oResponse, oPayload);

    // Data is ok
    if(ok && !oResponse.error) {
        this.dataReturnEvent.fire(this, sQuery, oResponse.results);
        
        // Continue only if instance is still active (i.e., user hasn't already moved on)
        if(this._bFocused) {
            // Store state for this interaction
            var sCurQuery = decodeURIComponent(sQuery);
            this._sCurQuery = sCurQuery;
            this._bItemSelected = false;
        
            var allResults = oResponse.results,
                nItemsToShow = Math.min(allResults.length,this.maxResultsDisplayed),
                sMatchKey = (this.dataSource.responseSchema.fields) ? 
                    (this.dataSource.responseSchema.fields[0].key || this.dataSource.responseSchema.fields[0]) : 0;
            
            if(nItemsToShow > 0) {
                // Make sure container and helpers are ready to go
                if(!this._elList || (this._elList.childNodes.length < nItemsToShow)) {
                    this._initListEl();
                }
                this._initContainerHelperEls();
                
                var allListItemEls = this._elList.childNodes;
                // Fill items with data from the bottom up
                for(var i = nItemsToShow-1; i >= 0; i--) {
                    var elListItem = allListItemEls[i],
                    oResult = allResults[i];
                    
                    // Backward compatibility
                    if(this.resultTypeList) {
                        // Results need to be converted back to an array
                        var aResult = [];
                        // Match key is first
                        aResult[0] = (YAHOO.lang.isString(oResult)) ? oResult : oResult[sMatchKey] || oResult[this.key];
                        // Add additional data to the result array
                        var fields = this.dataSource.responseSchema.fields;
                        if(YAHOO.lang.isArray(fields) && (fields.length > 1)) {
                            for(var k=1, len=fields.length; k<len; k++) {
                                aResult[aResult.length] = oResult[fields[k].key || fields[k]];
                            }
                        }
                        // No specific fields defined, so pass along entire data object
                        else {
                            // Already an array
                            if(YAHOO.lang.isArray(oResult)) {
                                aResult = oResult;
                            }
                            // Simple string 
                            else if(YAHOO.lang.isString(oResult)) {
                                aResult = [oResult];
                            }
                            // Object
                            else {
                                aResult[1] = oResult;
                            }
                        }
                        oResult = aResult;
                    }

                    // The matching value, including backward compatibility for array format and safety net
                    elListItem._sResultMatch = (YAHOO.lang.isString(oResult)) ? oResult : (YAHOO.lang.isArray(oResult)) ? oResult[0] : (oResult[sMatchKey] || "");
                    elListItem._oResultData = oResult; // Additional data
                    this._populateListItem(elListItem, oResult, sCurQuery);
                    elListItem.style.display = "";
                }
        
                // Clear out extraneous items
                if(nItemsToShow < allListItemEls.length) {
                    var extraListItem;
                    for(var j = allListItemEls.length-1; j >= nItemsToShow; j--) {
                        extraListItem = allListItemEls[j];
                        extraListItem.style.display = "none";
                    }
                }
                
                this._nDisplayedItems = nItemsToShow;
                
                this.containerPopulateEvent.fire(this, sQuery, allResults);
                
                // Highlight the first item
                if(this.autoHighlight) {
                    var elFirstListItem = this._elList.firstChild;
                    this._toggleHighlight(elFirstListItem,"to");
                    this.itemArrowToEvent.fire(this, elFirstListItem);
                    YAHOO.log("Arrowed to first item", "info", this.toString());
                    this._typeAhead(elFirstListItem,sQuery);
                }
                // Unhighlight any previous time
                else {
                    this._toggleHighlight(this._elCurListItem,"from");
                }
        
                // Pre-expansion stuff
                ok = this._doBeforeExpandContainer(this._elTextbox, this._elContainer, sQuery, allResults);
                
                // Expand the container
                this._toggleContainer(ok);
            }
            else {
                this._toggleContainer(false);
            }

            YAHOO.log("Container populated with " + nItemsToShow +  " list items", "info", this.toString());
            return;
        }
    }
    // Error
    else {
        this.dataErrorEvent.fire(this, sQuery, oResponse);
    }
        
    YAHOO.log("Could not populate list", "info", this.toString());    
};

/**
 * Called before container expands, by default snaps container to the
 * bottom-left corner of the input element, then calls public overrideable method.
 *
 * @method _doBeforeExpandContainer
 * @param elTextbox {HTMLElement} The text input box.
 * @param elContainer {HTMLElement} The container element.
 * @param sQuery {String} The query string.
 * @param aResults {Object[]}  An array of query results.
 * @return {Boolean} Return true to continue expanding container, false to cancel the expand.
 * @private 
 */
YAHOO.widget.AutoComplete.prototype._doBeforeExpandContainer = function(elTextbox, elContainer, sQuery, aResults) {
    if(this.autoSnapContainer) {
        this.snapContainer();
    }

    return this.doBeforeExpandContainer(elTextbox, elContainer, sQuery, aResults);
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
    var extraction = (this.delimChar) ? this._extractQuery(this._elTextbox.value) :
            {previous:"",query:this._elTextbox.value};
    this._elTextbox.value = extraction.previous;
    this.selectionEnforceEvent.fire(this, extraction.query);
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
    var elMatch = null;

    for(var i=0; i<this._nDisplayedItems; i++) {
        var elListItem = this._elList.childNodes[i];
        var sMatch = ("" + elListItem._sResultMatch).toLowerCase();
        if(sMatch == this._sCurQuery.toLowerCase()) {
            elMatch = elListItem;
            break;
        }
    }
    return(elMatch);
};

/**
 * Updates in the text input box with the first query result as the user types,
 * selecting the substring that the user has not typed.
 *
 * @method _typeAhead
 * @param elListItem {HTMLElement} The &lt;li&gt; element item whose data populates the input field.
 * @param sQuery {String} Query string.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._typeAhead = function(elListItem, sQuery) {
    // Don't typeAhead if turned off or is backspace
    if(!this.typeAhead || (this._nKeyCode == 8)) {
        return;
    }

    var oSelf = this,
        elTextbox = this._elTextbox;
        
    // Only if text selection is supported
    if(elTextbox.setSelectionRange || elTextbox.createTextRange) {
        // Set and store timeout for this typeahead
        this._nTypeAheadDelayID = setTimeout(function() {
                // Select the portion of text that the user has not typed
                var nStart = elTextbox.value.length; // any saved queries plus what user has typed
                oSelf._updateValue(elListItem);
                var nEnd = elTextbox.value.length;
                oSelf._selectText(elTextbox,nStart,nEnd);
                var sPrefill = elTextbox.value.substr(nStart,nEnd);
                // Bug 2528552: Store as a selection
                oSelf._sCurQuery = elListItem._sResultMatch;
                oSelf.typeAheadEvent.fire(oSelf,sQuery,sPrefill);
                YAHOO.log("Typeahead occured with prefill string \"" + sPrefill + "\"", "info", oSelf.toString());
            },(this.typeAheadDelay*1000));            
    }
};

/**
 * Selects text in the input field.
 *
 * @method _selectText
 * @param elTextbox {HTMLElement} Text input box element in which to select text.
 * @param nStart {Number} Starting index of text string to select.
 * @param nEnd {Number} Ending index of text selection.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._selectText = function(elTextbox, nStart, nEnd) {
    if(elTextbox.setSelectionRange) { // For Mozilla
        elTextbox.setSelectionRange(nStart,nEnd);
    }
    else if(elTextbox.createTextRange) { // For IE
        var oTextRange = elTextbox.createTextRange();
        oTextRange.moveStart("character", nStart);
        oTextRange.moveEnd("character", nEnd-elTextbox.value.length);
        oTextRange.select();
    }
    else {
        elTextbox.select();
    }
};

/**
 * Extracts rightmost query from delimited string.
 *
 * @method _extractQuery
 * @param sQuery {String} String to parse
 * @return {Object} Object literal containing properties "query" and "previous".  
 * @private
 */
YAHOO.widget.AutoComplete.prototype._extractQuery = function(sQuery) {
    var aDelimChar = this.delimChar,
        nDelimIndex = -1,
        nNewIndex, nQueryStart,
        i = aDelimChar.length-1,
        sPrevious;
        
    // Loop through all possible delimiters and find the rightmost one in the query
    // A " " may be a false positive if they are defined as delimiters AND
    // are used to separate delimited queries
    for(; i >= 0; i--) {
        nNewIndex = sQuery.lastIndexOf(aDelimChar[i]);
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
    // A delimiter has been found in the query so extract the latest query from past selections
    if(nDelimIndex > -1) {
        nQueryStart = nDelimIndex + 1;
        // Trim any white space from the beginning...
        while(sQuery.charAt(nQueryStart) == " ") {
            nQueryStart += 1;
        }
        // ...and save the rest of the string for later
        sPrevious = sQuery.substring(0,nQueryStart);
        // Here is the query itself
        sQuery = sQuery.substr(nQueryStart);
    }
    // No delimiter found in the query, so there are no selections from past queries
    else {
        sPrevious = "";
    }
    
    return {
        previous: sPrevious,
        query: sQuery
    };
};

/**
 * Syncs results container with its helpers.
 *
 * @method _toggleContainerHelpers
 * @param bShow {Boolean} True if container is expanded, false if collapsed
 * @private
 */
YAHOO.widget.AutoComplete.prototype._toggleContainerHelpers = function(bShow) {
    var width = this._elContent.offsetWidth + "px";
    var height = this._elContent.offsetHeight + "px";

    if(this.useIFrame && this._elIFrame) {
    var elIFrame = this._elIFrame;
        if(bShow) {
            elIFrame.style.width = width;
            elIFrame.style.height = height;
            elIFrame.style.padding = "";
            YAHOO.log("Iframe expanded", "info", this.toString());
        }
        else {
            elIFrame.style.width = 0;
            elIFrame.style.height = 0;
            elIFrame.style.padding = 0;
            YAHOO.log("Iframe collapsed", "info", this.toString());
        }
    }
    if(this.useShadow && this._elShadow) {
    var elShadow = this._elShadow;
        if(bShow) {
            elShadow.style.width = width;
            elShadow.style.height = height;
            YAHOO.log("Shadow expanded", "info", this.toString());
        }
        else {
            elShadow.style.width = 0;
            elShadow.style.height = 0;
            YAHOO.log("Shadow collapsed", "info", this.toString());
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
    YAHOO.log("Toggling container " + ((bShow) ? "open" : "closed"), "info", this.toString());

    var elContainer = this._elContainer;

    // If implementer has container always open and it's already open, don't mess with it
    // Container is initialized with display "none" so it may need to be shown first time through
    if(this.alwaysShowContainer && this._bContainerOpen) {
        return;
    }
    
    // Reset states
    if(!bShow) {
        this._toggleHighlight(this._elCurListItem,"from");
        this._nDisplayedItems = 0;
        this._sCurQuery = null;
        
        // Container is already closed, so don't bother with changing the UI
        if(this._elContent.style.display == "none") {
            return;
        }
    }

    // If animation is enabled...
    var oAnim = this._oAnim;
    if(oAnim && oAnim.getEl() && (this.animHoriz || this.animVert)) {
        if(oAnim.isAnimated()) {
            oAnim.stop(true);
        }

        // Clone container to grab current size offscreen
        var oClone = this._elContent.cloneNode(true);
        elContainer.appendChild(oClone);
        oClone.style.top = "-9000px";
        oClone.style.width = "";
        oClone.style.height = "";
        oClone.style.display = "";

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
            this._elContent.style.width = wColl+"px";
            this._elContent.style.height = hColl+"px";
        }
        // Else, set it to its last known size.
        else {
            this._elContent.style.width = wExp+"px";
            this._elContent.style.height = hExp+"px";
        }

        elContainer.removeChild(oClone);
        oClone = null;

    	var oSelf = this;
    	var onAnimComplete = function() {
            // Finish the collapse
    		oAnim.onComplete.unsubscribeAll();

            if(bShow) {
                oSelf._toggleContainerHelpers(true);
                oSelf._bContainerOpen = bShow;
                oSelf.containerExpandEvent.fire(oSelf);
                YAHOO.log("Container expanded", "info", oSelf.toString());
            }
            else {
                oSelf._elContent.style.display = "none";
                oSelf._bContainerOpen = bShow;
                oSelf.containerCollapseEvent.fire(oSelf);
                YAHOO.log("Container collapsed", "info", oSelf.toString());
            }
     	};

        // Display container and animate it
        this._toggleContainerHelpers(false); // Bug 1424486: Be early to hide, late to show;
        this._elContent.style.display = "";
        oAnim.onComplete.subscribe(onAnimComplete);
        oAnim.animate();
    }
    // Else don't animate, just show or hide
    else {
        if(bShow) {
            this._elContent.style.display = "";
            this._toggleContainerHelpers(true);
            this._bContainerOpen = bShow;
            this.containerExpandEvent.fire(this);
            YAHOO.log("Container expanded", "info", this.toString());
        }
        else {
            this._toggleContainerHelpers(false);
            this._elContent.style.display = "none";
            this._bContainerOpen = bShow;
            this.containerCollapseEvent.fire(this);
            YAHOO.log("Container collapsed", "info", this.toString());
        }
   }

};

/**
 * Toggles the highlight on or off for an item in the container, and also cleans
 * up highlighting of any previous item.
 *
 * @method _toggleHighlight
 * @param elNewListItem {HTMLElement} The &lt;li&gt; element item to receive highlight behavior.
 * @param sType {String} Type "mouseover" will toggle highlight on, and "mouseout" will toggle highlight off.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._toggleHighlight = function(elNewListItem, sType) {
    if(elNewListItem) {
        var sHighlight = this.highlightClassName;
        if(this._elCurListItem) {
            // Remove highlight from old item
            YAHOO.util.Dom.removeClass(this._elCurListItem, sHighlight);
            this._elCurListItem = null;
        }
    
        if((sType == "to") && sHighlight) {
            // Apply highlight to new item
            YAHOO.util.Dom.addClass(elNewListItem, sHighlight);
            this._elCurListItem = elNewListItem;
        }
    }
};

/**
 * Toggles the pre-highlight on or off for an item in the container, and also cleans
 * up pre-highlighting of any previous item.
 *
 * @method _togglePrehighlight
 * @param elNewListItem {HTMLElement} The &lt;li&gt; element item to receive highlight behavior.
 * @param sType {String} Type "mouseover" will toggle highlight on, and "mouseout" will toggle highlight off.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._togglePrehighlight = function(elNewListItem, sType) {
    var sPrehighlight = this.prehighlightClassName;

    if(this._elCurPrehighlightItem) {
        YAHOO.util.Dom.removeClass(this._elCurPrehighlightItem, sPrehighlight);
    }
    if(elNewListItem == this._elCurListItem) {
        return;
    }

    if((sType == "mouseover") && sPrehighlight) {
        // Apply prehighlight to new item
        YAHOO.util.Dom.addClass(elNewListItem, sPrehighlight);
        this._elCurPrehighlightItem = elNewListItem;
    }
    else {
        // Remove prehighlight from old item
        YAHOO.util.Dom.removeClass(elNewListItem, sPrehighlight);
    }
};

/**
 * Updates the text input box value with selected query result. If a delimiter
 * has been defined, then the value gets appended with the delimiter.
 *
 * @method _updateValue
 * @param elListItem {HTMLElement} The &lt;li&gt; element item with which to update the value.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._updateValue = function(elListItem) {
    if(!this.suppressInputUpdate) {    
        var elTextbox = this._elTextbox;
        var sDelimChar = (this.delimChar) ? (this.delimChar[0] || this.delimChar) : null;
        var sResultMatch = elListItem._sResultMatch;
    
        // Calculate the new value
        var sNewValue = "";
        if(sDelimChar) {
            // Preserve selections from past queries
            sNewValue = this._sPastSelections;
            // Add new selection plus delimiter
            sNewValue += sResultMatch + sDelimChar;
            if(sDelimChar != " ") {
                sNewValue += " ";
            }
        }
        else { 
            sNewValue = sResultMatch;
        }
        
        // Update input field
        elTextbox.value = sNewValue;
    
        // Scroll to bottom of textarea if necessary
        if(elTextbox.type == "textarea") {
            elTextbox.scrollTop = elTextbox.scrollHeight;
        }
    
        // Move cursor to end
        var end = elTextbox.value.length;
        this._selectText(elTextbox,end,end);
    
        this._elCurListItem = elListItem;
    }
};

/**
 * Selects a result item from the container
 *
 * @method _selectItem
 * @param elListItem {HTMLElement} The selected &lt;li&gt; element item.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._selectItem = function(elListItem) {
    this._bItemSelected = true;
    this._updateValue(elListItem);
    this._sPastSelections = this._elTextbox.value;
    this._clearInterval();
    this.itemSelectEvent.fire(this, elListItem, elListItem._oResultData);
    YAHOO.log("Item selected: " + YAHOO.lang.dump(elListItem._oResultData), "info", this.toString());
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
    if(this._elCurListItem) {
        this._selectItem(this._elCurListItem);
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
        var elCurListItem = this._elCurListItem,
            nCurItemIndex = -1;

        if(elCurListItem) {
            nCurItemIndex = elCurListItem._nItemIndex;
        }

        var nNewItemIndex = (nKeyCode == 40) ?
                (nCurItemIndex + 1) : (nCurItemIndex - 1);

        // Out of bounds
        if(nNewItemIndex < -2 || nNewItemIndex >= this._nDisplayedItems) {
            return;
        }

        if(elCurListItem) {
            // Unhighlight current item
            this._toggleHighlight(elCurListItem, "from");
            this.itemArrowFromEvent.fire(this, elCurListItem);
            YAHOO.log("Item arrowed from: " + elCurListItem._nItemIndex, "info", this.toString());
        }
        if(nNewItemIndex == -1) {
           // Go back to query (remove type-ahead string)
            if(this.delimChar) {
                this._elTextbox.value = this._sPastSelections + this._sCurQuery;
            }
            else {
                this._elTextbox.value = this._sCurQuery;
            }
            return;
        }
        if(nNewItemIndex == -2) {
            // Close container
            this._toggleContainer(false);
            return;
        }
        
        var elNewListItem = this._elList.childNodes[nNewItemIndex],

        // Scroll the container if necessary
            elContent = this._elContent,
            sOF = YAHOO.util.Dom.getStyle(elContent,"overflow"),
            sOFY = YAHOO.util.Dom.getStyle(elContent,"overflowY"),
            scrollOn = ((sOF == "auto") || (sOF == "scroll") || (sOFY == "auto") || (sOFY == "scroll"));
        if(scrollOn && (nNewItemIndex > -1) &&
        (nNewItemIndex < this._nDisplayedItems)) {
            // User is keying down
            if(nKeyCode == 40) {
                // Bottom of selected item is below scroll area...
                if((elNewListItem.offsetTop+elNewListItem.offsetHeight) > (elContent.scrollTop + elContent.offsetHeight)) {
                    // Set bottom of scroll area to bottom of selected item
                    elContent.scrollTop = (elNewListItem.offsetTop+elNewListItem.offsetHeight) - elContent.offsetHeight;
                }
                // Bottom of selected item is above scroll area...
                else if((elNewListItem.offsetTop+elNewListItem.offsetHeight) < elContent.scrollTop) {
                    // Set top of selected item to top of scroll area
                    elContent.scrollTop = elNewListItem.offsetTop;

                }
            }
            // User is keying up
            else {
                // Top of selected item is above scroll area
                if(elNewListItem.offsetTop < elContent.scrollTop) {
                    // Set top of scroll area to top of selected item
                    this._elContent.scrollTop = elNewListItem.offsetTop;
                }
                // Top of selected item is below scroll area
                else if(elNewListItem.offsetTop > (elContent.scrollTop + elContent.offsetHeight)) {
                    // Set bottom of selected item to bottom of scroll area
                    this._elContent.scrollTop = (elNewListItem.offsetTop+elNewListItem.offsetHeight) - elContent.offsetHeight;
                }
            }
        }

        this._toggleHighlight(elNewListItem, "to");
        this.itemArrowToEvent.fire(this, elNewListItem);
        YAHOO.log("Item arrowed to " + elNewListItem._nItemIndex, "info", this.toString());
        if(this.typeAhead) {
            this._updateValue(elNewListItem);
            // Bug 2528552: Store as a selection
            this._sCurQuery = elNewListItem._sResultMatch;
        }
    }
};

/////////////////////////////////////////////////////////////////////////////
//
// Private event handlers
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Handles container mouseover events.
 *
 * @method _onContainerMouseover
 * @param v {HTMLEvent} The mouseover event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onContainerMouseover = function(v,oSelf) {
    var elTarget = YAHOO.util.Event.getTarget(v);
    var elTag = elTarget.nodeName.toLowerCase();
    while(elTarget && (elTag != "table")) {
        switch(elTag) {
            case "body":
                return;
            case "li":
                if(oSelf.prehighlightClassName) {
                    oSelf._togglePrehighlight(elTarget,"mouseover");
                }
                else {
                    oSelf._toggleHighlight(elTarget,"to");
                }
            
                oSelf.itemMouseOverEvent.fire(oSelf, elTarget);
                YAHOO.log("Item moused over " + elTarget._nItemIndex, "info", oSelf.toString());
                break;
            case "div":
                if(YAHOO.util.Dom.hasClass(elTarget,"yui-ac-container")) {
                    oSelf._bOverContainer = true;
                    return;
                }
                break;
            default:
                break;
        }
        
        elTarget = elTarget.parentNode;
        if(elTarget) {
            elTag = elTarget.nodeName.toLowerCase();
        }
    }
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
    var elTarget = YAHOO.util.Event.getTarget(v);
    var elTag = elTarget.nodeName.toLowerCase();
    while(elTarget && (elTag != "table")) {
        switch(elTag) {
            case "body":
                return;
            case "li":
                if(oSelf.prehighlightClassName) {
                    oSelf._togglePrehighlight(elTarget,"mouseout");
                }
                else {
                    oSelf._toggleHighlight(elTarget,"from");
                }
            
                oSelf.itemMouseOutEvent.fire(oSelf, elTarget);
                YAHOO.log("Item moused out " + elTarget._nItemIndex, "info", oSelf.toString());
                break;
            case "ul":
                oSelf._toggleHighlight(oSelf._elCurListItem,"to");
                break;
            case "div":
                if(YAHOO.util.Dom.hasClass(elTarget,"yui-ac-container")) {
                    oSelf._bOverContainer = false;
                    return;
                }
                break;
            default:
                break;
        }

        elTarget = elTarget.parentNode;
        if(elTarget) {
            elTag = elTarget.nodeName.toLowerCase();
        }
    }
};

/**
 * Handles container click events.
 *
 * @method _onContainerClick
 * @param v {HTMLEvent} The click event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onContainerClick = function(v,oSelf) {
    var elTarget = YAHOO.util.Event.getTarget(v);
    var elTag = elTarget.nodeName.toLowerCase();
    while(elTarget && (elTag != "table")) {
        switch(elTag) {
            case "body":
                return;
            case "li":
                // In case item has not been moused over
                oSelf._toggleHighlight(elTarget,"to");
                oSelf._selectItem(elTarget);
                return;
            default:
                break;
        }

        elTarget = elTarget.parentNode;
        if(elTarget) {
            elTag = elTarget.nodeName.toLowerCase();
        }
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
    oSelf._focus();
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

    // Clear timeout
    if(oSelf._nTypeAheadDelayID != -1) {
        clearTimeout(oSelf._nTypeAheadDelayID);
    }
    
    switch (nKeyCode) {
        case 9: // tab
            if(!YAHOO.env.ua.opera && (navigator.userAgent.toLowerCase().indexOf("mac") == -1) || (YAHOO.env.ua.webkit>420)) {
                // select an item or clear out
                if(oSelf._elCurListItem) {
                    if(oSelf.delimChar && (oSelf._nKeyCode != nKeyCode)) {
                        if(oSelf._bContainerOpen) {
                            YAHOO.util.Event.stopEvent(v);
                        }
                    }
                    oSelf._selectItem(oSelf._elCurListItem);
                }
                else {
                    oSelf._toggleContainer(false);
                }
            }
            break;
        case 13: // enter
            if(!YAHOO.env.ua.opera && (navigator.userAgent.toLowerCase().indexOf("mac") == -1) || (YAHOO.env.ua.webkit>420)) {
                if(oSelf._elCurListItem) {
                    if(oSelf._nKeyCode != nKeyCode) {
                        if(oSelf._bContainerOpen) {
                            YAHOO.util.Event.stopEvent(v);
                        }
                    }
                    oSelf._selectItem(oSelf._elCurListItem);
                }
                else {
                    oSelf._toggleContainer(false);
                }
            }
            break;
        case 27: // esc
            oSelf._toggleContainer(false);
            return;
        case 39: // right
            oSelf._jumpSelection();
            break;
        case 38: // up
            if(oSelf._bContainerOpen) {
                YAHOO.util.Event.stopEvent(v);
                oSelf._moveSelection(nKeyCode);
            }
            break;
        case 40: // down
            if(oSelf._bContainerOpen) {
                YAHOO.util.Event.stopEvent(v);
                oSelf._moveSelection(nKeyCode);
            }
            break;
        default: 
            oSelf._bItemSelected = false;
            oSelf._toggleHighlight(oSelf._elCurListItem, "from");

            oSelf.textboxKeyEvent.fire(oSelf, nKeyCode);
            YAHOO.log("Textbox keyed", "info", oSelf.toString());
            break;
    }

    if(nKeyCode === 18){
        oSelf._enableIntervalDetection();
    }    
    oSelf._nKeyCode = nKeyCode;
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

        // Expose only to non SF3 (bug 1978549) Mac browsers (bug 790337) and  Opera browsers (bug 583531),
        // where stopEvent is ineffective on keydown events 
        if(YAHOO.env.ua.opera || (navigator.userAgent.toLowerCase().indexOf("mac") != -1) && (YAHOO.env.ua.webkit < 420)) {
            switch (nKeyCode) {
            case 9: // tab
                // select an item or clear out
                if(oSelf._bContainerOpen) {
                    if(oSelf.delimChar) {
                        YAHOO.util.Event.stopEvent(v);
                    }
                    if(oSelf._elCurListItem) {
                        oSelf._selectItem(oSelf._elCurListItem);
                    }
                    else {
                        oSelf._toggleContainer(false);
                    }
                }
                break;
            case 13: // enter
                if(oSelf._bContainerOpen) {
                    YAHOO.util.Event.stopEvent(v);
                    if(oSelf._elCurListItem) {
                        oSelf._selectItem(oSelf._elCurListItem);
                    }
                    else {
                        oSelf._toggleContainer(false);
                    }
                }
                break;
            default:
                break;
            }
        }

        //TODO: (?) limit only to non-IE, non-Mac-FF for Korean IME support (bug 811948)
        // Korean IME detected
        else if(nKeyCode == 229) {
            oSelf._enableIntervalDetection();
        }
};

/**
 * Handles textbox keyup events to trigger queries.
 *
 * @method _onTextboxKeyUp
 * @param v {HTMLEvent} The keyup event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onTextboxKeyUp = function(v,oSelf) {
    var sText = this.value; //string in textbox
    
    // Check to see if any of the public properties have been updated
    oSelf._initProps();

    // Filter out chars that don't trigger queries
    var nKeyCode = v.keyCode;
    if(oSelf._isIgnoreKey(nKeyCode)) {
        return;
    }

    // Clear previous timeout
    if(oSelf._nDelayID != -1) {
        clearTimeout(oSelf._nDelayID);
    }

    // Set new timeout
    oSelf._nDelayID = setTimeout(function(){
            oSelf._sendQuery(sText);
        },(oSelf.queryDelay * 1000));
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
    // Start of a new interaction
    if(!oSelf._bFocused) {
        oSelf._elTextbox.setAttribute("autocomplete","off");
        oSelf._bFocused = true;
        oSelf._sInitInputValue = oSelf._elTextbox.value;
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
    // Is a true blur
    if(!oSelf._bOverContainer || (oSelf._nKeyCode == 9)) {
        // Current query needs to be validated as a selection
        if(!oSelf._bItemSelected) {
            var elMatchListItem = oSelf._textMatchesOption();
            // Container is closed or current query doesn't match any result
            if(!oSelf._bContainerOpen || (oSelf._bContainerOpen && (elMatchListItem === null))) {
                // Force selection is enabled so clear the current query
                if(oSelf.forceSelection) {
                    oSelf._clearSelection();
                }
                // Treat current query as a valid selection
                else {
                    oSelf.unmatchedItemSelectEvent.fire(oSelf, oSelf._sCurQuery);
                    YAHOO.log("Unmatched item selected: " + oSelf._sCurQuery, "info", oSelf.toString());
                }
            }
            // Container is open and current query matches a result
            else {
                // Force a selection when textbox is blurred with a match
                if(oSelf.forceSelection) {
                    oSelf._selectItem(elMatchListItem);
                }
            }
        }

        oSelf._clearInterval();
        oSelf._bFocused = false;
        if(oSelf._sInitInputValue !== oSelf._elTextbox.value) {
            oSelf.textboxChangeEvent.fire(oSelf);
        }
        oSelf.textboxBlurEvent.fire(oSelf);
        YAHOO.log("Textbox blurred", "info", oSelf.toString());

        oSelf._toggleContainer(false);
    }
    // Not a true blur if it was a selection via mouse click
    else {
        oSelf._focus();
    }
};

/**
 * Handles window unload event.
 *
 * @method _onWindowUnload
 * @param v {HTMLEvent} The unload event.
 * @param oSelf {YAHOO.widget.AutoComplete} The AutoComplete instance.
 * @private
 */
YAHOO.widget.AutoComplete.prototype._onWindowUnload = function(v,oSelf) {
    if(oSelf && oSelf._elTextbox && oSelf.allowBrowserAutocomplete) {
        oSelf._elTextbox.setAttribute("autocomplete","on");
    }
};

/////////////////////////////////////////////////////////////////////////////
//
// Deprecated for Backwards Compatibility
//
/////////////////////////////////////////////////////////////////////////////
/**
 * @method doBeforeSendQuery
 * @deprecated Use generateRequest.
 */
YAHOO.widget.AutoComplete.prototype.doBeforeSendQuery = function(sQuery) {
    return this.generateRequest(sQuery);
};

/**
 * @method getListItems
 * @deprecated Use getListEl().childNodes.
 */
YAHOO.widget.AutoComplete.prototype.getListItems = function() {
    var allListItemEls = [],
        els = this._elList.childNodes;
    for(var i=els.length-1; i>=0; i--) {
        allListItemEls[i] = els[i];
    }
    return allListItemEls;
};

/////////////////////////////////////////////////////////////////////////
//
// Private static methods
//
/////////////////////////////////////////////////////////////////////////

/**
 * Clones object literal or array of object literals.
 *
 * @method AutoComplete._cloneObject
 * @param o {Object} Object.
 * @private
 * @static     
 */
YAHOO.widget.AutoComplete._cloneObject = function(o) {
    if(!YAHOO.lang.isValue(o)) {
        return o;
    }
    
    var copy = {};
    
    if(YAHOO.lang.isFunction(o)) {
        copy = o;
    }
    else if(YAHOO.lang.isArray(o)) {
        var array = [];
        for(var i=0,len=o.length;i<len;i++) {
            array[i] = YAHOO.widget.AutoComplete._cloneObject(o[i]);
        }
        copy = array;
    }
    else if(YAHOO.lang.isObject(o)) { 
        for (var x in o){
            if(YAHOO.lang.hasOwnProperty(o, x)) {
                if(YAHOO.lang.isValue(o[x]) && YAHOO.lang.isObject(o[x]) || YAHOO.lang.isArray(o[x])) {
                    copy[x] = YAHOO.widget.AutoComplete._cloneObject(o[x]);
                }
                else {
                    copy[x] = o[x];
                }
            }
        }
    }
    else {
        copy = o;
    }

    return copy;
};




YAHOO.register("autocomplete", YAHOO.widget.AutoComplete, {version: "2.9.0", build: "2800"});
