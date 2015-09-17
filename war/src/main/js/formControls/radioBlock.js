console.log('formControls/radioBlock.js');
var jquery = require('jquery-detached-2.1.4');

var tagName = '[data-tagName="radioBlock"]'; //what DOM element am I attached to...
var scope = 'form'; //how much of the DOM do I need to watch for changes...

function attachEvents(event, eventHandler, target ){
  var $ = jquery.getJQuery();
  // This 'on' syntax is from JQuery: http://api.jquery.com/on/
  // scope allows for a sub portion of the document to be watched for matching event selectors
  $(scope).on(event,tagName + target ,eventHandler);
}

function watchDomModification(){
  var $ = jquery.getJQuery();  
  $(scope).on('DOMNodeInserted', addDomDecorations);
}

function addDomDecorations(){
  var $ = jquery.getJQuery();
  var $elems = $(tagName)
    .each(tagSelectedGroup);        
  
  return $elems;  
}

function tagSelectedGroup(e,elem){
  var $ = jquery.getJQuery();
  var $elem = (e.currentTarget)? $(e.currentTarget) : $(elem); 
  var $groupBox = $elem.closest(tagName);
  var $group = $groupBox.find('.radio-group').first();
  var $labelBox = $groupBox.find('.radio-group-label');
  var $label = $labelBox.find('label');
  var $chk = $labelBox.find('input');
  var checked = $chk.is(':checked');
  var name = '[data-inputName="'+$groupBox.attr('data-inputName')+'"]';

  if($group.children().length === 0)
    $groupBox.addClass('none');
  else
    $groupBox.removeClass('none');
  
  if(checked){ 
    $group.show();
    $groupBox.parent().children(tagName+name).removeClass('shown');
    $groupBox.addClass('shown');
    
  }
  else{
    $group.hide();
    $groupBox.removeClass('shown');
  }  
}

exports.init = function() {
  var $ = jquery.getJQuery();
  
  attachEvents('change',tagSelectedGroup,' > .radio-group-label input');
  
  watchDomModification();
  
  addDomDecorations();
  
  return exports;

};