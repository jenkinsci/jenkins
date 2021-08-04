/*
Copyright (c) 2011, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.com/yui/license.html
version: 2.9.0
*/
/**
 * The Connection Manager provides a simplified interface to the XMLHttpRequest
 * object.  It handles cross-browser instantiantion of XMLHttpRequest, negotiates the
 * interactive states and server response, returning the results to a pre-defined
 * callback you create.
 *
 * @namespace YAHOO.util
 * @module connection
 * @requires yahoo
 * @requires event
 */

/**
 * The Connection Manager singleton provides methods for creating and managing
 * asynchronous transactions.
 *
 * @class YAHOO.util.Connect
 */

YAHOO.util.Connect =
{
  /**
   * @description Array of MSFT ActiveX ids for XMLHttpRequest.
   * @property _msxml_progid
   * @private
   * @static
   * @type array
   */
    _msxml_progid:[
        'Microsoft.XMLHTTP',
        'MSXML2.XMLHTTP.3.0',
        'MSXML2.XMLHTTP'
        ],

  /**
   * @description Object literal of HTTP header(s)
   * @property _http_header
   * @private
   * @static
   * @type object
   */
    _http_headers:{},

  /**
   * @description Determines if HTTP headers are set.
   * @property _has_http_headers
   * @private
   * @static
   * @type boolean
   */
    _has_http_headers:false,

 /**
  * @description Determines if a default header of
  * Content-Type of 'application/x-www-form-urlencoded'
  * will be added to any client HTTP headers sent for POST
  * transactions.
  * @property _use_default_post_header
  * @private
  * @static
  * @type boolean
  */
    _use_default_post_header:true,

 /**
  * @description The default header used for POST transactions.
  * @property _default_post_header
  * @private
  * @static
  * @type boolean
  */
    _default_post_header:'application/x-www-form-urlencoded; charset=UTF-8',

 /**
  * @description The default header used for transactions involving the
  * use of HTML forms.
  * @property _default_form_header
  * @private
  * @static
  * @type boolean
  */
    _default_form_header:'application/x-www-form-urlencoded',

 /**
  * @description Determines if a default header of
  * 'X-Requested-With: XMLHttpRequest'
  * will be added to each transaction.
  * @property _use_default_xhr_header
  * @private
  * @static
  * @type boolean
  */
    _use_default_xhr_header:true,

 /**
  * @description The default header value for the label
  * "X-Requested-With".  This is sent with each
  * transaction, by default, to identify the
  * request as being made by YUI Connection Manager.
  * @property _default_xhr_header
  * @private
  * @static
  * @type boolean
  */
    _default_xhr_header:'XMLHttpRequest',

 /**
  * @description Determines if custom, default headers
  * are set for each transaction.
  * @property _has_default_header
  * @private
  * @static
  * @type boolean
  */
    _has_default_headers:true,

 /**
   * @description Property modified by setForm() to determine if the data
   * should be submitted as an HTML form.
   * @property _isFormSubmit
   * @private
   * @static
   * @type boolean
   */
	_isFormSubmit:false,

 /**
  * @description Determines if custom, default headers
  * are set for each transaction.
  * @property _has_default_header
  * @private
  * @static
  * @type boolean
  */
    _default_headers:{},

 /**
  * @description Collection of polling references to the polling mechanism in handleReadyState.
  * @property _poll
  * @private
  * @static
  * @type object
  */
    _poll:{},

 /**
  * @description Queue of timeout values for each transaction callback with a defined timeout value.
  * @property _timeOut
  * @private
  * @static
  * @type object
  */
    _timeOut:{},

  /**
   * @description The polling frequency, in milliseconds, for HandleReadyState.
   * when attempting to determine a transaction's XHR readyState.
   * The default is 50 milliseconds.
   * @property _polling_interval
   * @private
   * @static
   * @type int
   */
     _polling_interval:50,

  /**
   * @description A transaction counter that increments the transaction id for each transaction.
   * @property _transaction_id
   * @private
   * @static
   * @type int
   */
     _transaction_id:0,

  /**
   * @description Custom event that fires at the start of a transaction
   * @property startEvent
   * @private
   * @static
   * @type CustomEvent
   */
    startEvent: new YAHOO.util.CustomEvent('start'),

  /**
   * @description Custom event that fires when a transaction response has completed.
   * @property completeEvent
   * @private
   * @static
   * @type CustomEvent
   */
    completeEvent: new YAHOO.util.CustomEvent('complete'),

  /**
   * @description Custom event that fires when handleTransactionResponse() determines a
   * response in the HTTP 2xx range.
   * @property successEvent
   * @private
   * @static
   * @type CustomEvent
   */
    successEvent: new YAHOO.util.CustomEvent('success'),

  /**
   * @description Custom event that fires when handleTransactionResponse() determines a
   * response in the HTTP 4xx/5xx range.
   * @property failureEvent
   * @private
   * @static
   * @type CustomEvent
   */
    failureEvent: new YAHOO.util.CustomEvent('failure'),

  /**
   * @description Custom event that fires when a transaction is successfully aborted.
   * @property abortEvent
   * @private
   * @static
   * @type CustomEvent
   */
    abortEvent: new YAHOO.util.CustomEvent('abort'),

  /**
   * @description A reference table that maps callback custom events members to its specific
   * event name.
   * @property _customEvents
   * @private
   * @static
   * @type object
   */
    _customEvents:
    {
        onStart:['startEvent', 'start'],
        onComplete:['completeEvent', 'complete'],
        onSuccess:['successEvent', 'success'],
        onFailure:['failureEvent', 'failure'],
        onUpload:['uploadEvent', 'upload'],
        onAbort:['abortEvent', 'abort']
    },

  /**
   * @description Member to add an ActiveX id to the existing xml_progid array.
   * In the event(unlikely) a new ActiveX id is introduced, it can be added
   * without internal code modifications.
   * @method setProgId
   * @public
   * @static
   * @param {string} id The ActiveX id to be added to initialize the XHR object.
   * @return void
   */
    setProgId:function(id)
    {
        this._msxml_progid.unshift(id);
        YAHOO.log('ActiveX Program Id  ' + id + ' added to _msxml_progid.', 'info', 'Connection');
    },

  /**
   * @description Member to override the default POST header.
   * @method setDefaultPostHeader
   * @public
   * @static
   * @param {boolean} b Set and use default header - true or false .
   * @return void
   */
    setDefaultPostHeader:function(b)
    {
        if(typeof b == 'string'){
            this._default_post_header = b;
			this._use_default_post_header = true;

            YAHOO.log('Default POST header set to  ' + b, 'info', 'Connection');
        }
        else if(typeof b == 'boolean'){
            this._use_default_post_header = b;
        }
    },

  /**
   * @description Member to override the default transaction header..
   * @method setDefaultXhrHeader
   * @public
   * @static
   * @param {boolean} b Set and use default header - true or false .
   * @return void
   */
    setDefaultXhrHeader:function(b)
    {
        if(typeof b == 'string'){
            this._default_xhr_header = b;
            YAHOO.log('Default XHR header set to  ' + b, 'info', 'Connection');
        }
        else{
            this._use_default_xhr_header = b;
        }
    },

  /**
   * @description Member to modify the default polling interval.
   * @method setPollingInterval
   * @public
   * @static
   * @param {int} i The polling interval in milliseconds.
   * @return void
   */
    setPollingInterval:function(i)
    {
        if(typeof i == 'number' && isFinite(i)){
            this._polling_interval = i;
            YAHOO.log('Default polling interval set to ' + i +'ms', 'info', 'Connection');
        }
    },

  /**
   * @description Instantiates a XMLHttpRequest object and returns an object with two properties:
   * the XMLHttpRequest instance and the transaction id.
   * @method createXhrObject
   * @private
   * @static
   * @param {int} transactionId Property containing the transaction id for this transaction.
   * @return object
   */
    createXhrObject:function(transactionId)
    {
        var obj,http,i;
        try
        {
            // Instantiates XMLHttpRequest in non-IE browsers and assigns to http.
            http = new XMLHttpRequest();
            //  Object literal with http and tId properties
            obj = { conn:http, tId:transactionId, xhr: true };
            YAHOO.log('XHR object created for transaction ' + transactionId, 'info', 'Connection');
        }
        catch(e)
        {
            for(i=0; i<this._msxml_progid.length; ++i){
                try
                {
                    // Instantiates XMLHttpRequest for IE and assign to http
                    http = new ActiveXObject(this._msxml_progid[i]);
                    //  Object literal with conn and tId properties
                    obj = { conn:http, tId:transactionId, xhr: true };
                    YAHOO.log('ActiveX XHR object created for transaction ' + transactionId, 'info', 'Connection');
                    break;
                }
                catch(e1){}
            }
        }
        finally
        {
            return obj;
        }
    },

  /**
   * @description This method is called by asyncRequest to create a
   * valid connection object for the transaction.  It also passes a
   * transaction id and increments the transaction id counter.
   * @method getConnectionObject
   * @private
   * @static
   * @return {object}
   */
    getConnectionObject:function(t)
    {
        var o, tId = this._transaction_id;

        try
        {
            if(!t){
                o = this.createXhrObject(tId);
            }
            else{
                o = {tId:tId};
                if(t==='xdr'){
                    o.conn = this._transport;
                    o.xdr = true;
                }
                else if(t==='upload'){
                    o.upload = true;
                }
            }

            if(o){
                this._transaction_id++;
            }
        }
        catch(e){}
        return o;
    },

  /**
   * @description Method for initiating an asynchronous request via the XHR object.
   * @method asyncRequest
   * @public
   * @static
   * @param {string} method HTTP transaction method
   * @param {string} uri Fully qualified path of resource
   * @param {callback} callback User-defined callback function or object
   * @param {string} postData POST body
   * @return {object} Returns the connection object
   */
    asyncRequest:function(method, uri, callback, postData)
    {
        var args = callback&&callback.argument?callback.argument:null,
            YCM = this,
            o, t;

        if(this._isFileUpload){
            t = 'upload';
        }
        else if(callback && callback.xdr){
            t = 'xdr';
        }

        o = this.getConnectionObject(t);
        if(!o){
            YAHOO.log('Unable to create connection object.', 'error', 'Connection');
            return null;
        }
        else{

            // Initialize any transaction-specific custom events, if provided.
            if(callback && callback.customevents){
                this.initCustomEvents(o, callback);
            }

            if(this._isFormSubmit){
                if(this._isFileUpload){
                    window.setTimeout(function(){YCM.uploadFile(o, callback, uri, postData);}, 10);
                    return o;
                }

                // If the specified HTTP method is GET, setForm() will return an
                // encoded string that is concatenated to the uri to
                // create a querystring.
                if(method.toUpperCase() == 'GET'){
                    if(this._sFormData.length !== 0){
                        // If the URI already contains a querystring, append an ampersand
                        // and then concatenate _sFormData to the URI.
                        uri += ((uri.indexOf('?') == -1)?'?':'&') + this._sFormData;
                    }
                }
                else if(method.toUpperCase() == 'POST'){
                    // If POST data exist in addition to the HTML form data,
                    // it will be concatenated to the form data.
                    postData = postData?this._sFormData + "&" + postData:this._sFormData;
                }
            }

            if(method.toUpperCase() == 'GET' && (callback && callback.cache === false)){
                // If callback.cache is defined and set to false, a
                // timestamp value will be added to the querystring.
                uri += ((uri.indexOf('?') == -1)?'?':'&') + "rnd=" + new Date().valueOf().toString();
            }

            // Each transaction will automatically include a custom header of
            // "X-Requested-With: XMLHttpRequest" to identify the request as
            // having originated from Connection Manager.
            if(this._use_default_xhr_header){
                if(!this._default_headers['X-Requested-With']){
                    this.initHeader('X-Requested-With', this._default_xhr_header, true);
                    YAHOO.log('Initialize transaction header X-Request-Header to XMLHttpRequest.', 'info', 'Connection');
                }
            }

            //If the transaction method is POST and the POST header value is set to true
            //or a custom value, initalize the Content-Type header to this value.
            if((method.toUpperCase() === 'POST' && this._use_default_post_header) && this._isFormSubmit === false){
                this.initHeader('Content-Type', this._default_post_header);
                YAHOO.log('Initialize header Content-Type to application/x-www-form-urlencoded; UTF-8 for POST transaction.', 'info', 'Connection');
            }

            if(o.xdr){
                this.xdr(o, method, uri, callback, postData);
                return o;
            }

            o.conn.open(method, uri, true);
            //Initialize all default and custom HTTP headers,
            if(this._has_default_headers || this._has_http_headers){
                this.setHeader(o);
            }

            this.handleReadyState(o, callback);
            o.conn.send(postData || '');
            YAHOO.log('Transaction ' + o.tId + ' sent.', 'info', 'Connection');

            // Reset the HTML form data and state properties as
            // soon as the data are submitted.
            if(this._isFormSubmit === true){
                this.resetFormState();
            }

            // Fire global custom event -- startEvent
            this.startEvent.fire(o, args);

            if(o.startEvent){
                // Fire transaction custom event -- startEvent
                o.startEvent.fire(o, args);
            }

            return o;
        }
    },

  /**
   * @description This method creates and subscribes custom events,
   * specific to each transaction
   * @method initCustomEvents
   * @private
   * @static
   * @param {object} o The connection object
   * @param {callback} callback The user-defined callback object
   * @return {void}
   */
    initCustomEvents:function(o, callback)
    {
        var prop;
        // Enumerate through callback.customevents members and bind/subscribe
        // events that match in the _customEvents table.
        for(prop in callback.customevents){
            if(this._customEvents[prop][0]){
                // Create the custom event
                o[this._customEvents[prop][0]] = new YAHOO.util.CustomEvent(this._customEvents[prop][1], (callback.scope)?callback.scope:null);
                YAHOO.log('Transaction-specific Custom Event ' + o[this._customEvents[prop][1]] + ' created.', 'info', 'Connection');

                // Subscribe the custom event
                o[this._customEvents[prop][0]].subscribe(callback.customevents[prop]);
                YAHOO.log('Transaction-specific Custom Event ' + o[this._customEvents[prop][1]] + ' subscribed.', 'info', 'Connection');
            }
        }
    },

  /**
   * @description This method serves as a timer that polls the XHR object's readyState
   * property during a transaction, instead of binding a callback to the
   * onreadystatechange event.  Upon readyState 4, handleTransactionResponse
   * will process the response, and the timer will be cleared.
   * @method handleReadyState
   * @private
   * @static
   * @param {object} o The connection object
   * @param {callback} callback The user-defined callback object
   * @return {void}
   */

    handleReadyState:function(o, callback)

    {
        var oConn = this,
            args = (callback && callback.argument)?callback.argument:null;

        if(callback && callback.timeout){
            this._timeOut[o.tId] = window.setTimeout(function(){ oConn.abort(o, callback, true); }, callback.timeout);
        }

        this._poll[o.tId] = window.setInterval(
            function(){
                if(o.conn && o.conn.readyState === 4){

                    // Clear the polling interval for the transaction
                    // and remove the reference from _poll.
                    window.clearInterval(oConn._poll[o.tId]);
                    delete oConn._poll[o.tId];

                    if(callback && callback.timeout){
                        window.clearTimeout(oConn._timeOut[o.tId]);
                        delete oConn._timeOut[o.tId];
                    }

                    // Fire global custom event -- completeEvent
                    oConn.completeEvent.fire(o, args);

                    if(o.completeEvent){
                        // Fire transaction custom event -- completeEvent
                        o.completeEvent.fire(o, args);
                    }

                    oConn.handleTransactionResponse(o, callback);
                }
            }
        ,this._polling_interval);
    },

  /**
   * @description This method attempts to interpret the server response and
   * determine whether the transaction was successful, or if an error or
   * exception was encountered.
   * @method handleTransactionResponse
   * @private
   * @static
   * @param {object} o The connection object
   * @param {object} callback The user-defined callback object
   * @param {boolean} isAbort Determines if the transaction was terminated via abort().
   * @return {void}
   */
    handleTransactionResponse:function(o, callback, isAbort)
    {
        var httpStatus, responseObject,
            args = (callback && callback.argument)?callback.argument:null,
            xdrS = (o.r && o.r.statusText === 'xdr:success')?true:false,
            xdrF = (o.r && o.r.statusText === 'xdr:failure')?true:false,
            xdrA = isAbort;

        try
        {
            if((o.conn.status !== undefined && o.conn.status !== 0) || xdrS){
                // XDR requests will not have HTTP status defined. The
                // statusText property will define the response status
                // set by the Flash transport.
                httpStatus = o.conn.status;
            }
            else if(xdrF && !xdrA){
                // Set XDR transaction failure to a status of 0, which
                // resolves as an HTTP failure, instead of an exception.
                httpStatus = 0;
            }
            else{
                httpStatus = 13030;
            }
        }
        catch(e){

             // 13030 is a custom code to indicate the condition -- in Mozilla/FF --
             // when the XHR object's status and statusText properties are
             // unavailable, and a query attempt throws an exception.
            httpStatus = 13030;
        }

        if((httpStatus >= 200 && httpStatus < 300) || httpStatus === 1223 || xdrS){
            responseObject = o.xdr ? o.r : this.createResponseObject(o, args);
            if(callback && callback.success){
                if(!callback.scope){
                    callback.success(responseObject);
                    YAHOO.log('Success callback. HTTP code is ' + httpStatus, 'info', 'Connection');
                }
                else{
                    // If a scope property is defined, the callback will be fired from
                    // the context of the object.
                    callback.success.apply(callback.scope, [responseObject]);
                    YAHOO.log('Success callback with scope. HTTP code is ' + httpStatus, 'info', 'Connection');
                }
            }

            // Fire global custom event -- successEvent
            this.successEvent.fire(responseObject);

            if(o.successEvent){
                // Fire transaction custom event -- successEvent
                o.successEvent.fire(responseObject);
            }
        }
        else{
            switch(httpStatus){
                // The following cases are wininet.dll error codes that may be encountered.
                case 12002: // Server timeout
                case 12029: // 12029 to 12031 correspond to dropped connections.
                case 12030:
                case 12031:
                case 12152: // Connection closed by server.
                case 13030: // See above comments for variable status.
                    // XDR transactions will not resolve to this case, since the
                    // response object is already built in the xdr response.
                    responseObject = this.createExceptionObject(o.tId, args, (isAbort?isAbort:false));
                    if(callback && callback.failure){
                        if(!callback.scope){
                            callback.failure(responseObject);
                            YAHOO.log('Failure callback. Exception detected. Status code is ' + httpStatus, 'warn', 'Connection');
                        }
                        else{
                            callback.failure.apply(callback.scope, [responseObject]);
                            YAHOO.log('Failure callback with scope. Exception detected. Status code is ' + httpStatus, 'warn', 'Connection');
                        }
                    }

                    break;
                default:
                    responseObject = (o.xdr) ? o.response : this.createResponseObject(o, args);
                    if(callback && callback.failure){
                        if(!callback.scope){
                            callback.failure(responseObject);
                            YAHOO.log('Failure callback. HTTP status code is ' + httpStatus, 'warn', 'Connection');
                        }
                        else{
                            callback.failure.apply(callback.scope, [responseObject]);
                            YAHOO.log('Failure callback with scope. HTTP status code is ' + httpStatus, 'warn', 'Connection');
                        }
                    }
            }

            // Fire global custom event -- failureEvent
            this.failureEvent.fire(responseObject);

            if(o.failureEvent){
                // Fire transaction custom event -- failureEvent
                o.failureEvent.fire(responseObject);
            }

        }

        this.releaseObject(o);
        responseObject = null;
    },

  /**
   * @description This method evaluates the server response, creates and returns the results via
   * its properties.  Success and failure cases will differ in the response
   * object's property values.
   * @method createResponseObject
   * @private
   * @static
   * @param {object} o The connection object
   * @param {callbackArg} callbackArg The user-defined argument or arguments to be passed to the callback
   * @return {object}
   */
    createResponseObject:function(o, callbackArg)
    {
        var obj = {}, headerObj = {},
            i, headerStr, header, delimitPos;

        try
        {
            headerStr = o.conn.getAllResponseHeaders();
            header = headerStr.split('\n');
            for(i=0; i<header.length; i++){
                delimitPos = header[i].indexOf(':');
                if(delimitPos != -1){
                    headerObj[header[i].substring(0,delimitPos)] = YAHOO.lang.trim(header[i].substring(delimitPos+2));
                }
            }
        }
        catch(e){}

        obj.tId = o.tId;
        // Normalize IE's response to HTTP 204 when Win error 1223.
        obj.status = (o.conn.status == 1223)?204:o.conn.status;
        // Normalize IE's statusText to "No Content" instead of "Unknown".
        obj.statusText = (o.conn.status == 1223)?"No Content":o.conn.statusText;
        obj.getResponseHeader = headerObj;
        obj.getAllResponseHeaders = headerStr;
        obj.responseText = o.conn.responseText;
        obj.responseXML = o.conn.responseXML;

        if(callbackArg){
            obj.argument = callbackArg;
        }

        return obj;
    },

  /**
   * @description If a transaction cannot be completed due to dropped or closed connections,
   * there may be not be enough information to build a full response object.
   * The failure callback will be fired and this specific condition can be identified
   * by a status property value of 0.
   *
   * If an abort was successful, the status property will report a value of -1.
   *
   * @method createExceptionObject
   * @private
   * @static
   * @param {int} tId The Transaction Id
   * @param {callbackArg} callbackArg The user-defined argument or arguments to be passed to the callback
   * @param {boolean} isAbort Determines if the exception case is caused by a transaction abort
   * @return {object}
   */
    createExceptionObject:function(tId, callbackArg, isAbort)
    {
        var COMM_CODE = 0,
            COMM_ERROR = 'communication failure',
            ABORT_CODE = -1,
            ABORT_ERROR = 'transaction aborted',
            obj = {};

        obj.tId = tId;
        if(isAbort){
            obj.status = ABORT_CODE;
            obj.statusText = ABORT_ERROR;
        }
        else{
            obj.status = COMM_CODE;
            obj.statusText = COMM_ERROR;
        }

        if(callbackArg){
            obj.argument = callbackArg;
        }

        return obj;
    },

  /**
   * @description Method that initializes the custom HTTP headers for the each transaction.
   * @method initHeader
   * @public
   * @static
   * @param {string} label The HTTP header label
   * @param {string} value The HTTP header value
   * @param {string} isDefault Determines if the specific header is a default header
   * automatically sent with each transaction.
   * @return {void}
   */
    initHeader:function(label, value, isDefault)
    {
        var headerObj = (isDefault)?this._default_headers:this._http_headers;

        headerObj[label] = value;
        if(isDefault){
            this._has_default_headers = true;
        }
        else{
            this._has_http_headers = true;
        }
    },


  /**
   * @description Accessor that sets the HTTP headers for each transaction.
   * @method setHeader
   * @private
   * @static
   * @param {object} o The connection object for the transaction.
   * @return {void}
   */
    setHeader:function(o)
    {
        var prop;
        if(this._has_default_headers){
            for(prop in this._default_headers){
                if(YAHOO.lang.hasOwnProperty(this._default_headers, prop)){
                    o.conn.setRequestHeader(prop, this._default_headers[prop]);
                    YAHOO.log('Default HTTP header ' + prop + ' set with value of ' + this._default_headers[prop], 'info', 'Connection');
                }
            }
        }

        if(this._has_http_headers){
            for(prop in this._http_headers){
                if(YAHOO.lang.hasOwnProperty(this._http_headers, prop)){
                    o.conn.setRequestHeader(prop, this._http_headers[prop]);
                    YAHOO.log('HTTP header ' + prop + ' set with value of ' + this._http_headers[prop], 'info', 'Connection');
                }
            }

            this._http_headers = {};
            this._has_http_headers = false;
        }
    },

  /**
   * @description Resets the default HTTP headers object
   * @method resetDefaultHeaders
   * @public
   * @static
   * @return {void}
   */
    resetDefaultHeaders:function(){
        this._default_headers = {};
        this._has_default_headers = false;
    },

  /**
   * @description Method to terminate a transaction, if it has not reached readyState 4.
   * @method abort
   * @public
   * @static
   * @param {object} o The connection object returned by asyncRequest.
   * @param {object} callback  User-defined callback object.
   * @param {string} isTimeout boolean to indicate if abort resulted from a callback timeout.
   * @return {boolean}
   */
    abort:function(o, callback, isTimeout)
    {
        var abortStatus,
            args = (callback && callback.argument)?callback.argument:null;
            o = o || {};

        if(o.conn){
            if(o.xhr){
                if(this.isCallInProgress(o)){
                    // Issue abort request
                    o.conn.abort();

                    window.clearInterval(this._poll[o.tId]);
                    delete this._poll[o.tId];

                    if(isTimeout){
                        window.clearTimeout(this._timeOut[o.tId]);
                        delete this._timeOut[o.tId];
                    }

                    abortStatus = true;
                }
            }
            else if(o.xdr){
                o.conn.abort(o.tId);
                abortStatus = true;
            }
        }
        else if(o.upload){
            var frameId = 'yuiIO' + o.tId;
            var io = document.getElementById(frameId);

            if(io){
                // Remove all listeners on the iframe prior to
                // its destruction.
                YAHOO.util.Event.removeListener(io, "load");
                // Destroy the iframe facilitating the transaction.
                document.body.removeChild(io);
                YAHOO.log('File upload iframe destroyed. Id is:' + frameId, 'info', 'Connection');

                if(isTimeout){
                    window.clearTimeout(this._timeOut[o.tId]);
                    delete this._timeOut[o.tId];
                }

                abortStatus = true;
            }
        }
        else{
            abortStatus = false;
        }

        if(abortStatus === true){
            // Fire global custom event -- abortEvent
            this.abortEvent.fire(o, args);

            if(o.abortEvent){
                // Fire transaction custom event -- abortEvent
                o.abortEvent.fire(o, args);
            }

            this.handleTransactionResponse(o, callback, true);
            YAHOO.log('Transaction ' + o.tId + ' aborted.', 'info', 'Connection');
        }

        return abortStatus;
    },

  /**
   * @description Determines if the transaction is still being processed.
   * @method isCallInProgress
   * @public
   * @static
   * @param {object} o The connection object returned by asyncRequest
   * @return {boolean}
   */
    isCallInProgress:function(o)
    {
        o = o || {};
        // if the XHR object assigned to the transaction has not been dereferenced,
        // then check its readyState status.  Otherwise, return false.
        if(o.xhr && o.conn){
            return o.conn.readyState !== 4 && o.conn.readyState !== 0;
        }
        else if(o.xdr && o.conn){
            return o.conn.isCallInProgress(o.tId);
        }
        else if(o.upload === true){
            return document.getElementById('yuiIO' + o.tId)?true:false;
        }
        else{
            return false;
        }
    },

  /**
   * @description Dereference the XHR instance and the connection object after the transaction is completed.
   * @method releaseObject
   * @private
   * @static
   * @param {object} o The connection object
   * @return {void}
   */
    releaseObject:function(o)
    {
        if(o && o.conn){
            //dereference the XHR instance.
            o.conn = null;

            YAHOO.log('Connection object for transaction ' + o.tId + ' destroyed.', 'info', 'Connection');

            //dereference the connection object.
            o = null;
        }
    }
};

