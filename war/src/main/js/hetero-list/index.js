console.log('hetero-list/index.js');
var jquery = require('jquery-detached-2.1.4');
var tagName = '[data-tagName="hetero-list"]'; //what DOM element am I attached to...
var subTagName = '[data-tagName="hetero-list-item"]';
var scope = 'form'; //how much of the DOM do I need to watch for changes...

// Events to attach...
exports.removeItem = function(e){
  var $ = jquery.getJQuery();  
  e.preventDefault();
};


exports.init = function() {

  debugger;
  var $ = jquery.getJQuery();
  var thisObj = this;

  $(function(){
    $(scope).on('DOMNodeInserted',$.proxy(thisObj.findElems,thisObj));
  });
  
  return thisObj;

};

exports.findElems = function() {
  var $ = jquery.getJQuery();
  var thisObj = this;
  var $hList= $(tagName)
    .each(thisObj.findElemsItems);      
  
  
  
  return $hList;
};

exports.findElemsItems = function(i,elem){
  var $ = jquery.getJQuery();
  if(isNaN(i) && !elem) elem = i;
  var $elem = $(elem);
  if($elem.length === 0) 
    $elem = $(tagName + ' > ' + subTagName);
  
  var $hListItems = $elem.children('[data-tagName="hetero-list-item"]')
    .each(function(i,elem){
      var $elem = $(elem);
      
      //check to see if item has help...
      
      
    });
  
  return $hListItems;
  
};





