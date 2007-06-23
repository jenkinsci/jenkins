/*
Copyright (c) 2007, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.net/yui/license.txt
version: 2.2.2
*/

YAHOO.util.CustomEvent=function(type,oScope,silent,signature){this.type=type;this.scope=oScope||window;this.silent=silent;this.signature=signature||YAHOO.util.CustomEvent.LIST;this.subscribers=[];if(!this.silent){}
var onsubscribeType="_YUICEOnSubscribe";if(type!==onsubscribeType){this.subscribeEvent=new YAHOO.util.CustomEvent(onsubscribeType,this,true);}};YAHOO.util.CustomEvent.LIST=0;YAHOO.util.CustomEvent.FLAT=1;YAHOO.util.CustomEvent.prototype={subscribe:function(fn,obj,override){if(!fn){throw new Error("Invalid callback for subscriber to '"+this.type+"'");}
if(this.subscribeEvent){this.subscribeEvent.fire(fn,obj,override);}
this.subscribers.push(new YAHOO.util.Subscriber(fn,obj,override));},unsubscribe:function(fn,obj){if(!fn){return this.unsubscribeAll();}
var found=false;for(var i=0,len=this.subscribers.length;i<len;++i){var s=this.subscribers[i];if(s&&s.contains(fn,obj)){this._delete(i);found=true;}}
return found;},fire:function(){var len=this.subscribers.length;if(!len&&this.silent){return true;}
var args=[],ret=true,i;for(i=0;i<arguments.length;++i){args.push(arguments[i]);}
var argslength=args.length;if(!this.silent){}
for(i=0;i<len;++i){var s=this.subscribers[i];if(s){if(!this.silent){}
var scope=s.getScope(this.scope);if(this.signature==YAHOO.util.CustomEvent.FLAT){var param=null;if(args.length>0){param=args[0];}
ret=s.fn.call(scope,param,s.obj);}else{ret=s.fn.call(scope,this.type,args,s.obj);}
if(false===ret){if(!this.silent){}
return false;}}}
return true;},unsubscribeAll:function(){for(var i=0,len=this.subscribers.length;i<len;++i){this._delete(len-1-i);}
return i;},_delete:function(index){var s=this.subscribers[index];if(s){delete s.fn;delete s.obj;}
this.subscribers.splice(index,1);},toString:function(){return"CustomEvent: "+"'"+this.type+"', "+"scope: "+this.scope;}};YAHOO.util.Subscriber=function(fn,obj,override){this.fn=fn;this.obj=obj||null;this.override=override;};YAHOO.util.Subscriber.prototype.getScope=function(defaultScope){if(this.override){if(this.override===true){return this.obj;}else{return this.override;}}
return defaultScope;};YAHOO.util.Subscriber.prototype.contains=function(fn,obj){if(obj){return(this.fn==fn&&this.obj==obj);}else{return(this.fn==fn);}};YAHOO.util.Subscriber.prototype.toString=function(){return"Subscriber { obj: "+(this.obj||"")+", override: "+(this.override||"no")+" }";};if(!YAHOO.util.Event){YAHOO.util.Event=function(){var loadComplete=false;var DOMReady=false;var listeners=[];var unloadListeners=[];var legacyEvents=[];var legacyHandlers=[];var retryCount=0;var onAvailStack=[];var legacyMap=[];var counter=0;var lastError=null;return{POLL_RETRYS:200,POLL_INTERVAL:10,EL:0,TYPE:1,FN:2,WFN:3,OBJ:3,ADJ_SCOPE:4,isSafari:(/KHTML/gi).test(navigator.userAgent),webkit:function(){var v=navigator.userAgent.match(/AppleWebKit\/([^ ]*)/);if(v&&v[1]){return v[1];}
return null;}(),isIE:(!this.webkit&&!navigator.userAgent.match(/opera/gi)&&navigator.userAgent.match(/msie/gi)),_interval:null,startInterval:function(){if(!this._interval){var self=this;var callback=function(){self._tryPreloadAttach();};this._interval=setInterval(callback,this.POLL_INTERVAL);}},onAvailable:function(p_id,p_fn,p_obj,p_override){onAvailStack.push({id:p_id,fn:p_fn,obj:p_obj,override:p_override,checkReady:false});retryCount=this.POLL_RETRYS;this.startInterval();},onDOMReady:function(p_fn,p_obj,p_override){this.DOMReadyEvent.subscribe(p_fn,p_obj,p_override);},onContentReady:function(p_id,p_fn,p_obj,p_override){onAvailStack.push({id:p_id,fn:p_fn,obj:p_obj,override:p_override,checkReady:true});retryCount=this.POLL_RETRYS;this.startInterval();},addListener:function(el,sType,fn,obj,override){if(!fn||!fn.call){return false;}
if(this._isValidCollection(el)){var ok=true;for(var i=0,len=el.length;i<len;++i){ok=this.on(el[i],sType,fn,obj,override)&&ok;}
return ok;}else if(typeof el=="string"){var oEl=this.getEl(el);if(oEl){el=oEl;}else{this.onAvailable(el,function(){YAHOO.util.Event.on(el,sType,fn,obj,override);});return true;}}
if(!el){return false;}
if("unload"==sType&&obj!==this){unloadListeners[unloadListeners.length]=[el,sType,fn,obj,override];return true;}
var scope=el;if(override){if(override===true){scope=obj;}else{scope=override;}}
var wrappedFn=function(e){return fn.call(scope,YAHOO.util.Event.getEvent(e),obj);};var li=[el,sType,fn,wrappedFn,scope];var index=listeners.length;listeners[index]=li;if(this.useLegacyEvent(el,sType)){var legacyIndex=this.getLegacyIndex(el,sType);if(legacyIndex==-1||el!=legacyEvents[legacyIndex][0]){legacyIndex=legacyEvents.length;legacyMap[el.id+sType]=legacyIndex;legacyEvents[legacyIndex]=[el,sType,el["on"+sType]];legacyHandlers[legacyIndex]=[];el["on"+sType]=function(e){YAHOO.util.Event.fireLegacyEvent(YAHOO.util.Event.getEvent(e),legacyIndex);};}
legacyHandlers[legacyIndex].push(li);}else{try{this._simpleAdd(el,sType,wrappedFn,false);}catch(ex){this.lastError=ex;this.removeListener(el,sType,fn);return false;}}
return true;},fireLegacyEvent:function(e,legacyIndex){var ok=true,le,lh,li,scope,ret;lh=legacyHandlers[legacyIndex];for(var i=0,len=lh.length;i<len;++i){li=lh[i];if(li&&li[this.WFN]){scope=li[this.ADJ_SCOPE];ret=li[this.WFN].call(scope,e);ok=(ok&&ret);}}
le=legacyEvents[legacyIndex];if(le&&le[2]){le[2](e);}
return ok;},getLegacyIndex:function(el,sType){var key=this.generateId(el)+sType;if(typeof legacyMap[key]=="undefined"){return-1;}else{return legacyMap[key];}},useLegacyEvent:function(el,sType){if(this.webkit&&("click"==sType||"dblclick"==sType)){var v=parseInt(this.webkit,10);if(!isNaN(v)&&v<418){return true;}}
return false;},removeListener:function(el,sType,fn){var i,len;if(typeof el=="string"){el=this.getEl(el);}else if(this._isValidCollection(el)){var ok=true;for(i=0,len=el.length;i<len;++i){ok=(this.removeListener(el[i],sType,fn)&&ok);}
return ok;}
if(!fn||!fn.call){return this.purgeElement(el,false,sType);}
if("unload"==sType){for(i=0,len=unloadListeners.length;i<len;i++){var li=unloadListeners[i];if(li&&li[0]==el&&li[1]==sType&&li[2]==fn){unloadListeners.splice(i,1);return true;}}
return false;}
var cacheItem=null;var index=arguments[3];if("undefined"==typeof index){index=this._getCacheIndex(el,sType,fn);}
if(index>=0){cacheItem=listeners[index];}
if(!el||!cacheItem){return false;}
if(this.useLegacyEvent(el,sType)){var legacyIndex=this.getLegacyIndex(el,sType);var llist=legacyHandlers[legacyIndex];if(llist){for(i=0,len=llist.length;i<len;++i){li=llist[i];if(li&&li[this.EL]==el&&li[this.TYPE]==sType&&li[this.FN]==fn){llist.splice(i,1);break;}}}}else{try{this._simpleRemove(el,sType,cacheItem[this.WFN],false);}catch(ex){this.lastError=ex;return false;}}
delete listeners[index][this.WFN];delete listeners[index][this.FN];listeners.splice(index,1);return true;},getTarget:function(ev,resolveTextNode){var t=ev.target||ev.srcElement;return this.resolveTextNode(t);},resolveTextNode:function(node){if(node&&3==node.nodeType){return node.parentNode;}else{return node;}},getPageX:function(ev){var x=ev.pageX;if(!x&&0!==x){x=ev.clientX||0;if(this.isIE){x+=this._getScrollLeft();}}
return x;},getPageY:function(ev){var y=ev.pageY;if(!y&&0!==y){y=ev.clientY||0;if(this.isIE){y+=this._getScrollTop();}}
return y;},getXY:function(ev){return[this.getPageX(ev),this.getPageY(ev)];},getRelatedTarget:function(ev){var t=ev.relatedTarget;if(!t){if(ev.type=="mouseout"){t=ev.toElement;}else if(ev.type=="mouseover"){t=ev.fromElement;}}
return this.resolveTextNode(t);},getTime:function(ev){if(!ev.time){var t=new Date().getTime();try{ev.time=t;}catch(ex){this.lastError=ex;return t;}}
return ev.time;},stopEvent:function(ev){this.stopPropagation(ev);this.preventDefault(ev);},stopPropagation:function(ev){if(ev.stopPropagation){ev.stopPropagation();}else{ev.cancelBubble=true;}},preventDefault:function(ev){if(ev.preventDefault){ev.preventDefault();}else{ev.returnValue=false;}},getEvent:function(e){var ev=e||window.event;if(!ev){var c=this.getEvent.caller;while(c){ev=c.arguments[0];if(ev&&Event==ev.constructor){break;}
c=c.caller;}}
return ev;},getCharCode:function(ev){return ev.charCode||ev.keyCode||0;},_getCacheIndex:function(el,sType,fn){for(var i=0,len=listeners.length;i<len;++i){var li=listeners[i];if(li&&li[this.FN]==fn&&li[this.EL]==el&&li[this.TYPE]==sType){return i;}}
return-1;},generateId:function(el){var id=el.id;if(!id){id="yuievtautoid-"+counter;++counter;el.id=id;}
return id;},_isValidCollection:function(o){return(o&&o.length&&typeof o!="string"&&!o.tagName&&!o.alert&&typeof o[0]!="undefined");},elCache:{},getEl:function(id){return document.getElementById(id);},clearCache:function(){},DOMReadyEvent:new YAHOO.util.CustomEvent("DOMReady",this),_load:function(e){if(!loadComplete){loadComplete=true;var EU=YAHOO.util.Event;EU._ready();if(this.isIE){EU._simpleRemove(window,"load",EU._load);}}},_ready:function(e){if(!DOMReady){DOMReady=true;var EU=YAHOO.util.Event;EU.DOMReadyEvent.fire();EU._simpleRemove(document,"DOMContentLoaded",EU._ready);}},_tryPreloadAttach:function(){if(this.locked){return false;}
if(this.isIE&&!DOMReady){return false;}
this.locked=true;var tryAgain=!loadComplete;if(!tryAgain){tryAgain=(retryCount>0);}
var notAvail=[];var executeItem=function(el,item){var scope=el;if(item.override){if(item.override===true){scope=item.obj;}else{scope=item.override;}}
item.fn.call(scope,item.obj);};var i,len,item,el;for(i=0,len=onAvailStack.length;i<len;++i){item=onAvailStack[i];if(item&&!item.checkReady){el=this.getEl(item.id);if(el){executeItem(el,item);onAvailStack[i]=null;}else{notAvail.push(item);}}}
for(i=0,len=onAvailStack.length;i<len;++i){item=onAvailStack[i];if(item&&item.checkReady){el=this.getEl(item.id);if(el){if(loadComplete||el.nextSibling){executeItem(el,item);onAvailStack[i]=null;}}else{notAvail.push(item);}}}
retryCount=(notAvail.length===0)?0:retryCount-1;if(tryAgain){this.startInterval();}else{clearInterval(this._interval);this._interval=null;}
this.locked=false;return true;},purgeElement:function(el,recurse,sType){var elListeners=this.getListeners(el,sType);if(elListeners){for(var i=0,len=elListeners.length;i<len;++i){var l=elListeners[i];this.removeListener(el,l.type,l.fn);}}
if(recurse&&el&&el.childNodes){for(i=0,len=el.childNodes.length;i<len;++i){this.purgeElement(el.childNodes[i],recurse,sType);}}},getListeners:function(el,sType){var results=[],searchLists;if(!sType){searchLists=[listeners,unloadListeners];}else if(sType=="unload"){searchLists=[unloadListeners];}else{searchLists=[listeners];}
for(var j=0;j<searchLists.length;++j){var searchList=searchLists[j];if(searchList&&searchList.length>0){for(var i=0,len=searchList.length;i<len;++i){var l=searchList[i];if(l&&l[this.EL]===el&&(!sType||sType===l[this.TYPE])){results.push({type:l[this.TYPE],fn:l[this.FN],obj:l[this.OBJ],adjust:l[this.ADJ_SCOPE],index:i});}}}}
return(results.length)?results:null;},_unload:function(e){var EU=YAHOO.util.Event,i,j,l,len,index;for(i=0,len=unloadListeners.length;i<len;++i){l=unloadListeners[i];if(l){var scope=window;if(l[EU.ADJ_SCOPE]){if(l[EU.ADJ_SCOPE]===true){scope=l[EU.OBJ];}else{scope=l[EU.ADJ_SCOPE];}}
l[EU.FN].call(scope,EU.getEvent(e),l[EU.OBJ]);unloadListeners[i]=null;l=null;scope=null;}}
unloadListeners=null;if(listeners&&listeners.length>0){j=listeners.length;while(j){index=j-1;l=listeners[index];if(l){EU.removeListener(l[EU.EL],l[EU.TYPE],l[EU.FN],index);}
j=j-1;}
l=null;EU.clearCache();}
for(i=0,len=legacyEvents.length;i<len;++i){legacyEvents[i][0]=null;legacyEvents[i]=null;}
legacyEvents=null;EU._simpleRemove(window,"unload",EU._unload);},_getScrollLeft:function(){return this._getScroll()[1];},_getScrollTop:function(){return this._getScroll()[0];},_getScroll:function(){var dd=document.documentElement,db=document.body;if(dd&&(dd.scrollTop||dd.scrollLeft)){return[dd.scrollTop,dd.scrollLeft];}else if(db){return[db.scrollTop,db.scrollLeft];}else{return[0,0];}},regCE:function(){},_simpleAdd:function(){if(window.addEventListener){return function(el,sType,fn,capture){el.addEventListener(sType,fn,(capture));};}else if(window.attachEvent){return function(el,sType,fn,capture){el.attachEvent("on"+sType,fn);};}else{return function(){};}}(),_simpleRemove:function(){if(window.removeEventListener){return function(el,sType,fn,capture){el.removeEventListener(sType,fn,(capture));};}else if(window.detachEvent){return function(el,sType,fn){el.detachEvent("on"+sType,fn);};}else{return function(){};}}()};}();(function(){var EU=YAHOO.util.Event;EU.on=EU.addListener;if(EU.isIE){document.write('<scr'+'ipt id="_yui_eu_dr" defer="true" src="//:"></script>');var el=document.getElementById("_yui_eu_dr");el.onreadystatechange=function(){if("complete"==this.readyState){this.parentNode.removeChild(this);YAHOO.util.Event._ready();}};el=null;YAHOO.util.Event.onDOMReady(YAHOO.util.Event._tryPreloadAttach,YAHOO.util.Event,true);}else if(EU.webkit){EU._drwatch=setInterval(function(){var rs=document.readyState;if("loaded"==rs||"complete"==rs){clearInterval(EU._drwatch);EU._drwatch=null;EU._ready();}},EU.POLL_INTERVAL);}else{EU._simpleAdd(document,"DOMContentLoaded",EU._ready);}
EU._simpleAdd(window,"load",EU._load);EU._simpleAdd(window,"unload",EU._unload);EU._tryPreloadAttach();})();}
YAHOO.util.EventProvider=function(){};YAHOO.util.EventProvider.prototype={__yui_events:null,__yui_subscribers:null,subscribe:function(p_type,p_fn,p_obj,p_override){this.__yui_events=this.__yui_events||{};var ce=this.__yui_events[p_type];if(ce){ce.subscribe(p_fn,p_obj,p_override);}else{this.__yui_subscribers=this.__yui_subscribers||{};var subs=this.__yui_subscribers;if(!subs[p_type]){subs[p_type]=[];}
subs[p_type].push({fn:p_fn,obj:p_obj,override:p_override});}},unsubscribe:function(p_type,p_fn,p_obj){this.__yui_events=this.__yui_events||{};var ce=this.__yui_events[p_type];if(ce){return ce.unsubscribe(p_fn,p_obj);}else{return false;}},unsubscribeAll:function(p_type){return this.unsubscribe(p_type);},createEvent:function(p_type,p_config){this.__yui_events=this.__yui_events||{};var opts=p_config||{};var events=this.__yui_events;if(events[p_type]){}else{var scope=opts.scope||this;var silent=opts.silent||null;var ce=new YAHOO.util.CustomEvent(p_type,scope,silent,YAHOO.util.CustomEvent.FLAT);events[p_type]=ce;if(opts.onSubscribeCallback){ce.subscribeEvent.subscribe(opts.onSubscribeCallback);}
this.__yui_subscribers=this.__yui_subscribers||{};var qs=this.__yui_subscribers[p_type];if(qs){for(var i=0;i<qs.length;++i){ce.subscribe(qs[i].fn,qs[i].obj,qs[i].override);}}}
return events[p_type];},fireEvent:function(p_type,arg1,arg2,etc){this.__yui_events=this.__yui_events||{};var ce=this.__yui_events[p_type];if(ce){var args=[];for(var i=1;i<arguments.length;++i){args.push(arguments[i]);}
return ce.fire.apply(ce,args);}else{return null;}},hasEvent:function(type){if(this.__yui_events){if(this.__yui_events[type]){return true;}}
return false;}};YAHOO.util.KeyListener=function(attachTo,keyData,handler,event){if(!attachTo){}else if(!keyData){}else if(!handler){}
if(!event){event=YAHOO.util.KeyListener.KEYDOWN;}
var keyEvent=new YAHOO.util.CustomEvent("keyPressed");this.enabledEvent=new YAHOO.util.CustomEvent("enabled");this.disabledEvent=new YAHOO.util.CustomEvent("disabled");if(typeof attachTo=='string'){attachTo=document.getElementById(attachTo);}
if(typeof handler=='function'){keyEvent.subscribe(handler);}else{keyEvent.subscribe(handler.fn,handler.scope,handler.correctScope);}
function handleKeyPress(e,obj){if(!keyData.shift){keyData.shift=false;}
if(!keyData.alt){keyData.alt=false;}
if(!keyData.ctrl){keyData.ctrl=false;}
if(e.shiftKey==keyData.shift&&e.altKey==keyData.alt&&e.ctrlKey==keyData.ctrl){var dataItem;var keyPressed;if(keyData.keys instanceof Array){for(var i=0;i<keyData.keys.length;i++){dataItem=keyData.keys[i];if(dataItem==e.charCode){keyEvent.fire(e.charCode,e);break;}else if(dataItem==e.keyCode){keyEvent.fire(e.keyCode,e);break;}}}else{dataItem=keyData.keys;if(dataItem==e.charCode){keyEvent.fire(e.charCode,e);}else if(dataItem==e.keyCode){keyEvent.fire(e.keyCode,e);}}}}
this.enable=function(){if(!this.enabled){YAHOO.util.Event.addListener(attachTo,event,handleKeyPress);this.enabledEvent.fire(keyData);}
this.enabled=true;};this.disable=function(){if(this.enabled){YAHOO.util.Event.removeListener(attachTo,event,handleKeyPress);this.disabledEvent.fire(keyData);}
this.enabled=false;};this.toString=function(){return"KeyListener ["+keyData.keys+"] "+attachTo.tagName+
(attachTo.id?"["+attachTo.id+"]":"");};};YAHOO.util.KeyListener.KEYDOWN="keydown";YAHOO.util.KeyListener.KEYUP="keyup";YAHOO.register("event",YAHOO.util.Event,{version:"2.2.2",build:"204"});