/**
  * @for YAHOO.util.Connect
  */
(function() {
	var YCM = YAHOO.util.Connect, _fn = {};

   /**
    * @description This method creates and instantiates the Flash transport.
    * @method _swf
    * @private
    * @static
    * @param {string} URI to connection.swf.
    * @return {void}
    */
	function _swf(uri) {
		var o = '<object id="YUIConnectionSwf" type="application/x-shockwave-flash" data="' +
				uri + '" width="0" height="0">' +
				'<param name="movie" value="' + uri + '">' +
				'<param name="allowScriptAccess" value="always">' +
				'</object>',
		    c = document.createElement('div');

		document.body.appendChild(c);
		c.innerHTML = o;
	}

   /**
    * @description This method calls the public method on the
    * Flash transport to start the XDR transaction.  It is analogous
    * to Connection Manager's asyncRequest method.
    * @method xdr
    * @private
    * @static
    * @param {object} The transaction object.
    * @param {string} HTTP request method.
    * @param {string} URI for the transaction.
    * @param {object} The transaction's callback object.
    * @param {object} The JSON object used as HTTP POST data.
    * @return {void}
    */
	function _xdr(o, m, u, c, d) {
		_fn[parseInt(o.tId)] = { 'o':o, 'c':c };
		if (d) {
			c.method = m;
			c.data = d;
		}

		o.conn.send(u, c, o.tId);
	}

   /**
    * @description This method instantiates the Flash transport and
    * establishes a static reference to it, used for all XDR requests.
    * @method transport
    * @public
    * @static
    * @param {string} URI to connection.swf.
    * @return {void}
    */
	function _init(uri) {
		_swf(uri);
		YCM._transport = document.getElementById('YUIConnectionSwf');
	}

	function _xdrReady() {
		YCM.xdrReadyEvent.fire();
	}

   /**
    * @description This method fires the global and transaction start
    * events.
    * @method _xdrStart
    * @private
    * @static
    * @param {object} The transaction object.
    * @param {string} The transaction's callback object.
    * @return {void}
    */
	function _xdrStart(o, cb) {
		if (o) {
			// Fire global custom event -- startEvent
			YCM.startEvent.fire(o, cb.argument);

			if(o.startEvent){
				// Fire transaction custom event -- startEvent
				o.startEvent.fire(o, cb.argument);
			}
		}
	}

   /**
    * @description This method is the initial response handler
    * for XDR transactions.  The Flash transport calls this
    * function and sends the response payload.
    * @method handleXdrResponse
    * @private
    * @static
    * @param {object} The response object sent from the Flash transport.
    * @return {void}
    */
	function _handleXdrResponse(r) {
		var o = _fn[r.tId].o,
			cb = _fn[r.tId].c;

		if (r.statusText === 'xdr:start') {
			_xdrStart(o, cb);
			return;
		}

		r.responseText = decodeURI(r.responseText);
		o.r = r;
		if (cb.argument) {
			o.r.argument = cb.argument;
		}

		this.handleTransactionResponse(o, cb, r.statusText === 'xdr:abort' ? true : false);
		delete _fn[r.tId];
	}

	// Bind the functions to Connection Manager as static fields.
	YCM.xdr = _xdr;
	YCM.swf = _swf;
	YCM.transport = _init;
	YCM.xdrReadyEvent = new YAHOO.util.CustomEvent('xdrReady');
	YCM.xdrReady = _xdrReady;
	YCM.handleXdrResponse = _handleXdrResponse;
})();

