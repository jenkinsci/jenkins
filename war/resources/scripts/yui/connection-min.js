/*
Copyright (c) 2007, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.net/yui/license.txt
version: 2.2.2
*/

YAHOO.util.Connect={_msxml_progid:['MSXML2.XMLHTTP.3.0','MSXML2.XMLHTTP','Microsoft.XMLHTTP'],_http_headers:{},_has_http_headers:false,_use_default_post_header:true,_default_post_header:'application/x-www-form-urlencoded; charset=UTF-8',_use_default_xhr_header:true,_default_xhr_header:'XMLHttpRequest',_has_default_headers:true,_default_headers:{},_isFormSubmit:false,_isFileUpload:false,_formNode:null,_sFormData:null,_poll:{},_timeOut:{},_polling_interval:50,_transaction_id:0,_submitElementValue:null,_hasSubmitListener:(function()
{if(YAHOO.util.Event){YAHOO.util.Event.addListener(document,'click',function(e){var obj=YAHOO.util.Event.getTarget(e);if(obj.type=='submit'){YAHOO.util.Connect._submitElementValue=encodeURIComponent(obj.name)+"="+encodeURIComponent(obj.value);}})
return true;}
return false;})(),setProgId:function(id)
{this._msxml_progid.unshift(id);},setDefaultPostHeader:function(b)
{this._use_default_post_header=b;},setDefaultXhrHeader:function(b)
{this._use_default_xhr_header=b;},setPollingInterval:function(i)
{if(typeof i=='number'&&isFinite(i)){this._polling_interval=i;}},createXhrObject:function(transactionId)
{var obj,http;try
{http=new XMLHttpRequest();obj={conn:http,tId:transactionId};}
catch(e)
{for(var i=0;i<this._msxml_progid.length;++i){try
{http=new ActiveXObject(this._msxml_progid[i]);obj={conn:http,tId:transactionId};break;}
catch(e){}}}
finally
{return obj;}},getConnectionObject:function()
{var o;var tId=this._transaction_id;try
{o=this.createXhrObject(tId);if(o){this._transaction_id++;}}
catch(e){}
finally
{return o;}},asyncRequest:function(method,uri,callback,postData)
{var o=this.getConnectionObject();if(!o){return null;}
else{if(this._isFormSubmit){if(this._isFileUpload){this.uploadFile(o.tId,callback,uri,postData);this.releaseObject(o);return;}
if(method.toUpperCase()=='GET'){if(this._sFormData.length!=0){uri+=((uri.indexOf('?')==-1)?'?':'&')+this._sFormData;}
else{uri+="?"+this._sFormData;}}
else if(method.toUpperCase()=='POST'){postData=postData?this._sFormData+"&"+postData:this._sFormData;}}
o.conn.open(method,uri,true);if(this._use_default_xhr_header){if(!this._default_headers['X-Requested-With']){this.initHeader('X-Requested-With',this._default_xhr_header,true);}}
if(this._isFormSubmit||(postData&&this._use_default_post_header)){this.initHeader('Content-Type',this._default_post_header);if(this._isFormSubmit){this.resetFormState();}}
if(this._has_default_headers||this._has_http_headers){this.setHeader(o);}
this.handleReadyState(o,callback);o.conn.send(postData||null);return o;}},handleReadyState:function(o,callback)
{var oConn=this;if(callback&&callback.timeout){this._timeOut[o.tId]=window.setTimeout(function(){oConn.abort(o,callback,true);},callback.timeout);}
this._poll[o.tId]=window.setInterval(function(){if(o.conn&&o.conn.readyState===4){window.clearInterval(oConn._poll[o.tId]);delete oConn._poll[o.tId];if(callback&&callback.timeout){delete oConn._timeOut[o.tId];}
oConn.handleTransactionResponse(o,callback);}},this._polling_interval);},handleTransactionResponse:function(o,callback,isAbort)
{if(!callback){this.releaseObject(o);return;}
var httpStatus,responseObject;try
{if(o.conn.status!==undefined&&o.conn.status!==0){httpStatus=o.conn.status;}
else{httpStatus=13030;}}
catch(e){httpStatus=13030;}
if(httpStatus>=200&&httpStatus<300||httpStatus===1223){responseObject=this.createResponseObject(o,callback.argument);if(callback.success){if(!callback.scope){callback.success(responseObject);}
else{callback.success.apply(callback.scope,[responseObject]);}}}
else{switch(httpStatus){case 12002:case 12029:case 12030:case 12031:case 12152:case 13030:responseObject=this.createExceptionObject(o.tId,callback.argument,(isAbort?isAbort:false));if(callback.failure){if(!callback.scope){callback.failure(responseObject);}
else{callback.failure.apply(callback.scope,[responseObject]);}}
break;default:responseObject=this.createResponseObject(o,callback.argument);if(callback.failure){if(!callback.scope){callback.failure(responseObject);}
else{callback.failure.apply(callback.scope,[responseObject]);}}}}
this.releaseObject(o);responseObject=null;},createResponseObject:function(o,callbackArg)
{var obj={};var headerObj={};try
{var headerStr=o.conn.getAllResponseHeaders();var header=headerStr.split('\n');for(var i=0;i<header.length;i++){var delimitPos=header[i].indexOf(':');if(delimitPos!=-1){headerObj[header[i].substring(0,delimitPos)]=header[i].substring(delimitPos+2);}}}
catch(e){}
obj.tId=o.tId;obj.status=(o.conn.status==1223)?204:o.conn.status;obj.statusText=(o.conn.status==1223)?"No Content":o.conn.statusText;obj.getResponseHeader=headerObj;obj.getAllResponseHeaders=headerStr;obj.responseText=o.conn.responseText;obj.responseXML=o.conn.responseXML;if(typeof callbackArg!==undefined){obj.argument=callbackArg;}
return obj;},createExceptionObject:function(tId,callbackArg,isAbort)
{var COMM_CODE=0;var COMM_ERROR='communication failure';var ABORT_CODE=-1;var ABORT_ERROR='transaction aborted';var obj={};obj.tId=tId;if(isAbort){obj.status=ABORT_CODE;obj.statusText=ABORT_ERROR;}
else{obj.status=COMM_CODE;obj.statusText=COMM_ERROR;}
if(callbackArg){obj.argument=callbackArg;}
return obj;},initHeader:function(label,value,isDefault)
{var headerObj=(isDefault)?this._default_headers:this._http_headers;if(headerObj[label]===undefined){headerObj[label]=value;}
else{headerObj[label]=value+","+headerObj[label];}
if(isDefault){this._has_default_headers=true;}
else{this._has_http_headers=true;}},setHeader:function(o)
{if(this._has_default_headers){for(var prop in this._default_headers){if(YAHOO.lang.hasOwnProperty(this._default_headers,prop)){o.conn.setRequestHeader(prop,this._default_headers[prop]);}}}
if(this._has_http_headers){for(var prop in this._http_headers){if(YAHOO.lang.hasOwnProperty(this._http_headers,prop)){o.conn.setRequestHeader(prop,this._http_headers[prop]);}}
delete this._http_headers;this._http_headers={};this._has_http_headers=false;}},resetDefaultHeaders:function(){delete this._default_headers
this._default_headers={};this._has_default_headers=false;},setForm:function(formId,isUpload,secureUri)
{this.resetFormState();var oForm;if(typeof formId=='string'){oForm=(document.getElementById(formId)||document.forms[formId]);}
else if(typeof formId=='object'){oForm=formId;}
else{return;}
if(isUpload){this.createFrame(secureUri?secureUri:null);this._isFormSubmit=true;this._isFileUpload=true;this._formNode=oForm;return;}
var oElement,oName,oValue,oDisabled;var hasSubmit=false;for(var i=0;i<oForm.elements.length;i++){oElement=oForm.elements[i];oDisabled=oForm.elements[i].disabled;oName=oForm.elements[i].name;oValue=oForm.elements[i].value;if(!oDisabled&&oName)
{switch(oElement.type)
{case'select-one':case'select-multiple':for(var j=0;j<oElement.options.length;j++){if(oElement.options[j].selected){if(window.ActiveXObject){this._sFormData+=encodeURIComponent(oName)+'='+encodeURIComponent(oElement.options[j].attributes['value'].specified?oElement.options[j].value:oElement.options[j].text)+'&';}
else{this._sFormData+=encodeURIComponent(oName)+'='+encodeURIComponent(oElement.options[j].hasAttribute('value')?oElement.options[j].value:oElement.options[j].text)+'&';}}}
break;case'radio':case'checkbox':if(oElement.checked){this._sFormData+=encodeURIComponent(oName)+'='+encodeURIComponent(oValue)+'&';}
break;case'file':case undefined:case'reset':case'button':break;case'submit':if(hasSubmit===false){if(this._hasSubmitListener){this._sFormData+=this._submitElementValue+'&';}
else{this._sFormData+=encodeURIComponent(oName)+'='+encodeURIComponent(oValue)+'&';}
hasSubmit=true;}
break;default:this._sFormData+=encodeURIComponent(oName)+'='+encodeURIComponent(oValue)+'&';break;}}}
this._isFormSubmit=true;this._sFormData=this._sFormData.substr(0,this._sFormData.length-1);return this._sFormData;},resetFormState:function(){this._isFormSubmit=false;this._isFileUpload=false;this._formNode=null;this._sFormData="";},createFrame:function(secureUri){var frameId='yuiIO'+this._transaction_id;if(window.ActiveXObject){var io=document.createElement('<iframe id="'+frameId+'" name="'+frameId+'" />');if(typeof secureUri=='boolean'){io.src='javascript:false';}
else if(typeof secureURI=='string'){io.src=secureUri;}}
else{var io=document.createElement('iframe');io.id=frameId;io.name=frameId;}
io.style.position='absolute';io.style.top='-1000px';io.style.left='-1000px';document.body.appendChild(io);},appendPostData:function(postData)
{var formElements=[];var postMessage=postData.split('&');for(var i=0;i<postMessage.length;i++){var delimitPos=postMessage[i].indexOf('=');if(delimitPos!=-1){formElements[i]=document.createElement('input');formElements[i].type='hidden';formElements[i].name=postMessage[i].substring(0,delimitPos);formElements[i].value=postMessage[i].substring(delimitPos+1);this._formNode.appendChild(formElements[i]);}}
return formElements;},uploadFile:function(id,callback,uri,postData){var frameId='yuiIO'+id;var uploadEncoding='multipart/form-data';var io=document.getElementById(frameId);this._formNode.setAttribute('action',uri);this._formNode.setAttribute('method','POST');this._formNode.setAttribute("target",frameId);if(this._formNode.encoding){this._formNode.encoding=uploadEncoding;}
else{this._formNode.enctype=uploadEncoding;}
if(postData){var oElements=this.appendPostData(postData);}
this._formNode.submit();if(oElements&&oElements.length>0){for(var i=0;i<oElements.length;i++){this._formNode.removeChild(oElements[i]);}}
this.resetFormState();var uploadCallback=function()
{var obj={};obj.tId=id;obj.argument=callback.argument;try
{obj.responseText=io.contentWindow.document.body?io.contentWindow.document.body.innerHTML:null;obj.responseXML=io.contentWindow.document.XMLDocument?io.contentWindow.document.XMLDocument:io.contentWindow.document;}
catch(e){}
if(callback&&callback.upload){if(!callback.scope){callback.upload(obj);}
else{callback.upload.apply(callback.scope,[obj]);}}
if(YAHOO.util.Event){YAHOO.util.Event.removeListener(io,"load",uploadCallback);}
else if(window.detachEvent){io.detachEvent('onload',uploadCallback);}
else{io.removeEventListener('load',uploadCallback,false);}
setTimeout(function(){document.body.removeChild(io);},100);};if(YAHOO.util.Event){YAHOO.util.Event.addListener(io,"load",uploadCallback);}
else if(window.attachEvent){io.attachEvent('onload',uploadCallback);}
else{io.addEventListener('load',uploadCallback,false);}},abort:function(o,callback,isTimeout)
{if(this.isCallInProgress(o)){o.conn.abort();window.clearInterval(this._poll[o.tId]);delete this._poll[o.tId];if(isTimeout){delete this._timeOut[o.tId];}
this.handleTransactionResponse(o,callback,true);return true;}
else{return false;}},isCallInProgress:function(o)
{if(o.conn){return o.conn.readyState!==4&&o.conn.readyState!==0;}
else{return false;}},releaseObject:function(o)
{o.conn=null;o=null;}};YAHOO.register("connection",YAHOO.util.Connect,{version:"2.2.2",build:"204"});