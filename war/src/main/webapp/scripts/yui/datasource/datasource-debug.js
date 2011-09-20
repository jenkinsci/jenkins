/*
Copyright (c) 2011, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.com/yui/license.html
version: 2.9.0
*/
(function () {

var lang   = YAHOO.lang,
    util   = YAHOO.util,
    Ev     = util.Event;

/**
 * The DataSource utility provides a common configurable interface for widgets to
 * access a variety of data, from JavaScript arrays to online database servers.
 *
 * @module datasource
 * @requires yahoo, event
 * @optional json, get, connection 
 * @title DataSource Utility
 */

/****************************************************************************/
/****************************************************************************/
/****************************************************************************/

/**
 * Base class for the YUI DataSource utility.
 *
 * @namespace YAHOO.util
 * @class YAHOO.util.DataSourceBase
 * @constructor
 * @param oLiveData {HTMLElement}  Pointer to live data.
 * @param oConfigs {object} (optional) Object literal of configuration values.
 */
util.DataSourceBase = function(oLiveData, oConfigs) {
    if(oLiveData === null || oLiveData === undefined) {
        YAHOO.log("Could not instantiate DataSource due to invalid live database",
                "error", this.toString());
        return;
    }
    
    this.liveData = oLiveData;
    this._oQueue = {interval:null, conn:null, requests:[]};
    this.responseSchema = {};   

    // Set any config params passed in to override defaults
    if(oConfigs && (oConfigs.constructor == Object)) {
        for(var sConfig in oConfigs) {
            if(sConfig) {
                this[sConfig] = oConfigs[sConfig];
            }
        }
    }
    
    // Validate and initialize public configs
    var maxCacheEntries = this.maxCacheEntries;
    if(!lang.isNumber(maxCacheEntries) || (maxCacheEntries < 0)) {
        maxCacheEntries = 0;
    }

    // Initialize interval tracker
    this._aIntervals = [];

    /////////////////////////////////////////////////////////////////////////////
    //
    // Custom Events
    //
    /////////////////////////////////////////////////////////////////////////////

    /**
     * Fired when a request is made to the local cache.
     *
     * @event cacheRequestEvent
     * @param oArgs.request {Object} The request object.
     * @param oArgs.callback {Object} The callback object.
     * @param oArgs.caller {Object} (deprecated) Use callback.scope.
     */
    this.createEvent("cacheRequestEvent");

    /**
     * Fired when data is retrieved from the local cache.
     *
     * @event cacheResponseEvent
     * @param oArgs.request {Object} The request object.
     * @param oArgs.response {Object} The response object.
     * @param oArgs.callback {Object} The callback object.
     * @param oArgs.caller {Object} (deprecated) Use callback.scope.
     */
    this.createEvent("cacheResponseEvent");

    /**
     * Fired when a request is sent to the live data source.
     *
     * @event requestEvent
     * @param oArgs.request {Object} The request object.
     * @param oArgs.callback {Object} The callback object.
     * @param oArgs.tId {Number} Transaction ID.     
     * @param oArgs.caller {Object} (deprecated) Use callback.scope.
     */
    this.createEvent("requestEvent");

    /**
     * Fired when live data source sends response.
     *
     * @event responseEvent
     * @param oArgs.request {Object} The request object.
     * @param oArgs.response {Object} The raw response object.
     * @param oArgs.callback {Object} The callback object.
     * @param oArgs.tId {Number} Transaction ID.     
     * @param oArgs.caller {Object} (deprecated) Use callback.scope.
     */
    this.createEvent("responseEvent");

    /**
     * Fired when response is parsed.
     *
     * @event responseParseEvent
     * @param oArgs.request {Object} The request object.
     * @param oArgs.response {Object} The parsed response object.
     * @param oArgs.callback {Object} The callback object.
     * @param oArgs.caller {Object} (deprecated) Use callback.scope.
     */
    this.createEvent("responseParseEvent");

    /**
     * Fired when response is cached.
     *
     * @event responseCacheEvent
     * @param oArgs.request {Object} The request object.
     * @param oArgs.response {Object} The parsed response object.
     * @param oArgs.callback {Object} The callback object.
     * @param oArgs.caller {Object} (deprecated) Use callback.scope.
     */
    this.createEvent("responseCacheEvent");
    /**
     * Fired when an error is encountered with the live data source.
     *
     * @event dataErrorEvent
     * @param oArgs.request {Object} The request object.
     * @param oArgs.response {String} The response object (if available).
     * @param oArgs.callback {Object} The callback object.
     * @param oArgs.caller {Object} (deprecated) Use callback.scope.
     * @param oArgs.message {String} The error message.
     */
    this.createEvent("dataErrorEvent");

    /**
     * Fired when the local cache is flushed.
     *
     * @event cacheFlushEvent
     */
    this.createEvent("cacheFlushEvent");

    var DS = util.DataSourceBase;
    this._sName = "DataSource instance" + DS._nIndex;
    DS._nIndex++;
    YAHOO.log("DataSource initialized", "info", this.toString());
};

var DS = util.DataSourceBase;

lang.augmentObject(DS, {

/////////////////////////////////////////////////////////////////////////////
//
// DataSourceBase public constants
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Type is unknown.
 *
 * @property TYPE_UNKNOWN
 * @type Number
 * @final
 * @default -1
 */
TYPE_UNKNOWN : -1,

/**
 * Type is a JavaScript Array.
 *
 * @property TYPE_JSARRAY
 * @type Number
 * @final
 * @default 0
 */
TYPE_JSARRAY : 0,

/**
 * Type is a JavaScript Function.
 *
 * @property TYPE_JSFUNCTION
 * @type Number
 * @final
 * @default 1
 */
TYPE_JSFUNCTION : 1,

/**
 * Type is hosted on a server via an XHR connection.
 *
 * @property TYPE_XHR
 * @type Number
 * @final
 * @default 2
 */
TYPE_XHR : 2,

/**
 * Type is JSON.
 *
 * @property TYPE_JSON
 * @type Number
 * @final
 * @default 3
 */
TYPE_JSON : 3,

/**
 * Type is XML.
 *
 * @property TYPE_XML
 * @type Number
 * @final
 * @default 4
 */
TYPE_XML : 4,

/**
 * Type is plain text.
 *
 * @property TYPE_TEXT
 * @type Number
 * @final
 * @default 5
 */
TYPE_TEXT : 5,

/**
 * Type is an HTML TABLE element. Data is parsed out of TR elements from all TBODY elements.
 *
 * @property TYPE_HTMLTABLE
 * @type Number
 * @final
 * @default 6
 */
TYPE_HTMLTABLE : 6,

/**
 * Type is hosted on a server via a dynamic script node.
 *
 * @property TYPE_SCRIPTNODE
 * @type Number
 * @final
 * @default 7
 */
TYPE_SCRIPTNODE : 7,

/**
 * Type is local.
 *
 * @property TYPE_LOCAL
 * @type Number
 * @final
 * @default 8
 */
TYPE_LOCAL : 8,

/**
 * Error message for invalid dataresponses.
 *
 * @property ERROR_DATAINVALID
 * @type String
 * @final
 * @default "Invalid data"
 */
ERROR_DATAINVALID : "Invalid data",

/**
 * Error message for null data responses.
 *
 * @property ERROR_DATANULL
 * @type String
 * @final
 * @default "Null data"
 */
ERROR_DATANULL : "Null data",

/////////////////////////////////////////////////////////////////////////////
//
// DataSourceBase private static properties
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Internal class variable to index multiple DataSource instances.
 *
 * @property DataSourceBase._nIndex
 * @type Number
 * @private
 * @static
 */
_nIndex : 0,

/**
 * Internal class variable to assign unique transaction IDs.
 *
 * @property DataSourceBase._nTransactionId
 * @type Number
 * @private
 * @static
 */
_nTransactionId : 0,

/////////////////////////////////////////////////////////////////////////////
//
// DataSourceBase private static methods
//
/////////////////////////////////////////////////////////////////////////////
/**
 * Clones object literal or array of object literals.
 *
 * @method DataSourceBase._cloneObject
 * @param o {Object} Object.
 * @private
 * @static
 */
_cloneObject: function(o) {
    if(!lang.isValue(o)) {
        return o;
    }

    var copy = {};

    if(Object.prototype.toString.apply(o) === "[object RegExp]") {
        copy = o;
    }
    else if(lang.isFunction(o)) {
        copy = o;
    }
    else if(lang.isArray(o)) {
        var array = [];
        for(var i=0,len=o.length;i<len;i++) {
            array[i] = DS._cloneObject(o[i]);
        }
        copy = array;
    }
    else if(lang.isObject(o)) {
        for (var x in o){
            if(lang.hasOwnProperty(o, x)) {
                if(lang.isValue(o[x]) && lang.isObject(o[x]) || lang.isArray(o[x])) {
                    copy[x] = DS._cloneObject(o[x]);
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
},
    
/**
 * Get an XPath-specified value for a given field from an XML node or document.
 *
 * @method _getLocationValue
 * @param field {String | Object} Field definition.
 * @param context {Object} XML node or document to search within.
 * @return {Object} Data value or null.
 * @static
 * @private
 */
_getLocationValue: function(field, context) {
    var locator = field.locator || field.key || field,
        xmldoc = context.ownerDocument || context,
        result, res, value = null;

    try {
        // Standards mode
        if(!lang.isUndefined(xmldoc.evaluate)) {
            result = xmldoc.evaluate(locator, context, xmldoc.createNSResolver(!context.ownerDocument ? context.documentElement : context.ownerDocument.documentElement), 0, null);
            while(res = result.iterateNext()) {
                value = res.textContent;
            }
        }
        // IE mode
        else {
            xmldoc.setProperty("SelectionLanguage", "XPath");
            result = context.selectNodes(locator)[0];
            value = result.value || result.text || null;
        }
        return value;

    }
    catch(e) {
    }
},

/////////////////////////////////////////////////////////////////////////////
//
// DataSourceBase public static methods
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Executes a configured callback.  For object literal callbacks, the third
 * param determines whether to execute the success handler or failure handler.
 *  
 * @method issueCallback
 * @param callback {Function|Object} the callback to execute
 * @param params {Array} params to be passed to the callback method
 * @param error {Boolean} whether an error occurred
 * @param scope {Object} the scope from which to execute the callback
 * (deprecated - use an object literal callback)
 * @static     
 */
issueCallback : function (callback,params,error,scope) {
    if (lang.isFunction(callback)) {
        callback.apply(scope, params);
    } else if (lang.isObject(callback)) {
        scope = callback.scope || scope || window;
        var callbackFunc = callback.success;
        if (error) {
            callbackFunc = callback.failure;
        }
        if (callbackFunc) {
            callbackFunc.apply(scope, params.concat([callback.argument]));
        }
    }
},

/**
 * Converts data to type String.
 *
 * @method DataSourceBase.parseString
 * @param oData {String | Number | Boolean | Date | Array | Object} Data to parse.
 * The special values null and undefined will return null.
 * @return {String} A string, or null.
 * @static
 */
parseString : function(oData) {
    // Special case null and undefined
    if(!lang.isValue(oData)) {
        return null;
    }
    
    //Convert to string
    var string = oData + "";

    // Validate
    if(lang.isString(string)) {
        return string;
    }
    else {
        YAHOO.log("Could not convert data " + lang.dump(oData) + " to type String", "warn", this.toString());
        return null;
    }
},

/**
 * Converts data to type Number.
 *
 * @method DataSourceBase.parseNumber
 * @param oData {String | Number | Boolean} Data to convert. Note, the following
 * values return as null: null, undefined, NaN, "". 
 * @return {Number} A number, or null.
 * @static
 */
parseNumber : function(oData) {
    if(!lang.isValue(oData) || (oData === "")) {
        return null;
    }

    //Convert to number
    var number = oData * 1;
    
    // Validate
    if(lang.isNumber(number)) {
        return number;
    }
    else {
        YAHOO.log("Could not convert data " + lang.dump(oData) + " to type Number", "warn", this.toString());
        return null;
    }
},
// Backward compatibility
convertNumber : function(oData) {
    YAHOO.log("The method YAHOO.util.DataSourceBase.convertNumber() has been" +
    " deprecated in favor of YAHOO.util.DataSourceBase.parseNumber()", "warn",
    this.toString());
    return DS.parseNumber(oData);
},

/**
 * Converts data to type Date.
 *
 * @method DataSourceBase.parseDate
 * @param oData {Date | String | Number} Data to convert.
 * @return {Date} A Date instance.
 * @static
 */
parseDate : function(oData) {
    var date = null;
    
    //Convert to date
    if(lang.isValue(oData) && !(oData instanceof Date)) {
        date = new Date(oData);
    }
    else {
        return oData;
    }
    
    // Validate
    if(date instanceof Date) {
        return date;
    }
    else {
        YAHOO.log("Could not convert data " + lang.dump(oData) + " to type Date", "warn", this.toString());
        return null;
    }
},
// Backward compatibility
convertDate : function(oData) {
    YAHOO.log("The method YAHOO.util.DataSourceBase.convertDate() has been" +
    " deprecated in favor of YAHOO.util.DataSourceBase.parseDate()", "warn",
    this.toString());
    return DS.parseDate(oData);
}

});

// Done in separate step so referenced functions are defined.
/**
 * Data parsing functions.
 * @property DataSource.Parser
 * @type Object
 * @static
 */
DS.Parser = {
    string   : DS.parseString,
    number   : DS.parseNumber,
    date     : DS.parseDate
};

// Prototype properties and methods
DS.prototype = {

/////////////////////////////////////////////////////////////////////////////
//
// DataSourceBase private properties
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Name of DataSource instance.
 *
 * @property _sName
 * @type String
 * @private
 */
_sName : null,

/**
 * Local cache of data result object literals indexed chronologically.
 *
 * @property _aCache
 * @type Object[]
 * @private
 */
_aCache : null,

/**
 * Local queue of request connections, enabled if queue needs to be managed.
 *
 * @property _oQueue
 * @type Object
 * @private
 */
_oQueue : null,

/**
 * Array of polling interval IDs that have been enabled, needed to clear all intervals.
 *
 * @property _aIntervals
 * @type Array
 * @private
 */
_aIntervals : null,

/////////////////////////////////////////////////////////////////////////////
//
// DataSourceBase public properties
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
 * @default 0
 */
maxCacheEntries : 0,

 /**
 * Pointer to live database.
 *
 * @property liveData
 * @type Object
 */
liveData : null,

/**
 * Where the live data is held:
 * 
 * <dl>  
 *    <dt>TYPE_UNKNOWN</dt>
 *    <dt>TYPE_LOCAL</dt>
 *    <dt>TYPE_XHR</dt>
 *    <dt>TYPE_SCRIPTNODE</dt>
 *    <dt>TYPE_JSFUNCTION</dt>
 * </dl> 
 *  
 * @property dataType
 * @type Number
 * @default YAHOO.util.DataSourceBase.TYPE_UNKNOWN
 *
 */
dataType : DS.TYPE_UNKNOWN,

/**
 * Format of response:
 *  
 * <dl>  
 *    <dt>TYPE_UNKNOWN</dt>
 *    <dt>TYPE_JSARRAY</dt>
 *    <dt>TYPE_JSON</dt>
 *    <dt>TYPE_XML</dt>
 *    <dt>TYPE_TEXT</dt>
 *    <dt>TYPE_HTMLTABLE</dt> 
 * </dl> 
 *
 * @property responseType
 * @type Number
 * @default YAHOO.util.DataSourceBase.TYPE_UNKNOWN
 */
responseType : DS.TYPE_UNKNOWN,

/**
 * Response schema object literal takes a combination of the following properties:
 *
 * <dl>
 * <dt>resultsList</dt> <dd>Pointer to array of tabular data</dd>
 * <dt>resultNode</dt> <dd>Pointer to node name of row data (XML data only)</dd>
 * <dt>recordDelim</dt> <dd>Record delimiter (text data only)</dd>
 * <dt>fieldDelim</dt> <dd>Field delimiter (text data only)</dd>
 * <dt>fields</dt> <dd>Array of field names (aka keys), or array of object literals
 * such as: {key:"fieldname",parser:YAHOO.util.DataSourceBase.parseDate}</dd>
 * <dt>metaFields</dt> <dd>Object literal of keys to include in the oParsedResponse.meta collection</dd>
 * <dt>metaNode</dt> <dd>Name of the node under which to search for meta information in XML response data</dd>
 * </dl>
 *
 * @property responseSchema
 * @type Object
 */
responseSchema : null,

/**
 * Additional arguments passed to the JSON parse routine.  The JSON string
 * is the assumed first argument (where applicable).  This property is not
 * set by default, but the parse methods will use it if present.
 *
 * @property parseJSONArgs
 * @type {MIXED|Array} If an Array, contents are used as individual arguments.
 *                     Otherwise, value is used as an additional argument.
 */
// property intentionally undefined
 
/**
 * When working with XML data, setting this property to true enables support for
 * XPath-syntaxed locators in schema definitions.
 *
 * @property useXPath
 * @type Boolean
 * @default false
 */
useXPath : false,

/**
 * Clones entries before adding to cache.
 *
 * @property cloneBeforeCaching
 * @type Boolean
 * @default false
 */
cloneBeforeCaching : false,

/////////////////////////////////////////////////////////////////////////////
//
// DataSourceBase public methods
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Public accessor to the unique name of the DataSource instance.
 *
 * @method toString
 * @return {String} Unique name of the DataSource instance.
 */
toString : function() {
    return this._sName;
},

/**
 * Overridable method passes request to cache and returns cached response if any,
 * refreshing the hit in the cache as the newest item. Returns null if there is
 * no cache hit.
 *
 * @method getCachedResponse
 * @param oRequest {Object} Request object.
 * @param oCallback {Object} Callback object.
 * @param oCaller {Object} (deprecated) Use callback object.
 * @return {Object} Cached response object or null.
 */
getCachedResponse : function(oRequest, oCallback, oCaller) {
    var aCache = this._aCache;

    // If cache is enabled...
    if(this.maxCacheEntries > 0) {        
        // Initialize local cache
        if(!aCache) {
            this._aCache = [];
            YAHOO.log("Cache initialized", "info", this.toString());
        }
        // Look in local cache
        else {
            var nCacheLength = aCache.length;
            if(nCacheLength > 0) {
                var oResponse = null;
                this.fireEvent("cacheRequestEvent", {request:oRequest,callback:oCallback,caller:oCaller});
        
                // Loop through each cached element
                for(var i = nCacheLength-1; i >= 0; i--) {
                    var oCacheElem = aCache[i];
        
                    // Defer cache hit logic to a public overridable method
                    if(this.isCacheHit(oRequest,oCacheElem.request)) {
                        // The cache returned a hit!
                        // Grab the cached response
                        oResponse = oCacheElem.response;
                        this.fireEvent("cacheResponseEvent", {request:oRequest,response:oResponse,callback:oCallback,caller:oCaller});
                        
                        // Refresh the position of the cache hit
                        if(i < nCacheLength-1) {
                            // Remove element from its original location
                            aCache.splice(i,1);
                            // Add as newest
                            this.addToCache(oRequest, oResponse);
                            YAHOO.log("Refreshed cache position of the response for \"" +  oRequest + "\"", "info", this.toString());
                        }
                        
                        // Add a cache flag
                        oResponse.cached = true;
                        break;
                    }
                }
                YAHOO.log("The cached response for \"" + lang.dump(oRequest) +
                        "\" is " + lang.dump(oResponse), "info", this.toString());
                return oResponse;
            }
        }
    }
    else if(aCache) {
        this._aCache = null;
        YAHOO.log("Cache destroyed", "info", this.toString());
    }
    return null;
},

/**
 * Default overridable method matches given request to given cached request.
 * Returns true if is a hit, returns false otherwise.  Implementers should
 * override this method to customize the cache-matching algorithm.
 *
 * @method isCacheHit
 * @param oRequest {Object} Request object.
 * @param oCachedRequest {Object} Cached request object.
 * @return {Boolean} True if given request matches cached request, false otherwise.
 */
isCacheHit : function(oRequest, oCachedRequest) {
    return (oRequest === oCachedRequest);
},

/**
 * Adds a new item to the cache. If cache is full, evicts the stalest item
 * before adding the new item.
 *
 * @method addToCache
 * @param oRequest {Object} Request object.
 * @param oResponse {Object} Response object to cache.
 */
addToCache : function(oRequest, oResponse) {
    var aCache = this._aCache;
    if(!aCache) {
        return;
    }

    // If the cache is full, make room by removing stalest element (index=0)
    while(aCache.length >= this.maxCacheEntries) {
        aCache.shift();
    }

    // Add to cache in the newest position, at the end of the array
    oResponse = (this.cloneBeforeCaching) ? DS._cloneObject(oResponse) : oResponse;
    var oCacheElem = {request:oRequest,response:oResponse};
    aCache[aCache.length] = oCacheElem;
    this.fireEvent("responseCacheEvent", {request:oRequest,response:oResponse});
    YAHOO.log("Cached the response for \"" +  oRequest + "\"", "info", this.toString());
},

/**
 * Flushes cache.
 *
 * @method flushCache
 */
flushCache : function() {
    if(this._aCache) {
        this._aCache = [];
        this.fireEvent("cacheFlushEvent");
        YAHOO.log("Flushed the cache", "info", this.toString());
    }
},

/**
 * Sets up a polling mechanism to send requests at set intervals and forward
 * responses to given callback.
 *
 * @method setInterval
 * @param nMsec {Number} Length of interval in milliseconds.
 * @param oRequest {Object} Request object.
 * @param oCallback {Function} Handler function to receive the response.
 * @param oCaller {Object} (deprecated) Use oCallback.scope.
 * @return {Number} Interval ID.
 */
setInterval : function(nMsec, oRequest, oCallback, oCaller) {
    if(lang.isNumber(nMsec) && (nMsec >= 0)) {
        YAHOO.log("Enabling polling to live data for \"" + oRequest + "\" at interval " + nMsec, "info", this.toString());
        var oSelf = this;
        var nId = setInterval(function() {
            oSelf.makeConnection(oRequest, oCallback, oCaller);
        }, nMsec);
        this._aIntervals.push(nId);
        return nId;
    }
    else {
        YAHOO.log("Could not enable polling to live data for \"" + oRequest + "\" at interval " + nMsec, "info", this.toString());
    }
},

/**
 * Disables polling mechanism associated with the given interval ID. Does not
 * affect transactions that are in progress.
 *
 * @method clearInterval
 * @param nId {Number} Interval ID.
 */
clearInterval : function(nId) {
    // Remove from tracker if there
    var tracker = this._aIntervals || [];
    for(var i=tracker.length-1; i>-1; i--) {
        if(tracker[i] === nId) {
            tracker.splice(i,1);
            clearInterval(nId);
        }
    }
},

/**
 * Disables all known polling intervals. Does not affect transactions that are
 * in progress.
 *
 * @method clearAllIntervals
 */
clearAllIntervals : function() {
    var tracker = this._aIntervals || [];
    for(var i=tracker.length-1; i>-1; i--) {
        clearInterval(tracker[i]);
    }
    tracker = [];
},

/**
 * First looks for cached response, then sends request to live data. The
 * following arguments are passed to the callback function:
 *     <dl>
 *     <dt><code>oRequest</code></dt>
 *     <dd>The same value that was passed in as the first argument to sendRequest.</dd>
 *     <dt><code>oParsedResponse</code></dt>
 *     <dd>An object literal containing the following properties:
 *         <dl>
 *         <dt><code>tId</code></dt>
 *         <dd>Unique transaction ID number.</dd>
 *         <dt><code>results</code></dt>
 *         <dd>Schema-parsed data results.</dd>
 *         <dt><code>error</code></dt>
 *         <dd>True in cases of data error.</dd>
 *         <dt><code>cached</code></dt>
 *         <dd>True when response is returned from DataSource cache.</dd> 
 *         <dt><code>meta</code></dt>
 *         <dd>Schema-parsed meta data.</dd>
 *         </dl>
 *     <dt><code>oPayload</code></dt>
 *     <dd>The same value as was passed in as <code>argument</code> in the oCallback object literal.</dd>
 *     </dl> 
 *
 * @method sendRequest
 * @param oRequest {Object} Request object.
 * @param oCallback {Object} An object literal with the following properties:
 *     <dl>
 *     <dt><code>success</code></dt>
 *     <dd>The function to call when the data is ready.</dd>
 *     <dt><code>failure</code></dt>
 *     <dd>The function to call upon a response failure condition.</dd>
 *     <dt><code>scope</code></dt>
 *     <dd>The object to serve as the scope for the success and failure handlers.</dd>
 *     <dt><code>argument</code></dt>
 *     <dd>Arbitrary data that will be passed back to the success and failure handlers.</dd>
 *     </dl> 
 * @param oCaller {Object} (deprecated) Use oCallback.scope.
 * @return {Number} Transaction ID, or null if response found in cache.
 */
sendRequest : function(oRequest, oCallback, oCaller) {
    // First look in cache
    var oCachedResponse = this.getCachedResponse(oRequest, oCallback, oCaller);
    if(oCachedResponse) {
        DS.issueCallback(oCallback,[oRequest,oCachedResponse],false,oCaller);
        return null;
    }


    // Not in cache, so forward request to live data
    YAHOO.log("Making connection to live data for \"" + oRequest + "\"", "info", this.toString());
    return this.makeConnection(oRequest, oCallback, oCaller);
},

/**
 * Overridable default method generates a unique transaction ID and passes 
 * the live data reference directly to the  handleResponse function. This
 * method should be implemented by subclasses to achieve more complex behavior
 * or to access remote data.          
 *
 * @method makeConnection
 * @param oRequest {Object} Request object.
 * @param oCallback {Object} Callback object literal.
 * @param oCaller {Object} (deprecated) Use oCallback.scope.
 * @return {Number} Transaction ID.
 */
makeConnection : function(oRequest, oCallback, oCaller) {
    var tId = DS._nTransactionId++;
    this.fireEvent("requestEvent", {tId:tId, request:oRequest,callback:oCallback,caller:oCaller});

    /* accounts for the following cases:
    YAHOO.util.DataSourceBase.TYPE_UNKNOWN
    YAHOO.util.DataSourceBase.TYPE_JSARRAY
    YAHOO.util.DataSourceBase.TYPE_JSON
    YAHOO.util.DataSourceBase.TYPE_HTMLTABLE
    YAHOO.util.DataSourceBase.TYPE_XML
    YAHOO.util.DataSourceBase.TYPE_TEXT
    */
    var oRawResponse = this.liveData;
    
    this.handleResponse(oRequest, oRawResponse, oCallback, oCaller, tId);
    return tId;
},

/**
 * Receives raw data response and type converts to XML, JSON, etc as necessary.
 * Forwards oFullResponse to appropriate parsing function to get turned into
 * oParsedResponse. Calls doBeforeCallback() and adds oParsedResponse to 
 * the cache when appropriate before calling issueCallback().
 * 
 * The oParsedResponse object literal has the following properties:
 * <dl>
 *     <dd><dt>tId {Number}</dt> Unique transaction ID</dd>
 *     <dd><dt>results {Array}</dt> Array of parsed data results</dd>
 *     <dd><dt>meta {Object}</dt> Object literal of meta values</dd> 
 *     <dd><dt>error {Boolean}</dt> (optional) True if there was an error</dd>
 *     <dd><dt>cached {Boolean}</dt> (optional) True if response was cached</dd>
 * </dl>
 *
 * @method handleResponse
 * @param oRequest {Object} Request object
 * @param oRawResponse {Object} The raw response from the live database.
 * @param oCallback {Object} Callback object literal.
 * @param oCaller {Object} (deprecated) Use oCallback.scope.
 * @param tId {Number} Transaction ID.
 */
handleResponse : function(oRequest, oRawResponse, oCallback, oCaller, tId) {
    this.fireEvent("responseEvent", {tId:tId, request:oRequest, response:oRawResponse,
            callback:oCallback, caller:oCaller});
    YAHOO.log("Received live data response for \"" + oRequest + "\"", "info", this.toString());
    var xhr = (this.dataType == DS.TYPE_XHR) ? true : false;
    var oParsedResponse = null;
    var oFullResponse = oRawResponse;
    
    // Try to sniff data type if it has not been defined
    if(this.responseType === DS.TYPE_UNKNOWN) {
        var ctype = (oRawResponse && oRawResponse.getResponseHeader) ? oRawResponse.getResponseHeader["Content-Type"] : null;
        if(ctype) {
             // xml
            if(ctype.indexOf("text/xml") > -1) {
                this.responseType = DS.TYPE_XML;
            }
            else if(ctype.indexOf("application/json") > -1) { // json
                this.responseType = DS.TYPE_JSON;
            }
            else if(ctype.indexOf("text/plain") > -1) { // text
                this.responseType = DS.TYPE_TEXT;
            }
        }
        else {
            if(YAHOO.lang.isArray(oRawResponse)) { // array
                this.responseType = DS.TYPE_JSARRAY;
            }
             // xml
            else if(oRawResponse && oRawResponse.nodeType && (oRawResponse.nodeType === 9 || oRawResponse.nodeType === 1 || oRawResponse.nodeType === 11)) {
                this.responseType = DS.TYPE_XML;
            }
            else if(oRawResponse && oRawResponse.nodeName && (oRawResponse.nodeName.toLowerCase() == "table")) { // table
                this.responseType = DS.TYPE_HTMLTABLE;
            }    
            else if(YAHOO.lang.isObject(oRawResponse)) { // json
                this.responseType = DS.TYPE_JSON;
            }
            else if(YAHOO.lang.isString(oRawResponse)) { // text
                this.responseType = DS.TYPE_TEXT;
            }
        }
    }

    switch(this.responseType) {
        case DS.TYPE_JSARRAY:
            if(xhr && oRawResponse && oRawResponse.responseText) {
                oFullResponse = oRawResponse.responseText; 
            }
            try {
                // Convert to JS array if it's a string
                if(lang.isString(oFullResponse)) {
                    var parseArgs = [oFullResponse].concat(this.parseJSONArgs);
                    // Check for YUI JSON Util
                    if(lang.JSON) {
                        oFullResponse = lang.JSON.parse.apply(lang.JSON,parseArgs);
                    }
                    // Look for JSON parsers using an API similar to json2.js
                    else if(window.JSON && JSON.parse) {
                        oFullResponse = JSON.parse.apply(JSON,parseArgs);
                    }
                    // Look for JSON parsers using an API similar to json.js
                    else if(oFullResponse.parseJSON) {
                        oFullResponse = oFullResponse.parseJSON.apply(oFullResponse,parseArgs.slice(1));
                    }
                    // No JSON lib found so parse the string
                    else {
                        // Trim leading spaces
                        while (oFullResponse.length > 0 &&
                                (oFullResponse.charAt(0) != "{") &&
                                (oFullResponse.charAt(0) != "[")) {
                            oFullResponse = oFullResponse.substring(1, oFullResponse.length);
                        }

                        if(oFullResponse.length > 0) {
                            // Strip extraneous stuff at the end
                            var arrayEnd =
Math.max(oFullResponse.lastIndexOf("]"),oFullResponse.lastIndexOf("}"));
                            oFullResponse = oFullResponse.substring(0,arrayEnd+1);

                            // Turn the string into an object literal...
                            // ...eval is necessary here
                            oFullResponse = eval("(" + oFullResponse + ")");

                        }
                    }
                }
            }
            catch(e1) {
            }
            oFullResponse = this.doBeforeParseData(oRequest, oFullResponse, oCallback);
            oParsedResponse = this.parseArrayData(oRequest, oFullResponse);
            break;
        case DS.TYPE_JSON:
            if(xhr && oRawResponse && oRawResponse.responseText) {
                oFullResponse = oRawResponse.responseText;
            }
            try {
                // Convert to JSON object if it's a string
                if(lang.isString(oFullResponse)) {
                    var parseArgs = [oFullResponse].concat(this.parseJSONArgs);
                    // Check for YUI JSON Util
                    if(lang.JSON) {
                        oFullResponse = lang.JSON.parse.apply(lang.JSON,parseArgs);
                    }
                    // Look for JSON parsers using an API similar to json2.js
                    else if(window.JSON && JSON.parse) {
                        oFullResponse = JSON.parse.apply(JSON,parseArgs);
                    }
                    // Look for JSON parsers using an API similar to json.js
                    else if(oFullResponse.parseJSON) {
                        oFullResponse = oFullResponse.parseJSON.apply(oFullResponse,parseArgs.slice(1));
                    }
                    // No JSON lib found so parse the string
                    else {
                        // Trim leading spaces
                        while (oFullResponse.length > 0 &&
                                (oFullResponse.charAt(0) != "{") &&
                                (oFullResponse.charAt(0) != "[")) {
                            oFullResponse = oFullResponse.substring(1, oFullResponse.length);
                        }
    
                        if(oFullResponse.length > 0) {
                            // Strip extraneous stuff at the end
                            var objEnd = Math.max(oFullResponse.lastIndexOf("]"),oFullResponse.lastIndexOf("}"));
                            oFullResponse = oFullResponse.substring(0,objEnd+1);
    
                            // Turn the string into an object literal...
                            // ...eval is necessary here
                            oFullResponse = eval("(" + oFullResponse + ")");
    
                        }
                    }
                }
            }
            catch(e) {
            }

            oFullResponse = this.doBeforeParseData(oRequest, oFullResponse, oCallback);
            oParsedResponse = this.parseJSONData(oRequest, oFullResponse);
            break;
        case DS.TYPE_HTMLTABLE:
            if(xhr && oRawResponse.responseText) {
                var el = document.createElement('div');
                el.innerHTML = oRawResponse.responseText;
                oFullResponse = el.getElementsByTagName('table')[0];
            }
            oFullResponse = this.doBeforeParseData(oRequest, oFullResponse, oCallback);
            oParsedResponse = this.parseHTMLTableData(oRequest, oFullResponse);
            break;
        case DS.TYPE_XML:
            if(xhr && oRawResponse.responseXML) {
                oFullResponse = oRawResponse.responseXML;
            }
            oFullResponse = this.doBeforeParseData(oRequest, oFullResponse, oCallback);
            oParsedResponse = this.parseXMLData(oRequest, oFullResponse);
            break;
        case DS.TYPE_TEXT:
            if(xhr && lang.isString(oRawResponse.responseText)) {
                oFullResponse = oRawResponse.responseText;
            }
            oFullResponse = this.doBeforeParseData(oRequest, oFullResponse, oCallback);
            oParsedResponse = this.parseTextData(oRequest, oFullResponse);
            break;
        default:
            oFullResponse = this.doBeforeParseData(oRequest, oFullResponse, oCallback);
            oParsedResponse = this.parseData(oRequest, oFullResponse);
            break;
    }


    // Clean up for consistent signature
    oParsedResponse = oParsedResponse || {};
    if(!oParsedResponse.results) {
        oParsedResponse.results = [];
    }
    if(!oParsedResponse.meta) {
        oParsedResponse.meta = {};
    }

    // Success
    if(!oParsedResponse.error) {
        // Last chance to touch the raw response or the parsed response
        oParsedResponse = this.doBeforeCallback(oRequest, oFullResponse, oParsedResponse, oCallback);
        this.fireEvent("responseParseEvent", {request:oRequest,
                response:oParsedResponse, callback:oCallback, caller:oCaller});
        // Cache the response
        this.addToCache(oRequest, oParsedResponse);
    }
    // Error
    else {
        // Be sure the error flag is on
        oParsedResponse.error = true;
        this.fireEvent("dataErrorEvent", {request:oRequest, response: oRawResponse, callback:oCallback, 
                caller:oCaller, message:DS.ERROR_DATANULL});
        YAHOO.log(DS.ERROR_DATANULL, "error", this.toString());
    }

    // Send the response back to the caller
    oParsedResponse.tId = tId;
    DS.issueCallback(oCallback,[oRequest,oParsedResponse],oParsedResponse.error,oCaller);
},

/**
 * Overridable method gives implementers access to the original full response
 * before the data gets parsed. Implementers should take care not to return an
 * unparsable or otherwise invalid response.
 *
 * @method doBeforeParseData
 * @param oRequest {Object} Request object.
 * @param oFullResponse {Object} The full response from the live database.
 * @param oCallback {Object} The callback object.  
 * @return {Object} Full response for parsing.
  
 */
doBeforeParseData : function(oRequest, oFullResponse, oCallback) {
    return oFullResponse;
},

/**
 * Overridable method gives implementers access to the original full response and
 * the parsed response (parsed against the given schema) before the data
 * is added to the cache (if applicable) and then sent back to callback function.
 * This is your chance to access the raw response and/or populate the parsed
 * response with any custom data.
 *
 * @method doBeforeCallback
 * @param oRequest {Object} Request object.
 * @param oFullResponse {Object} The full response from the live database.
 * @param oParsedResponse {Object} The parsed response to return to calling object.
 * @param oCallback {Object} The callback object. 
 * @return {Object} Parsed response object.
 */
doBeforeCallback : function(oRequest, oFullResponse, oParsedResponse, oCallback) {
    return oParsedResponse;
},

/**
 * Overridable method parses data of generic RESPONSE_TYPE into a response object.
 *
 * @method parseData
 * @param oRequest {Object} Request object.
 * @param oFullResponse {Object} The full Array from the live database.
 * @return {Object} Parsed response object with the following properties:<br>
 *     - results {Array} Array of parsed data results<br>
 *     - meta {Object} Object literal of meta values<br>
 *     - error {Boolean} (optional) True if there was an error<br>
 */
parseData : function(oRequest, oFullResponse) {
    if(lang.isValue(oFullResponse)) {
        var oParsedResponse = {results:oFullResponse,meta:{}};
        YAHOO.log("Parsed generic data is " +
                lang.dump(oParsedResponse), "info", this.toString());
        return oParsedResponse;

    }
    YAHOO.log("Generic data could not be parsed: " + lang.dump(oFullResponse), 
            "error", this.toString());
    return null;
},

/**
 * Overridable method parses Array data into a response object.
 *
 * @method parseArrayData
 * @param oRequest {Object} Request object.
 * @param oFullResponse {Object} The full Array from the live database.
 * @return {Object} Parsed response object with the following properties:<br>
 *     - results (Array) Array of parsed data results<br>
 *     - error (Boolean) True if there was an error
 */
parseArrayData : function(oRequest, oFullResponse) {
    if(lang.isArray(oFullResponse)) {
        var results = [],
            i, j,
            rec, field, data;
        
        // Parse for fields
        if(lang.isArray(this.responseSchema.fields)) {
            var fields = this.responseSchema.fields;
            for (i = fields.length - 1; i >= 0; --i) {
                if (typeof fields[i] !== 'object') {
                    fields[i] = { key : fields[i] };
                }
            }

            var parsers = {}, p;
            for (i = fields.length - 1; i >= 0; --i) {
                p = (typeof fields[i].parser === 'function' ?
                          fields[i].parser :
                          DS.Parser[fields[i].parser+'']) || fields[i].converter;
                if (p) {
                    parsers[fields[i].key] = p;
                }
            }

            var arrType = lang.isArray(oFullResponse[0]);
            for(i=oFullResponse.length-1; i>-1; i--) {
                var oResult = {};
                rec = oFullResponse[i];
                if (typeof rec === 'object') {
                    for(j=fields.length-1; j>-1; j--) {
                        field = fields[j];
                        data = arrType ? rec[j] : rec[field.key];

                        if (parsers[field.key]) {
                            data = parsers[field.key].call(this,data);
                        }

                        // Safety measure
                        if(data === undefined) {
                            data = null;
                        }

                        oResult[field.key] = data;
                    }
                }
                else if (lang.isString(rec)) {
                    for(j=fields.length-1; j>-1; j--) {
                        field = fields[j];
                        data = rec;

                        if (parsers[field.key]) {
                            data = parsers[field.key].call(this,data);
                        }

                        // Safety measure
                        if(data === undefined) {
                            data = null;
                        }

                        oResult[field.key] = data;
                    }                
                }
                results[i] = oResult;
            }    
        }
        // Return entire data set
        else {
            results = oFullResponse;
        }
        var oParsedResponse = {results:results};
        YAHOO.log("Parsed array data is " +
                lang.dump(oParsedResponse), "info", this.toString());
        return oParsedResponse;

    }
    YAHOO.log("Array data could not be parsed: " + lang.dump(oFullResponse), 
            "error", this.toString());
    return null;
},

/**
 * Overridable method parses plain text data into a response object.
 *
 * @method parseTextData
 * @param oRequest {Object} Request object.
 * @param oFullResponse {Object} The full text response from the live database.
 * @return {Object} Parsed response object with the following properties:<br>
 *     - results (Array) Array of parsed data results<br>
 *     - error (Boolean) True if there was an error
 */
parseTextData : function(oRequest, oFullResponse) {
    if(lang.isString(oFullResponse)) {
        if(lang.isString(this.responseSchema.recordDelim) &&
                lang.isString(this.responseSchema.fieldDelim)) {
            var oParsedResponse = {results:[]};
            var recDelim = this.responseSchema.recordDelim;
            var fieldDelim = this.responseSchema.fieldDelim;
            if(oFullResponse.length > 0) {
                // Delete the last line delimiter at the end of the data if it exists
                var newLength = oFullResponse.length-recDelim.length;
                if(oFullResponse.substr(newLength) == recDelim) {
                    oFullResponse = oFullResponse.substr(0, newLength);
                }
                if(oFullResponse.length > 0) {
                    // Split along record delimiter to get an array of strings
                    var recordsarray = oFullResponse.split(recDelim);
                    // Cycle through each record
                    for(var i = 0, len = recordsarray.length, recIdx = 0; i < len; ++i) {
                        var bError = false,
                            sRecord = recordsarray[i];
                        if (lang.isString(sRecord) && (sRecord.length > 0)) {
                            // Split each record along field delimiter to get data
                            var fielddataarray = recordsarray[i].split(fieldDelim);
                            var oResult = {};
                            
                            // Filter for fields data
                            if(lang.isArray(this.responseSchema.fields)) {
                                var fields = this.responseSchema.fields;
                                for(var j=fields.length-1; j>-1; j--) {
                                    try {
                                        // Remove quotation marks from edges, if applicable
                                        var data = fielddataarray[j];
                                        if (lang.isString(data)) {
                                            if(data.charAt(0) == "\"") {
                                                data = data.substr(1);
                                            }
                                            if(data.charAt(data.length-1) == "\"") {
                                                data = data.substr(0,data.length-1);
                                            }
                                            var field = fields[j];
                                            var key = (lang.isValue(field.key)) ? field.key : field;
                                            // Backward compatibility
                                            if(!field.parser && field.converter) {
                                                field.parser = field.converter;
                                                YAHOO.log("The field property converter has been deprecated" +
                                                        " in favor of parser", "warn", this.toString());
                                            }
                                            var parser = (typeof field.parser === 'function') ?
                                                field.parser :
                                                DS.Parser[field.parser+''];
                                            if(parser) {
                                                data = parser.call(this, data);
                                            }
                                            // Safety measure
                                            if(data === undefined) {
                                                data = null;
                                            }
                                            oResult[key] = data;
                                        }
                                        else {
                                            bError = true;
                                        }
                                    }
                                    catch(e) {
                                        bError = true;
                                    }
                                }
                            }            
                            // No fields defined so pass along all data as an array
                            else {
                                oResult = fielddataarray;
                            }
                            if(!bError) {
                                oParsedResponse.results[recIdx++] = oResult;
                            }
                        }
                    }
                }
            }
            YAHOO.log("Parsed text data is " +
                    lang.dump(oParsedResponse), "info", this.toString());
            return oParsedResponse;
        }
    }
    YAHOO.log("Text data could not be parsed: " + lang.dump(oFullResponse), 
            "error", this.toString());
    return null;
            
},

/**
 * Overridable method parses XML data for one result into an object literal.
 *
 * @method parseXMLResult
 * @param result {XML} XML for one result.
 * @return {Object} Object literal of data for one result.
 */
parseXMLResult : function(result) {
    var oResult = {},
        schema = this.responseSchema;
        
    try {
        // Loop through each data field in each result using the schema
        for(var m = schema.fields.length-1; m >= 0 ; m--) {
            var field = schema.fields[m];
            var key = (lang.isValue(field.key)) ? field.key : field;
            var data = null;

            if(this.useXPath) {
                data = YAHOO.util.DataSource._getLocationValue(field, result);
            }
            else {
                // Values may be held in an attribute...
                var xmlAttr = result.attributes.getNamedItem(key);
                if(xmlAttr) {
                    data = xmlAttr.value;
                }
                // ...or in a node
                else {
                    var xmlNode = result.getElementsByTagName(key);
                    if(xmlNode && xmlNode.item(0)) {
                        var item = xmlNode.item(0);
                        // For IE, then DOM...
                        data = (item) ? ((item.text) ? item.text : (item.textContent) ? item.textContent : null) : null;
                        // ...then fallback, but check for multiple child nodes
                        if(!data) {
                            var datapieces = [];
                            for(var j=0, len=item.childNodes.length; j<len; j++) {
                                if(item.childNodes[j].nodeValue) {
                                    datapieces[datapieces.length] = item.childNodes[j].nodeValue;
                                }
                            }
                            if(datapieces.length > 0) {
                                data = datapieces.join("");
                            }
                        }
                    }
                }
            }
            
            
            // Safety net
            if(data === null) {
                   data = "";
            }
            // Backward compatibility
            if(!field.parser && field.converter) {
                field.parser = field.converter;
                YAHOO.log("The field property converter has been deprecated" +
                        " in favor of parser", "warn", this.toString());
            }
            var parser = (typeof field.parser === 'function') ?
                field.parser :
                DS.Parser[field.parser+''];
            if(parser) {
                data = parser.call(this, data);
            }
            // Safety measure
            if(data === undefined) {
                data = null;
            }
            oResult[key] = data;
        }
    }
    catch(e) {
        YAHOO.log("Error while parsing XML result: " + e.message);
    }

    return oResult;
},



/**
 * Overridable method parses XML data into a response object.
 *
 * @method parseXMLData
 * @param oRequest {Object} Request object.
 * @param oFullResponse {Object} The full XML response from the live database.
 * @return {Object} Parsed response object with the following properties<br>
 *     - results (Array) Array of parsed data results<br>
 *     - error (Boolean) True if there was an error
 */
parseXMLData : function(oRequest, oFullResponse) {
    var bError = false,
        schema = this.responseSchema,
        oParsedResponse = {meta:{}},
        xmlList = null,
        metaNode      = schema.metaNode,
        metaLocators  = schema.metaFields || {},
        i,k,loc,v;

    // In case oFullResponse is something funky
    try {
        // Pull any meta identified
        if(this.useXPath) {
            for (k in metaLocators) {
                oParsedResponse.meta[k] = YAHOO.util.DataSource._getLocationValue(metaLocators[k], oFullResponse);
            }
        }
        else {
            metaNode = metaNode ? oFullResponse.getElementsByTagName(metaNode)[0] :
                       oFullResponse;

            if (metaNode) {
                for (k in metaLocators) {
                    if (lang.hasOwnProperty(metaLocators, k)) {
                        loc = metaLocators[k];
                        // Look for a node
                        v = metaNode.getElementsByTagName(loc)[0];

                        if (v) {
                            v = v.firstChild.nodeValue;
                        } else {
                            // Look for an attribute
                            v = metaNode.attributes.getNamedItem(loc);
                            if (v) {
                                v = v.value;
                            }
                        }

                        if (lang.isValue(v)) {
                            oParsedResponse.meta[k] = v;
                        }
                    }
                }
            }
        }
        
        // For result data
        xmlList = (schema.resultNode) ?
            oFullResponse.getElementsByTagName(schema.resultNode) :
            null;
    }
    catch(e) {
        YAHOO.log("Error while parsing XML data: " + e.message);
    }
    if(!xmlList || !lang.isArray(schema.fields)) {
        bError = true;
    }
    // Loop through each result
    else {
        oParsedResponse.results = [];
        for(i = xmlList.length-1; i >= 0 ; --i) {
            var oResult = this.parseXMLResult(xmlList.item(i));
            // Capture each array of values into an array of results
            oParsedResponse.results[i] = oResult;
        }
    }
    if(bError) {
        YAHOO.log("XML data could not be parsed: " +
                lang.dump(oFullResponse), "error", this.toString());
        oParsedResponse.error = true;
    }
    else {
        YAHOO.log("Parsed XML data is " +
                lang.dump(oParsedResponse), "info", this.toString());
    }
    return oParsedResponse;
},

/**
 * Overridable method parses JSON data into a response object.
 *
 * @method parseJSONData
 * @param oRequest {Object} Request object.
 * @param oFullResponse {Object} The full JSON from the live database.
 * @return {Object} Parsed response object with the following properties<br>
 *     - results (Array) Array of parsed data results<br>
 *     - error (Boolean) True if there was an error
 */
parseJSONData : function(oRequest, oFullResponse) {
    var oParsedResponse = {results:[],meta:{}};
    
    if(lang.isObject(oFullResponse) && this.responseSchema.resultsList) {
        var schema = this.responseSchema,
            fields          = schema.fields,
            resultsList     = oFullResponse,
            results         = [],
            metaFields      = schema.metaFields || {},
            fieldParsers    = [],
            fieldPaths      = [],
            simpleFields    = [],
            bError          = false,
            i,len,j,v,key,parser,path;

        // Function to convert the schema's fields into walk paths
        var buildPath = function (needle) {
            var path = null, keys = [], i = 0;
            if (needle) {
                // Strip the ["string keys"] and [1] array indexes
                needle = needle.
                    replace(/\[(['"])(.*?)\1\]/g,
                    function (x,$1,$2) {keys[i]=$2;return '.@'+(i++);}).
                    replace(/\[(\d+)\]/g,
                    function (x,$1) {keys[i]=parseInt($1,10)|0;return '.@'+(i++);}).
                    replace(/^\./,''); // remove leading dot

                // If the cleaned needle contains invalid characters, the
                // path is invalid
                if (!/[^\w\.\$@]/.test(needle)) {
                    path = needle.split('.');
                    for (i=path.length-1; i >= 0; --i) {
                        if (path[i].charAt(0) === '@') {
                            path[i] = keys[parseInt(path[i].substr(1),10)];
                        }
                    }
                }
                else {
                    YAHOO.log("Invalid locator: " + needle, "error", this.toString());
                }
            }
            return path;
        };


        // Function to walk a path and return the pot of gold
        var walkPath = function (path, origin) {
            var v=origin,i=0,len=path.length;
            for (;i<len && v;++i) {
                v = v[path[i]];
            }
            return v;
        };

        // Parse the response
        // Step 1. Pull the resultsList from oFullResponse (default assumes
        // oFullResponse IS the resultsList)
        path = buildPath(schema.resultsList);
        if (path) {
            resultsList = walkPath(path, oFullResponse);
            if (resultsList === undefined) {
                bError = true;
            }
        } else {
            bError = true;
        }
        
        if (!resultsList) {
            resultsList = [];
        }

        if (!lang.isArray(resultsList)) {
            resultsList = [resultsList];
        }

        if (!bError) {
            // Step 2. Parse out field data if identified
            if(schema.fields) {
                var field;
                // Build the field parser map and location paths
                for (i=0, len=fields.length; i<len; i++) {
                    field = fields[i];
                    key    = field.key || field;
                    parser = ((typeof field.parser === 'function') ?
                        field.parser :
                        DS.Parser[field.parser+'']) || field.converter;
                    path   = buildPath(key);
    
                    if (parser) {
                        fieldParsers[fieldParsers.length] = {key:key,parser:parser};
                    }
    
                    if (path) {
                        if (path.length > 1) {
                            fieldPaths[fieldPaths.length] = {key:key,path:path};
                        } else {
                            simpleFields[simpleFields.length] = {key:key,path:path[0]};
                        }
                    } else {
                        YAHOO.log("Invalid key syntax: " + key,"warn",this.toString());
                    }
                }

                // Process the results, flattening the records and/or applying parsers if needed
                for (i = resultsList.length - 1; i >= 0; --i) {
                    var r = resultsList[i], rec = {};
                    if(r) {
                        for (j = simpleFields.length - 1; j >= 0; --j) {
                            // Bug 1777850: data might be held in an array
                            rec[simpleFields[j].key] =
                                    (r[simpleFields[j].path] !== undefined) ?
                                    r[simpleFields[j].path] : r[j];
                        }

                        for (j = fieldPaths.length - 1; j >= 0; --j) {
                            rec[fieldPaths[j].key] = walkPath(fieldPaths[j].path,r);
                        }

                        for (j = fieldParsers.length - 1; j >= 0; --j) {
                            var p = fieldParsers[j].key;
                            rec[p] = fieldParsers[j].parser.call(this, rec[p]);
                            if (rec[p] === undefined) {
                                rec[p] = null;
                            }
                        }
                    }
                    results[i] = rec;
                }
            }
            else {
                results = resultsList;
            }

            for (key in metaFields) {
                if (lang.hasOwnProperty(metaFields,key)) {
                    path = buildPath(metaFields[key]);
                    if (path) {
                        v = walkPath(path, oFullResponse);
                        oParsedResponse.meta[key] = v;
                    }
                }
            }

        } else {
            YAHOO.log("JSON data could not be parsed due to invalid responseSchema.resultsList or invalid response: " +
                    lang.dump(oFullResponse), "error", this.toString());

            oParsedResponse.error = true;
        }

        oParsedResponse.results = results;
    }
    else {
        YAHOO.log("JSON data could not be parsed: " +
                lang.dump(oFullResponse), "error", this.toString());
        oParsedResponse.error = true;
    }

    return oParsedResponse;
},

/**
 * Overridable method parses an HTML TABLE element reference into a response object.
 * Data is parsed out of TR elements from all TBODY elements. 
 *
 * @method parseHTMLTableData
 * @param oRequest {Object} Request object.
 * @param oFullResponse {Object} The full HTML element reference from the live database.
 * @return {Object} Parsed response object with the following properties<br>
 *     - results (Array) Array of parsed data results<br>
 *     - error (Boolean) True if there was an error
 */
parseHTMLTableData : function(oRequest, oFullResponse) {
    var bError = false;
    var elTable = oFullResponse;
    var fields = this.responseSchema.fields;
    var oParsedResponse = {results:[]};

    if(lang.isArray(fields)) {
        // Iterate through each TBODY
        for(var i=0; i<elTable.tBodies.length; i++) {
            var elTbody = elTable.tBodies[i];
    
            // Iterate through each TR
            for(var j=elTbody.rows.length-1; j>-1; j--) {
                var elRow = elTbody.rows[j];
                var oResult = {};
                
                for(var k=fields.length-1; k>-1; k--) {
                    var field = fields[k];
                    var key = (lang.isValue(field.key)) ? field.key : field;
                    var data = elRow.cells[k].innerHTML;
    
                    // Backward compatibility
                    if(!field.parser && field.converter) {
                        field.parser = field.converter;
                        YAHOO.log("The field property converter has been deprecated" +
                                " in favor of parser", "warn", this.toString());
                    }
                    var parser = (typeof field.parser === 'function') ?
                        field.parser :
                        DS.Parser[field.parser+''];
                    if(parser) {
                        data = parser.call(this, data);
                    }
                    // Safety measure
                    if(data === undefined) {
                        data = null;
                    }
                    oResult[key] = data;
                }
                oParsedResponse.results[j] = oResult;
            }
        }
    }
    else {
        bError = true;
        YAHOO.log("Invalid responseSchema.fields", "error", this.toString());
    }

    if(bError) {
        YAHOO.log("HTML TABLE data could not be parsed: " +
                lang.dump(oFullResponse), "error", this.toString());
        oParsedResponse.error = true;
    }
    else {
        YAHOO.log("Parsed HTML TABLE data is " +
                lang.dump(oParsedResponse), "info", this.toString());
    }
    return oParsedResponse;
}

};

// DataSourceBase uses EventProvider
lang.augmentProto(DS, util.EventProvider);



/****************************************************************************/
/****************************************************************************/
/****************************************************************************/

/**
 * LocalDataSource class for in-memory data structs including JavaScript arrays,
 * JavaScript object literals (JSON), XML documents, and HTML tables.
 *
 * @namespace YAHOO.util
 * @class YAHOO.util.LocalDataSource
 * @extends YAHOO.util.DataSourceBase 
 * @constructor
 * @param oLiveData {HTMLElement}  Pointer to live data.
 * @param oConfigs {object} (optional) Object literal of configuration values.
 */
util.LocalDataSource = function(oLiveData, oConfigs) {
    this.dataType = DS.TYPE_LOCAL;
    
    if(oLiveData) {
        if(YAHOO.lang.isArray(oLiveData)) { // array
            this.responseType = DS.TYPE_JSARRAY;
        }
         // xml
        else if(oLiveData.nodeType && oLiveData.nodeType == 9) {
            this.responseType = DS.TYPE_XML;
        }
        else if(oLiveData.nodeName && (oLiveData.nodeName.toLowerCase() == "table")) { // table
            this.responseType = DS.TYPE_HTMLTABLE;
            oLiveData = oLiveData.cloneNode(true);
        }    
        else if(YAHOO.lang.isString(oLiveData)) { // text
            this.responseType = DS.TYPE_TEXT;
        }
        else if(YAHOO.lang.isObject(oLiveData)) { // json
            this.responseType = DS.TYPE_JSON;
        }
    }
    else {
        oLiveData = [];
        this.responseType = DS.TYPE_JSARRAY;
    }
    
    util.LocalDataSource.superclass.constructor.call(this, oLiveData, oConfigs); 
};

// LocalDataSource extends DataSourceBase
lang.extend(util.LocalDataSource, DS);

// Copy static members to LocalDataSource class
lang.augmentObject(util.LocalDataSource, DS);













/****************************************************************************/
/****************************************************************************/
/****************************************************************************/

/**
 * FunctionDataSource class for JavaScript functions.
 *
 * @namespace YAHOO.util
 * @class YAHOO.util.FunctionDataSource
 * @extends YAHOO.util.DataSourceBase  
 * @constructor
 * @param oLiveData {HTMLElement}  Pointer to live data.
 * @param oConfigs {object} (optional) Object literal of configuration values.
 */
util.FunctionDataSource = function(oLiveData, oConfigs) {
    this.dataType = DS.TYPE_JSFUNCTION;
    oLiveData = oLiveData || function() {};
    
    util.FunctionDataSource.superclass.constructor.call(this, oLiveData, oConfigs); 
};

// FunctionDataSource extends DataSourceBase
lang.extend(util.FunctionDataSource, DS, {

/////////////////////////////////////////////////////////////////////////////
//
// FunctionDataSource public properties
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Context in which to execute the function. By default, is the DataSource
 * instance itself. If set, the function will receive the DataSource instance
 * as an additional argument. 
 *
 * @property scope
 * @type Object
 * @default null
 */
scope : null,


/////////////////////////////////////////////////////////////////////////////
//
// FunctionDataSource public methods
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Overriding method passes query to a function. The returned response is then
 * forwarded to the handleResponse function.
 *
 * @method makeConnection
 * @param oRequest {Object} Request object.
 * @param oCallback {Object} Callback object literal.
 * @param oCaller {Object} (deprecated) Use oCallback.scope.
 * @return {Number} Transaction ID.
 */
makeConnection : function(oRequest, oCallback, oCaller) {
    var tId = DS._nTransactionId++;
    this.fireEvent("requestEvent", {tId:tId,request:oRequest,callback:oCallback,caller:oCaller});

    // Pass the request in as a parameter and
    // forward the return value to the handler
    
    
    var oRawResponse = (this.scope) ? this.liveData.call(this.scope, oRequest, this, oCallback) : this.liveData(oRequest, oCallback);
    
    // Try to sniff data type if it has not been defined
    if(this.responseType === DS.TYPE_UNKNOWN) {
        if(YAHOO.lang.isArray(oRawResponse)) { // array
            this.responseType = DS.TYPE_JSARRAY;
        }
         // xml
        else if(oRawResponse && oRawResponse.nodeType && oRawResponse.nodeType == 9) {
            this.responseType = DS.TYPE_XML;
        }
        else if(oRawResponse && oRawResponse.nodeName && (oRawResponse.nodeName.toLowerCase() == "table")) { // table
            this.responseType = DS.TYPE_HTMLTABLE;
        }    
        else if(YAHOO.lang.isObject(oRawResponse)) { // json
            this.responseType = DS.TYPE_JSON;
        }
        else if(YAHOO.lang.isString(oRawResponse)) { // text
            this.responseType = DS.TYPE_TEXT;
        }
    }

    this.handleResponse(oRequest, oRawResponse, oCallback, oCaller, tId);
    return tId;
}

});

// Copy static members to FunctionDataSource class
lang.augmentObject(util.FunctionDataSource, DS);













/****************************************************************************/
/****************************************************************************/
/****************************************************************************/

/**
 * ScriptNodeDataSource class for accessing remote data via the YUI Get Utility. 
 *
 * @namespace YAHOO.util
 * @class YAHOO.util.ScriptNodeDataSource
 * @extends YAHOO.util.DataSourceBase  
 * @constructor
 * @param oLiveData {HTMLElement}  Pointer to live data.
 * @param oConfigs {object} (optional) Object literal of configuration values.
 */
util.ScriptNodeDataSource = function(oLiveData, oConfigs) {
    this.dataType = DS.TYPE_SCRIPTNODE;
    oLiveData = oLiveData || "";
    
    util.ScriptNodeDataSource.superclass.constructor.call(this, oLiveData, oConfigs); 
};

// ScriptNodeDataSource extends DataSourceBase
lang.extend(util.ScriptNodeDataSource, DS, {

/////////////////////////////////////////////////////////////////////////////
//
// ScriptNodeDataSource public properties
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Alias to YUI Get Utility, to allow implementers to use a custom class.
 *
 * @property getUtility
 * @type Object
 * @default YAHOO.util.Get
 */
getUtility : util.Get,

/**
 * Defines request/response management in the following manner:
 * <dl>
 *     <!--<dt>queueRequests</dt>
 *     <dd>If a request is already in progress, wait until response is returned before sending the next request.</dd>
 *     <dt>cancelStaleRequests</dt>
 *     <dd>If a request is already in progress, cancel it before sending the next request.</dd>-->
 *     <dt>ignoreStaleResponses</dt>
 *     <dd>Send all requests, but handle only the response for the most recently sent request.</dd>
 *     <dt>allowAll</dt>
 *     <dd>Send all requests and handle all responses.</dd>
 * </dl>
 *
 * @property asyncMode
 * @type String
 * @default "allowAll"
 */
asyncMode : "allowAll",

/**
 * Callback string parameter name sent to the remote script. By default,
 * requests are sent to
 * &#60;URI&#62;?&#60;scriptCallbackParam&#62;=callback
 *
 * @property scriptCallbackParam
 * @type String
 * @default "callback"
 */
scriptCallbackParam : "callback",


/////////////////////////////////////////////////////////////////////////////
//
// ScriptNodeDataSource public methods
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Creates a request callback that gets appended to the script URI. Implementers
 * can customize this string to match their server's query syntax.
 *
 * @method generateRequestCallback
 * @return {String} String fragment that gets appended to script URI that 
 * specifies the callback function 
 */
generateRequestCallback : function(id) {
    return "&" + this.scriptCallbackParam + "=YAHOO.util.ScriptNodeDataSource.callbacks["+id+"]" ;
},

/**
 * Overridable method gives implementers access to modify the URI before the dynamic
 * script node gets inserted. Implementers should take care not to return an
 * invalid URI.
 *
 * @method doBeforeGetScriptNode
 * @param {String} URI to the script 
 * @return {String} URI to the script
 */
doBeforeGetScriptNode : function(sUri) {
    return sUri;
},

/**
 * Overriding method passes query to Get Utility. The returned
 * response is then forwarded to the handleResponse function.
 *
 * @method makeConnection
 * @param oRequest {Object} Request object.
 * @param oCallback {Object} Callback object literal.
 * @param oCaller {Object} (deprecated) Use oCallback.scope.
 * @return {Number} Transaction ID.
 */
makeConnection : function(oRequest, oCallback, oCaller) {
    var tId = DS._nTransactionId++;
    this.fireEvent("requestEvent", {tId:tId,request:oRequest,callback:oCallback,caller:oCaller});
    
    // If there are no global pending requests, it is safe to purge global callback stack and global counter
    if(util.ScriptNodeDataSource._nPending === 0) {
        util.ScriptNodeDataSource.callbacks = [];
        util.ScriptNodeDataSource._nId = 0;
    }
    
    // ID for this request
    var id = util.ScriptNodeDataSource._nId;
    util.ScriptNodeDataSource._nId++;
    
    // Dynamically add handler function with a closure to the callback stack
    var oSelf = this;
    util.ScriptNodeDataSource.callbacks[id] = function(oRawResponse) {
        if((oSelf.asyncMode !== "ignoreStaleResponses")||
                (id === util.ScriptNodeDataSource.callbacks.length-1)) { // Must ignore stale responses
                
            // Try to sniff data type if it has not been defined
            if(oSelf.responseType === DS.TYPE_UNKNOWN) {
                if(YAHOO.lang.isArray(oRawResponse)) { // array
                    oSelf.responseType = DS.TYPE_JSARRAY;
                }
                 // xml
                else if(oRawResponse.nodeType && oRawResponse.nodeType == 9) {
                    oSelf.responseType = DS.TYPE_XML;
                }
                else if(oRawResponse.nodeName && (oRawResponse.nodeName.toLowerCase() == "table")) { // table
                    oSelf.responseType = DS.TYPE_HTMLTABLE;
                }    
                else if(YAHOO.lang.isObject(oRawResponse)) { // json
                    oSelf.responseType = DS.TYPE_JSON;
                }
                else if(YAHOO.lang.isString(oRawResponse)) { // text
                    oSelf.responseType = DS.TYPE_TEXT;
                }
            }

            oSelf.handleResponse(oRequest, oRawResponse, oCallback, oCaller, tId);
        }
        else {
            YAHOO.log("DataSource ignored stale response for tId " + tId + "(" + oRequest + ")", "info", oSelf.toString());
        }
    
        delete util.ScriptNodeDataSource.callbacks[id];
    };
    
    // We are now creating a request
    util.ScriptNodeDataSource._nPending++;
    var sUri = this.liveData + oRequest + this.generateRequestCallback(id);
    sUri = this.doBeforeGetScriptNode(sUri);
    YAHOO.log("DataSource is querying URL " + sUri, "info", this.toString());
    this.getUtility.script(sUri,
            {autopurge: true,
            onsuccess: util.ScriptNodeDataSource._bumpPendingDown,
            onfail: util.ScriptNodeDataSource._bumpPendingDown});

    return tId;
}

});

// Copy static members to ScriptNodeDataSource class
lang.augmentObject(util.ScriptNodeDataSource, DS);

// Copy static members to ScriptNodeDataSource class
lang.augmentObject(util.ScriptNodeDataSource,  {

/////////////////////////////////////////////////////////////////////////////
//
// ScriptNodeDataSource private static properties
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Unique ID to track requests.
 *
 * @property _nId
 * @type Number
 * @private
 * @static
 */
_nId : 0,

/**
 * Counter for pending requests. When this is 0, it is safe to purge callbacks
 * array.
 *
 * @property _nPending
 * @type Number
 * @private
 * @static
 */
_nPending : 0,

/**
 * Global array of callback functions, one for each request sent.
 *
 * @property callbacks
 * @type Function[]
 * @static
 */
callbacks : []

});














/****************************************************************************/
/****************************************************************************/
/****************************************************************************/

/**
 * XHRDataSource class for accessing remote data via the YUI Connection Manager
 * Utility
 *
 * @namespace YAHOO.util
 * @class YAHOO.util.XHRDataSource
 * @extends YAHOO.util.DataSourceBase  
 * @constructor
 * @param oLiveData {HTMLElement}  Pointer to live data.
 * @param oConfigs {object} (optional) Object literal of configuration values.
 */
util.XHRDataSource = function(oLiveData, oConfigs) {
    this.dataType = DS.TYPE_XHR;
    this.connMgr = this.connMgr || util.Connect;
    oLiveData = oLiveData || "";
    
    util.XHRDataSource.superclass.constructor.call(this, oLiveData, oConfigs); 
};

// XHRDataSource extends DataSourceBase
lang.extend(util.XHRDataSource, DS, {

/////////////////////////////////////////////////////////////////////////////
//
// XHRDataSource public properties
//
/////////////////////////////////////////////////////////////////////////////

 /**
 * Alias to YUI Connection Manager, to allow implementers to use a custom class.
 *
 * @property connMgr
 * @type Object
 * @default YAHOO.util.Connect
 */
connMgr: null,

 /**
 * Defines request/response management in the following manner:
 * <dl>
 *     <dt>queueRequests</dt>
 *     <dd>If a request is already in progress, wait until response is returned
 *     before sending the next request.</dd>
 *
 *     <dt>cancelStaleRequests</dt>
 *     <dd>If a request is already in progress, cancel it before sending the next
 *     request.</dd>
 *
 *     <dt>ignoreStaleResponses</dt>
 *     <dd>Send all requests, but handle only the response for the most recently
 *     sent request.</dd>
 *
 *     <dt>allowAll</dt>
 *     <dd>Send all requests and handle all responses.</dd>
 *
 * </dl>
 *
 * @property connXhrMode
 * @type String
 * @default "allowAll"
 */
connXhrMode: "allowAll",

 /**
 * True if data is to be sent via POST. By default, data will be sent via GET.
 *
 * @property connMethodPost
 * @type Boolean
 * @default false
 */
connMethodPost: false,

 /**
 * The connection timeout defines how many  milliseconds the XHR connection will
 * wait for a server response. Any non-zero value will enable the Connection Manager's
 * Auto-Abort feature.
 *
 * @property connTimeout
 * @type Number
 * @default 0
 */
connTimeout: 0,

/////////////////////////////////////////////////////////////////////////////
//
// XHRDataSource public methods
//
/////////////////////////////////////////////////////////////////////////////

/**
 * Overriding method passes query to Connection Manager. The returned
 * response is then forwarded to the handleResponse function.
 *
 * @method makeConnection
 * @param oRequest {Object} Request object.
 * @param oCallback {Object} Callback object literal.
 * @param oCaller {Object} (deprecated) Use oCallback.scope.
 * @return {Number} Transaction ID.
 */
makeConnection : function(oRequest, oCallback, oCaller) {

    var oRawResponse = null;
    var tId = DS._nTransactionId++;
    this.fireEvent("requestEvent", {tId:tId,request:oRequest,callback:oCallback,caller:oCaller});

    // Set up the callback object and
    // pass the request in as a URL query and
    // forward the response to the handler
    var oSelf = this;
    var oConnMgr = this.connMgr;
    var oQueue = this._oQueue;

    /**
     * Define Connection Manager success handler
     *
     * @method _xhrSuccess
     * @param oResponse {Object} HTTPXMLRequest object
     * @private
     */
    var _xhrSuccess = function(oResponse) {
        // If response ID does not match last made request ID,
        // silently fail and wait for the next response
        if(oResponse && (this.connXhrMode == "ignoreStaleResponses") &&
                (oResponse.tId != oQueue.conn.tId)) {
            YAHOO.log("Ignored stale response", "warn", this.toString());
            return null;
        }
        // Error if no response
        else if(!oResponse) {
            this.fireEvent("dataErrorEvent", {request:oRequest, response:null,
                    callback:oCallback, caller:oCaller,
                    message:DS.ERROR_DATANULL});
            YAHOO.log(DS.ERROR_DATANULL, "error", this.toString());

            // Send error response back to the caller with the error flag on
            DS.issueCallback(oCallback,[oRequest, {error:true}], true, oCaller);

            return null;
        }
        // Forward to handler
        else {
            // Try to sniff data type if it has not been defined
            if(this.responseType === DS.TYPE_UNKNOWN) {
                var ctype = (oResponse.getResponseHeader) ? oResponse.getResponseHeader["Content-Type"] : null;
                if(ctype) {
                    // xml
                    if(ctype.indexOf("text/xml") > -1) {
                        this.responseType = DS.TYPE_XML;
                    }
                    else if(ctype.indexOf("application/json") > -1) { // json
                        this.responseType = DS.TYPE_JSON;
                    }
                    else if(ctype.indexOf("text/plain") > -1) { // text
                        this.responseType = DS.TYPE_TEXT;
                    }
                }
            }
            this.handleResponse(oRequest, oResponse, oCallback, oCaller, tId);
        }
    };

    /**
     * Define Connection Manager failure handler
     *
     * @method _xhrFailure
     * @param oResponse {Object} HTTPXMLRequest object
     * @private
     */
    var _xhrFailure = function(oResponse) {
        this.fireEvent("dataErrorEvent", {request:oRequest, response: oResponse,
                callback:oCallback, caller:oCaller,
                message:DS.ERROR_DATAINVALID});
        YAHOO.log(DS.ERROR_DATAINVALID + ": " +
                oResponse.statusText, "error", this.toString());

        // Backward compatibility
        if(lang.isString(this.liveData) && lang.isString(oRequest) &&
            (this.liveData.lastIndexOf("?") !== this.liveData.length-1) &&
            (oRequest.indexOf("?") !== 0)){
                YAHOO.log("DataSources using XHR no longer automatically supply " + 
                "a \"?\" between the host and query parameters" +
                " -- please check that the request URL is correct", "warn", this.toString());
        }

        // Send failure response back to the caller with the error flag on
        oResponse = oResponse || {};
        oResponse.error = true;
        DS.issueCallback(oCallback,[oRequest,oResponse],true, oCaller);

        return null;
    };

    /**
     * Define Connection Manager callback object
     *
     * @property _xhrCallback
     * @param oResponse {Object} HTTPXMLRequest object
     * @private
     */
     var _xhrCallback = {
        success:_xhrSuccess,
        failure:_xhrFailure,
        scope: this
    };

    // Apply Connection Manager timeout
    if(lang.isNumber(this.connTimeout)) {
        _xhrCallback.timeout = this.connTimeout;
    }

    // Cancel stale requests
    if(this.connXhrMode == "cancelStaleRequests") {
            // Look in queue for stale requests
            if(oQueue.conn) {
                if(oConnMgr.abort) {
                    oConnMgr.abort(oQueue.conn);
                    oQueue.conn = null;
                    YAHOO.log("Canceled stale request", "warn", this.toString());
                }
                else {
                    YAHOO.log("Could not find Connection Manager abort() function", "error", this.toString());
                }
            }
    }

    // Get ready to send the request URL
    if(oConnMgr && oConnMgr.asyncRequest) {
        var sLiveData = this.liveData;
        var isPost = this.connMethodPost;
        var sMethod = (isPost) ? "POST" : "GET";
        // Validate request
        var sUri = (isPost || !lang.isValue(oRequest)) ? sLiveData : sLiveData+oRequest;
        var sRequest = (isPost) ? oRequest : null;

        // Send the request right away
        if(this.connXhrMode != "queueRequests") {
            oQueue.conn = oConnMgr.asyncRequest(sMethod, sUri, _xhrCallback, sRequest);
        }
        // Queue up then send the request
        else {
            // Found a request already in progress
            if(oQueue.conn) {
                var allRequests = oQueue.requests;
                // Add request to queue
                allRequests.push({request:oRequest, callback:_xhrCallback});

                // Interval needs to be started
                if(!oQueue.interval) {
                    oQueue.interval = setInterval(function() {
                        // Connection is in progress
                        if(oConnMgr.isCallInProgress(oQueue.conn)) {
                            return;
                        }
                        else {
                            // Send next request
                            if(allRequests.length > 0) {
                                // Validate request
                                sUri = (isPost || !lang.isValue(allRequests[0].request)) ? sLiveData : sLiveData+allRequests[0].request;
                                sRequest = (isPost) ? allRequests[0].request : null;
                                oQueue.conn = oConnMgr.asyncRequest(sMethod, sUri, allRequests[0].callback, sRequest);

                                // Remove request from queue
                                allRequests.shift();
                            }
                            // No more requests
                            else {
                                clearInterval(oQueue.interval);
                                oQueue.interval = null;
                            }
                        }
                    }, 50);
                }
            }
            // Nothing is in progress
            else {
                oQueue.conn = oConnMgr.asyncRequest(sMethod, sUri, _xhrCallback, sRequest);
            }
        }
    }
    else {
        YAHOO.log("Could not find Connection Manager asyncRequest() function", "error", this.toString());
        // Send null response back to the caller with the error flag on
        DS.issueCallback(oCallback,[oRequest,{error:true}],true,oCaller);
    }

    return tId;
}

});

// Copy static members to XHRDataSource class
lang.augmentObject(util.XHRDataSource, DS);













/****************************************************************************/
/****************************************************************************/
/****************************************************************************/

/**
 * Factory class for creating a BaseDataSource subclass instance. The sublcass is
 * determined by oLiveData's type, unless the dataType config is explicitly passed in.  
 *
 * @namespace YAHOO.util
 * @class YAHOO.util.DataSource
 * @constructor
 * @param oLiveData {HTMLElement}  Pointer to live data.
 * @param oConfigs {object} (optional) Object literal of configuration values.
 */
util.DataSource = function(oLiveData, oConfigs) {
    oConfigs = oConfigs || {};
    
    // Point to one of the subclasses, first by dataType if given, then by sniffing oLiveData type.
    var dataType = oConfigs.dataType;
    if(dataType) {
        if(dataType == DS.TYPE_LOCAL) {
            return new util.LocalDataSource(oLiveData, oConfigs);
        }
        else if(dataType == DS.TYPE_XHR) {
            return new util.XHRDataSource(oLiveData, oConfigs);            
        }
        else if(dataType == DS.TYPE_SCRIPTNODE) {
            return new util.ScriptNodeDataSource(oLiveData, oConfigs);            
        }
        else if(dataType == DS.TYPE_JSFUNCTION) {
            return new util.FunctionDataSource(oLiveData, oConfigs);            
        }
    }
    
    if(YAHOO.lang.isString(oLiveData)) { // strings default to xhr
        return new util.XHRDataSource(oLiveData, oConfigs);
    }
    else if(YAHOO.lang.isFunction(oLiveData)) {
        return new util.FunctionDataSource(oLiveData, oConfigs);
    }
    else { // ultimate default is local
        return new util.LocalDataSource(oLiveData, oConfigs);
    }
};

// Copy static members to DataSource class
lang.augmentObject(util.DataSource, DS);

})();

/****************************************************************************/
/****************************************************************************/
/****************************************************************************/

/**
 * The static Number class provides helper functions to deal with data of type
 * Number.
 *
 * @namespace YAHOO.util
 * @requires yahoo
 * @class Number
 * @static
 */
 YAHOO.util.Number = {
 
     /**
     * Takes a native JavaScript Number and formats to a string for display.
     *
     * @method format
     * @param nData {Number} Number.
     * @param oConfig {Object} (Optional) Optional configuration values:
     *  <dl>
     *   <dt>format</dt>
     *   <dd>String used as a template for formatting positive numbers.
     *   {placeholders} in the string are applied from the values in this
     *   config object. {number} is used to indicate where the numeric portion
     *   of the output goes.  For example &quot;{prefix}{number} per item&quot;
     *   might yield &quot;$5.25 per item&quot;.  The only required
     *   {placeholder} is {number}.</dd>
     *
     *   <dt>negativeFormat</dt>
     *   <dd>Like format, but applied to negative numbers.  If set to null,
     *   defaults from the configured format, prefixed with -.  This is
     *   separate from format to support formats like &quot;($12,345.67)&quot;.
     *
     *   <dt>prefix {String} (deprecated, use format/negativeFormat)</dt>
     *   <dd>String prepended before each number, like a currency designator "$"</dd>
     *   <dt>decimalPlaces {Number}</dt>
     *   <dd>Number of decimal places to round.</dd>
     *
     *   <dt>decimalSeparator {String}</dt>
     *   <dd>Decimal separator</dd>
     *
     *   <dt>thousandsSeparator {String}</dt>
     *   <dd>Thousands separator</dd>
     *
     *   <dt>suffix {String} (deprecated, use format/negativeFormat)</dt>
     *   <dd>String appended after each number, like " items" (note the space)</dd>
     *  </dl>
     * @return {String} Formatted number for display. Note, the following values
     * return as "": null, undefined, NaN, "".
     */
    format : function(n, cfg) {
        if (n === '' || n === null || !isFinite(n)) {
            return '';
        }

        n   = +n;
        cfg = YAHOO.lang.merge(YAHOO.util.Number.format.defaults, (cfg || {}));

        var stringN = n+'',
            absN   = Math.abs(n),
            places = cfg.decimalPlaces || 0,
            sep    = cfg.thousandsSeparator,
            negFmt = cfg.negativeFormat || ('-' + cfg.format),
            s, bits, i, precision;

        if (negFmt.indexOf('#') > -1) {
            // for backward compatibility of negativeFormat supporting '-#'
            negFmt = negFmt.replace(/#/, cfg.format);
        }

        if (places < 0) {
            // Get rid of the decimal info
            s = absN - (absN % 1) + '';
            i = s.length + places;

            // avoid 123 vs decimalPlaces -4 (should return "0")
            if (i > 0) {
                // leverage toFixed by making 123 => 0.123 for the rounding
                // operation, then add the appropriate number of zeros back on
                s = Number('.' + s).toFixed(i).slice(2) +
                    new Array(s.length - i + 1).join('0');
            } else {
                s = "0";
            }
        } else {
            // Avoid toFixed on floats:
            // Bug 2528976
            // Bug 2528977
            var unfloatedN = absN+'';
            if(places > 0 || unfloatedN.indexOf('.') > 0) {
                var power = Math.pow(10, places);
                s = Math.round(absN * power) / power + '';
                var dot = s.indexOf('.'),
                    padding, zeroes;
                
                // Add padding
                if(dot < 0) {
                    padding = places;
                    zeroes = (Math.pow(10, padding) + '').substring(1);
                    if(places > 0) {
                        s = s + '.' + zeroes;
                    }
                }
                else {
                    padding = places - (s.length - dot - 1);
                    zeroes = (Math.pow(10, padding) + '').substring(1);
                    s = s + zeroes;
                }
            }
            else {
                s = absN.toFixed(places)+'';
            }
        }

        bits  = s.split(/\D/);

        if (absN >= 1000) {
            i  = bits[0].length % 3 || 3;

            bits[0] = bits[0].slice(0,i) +
                      bits[0].slice(i).replace(/(\d{3})/g, sep + '$1');

        }

        return YAHOO.util.Number.format._applyFormat(
            (n < 0 ? negFmt : cfg.format),
            bits.join(cfg.decimalSeparator),
            cfg);
    }
};

/**
 * <p>Default values for Number.format behavior.  Override properties of this
 * object if you want every call to Number.format in your system to use
 * specific presets.</p>
 *
 * <p>Available keys include:</p>
 * <ul>
 *   <li>format</li>
 *   <li>negativeFormat</li>
 *   <li>decimalSeparator</li>
 *   <li>decimalPlaces</li>
 *   <li>thousandsSeparator</li>
 *   <li>prefix/suffix or any other token you want to use in the format templates</li>
 * </ul>
 *
 * @property Number.format.defaults
 * @type {Object}
 * @static
 */
YAHOO.util.Number.format.defaults = {
    format : '{prefix}{number}{suffix}',
    negativeFormat : null, // defaults to -(format)
    decimalSeparator : '.',
    decimalPlaces    : null,
    thousandsSeparator : ''
};

/**
 * Apply any special formatting to the "d,ddd.dd" string.  Takes either the
 * cfg.format or cfg.negativeFormat template and replaces any {placeholders}
 * with either the number or a value from a so-named property of the config
 * object.
 *
 * @method Number.format._applyFormat
 * @static
 * @param tmpl {String} the cfg.format or cfg.numberFormat template string
 * @param num {String} the number with separators and decimalPlaces applied
 * @param data {Object} the config object, used here to populate {placeholder}s
 * @return {String} the number with any decorators added
 */
YAHOO.util.Number.format._applyFormat = function (tmpl, num, data) {
    return tmpl.replace(/\{(\w+)\}/g, function (_, token) {
        return token === 'number' ? num :
               token in data ? data[token] : '';
    });
};


/****************************************************************************/
/****************************************************************************/
/****************************************************************************/

(function () {

var xPad=function (x, pad, r)
{
    if(typeof r === 'undefined')
    {
        r=10;
    }
    for( ; parseInt(x, 10)<r && r>1; r/=10) {
        x = pad.toString() + x;
    }
    return x.toString();
};


/**
 * The static Date class provides helper functions to deal with data of type Date.
 *
 * @namespace YAHOO.util
 * @requires yahoo
 * @class Date
 * @static
 */
 var Dt = {
    formats: {
        a: function (d, l) { return l.a[d.getDay()]; },
        A: function (d, l) { return l.A[d.getDay()]; },
        b: function (d, l) { return l.b[d.getMonth()]; },
        B: function (d, l) { return l.B[d.getMonth()]; },
        C: function (d) { return xPad(parseInt(d.getFullYear()/100, 10), 0); },
        d: ['getDate', '0'],
        e: ['getDate', ' '],
        g: function (d) { return xPad(parseInt(Dt.formats.G(d)%100, 10), 0); },
        G: function (d) {
                var y = d.getFullYear();
                var V = parseInt(Dt.formats.V(d), 10);
                var W = parseInt(Dt.formats.W(d), 10);
    
                if(W > V) {
                    y++;
                } else if(W===0 && V>=52) {
                    y--;
                }
    
                return y;
            },
        H: ['getHours', '0'],
        I: function (d) { var I=d.getHours()%12; return xPad(I===0?12:I, 0); },
        j: function (d) {
                var gmd_1 = new Date('' + d.getFullYear() + '/1/1 GMT');
                var gmdate = new Date('' + d.getFullYear() + '/' + (d.getMonth()+1) + '/' + d.getDate() + ' GMT');
                var ms = gmdate - gmd_1;
                var doy = parseInt(ms/60000/60/24, 10)+1;
                return xPad(doy, 0, 100);
            },
        k: ['getHours', ' '],
        l: function (d) { var I=d.getHours()%12; return xPad(I===0?12:I, ' '); },
        m: function (d) { return xPad(d.getMonth()+1, 0); },
        M: ['getMinutes', '0'],
        p: function (d, l) { return l.p[d.getHours() >= 12 ? 1 : 0 ]; },
        P: function (d, l) { return l.P[d.getHours() >= 12 ? 1 : 0 ]; },
        s: function (d, l) { return parseInt(d.getTime()/1000, 10); },
        S: ['getSeconds', '0'],
        u: function (d) { var dow = d.getDay(); return dow===0?7:dow; },
        U: function (d) {
                var doy = parseInt(Dt.formats.j(d), 10);
                var rdow = 6-d.getDay();
                var woy = parseInt((doy+rdow)/7, 10);
                return xPad(woy, 0);
            },
        V: function (d) {
                var woy = parseInt(Dt.formats.W(d), 10);
                var dow1_1 = (new Date('' + d.getFullYear() + '/1/1')).getDay();
                // First week is 01 and not 00 as in the case of %U and %W,
                // so we add 1 to the final result except if day 1 of the year
                // is a Monday (then %W returns 01).
                // We also need to subtract 1 if the day 1 of the year is 
                // Friday-Sunday, so the resulting equation becomes:
                var idow = woy + (dow1_1 > 4 || dow1_1 <= 1 ? 0 : 1);
                if(idow === 53 && (new Date('' + d.getFullYear() + '/12/31')).getDay() < 4)
                {
                    idow = 1;
                }
                else if(idow === 0)
                {
                    idow = Dt.formats.V(new Date('' + (d.getFullYear()-1) + '/12/31'));
                }
    
                return xPad(idow, 0);
            },
        w: 'getDay',
        W: function (d) {
                var doy = parseInt(Dt.formats.j(d), 10);
                var rdow = 7-Dt.formats.u(d);
                var woy = parseInt((doy+rdow)/7, 10);
                return xPad(woy, 0, 10);
            },
        y: function (d) { return xPad(d.getFullYear()%100, 0); },
        Y: 'getFullYear',
        z: function (d) {
                var o = d.getTimezoneOffset();
                var H = xPad(parseInt(Math.abs(o/60), 10), 0);
                var M = xPad(Math.abs(o%60), 0);
                return (o>0?'-':'+') + H + M;
            },
        Z: function (d) {
		var tz = d.toString().replace(/^.*:\d\d( GMT[+-]\d+)? \(?([A-Za-z ]+)\)?\d*$/, '$2').replace(/[a-z ]/g, '');
		if(tz.length > 4) {
			tz = Dt.formats.z(d);
		}
		return tz;
	},
        '%': function (d) { return '%'; }
    },

    aggregates: {
        c: 'locale',
        D: '%m/%d/%y',
        F: '%Y-%m-%d',
        h: '%b',
        n: '\n',
        r: 'locale',
        R: '%H:%M',
        t: '\t',
        T: '%H:%M:%S',
        x: 'locale',
        X: 'locale'
        //'+': '%a %b %e %T %Z %Y'
    },

     /**
     * Takes a native JavaScript Date and formats to string for display to user.
     *
     * @method format
     * @param oDate {Date} Date.
     * @param oConfig {Object} (Optional) Object literal of configuration values:
     *  <dl>
     *   <dt>format &lt;String&gt;</dt>
     *   <dd>
     *   <p>
     *   Any strftime string is supported, such as "%I:%M:%S %p". strftime has several format specifiers defined by the Open group at 
     *   <a href="http://www.opengroup.org/onlinepubs/007908799/xsh/strftime.html">http://www.opengroup.org/onlinepubs/007908799/xsh/strftime.html</a>
     *   </p>
     *   <p>   
     *   PHP added a few of its own, defined at <a href="http://www.php.net/strftime">http://www.php.net/strftime</a>
     *   </p>
     *   <p>
     *   This javascript implementation supports all the PHP specifiers and a few more.  The full list is below:
     *   </p>
     *   <dl>
     *    <dt>%a</dt> <dd>abbreviated weekday name according to the current locale</dd>
     *    <dt>%A</dt> <dd>full weekday name according to the current locale</dd>
     *    <dt>%b</dt> <dd>abbreviated month name according to the current locale</dd>
     *    <dt>%B</dt> <dd>full month name according to the current locale</dd>
     *    <dt>%c</dt> <dd>preferred date and time representation for the current locale</dd>
     *    <dt>%C</dt> <dd>century number (the year divided by 100 and truncated to an integer, range 00 to 99)</dd>
     *    <dt>%d</dt> <dd>day of the month as a decimal number (range 01 to 31)</dd>
     *    <dt>%D</dt> <dd>same as %m/%d/%y</dd>
     *    <dt>%e</dt> <dd>day of the month as a decimal number, a single digit is preceded by a space (range ' 1' to '31')</dd>
     *    <dt>%F</dt> <dd>same as %Y-%m-%d (ISO 8601 date format)</dd>
     *    <dt>%g</dt> <dd>like %G, but without the century</dd>
     *    <dt>%G</dt> <dd>The 4-digit year corresponding to the ISO week number</dd>
     *    <dt>%h</dt> <dd>same as %b</dd>
     *    <dt>%H</dt> <dd>hour as a decimal number using a 24-hour clock (range 00 to 23)</dd>
     *    <dt>%I</dt> <dd>hour as a decimal number using a 12-hour clock (range 01 to 12)</dd>
     *    <dt>%j</dt> <dd>day of the year as a decimal number (range 001 to 366)</dd>
     *    <dt>%k</dt> <dd>hour as a decimal number using a 24-hour clock (range 0 to 23); single digits are preceded by a blank. (See also %H.)</dd>
     *    <dt>%l</dt> <dd>hour as a decimal number using a 12-hour clock (range 1 to 12); single digits are preceded by a blank. (See also %I.) </dd>
     *    <dt>%m</dt> <dd>month as a decimal number (range 01 to 12)</dd>
     *    <dt>%M</dt> <dd>minute as a decimal number</dd>
     *    <dt>%n</dt> <dd>newline character</dd>
     *    <dt>%p</dt> <dd>either `AM' or `PM' according to the given time value, or the corresponding strings for the current locale</dd>
     *    <dt>%P</dt> <dd>like %p, but lower case</dd>
     *    <dt>%r</dt> <dd>time in a.m. and p.m. notation equal to %I:%M:%S %p</dd>
     *    <dt>%R</dt> <dd>time in 24 hour notation equal to %H:%M</dd>
     *    <dt>%s</dt> <dd>number of seconds since the Epoch, ie, since 1970-01-01 00:00:00 UTC</dd>
     *    <dt>%S</dt> <dd>second as a decimal number</dd>
     *    <dt>%t</dt> <dd>tab character</dd>
     *    <dt>%T</dt> <dd>current time, equal to %H:%M:%S</dd>
     *    <dt>%u</dt> <dd>weekday as a decimal number [1,7], with 1 representing Monday</dd>
     *    <dt>%U</dt> <dd>week number of the current year as a decimal number, starting with the
     *            first Sunday as the first day of the first week</dd>
     *    <dt>%V</dt> <dd>The ISO 8601:1988 week number of the current year as a decimal number,
     *            range 01 to 53, where week 1 is the first week that has at least 4 days
     *            in the current year, and with Monday as the first day of the week.</dd>
     *    <dt>%w</dt> <dd>day of the week as a decimal, Sunday being 0</dd>
     *    <dt>%W</dt> <dd>week number of the current year as a decimal number, starting with the
     *            first Monday as the first day of the first week</dd>
     *    <dt>%x</dt> <dd>preferred date representation for the current locale without the time</dd>
     *    <dt>%X</dt> <dd>preferred time representation for the current locale without the date</dd>
     *    <dt>%y</dt> <dd>year as a decimal number without a century (range 00 to 99)</dd>
     *    <dt>%Y</dt> <dd>year as a decimal number including the century</dd>
     *    <dt>%z</dt> <dd>numerical time zone representation</dd>
     *    <dt>%Z</dt> <dd>time zone name or abbreviation</dd>
     *    <dt>%%</dt> <dd>a literal `%' character</dd>
     *   </dl>
     *  </dd>
     * </dl>
     * @param sLocale {String} (Optional) The locale to use when displaying days of week,
     *  months of the year, and other locale specific strings.  The following locales are
     *  built in:
     *  <dl>
     *   <dt>en</dt>
     *   <dd>English</dd>
     *   <dt>en-US</dt>
     *   <dd>US English</dd>
     *   <dt>en-GB</dt>
     *   <dd>British English</dd>
     *   <dt>en-AU</dt>
     *   <dd>Australian English (identical to British English)</dd>
     *  </dl>
     *  More locales may be added by subclassing of YAHOO.util.DateLocale.
     *  See YAHOO.util.DateLocale for more information.
     * @return {HTML} Formatted date for display. Non-date values are passed
     * through as-is.
     * @sa YAHOO.util.DateLocale
     */
    format : function (oDate, oConfig, sLocale) {
        oConfig = oConfig || {};
        
        if(!(oDate instanceof Date)) {
            return YAHOO.lang.isValue(oDate) ? oDate : "";
        }

        var format = oConfig.format || "%m/%d/%Y";

        // Be backwards compatible, support strings that are
        // exactly equal to YYYY/MM/DD, DD/MM/YYYY and MM/DD/YYYY
        if(format === 'YYYY/MM/DD') {
            format = '%Y/%m/%d';
        } else if(format === 'DD/MM/YYYY') {
            format = '%d/%m/%Y';
        } else if(format === 'MM/DD/YYYY') {
            format = '%m/%d/%Y';
        }
        // end backwards compatibility block
 
        sLocale = sLocale || "en";

        // Make sure we have a definition for the requested locale, or default to en.
        if(!(sLocale in YAHOO.util.DateLocale)) {
            if(sLocale.replace(/-[a-zA-Z]+$/, '') in YAHOO.util.DateLocale) {
                sLocale = sLocale.replace(/-[a-zA-Z]+$/, '');
            } else {
                sLocale = "en";
            }
        }

        var aLocale = YAHOO.util.DateLocale[sLocale];

        var replace_aggs = function (m0, m1) {
            var f = Dt.aggregates[m1];
            return (f === 'locale' ? aLocale[m1] : f);
        };

        var replace_formats = function (m0, m1) {
            var f = Dt.formats[m1];
            if(typeof f === 'string') {             // string => built in date function
                return oDate[f]();
            } else if(typeof f === 'function') {    // function => our own function
                return f.call(oDate, oDate, aLocale);
            } else if(typeof f === 'object' && typeof f[0] === 'string') {  // built in function with padding
                return xPad(oDate[f[0]](), f[1]);
            } else {
                return m1;
            }
        };

        // First replace aggregates (run in a loop because an agg may be made up of other aggs)
        while(format.match(/%[cDFhnrRtTxX]/)) {
            format = format.replace(/%([cDFhnrRtTxX])/g, replace_aggs);
        }

        // Now replace formats (do not run in a loop otherwise %%a will be replace with the value of %a)
        var str = format.replace(/%([aAbBCdegGHIjklmMpPsSuUVwWyYzZ%])/g, replace_formats);

        replace_aggs = replace_formats = undefined;

        return str;
    }
 };
 
 YAHOO.namespace("YAHOO.util");
 YAHOO.util.Date = Dt;

/**
 * The DateLocale class is a container and base class for all
 * localised date strings used by YAHOO.util.Date. It is used
 * internally, but may be extended to provide new date localisations.
 *
 * To create your own DateLocale, follow these steps:
 * <ol>
 *  <li>Find an existing locale that matches closely with your needs</li>
 *  <li>Use this as your base class.  Use YAHOO.util.DateLocale if nothing
 *   matches.</li>
 *  <li>Create your own class as an extension of the base class using
 *   YAHOO.lang.merge, and add your own localisations where needed.</li>
 * </ol>
 * See the YAHOO.util.DateLocale['en-US'] and YAHOO.util.DateLocale['en-GB']
 * classes which extend YAHOO.util.DateLocale['en'].
 *
 * For example, to implement locales for French french and Canadian french,
 * we would do the following:
 * <ol>
 *  <li>For French french, we have no existing similar locale, so use
 *   YAHOO.util.DateLocale as the base, and extend it:
 *   <pre>
 *      YAHOO.util.DateLocale['fr'] = YAHOO.lang.merge(YAHOO.util.DateLocale, {
 *          a: ['dim', 'lun', 'mar', 'mer', 'jeu', 'ven', 'sam'],
 *          A: ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'],
 *          b: ['jan', 'f&eacute;v', 'mar', 'avr', 'mai', 'jun', 'jui', 'ao&ucirc;', 'sep', 'oct', 'nov', 'd&eacute;c'],
 *          B: ['janvier', 'f&eacute;vrier', 'mars', 'avril', 'mai', 'juin', 'juillet', 'ao&ucirc;t', 'septembre', 'octobre', 'novembre', 'd&eacute;cembre'],
 *          c: '%a %d %b %Y %T %Z',
 *          p: ['', ''],
 *          P: ['', ''],
 *          x: '%d.%m.%Y',
 *          X: '%T'
 *      });
 *   </pre>
 *  </li>
 *  <li>For Canadian french, we start with French french and change the meaning of \%x:
 *   <pre>
 *      YAHOO.util.DateLocale['fr-CA'] = YAHOO.lang.merge(YAHOO.util.DateLocale['fr'], {
 *          x: '%Y-%m-%d'
 *      });
 *   </pre>
 *  </li>
 * </ol>
 *
 * With that, you can use your new locales:
 * <pre>
 *    var d = new Date("2008/04/22");
 *    YAHOO.util.Date.format(d, {format: "%A, %d %B == %x"}, "fr");
 * </pre>
 * will return:
 * <pre>
 *    mardi, 22 avril == 22.04.2008
 * </pre>
 * And
 * <pre>
 *    YAHOO.util.Date.format(d, {format: "%A, %d %B == %x"}, "fr-CA");
 * </pre>
 * Will return:
 * <pre>
 *   mardi, 22 avril == 2008-04-22
 * </pre>
 * @namespace YAHOO.util
 * @requires yahoo
 * @class DateLocale
 */
 YAHOO.util.DateLocale = {
        a: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
        A: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],
        b: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
        B: ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'],
        c: '%a %d %b %Y %T %Z',
        p: ['AM', 'PM'],
        P: ['am', 'pm'],
        r: '%I:%M:%S %p',
        x: '%d/%m/%y',
        X: '%T'
 };

 YAHOO.util.DateLocale['en'] = YAHOO.lang.merge(YAHOO.util.DateLocale, {});

 YAHOO.util.DateLocale['en-US'] = YAHOO.lang.merge(YAHOO.util.DateLocale['en'], {
        c: '%a %d %b %Y %I:%M:%S %p %Z',
        x: '%m/%d/%Y',
        X: '%I:%M:%S %p'
 });

 YAHOO.util.DateLocale['en-GB'] = YAHOO.lang.merge(YAHOO.util.DateLocale['en'], {
        r: '%l:%M:%S %P %Z'
 });
 YAHOO.util.DateLocale['en-AU'] = YAHOO.lang.merge(YAHOO.util.DateLocale['en']);

})();

YAHOO.register("datasource", YAHOO.util.DataSource, {version: "2.9.0", build: "2800"});