/**
  * @for YAHOO.util.Connect
  */
(function(){
	var YCM = YAHOO.util.Connect,
		YE = YAHOO.util.Event,
		dM = document.documentMode ? document.documentMode : false;

   /**
	* @description Property modified by setForm() to determine if a file(s)
	* upload is expected.
	* @property _isFileUpload
	* @private
	* @static
	* @type boolean
	*/
	YCM._isFileUpload = false;

   /**
	* @description Property modified by setForm() to set a reference to the HTML
	* form node if the desired action is file upload.
	* @property _formNode
	* @private
	* @static
	* @type object
	*/
	YCM._formNode = null;

   /**
	* @description Property modified by setForm() to set the HTML form data
	* for each transaction.
	* @property _sFormData
	* @private
	* @static
	* @type string
	*/
	YCM._sFormData = null;

   /**
	* @description Tracks the name-value pair of the "clicked" submit button if multiple submit
	* buttons are present in an HTML form; and, if YAHOO.util.Event is available.
	* @property _submitElementValue
	* @private
	* @static
	* @type string
	*/
	YCM._submitElementValue = null;

   /**
    * @description Custom event that fires when handleTransactionResponse() determines a
    * response in the HTTP 4xx/5xx range.
    * @property failureEvent
    * @private
    * @static
    * @type CustomEvent
    */
	YCM.uploadEvent = new YAHOO.util.CustomEvent('upload');

   /**
	* @description Determines whether YAHOO.util.Event is available and returns true or false.
	* If true, an event listener is bound at the document level to trap click events that
	* resolve to a target type of "Submit".  This listener will enable setForm() to determine
	* the clicked "Submit" value in a multi-Submit button, HTML form.
	* @property _hasSubmitListener
	* @private
	* @static
	*/
	YCM._hasSubmitListener = function() {
		if(YE){
			YE.addListener(
				document,
				'click',
				function(e){
					var obj = YE.getTarget(e),
						name = obj.nodeName.toLowerCase();

					if((name === 'input' || name === 'button') && (obj.type && obj.type.toLowerCase() == 'submit')){
						YCM._submitElementValue = encodeURIComponent(obj.name) + "=" + encodeURIComponent(obj.value);
					}
				});
			return true;
		}
		return false;
	}();

  /**
   * @description This method assembles the form label and value pairs and
   * constructs an encoded string.
   * asyncRequest() will automatically initialize the transaction with a
   * a HTTP header Content-Type of application/x-www-form-urlencoded.
   * @method setForm
   * @public
   * @static
   * @param {string || object} form id or name attribute, or form object.
   * @param {boolean} optional enable file upload.
   * @param {boolean} optional enable file upload over SSL in IE only.
   * @return {string} string of the HTML form field name and value pairs..
   */
	function _setForm(formId, isUpload, secureUri)
	{
		var oForm, oElement, oName, oValue, oDisabled,
			hasSubmit = false,
			data = [], item = 0,
			i,len,j,jlen,opt;

		this.resetFormState();

		if(typeof formId == 'string'){
			// Determine if the argument is a form id or a form name.
			// Note form name usage is deprecated by supported
			// here for legacy reasons.
			oForm = (document.getElementById(formId) || document.forms[formId]);
		}
		else if(typeof formId == 'object'){
			// Treat argument as an HTML form object.
			oForm = formId;
		}
		else{
			YAHOO.log('Unable to create form object ' + formId, 'warn', 'Connection');
			return;
		}

		// If the isUpload argument is true, setForm will call createFrame to initialize
		// an iframe as the form target.
		//
		// The argument secureURI is also required by IE in SSL environments
		// where the secureURI string is a fully qualified HTTP path, used to set the source
		// of the iframe, to a stub resource in the same domain.
		if(isUpload){

			// Create iframe in preparation for file upload.
			this.createFrame(secureUri?secureUri:null);

			// Set form reference and file upload properties to true.
			this._isFormSubmit = true;
			this._isFileUpload = true;
			this._formNode = oForm;

			return;
		}

		// Iterate over the form elements collection to construct the
		// label-value pairs.
		for (i=0,len=oForm.elements.length; i<len; ++i){
			oElement  = oForm.elements[i];
			oDisabled = oElement.disabled;
			oName     = oElement.name;

			// Do not submit fields that are disabled or
			// do not have a name attribute value.
			if(!oDisabled && oName)
			{
				oName  = encodeURIComponent(oName)+'=';
				oValue = encodeURIComponent(oElement.value);

				switch(oElement.type)
				{
					// Safari, Opera, FF all default opt.value from .text if
					// value attribute not specified in markup
					case 'select-one':
						if (oElement.selectedIndex > -1) {
							opt = oElement.options[oElement.selectedIndex];
							data[item++] = oName + encodeURIComponent(
								(opt.attributes.value && opt.attributes.value.specified) ? opt.value : opt.text);
						}
						break;
					case 'select-multiple':
						if (oElement.selectedIndex > -1) {
							for(j=oElement.selectedIndex, jlen=oElement.options.length; j<jlen; ++j){
								opt = oElement.options[j];
								if (opt.selected) {
									data[item++] = oName + encodeURIComponent(
										(opt.attributes.value && opt.attributes.value.specified) ? opt.value : opt.text);
								}
							}
						}
						break;
					case 'radio':
					case 'checkbox':
						if(oElement.checked){
							data[item++] = oName + oValue;
						}
						break;
					case 'file':
						// stub case as XMLHttpRequest will only send the file path as a string.
					case undefined:
						// stub case for fieldset element which returns undefined.
					case 'reset':
						// stub case for input type reset button.
					case 'button':
						// stub case for input type button elements.
						break;
					case 'submit':
						if(hasSubmit === false){
							if(this._hasSubmitListener && this._submitElementValue){
								data[item++] = this._submitElementValue;
							}
							hasSubmit = true;
						}
						break;
					default:
						data[item++] = oName + oValue;
				}
			}
		}

		this._isFormSubmit = true;
		this._sFormData = data.join('&');

		YAHOO.log('Form initialized for transaction. HTML form POST message is: ' + this._sFormData, 'info', 'Connection');

		this.initHeader('Content-Type', this._default_form_header);
		YAHOO.log('Initialize header Content-Type to application/x-www-form-urlencoded for setForm() transaction.', 'info', 'Connection');

		return this._sFormData;
	}

   /**
    * @description Resets HTML form properties when an HTML form or HTML form
    * with file upload transaction is sent.
    * @method resetFormState
    * @private
    * @static
    * @return {void}
    */
	function _resetFormState(){
		this._isFormSubmit = false;
		this._isFileUpload = false;
		this._formNode = null;
		this._sFormData = "";
	}


   /**
    * @description Creates an iframe to be used for form file uploads.  It is remove from the
    * document upon completion of the upload transaction.
    * @method createFrame
    * @private
    * @static
    * @param {string} optional qualified path of iframe resource for SSL in IE.
    * @return {void}
    */
	function _createFrame(secureUri){

		// IE does not allow the setting of id and name attributes as object
		// properties via createElement().  A different iframe creation
		// pattern is required for IE.
		var frameId = 'yuiIO' + this._transaction_id,
			ie9 = (dM === 9) ? true : false,
			io;

		if(YAHOO.env.ua.ie && !ie9){
			io = document.createElement('<iframe id="' + frameId + '" name="' + frameId + '" />');

			// IE will throw a security exception in an SSL environment if the
			// iframe source is undefined.
			if(typeof secureUri == 'boolean'){
				io.src = 'javascript:false';
			}
		}
		else{
			io = document.createElement('iframe');
			io.id = frameId;
			io.name = frameId;
		}

		io.style.position = 'absolute';
		io.style.top = '-1000px';
		io.style.left = '-1000px';

		document.body.appendChild(io);
		YAHOO.log('File upload iframe created. Id is:' + frameId, 'info', 'Connection');
	}

   /**
    * @description Parses the POST data and creates hidden form elements
    * for each key-value, and appends them to the HTML form object.
    * @method appendPostData
    * @private
    * @static
    * @param {string} postData The HTTP POST data
    * @return {array} formElements Collection of hidden fields.
    */
	function _appendPostData(postData){
		var formElements = [],
			postMessage = postData.split('&'),
			i, delimitPos;

		for(i=0; i < postMessage.length; i++){
			delimitPos = postMessage[i].indexOf('=');
			if(delimitPos != -1){
				formElements[i] = document.createElement('input');
				formElements[i].type = 'hidden';
				formElements[i].name = decodeURIComponent(postMessage[i].substring(0,delimitPos));
				formElements[i].value = decodeURIComponent(postMessage[i].substring(delimitPos+1));
				this._formNode.appendChild(formElements[i]);
			}
		}

		return formElements;
	}

   /**
    * @description Uploads HTML form, inclusive of files/attachments, using the
    * iframe created in createFrame to facilitate the transaction.
    * @method uploadFile
    * @private
    * @static
    * @param {int} id The transaction id.
    * @param {object} callback User-defined callback object.
    * @param {string} uri Fully qualified path of resource.
    * @param {string} postData POST data to be submitted in addition to HTML form.
    * @return {void}
    */
	function _uploadFile(o, callback, uri, postData){
		// Each iframe has an id prefix of "yuiIO" followed
		// by the unique transaction id.
		var frameId = 'yuiIO' + o.tId,
		    uploadEncoding = 'multipart/form-data',
		    io = document.getElementById(frameId),
		    ie8 = (dM >= 8) ? true : false,
		    oConn = this,
			args = (callback && callback.argument)?callback.argument:null,
            oElements,i,prop,obj, rawFormAttributes, uploadCallback;

		// Track original HTML form attribute values.
		rawFormAttributes = {
			action:this._formNode.getAttribute('action'),
			method:this._formNode.getAttribute('method'),
			target:this._formNode.getAttribute('target')
		};

		// Initialize the HTML form properties in case they are
		// not defined in the HTML form.
		this._formNode.setAttribute('action', uri);
		this._formNode.setAttribute('method', 'POST');
		this._formNode.setAttribute('target', frameId);

		if(YAHOO.env.ua.ie && !ie8){
			// IE does not respect property enctype for HTML forms.
			// Instead it uses the property - "encoding".
			this._formNode.setAttribute('encoding', uploadEncoding);
		}
		else{
			this._formNode.setAttribute('enctype', uploadEncoding);
		}

		if(postData){
			oElements = this.appendPostData(postData);
		}

		// Start file upload.
		this._formNode.submit();

		// Fire global custom event -- startEvent
		this.startEvent.fire(o, args);

		if(o.startEvent){
			// Fire transaction custom event -- startEvent
			o.startEvent.fire(o, args);
		}

		// Start polling if a callback is present and the timeout
		// property has been defined.
		if(callback && callback.timeout){
			this._timeOut[o.tId] = window.setTimeout(function(){ oConn.abort(o, callback, true); }, callback.timeout);
		}

		// Remove HTML elements created by appendPostData
		if(oElements && oElements.length > 0){
			for(i=0; i < oElements.length; i++){
				this._formNode.removeChild(oElements[i]);
			}
		}

		// Restore HTML form attributes to their original
		// values prior to file upload.
		for(prop in rawFormAttributes){
			if(YAHOO.lang.hasOwnProperty(rawFormAttributes, prop)){
				if(rawFormAttributes[prop]){
					this._formNode.setAttribute(prop, rawFormAttributes[prop]);
				}
				else{
					this._formNode.removeAttribute(prop);
				}
			}
		}

		// Reset HTML form state properties.
		this.resetFormState();

		// Create the upload callback handler that fires when the iframe
		// receives the load event.  Subsequently, the event handler is detached
		// and the iframe removed from the document.
		uploadCallback = function() {
			var body, pre, text;

			if(callback && callback.timeout){
				window.clearTimeout(oConn._timeOut[o.tId]);
				delete oConn._timeOut[o.tId];
			}

			// Fire global custom event -- completeEvent
			oConn.completeEvent.fire(o, args);

			if(o.completeEvent){
				// Fire transaction custom event -- completeEvent
				o.completeEvent.fire(o, args);
			}

			obj = {
			    tId : o.tId,
			    argument : args
            };

			try
			{
				body = io.contentWindow.document.getElementsByTagName('body')[0];
				pre = io.contentWindow.document.getElementsByTagName('pre')[0];

				if (body) {
					if (pre) {
						text = pre.textContent?pre.textContent:pre.innerText;
					}
					else {
						text = body.textContent?body.textContent:body.innerText;
					}
				}
				obj.responseText = text;
				// responseText and responseXML will be populated with the same data from the iframe.
				// Since the HTTP headers cannot be read from the iframe
				obj.responseXML = io.contentWindow.document.XMLDocument?io.contentWindow.document.XMLDocument:io.contentWindow.document;
			}
			catch(e){}

			if(callback && callback.upload){
				if(!callback.scope){
					callback.upload(obj);
					YAHOO.log('Upload callback.', 'info', 'Connection');
				}
				else{
					callback.upload.apply(callback.scope, [obj]);
					YAHOO.log('Upload callback with scope.', 'info', 'Connection');
				}
			}

			// Fire global custom event -- uploadEvent
			oConn.uploadEvent.fire(obj);

			if(o.uploadEvent){
				// Fire transaction custom event -- uploadEvent
				o.uploadEvent.fire(obj);
			}

			YE.removeListener(io, "load", uploadCallback);

			setTimeout(
				function(){
					document.body.removeChild(io);
					oConn.releaseObject(o);
					YAHOO.log('File upload iframe destroyed. Id is:' + frameId, 'info', 'Connection');
				}, 100);
		};

		// Bind the onload handler to the iframe to detect the file upload response.
		YE.addListener(io, "load", uploadCallback);
	}

	YCM.setForm = _setForm;
	YCM.resetFormState = _resetFormState;
	YCM.createFrame = _createFrame;
	YCM.appendPostData = _appendPostData;
	YCM.uploadFile = _uploadFile;
})();

YAHOO.register("connection", YAHOO.util.Connect, {version: "2.9.0", build: "2800"});
