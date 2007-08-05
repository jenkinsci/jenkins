/*
Copyright (c) 2007, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.net/yui/license.txt
version: 2.2.2
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
 */

/**
 * The Connection Manager singleton provides methods for creating and managing
 * asynchronous transactions.
 *
 * @class Connect
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
		'MSXML2.XMLHTTP.3.0',
		'MSXML2.XMLHTTP',
		'Microsoft.XMLHTTP'
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
  * @description Determines if a default header of
  * Content-Type of 'application/x-www-form-urlencoded'
  * will be added to client HTTP headers sent for POST
  * transactions.
  * @property _default_post_header
  * @private
  * @static
  * @type boolean
  */
    _default_post_header:'application/x-www-form-urlencoded; charset=UTF-8',

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
  * @description Determines if custom, default headers
  * are set for each transaction.
  * @property _has_default_header
  * @private
  * @static
  * @type boolean
  */
    _default_headers:{},

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
  * @description Property modified by setForm() to determine if a file(s)
  * upload is expected.
  * @property _isFileUpload
  * @private
  * @static
  * @type boolean
  */
    _isFileUpload:false,

 /**
  * @description Property modified by setForm() to set a reference to the HTML
  * form node if the desired action is file upload.
  * @property _formNode
  * @private
  * @static
  * @type object
  */
    _formNode:null,

 /**
  * @description Property modified by setForm() to set the HTML form data
  * for each transaction.
  * @property _sFormData
  * @private
  * @static
  * @type string
  */
    _sFormData:null,

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
   * @description Tracks the name-value pair of the "clicked" submit button if multiple submit
   * buttons are present in an HTML form; and, if YAHOO.util.Event is available.
   * @property _submitElementValue
   * @private
   * @static
   * @type string
   */
	 _submitElementValue:null,

  /**
   * @description Determines whether YAHOO.util.Event is available and returns true or false.
   * If true, an event listener is bound at the document level to trap click events that
   * resolve to a target type of "Submit".  This listener will enable setForm() to determine
   * the clicked "Submit" value in a multi-Submit button, HTML form.
   * @property _hasSubmitListener
   * @private
   * @static
   * @type boolean
   */
	 _hasSubmitListener:(function()
	 {
		if(YAHOO.util.Event){
			YAHOO.util.Event.addListener(
				document,
				'click',
				function(e){
					var obj = YAHOO.util.Event.getTarget(e);
					if(obj.type == 'submit'){
						YAHOO.util.Connect._submitElementValue = encodeURIComponent(obj.name) + "=" + encodeURIComponent(obj.value);
					}
				})
			return true;
	    }
	    return false;
	 })(),

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
   * @description Member to enable or disable the default POST header.
   * @method setDefaultPostHeader
   * @public
   * @static
   * @param {boolean} b Set and use default header - true or false .
   * @return void
   */
	setDefaultPostHeader:function(b)
	{
		this._use_default_post_header = b;
		YAHOO.log('Use default POST header set to  ' + b, 'info', 'Connection');
	},

  /**
   * @description Member to enable or disable the default POST header.
   * @method setDefaultXhrHeader
   * @public
   * @static
   * @param {boolean} b Set and use default header - true or false .
   * @return void
   */
	setDefaultXhrHeader:function(b)
	{
		this._use_default_xhr_header = b;
		YAHOO.log('Use default transaction header set to  ' + b, 'info', 'Connection');
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
		var obj,http;
		try
		{
			// Instantiates XMLHttpRequest in non-IE browsers and assigns to http.
			http = new XMLHttpRequest();
			//  Object literal with http and tId properties
			obj = { conn:http, tId:transactionId };
			YAHOO.log('XHR object created for transaction ' + transactionId, 'info', 'Connection');
		}
		catch(e)
		{
			for(var i=0; i<this._msxml_progid.length; ++i){
				try
				{
					// Instantiates XMLHttpRequest for IE and assign to http.
					http = new ActiveXObject(this._msxml_progid[i]);
					//  Object literal with conn and tId properties
					obj = { conn:http, tId:transactionId };
					YAHOO.log('ActiveX XHR object created for transaction ' + transactionId, 'info', 'Connection');
					break;
				}
				catch(e){}
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
	getConnectionObject:function()
	{
		var o;
		var tId = this._transaction_id;

		try
		{
			o = this.createXhrObject(tId);
			if(o){
				this._transaction_id++;
			}
		}
		catch(e){}
		finally
		{
			return o;
		}
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
		var o = this.getConnectionObject();

		if(!o){
			YAHOO.log('Unable to create connection object.', 'error', 'Connection');
			return null;
		}
		else{
			if(this._isFormSubmit){
				if(this._isFileUpload){
					this.uploadFile(o.tId, callback, uri, postData);
					this.releaseObject(o);

					return;
				}

				//If the specified HTTP method is GET, setForm() will return an
				//encoded string that is concatenated to the uri to
				//create a querystring.
				if(method.toUpperCase() == 'GET'){
					if(this._sFormData.length != 0){
						// If the URI already contains a querystring, append an ampersand
						// and then concatenate _sFormData to the URI.
						uri += ((uri.indexOf('?') == -1)?'?':'&') + this._sFormData;
					}
					else{
						uri += "?" + this._sFormData;
					}
				}
				else if(method.toUpperCase() == 'POST'){
					//If POST data exist in addition to the HTML form data,
					//it will be concatenated to the form data.
					postData = postData?this._sFormData + "&" + postData:this._sFormData;
				}
			}

			o.conn.open(method, uri, true);

			if(this._use_default_xhr_header){
				if(!this._default_headers['X-Requested-With']){
					this.initHeader('X-Requested-With', this._default_xhr_header, true);
					YAHOO.log('Initialize transaction header X-Request-Header to XMLHttpRequest.', 'info', 'Connection');
				}
			}
			if(this._isFormSubmit || (postData && this._use_default_post_header)){
				this.initHeader('Content-Type', this._default_post_header);
				YAHOO.log('Initialize header Content-Type to application/x-www-form-urlencoded for POST transaction.', 'info', 'Connection');
				if(this._isFormSubmit){
					this.resetFormState();
				}
			}

			if(this._has_default_headers || this._has_http_headers){
				this.setHeader(o);
			}

			this.handleReadyState(o, callback);
			o.conn.send(postData || null);

			return o;
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
		var oConn = this;

		if(callback && callback.timeout){
			this._timeOut[o.tId] = window.setTimeout(function(){ oConn.abort(o, callback, true); }, callback.timeout);
		}

		this._poll[o.tId] = window.setInterval(
			function(){
				if(o.conn && o.conn.readyState === 4){
					window.clearInterval(oConn._poll[o.tId]);
					delete oConn._poll[o.tId];

					if(callback && callback.timeout){
						delete oConn._timeOut[o.tId];
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
   * @param {object} callback The sser-defined callback object
   * @param {boolean} isAbort Determines if the transaction was aborted.
   * @return {void}
   */
    handleTransactionResponse:function(o, callback, isAbort)
    {
		// If no valid callback is provided, then do not process any callback handling.
		if(!callback){
			this.releaseObject(o);
			YAHOO.log('No callback object to process. Transaction complete.', 'warn', 'Connection');
			return;
		}

		var httpStatus, responseObject;

		try
		{
			if(o.conn.status !== undefined && o.conn.status !== 0){
				httpStatus = o.conn.status;
			}
			else{
				httpStatus = 13030;
			}
		}
		catch(e){

			 // 13030 is the custom code to indicate the condition -- in Mozilla/FF --
			 // when the o object's status and statusText properties are
			 // unavailable, and a query attempt throws an exception.
			httpStatus = 13030;
		}

		if(httpStatus >= 200 && httpStatus < 300 || httpStatus === 1223){
			responseObject = this.createResponseObject(o, callback.argument);
			if(callback.success){
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
					responseObject = this.createExceptionObject(o.tId, callback.argument, (isAbort?isAbort:false));
					if(callback.failure){
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
					responseObject = this.createResponseObject(o, callback.argument);
					if(callback.failure){
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
		var obj = {};
		var headerObj = {};

		try
		{
			var headerStr = o.conn.getAllResponseHeaders();
			var header = headerStr.split('\n');
			for(var i=0; i<header.length; i++){
				var delimitPos = header[i].indexOf(':');
				if(delimitPos != -1){
					headerObj[header[i].substring(0,delimitPos)] = header[i].substring(delimitPos+2);
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

		if(typeof callbackArg !== undefined){
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
		var COMM_CODE = 0;
		var COMM_ERROR = 'communication failure';
		var ABORT_CODE = -1;
		var ABORT_ERROR = 'transaction aborted';

		var obj = {};

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
	initHeader:function(label,value,isDefault)
	{
		var headerObj = (isDefault)?this._default_headers:this._http_headers;

		if(headerObj[label] === undefined){
			headerObj[label] = value;
		}
		else{
			// Concatenate multiple values, comma-delimited,
			// for the same header label,
			headerObj[label] =  value + "," + headerObj[label];
		}

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
		if(this._has_default_headers){
			for(var prop in this._default_headers){
				if(YAHOO.lang.hasOwnProperty(this._default_headers,prop)){
					o.conn.setRequestHeader(prop, this._default_headers[prop]);
					YAHOO.log('Default HTTP header ' + prop + ' set with value of ' + this._default_headers[prop], 'info', 'Connection');
				}
			}
		}

		if(this._has_http_headers){
			for(var prop in this._http_headers){
				if(YAHOO.lang.hasOwnProperty(this._http_headers,prop)){
					o.conn.setRequestHeader(prop, this._http_headers[prop]);
					YAHOO.log('HTTP header ' + prop + ' set with value of ' + this._http_headers[prop], 'info', 'Connection');
				}
			}
			delete this._http_headers;

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
		delete this._default_headers
		this._default_headers = {};
		this._has_default_headers = false;
	},

  /**
   * @description This method assembles the form label and value pairs and
   * constructs an encoded string.
   * asyncRequest() will automatically initialize the
   * transaction with a HTTP header Content-Type of
   * application/x-www-form-urlencoded.
   * @method setForm
   * @public
   * @static
   * @param {string || object} form id or name attribute, or form object.
   * @param {string} optional boolean to indicate SSL environment.
   * @param {string || boolean} optional qualified path of iframe resource for SSL in IE.
   * @return {string} string of the HTML form field name and value pairs..
   */
	setForm:function(formId, isUpload, secureUri)
	{
		this.resetFormState();
		var oForm;
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

		var oElement, oName, oValue, oDisabled;
		var hasSubmit = false;

		// Iterate over the form elements collection to construct the
		// label-value pairs.
		for (var i=0; i<oForm.elements.length; i++){
			oElement = oForm.elements[i];
			oDisabled = oForm.elements[i].disabled;
			oName = oForm.elements[i].name;
			oValue = oForm.elements[i].value;

			// Do not submit fields that are disabled or
			// do not have a name attribute value.
			if(!oDisabled && oName)
			{
				switch (oElement.type)
				{
					case 'select-one':
					case 'select-multiple':
						for(var j=0; j<oElement.options.length; j++){
							if(oElement.options[j].selected){
								if(window.ActiveXObject){
									this._sFormData += encodeURIComponent(oName) + '=' + encodeURIComponent(oElement.options[j].attributes['value'].specified?oElement.options[j].value:oElement.options[j].text) + '&';
								}
								else{
									this._sFormData += encodeURIComponent(oName) + '=' + encodeURIComponent(oElement.options[j].hasAttribute('value')?oElement.options[j].value:oElement.options[j].text) + '&';
								}

							}
						}
						break;
					case 'radio':
					case 'checkbox':
						if(oElement.checked){
							this._sFormData += encodeURIComponent(oName) + '=' + encodeURIComponent(oValue) + '&';
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
							if(this._hasSubmitListener){
								this._sFormData += this._submitElementValue + '&';
							}
							else{
								this._sFormData += encodeURIComponent(oName) + '=' + encodeURIComponent(oValue) + '&';
							}

							hasSubmit = true;
						}
						break;
					default:
						this._sFormData += encodeURIComponent(oName) + '=' + encodeURIComponent(oValue) + '&';
						break;
				}
			}
		}

		this._isFormSubmit = true;
		this._sFormData = this._sFormData.substr(0, this._sFormData.length - 1);

		YAHOO.log('Form initialized for transaction. HTML form POST message is: ' + this._sFormData, 'info', 'Connection');

		return this._sFormData;
	},

  /**
   * @description Resets HTML form properties when an HTML form or HTML form
   * with file upload transaction is sent.
   * @method resetFormState
   * @private
   * @static
   * @return {void}
   */
	resetFormState:function(){
		this._isFormSubmit = false;
		this._isFileUpload = false;
		this._formNode = null;
		this._sFormData = "";
	},

  /**
   * @description Creates an iframe to be used for form file uploads.  It is remove from the
   * document upon completion of the upload transaction.
   * @method createFrame
   * @private
   * @static
   * @param {string} optional qualified path of iframe resource for SSL in IE.
   * @return {void}
   */
	createFrame:function(secureUri){

		// IE does not allow the setting of id and name attributes as object
		// properties via createElement().  A different iframe creation
		// pattern is required for IE.
		var frameId = 'yuiIO' + this._transaction_id;
		if(window.ActiveXObject){
			var io = document.createElement('<iframe id="' + frameId + '" name="' + frameId + '" />');

			// IE will throw a security exception in an SSL environment if the
			// iframe source is undefined.
			if(typeof secureUri == 'boolean'){
				io.src = 'javascript:false';
			}
			else if(typeof secureURI == 'string'){
				// Deprecated
				io.src = secureUri;
			}
		}
		else{
			var io = document.createElement('iframe');
			io.id = frameId;
			io.name = frameId;
		}

		io.style.position = 'absolute';
		io.style.top = '-1000px';
		io.style.left = '-1000px';

		document.body.appendChild(io);
		YAHOO.log('File upload iframe created. Id is:' + frameId, 'info', 'Connection');
	},

  /**
   * @description Parses the POST data and creates hidden form elements
   * for each key-value, and appends them to the HTML form object.
   * @method appendPostData
   * @private
   * @static
   * @param {string} postData The HTTP POST data
   * @return {array} formElements Collection of hidden fields.
   */
	appendPostData:function(postData)
	{
		var formElements = [];
		var postMessage = postData.split('&');
		for(var i=0; i < postMessage.length; i++){
			var delimitPos = postMessage[i].indexOf('=');
			if(delimitPos != -1){
				formElements[i] = document.createElement('input');
				formElements[i].type = 'hidden';
				formElements[i].name = postMessage[i].substring(0,delimitPos);
				formElements[i].value = postMessage[i].substring(delimitPos+1);
				this._formNode.appendChild(formElements[i]);
			}
		}

		return formElements;
	},

  /**
   * @description Uploads HTML form, including files/attachments, to the
   * iframe created in createFrame.
   * @method uploadFile
   * @private
   * @static
   * @param {int} id The transaction id.
   * @param {object} callback - User-defined callback object.
   * @param {string} uri Fully qualified path of resource.
   * @return {void}
   */
	uploadFile:function(id, callback, uri, postData){

		// Each iframe has an id prefix of "yuiIO" followed
		// by the unique transaction id.
		var frameId = 'yuiIO' + id;
		var uploadEncoding = 'multipart/form-data';
		var io = document.getElementById(frameId);

		// Initialize the HTML form properties in case they are
		// not defined in the HTML form.
		this._formNode.setAttribute('action', uri);
		this._formNode.setAttribute('method', 'POST');
		this._formNode.setAttribute("target", frameId);

		if(this._formNode.encoding){
			// IE does not respect property enctype for HTML forms.
			// Instead it uses the property - "encoding".
			this._formNode.encoding = uploadEncoding;
		}
		else{
			this._formNode.enctype = uploadEncoding;
		}


		if(postData){
			var oElements = this.appendPostData(postData);
		}

		this._formNode.submit();

		if(oElements && oElements.length > 0){
			for(var i=0; i < oElements.length; i++){
				this._formNode.removeChild(oElements[i]);
			}
		}

		// Reset HTML form status properties.
		this.resetFormState();

		// Create the upload callback handler that fires when the iframe
		// receives the load event.  Subsequently, the event handler is detached
		// and the iframe removed from the document.

		var uploadCallback = function()
		{
			var obj = {};
			obj.tId = id;
			obj.argument = callback.argument;

			try
			{
				obj.responseText = io.contentWindow.document.body?io.contentWindow.document.body.innerHTML:null;
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

			if(YAHOO.util.Event){
				YAHOO.util.Event.removeListener(io, "load", uploadCallback);
			}
			else if(window.detachEvent){
				io.detachEvent('onload', uploadCallback);
			}
			else{
				io.removeEventListener('load', uploadCallback, false);
			}
			setTimeout(
				function(){
					document.body.removeChild(io);
					YAHOO.log('File upload iframe destroyed. Id is:' + frameId, 'info', 'Connection');
				}, 100);
		};


		// Bind the onload handler to the iframe to detect the file upload response.
		if(YAHOO.util.Event){
			YAHOO.util.Event.addListener(io, "load", uploadCallback);
		}
		else if(window.attachEvent){
			io.attachEvent('onload', uploadCallback);
		}
		else{
			io.addEventListener('load', uploadCallback, false);
		}
	},

  /**
   * @description Method to terminate a transaction, if it has not reached readyState 4.
   * @method abort
   * @public
   * @static
   * @param {object} o The connection object returned by asyncRequest.
   * @param {object} callback  User-defined callback object.
   * @param {string} isTimeout boolean to indicate if abort was a timeout.
   * @return {boolean}
   */
	abort:function(o, callback, isTimeout)
	{
		if(this.isCallInProgress(o)){
			o.conn.abort();
			window.clearInterval(this._poll[o.tId]);
			delete this._poll[o.tId];
			if(isTimeout){
				delete this._timeOut[o.tId];
			}

			this.handleTransactionResponse(o, callback, true);
			YAHOO.log('Transaction ' + o.tId + ' aborted.', 'info', 'Connection');

			return true;
		}
		else{
			YAHOO.log('Transaction ' + o.tId + ' abort call failed.', 'warn', 'Connection');
			return false;
		}
	},

  /**
   * Public method to check if the transaction is still being processed.
   *
   * @method isCallInProgress
   * @public
   * @static
   * @param {object} o The connection object returned by asyncRequest
   * @return {boolean}
   */
	isCallInProgress:function(o)
	{
		// if the XHR object assigned to the transaction has not been dereferenced,
		// then check its readyState status.  Otherwise, return false.
		if(o.conn){
			return o.conn.readyState !== 4 && o.conn.readyState !== 0;
		}
		else{
			//The XHR object has been destroyed.
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
		//dereference the XHR instance.
		o.conn = null;
		YAHOO.log('Connection object for transaction ' + o.tId + ' destroyed.', 'info', 'Connection');
		//dereference the connection object.
		o = null;
	}
};

YAHOO.register("connection", YAHOO.util.Connect, {version: "2.2.2", build: "204"});
