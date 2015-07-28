console.log('formControls/expandableTextbox.js');
var jquery = require('jquery-detached-2.1.4');

var tagName = '[data-tagName="expandableTextbox"]'; //what DOM element am I attached to...
var scope = 'form'; //how much of the DOM do I need to watch for changes...

function attachEvents(event, eventHandler, target ){
  var $ = jquery.getJQuery();
  // This 'on' syntax is from JQuery: http://api.jquery.com/on/
  // scope allows for a sub portion of the document to be watched for matching event selectors
  $(scope).on(event,tagName + target ,eventHandler);
}

function changeTextbox(e){
  var $ = jquery.getJQuery();    
  e.preventDefault();
  var $btn = $(this);
  var $elem = $btn.closest(tagName);
  var $input = $elem.find('.textbox-area > .setting-input');
  var attrs = $input[0].attributes;
  var value = $input.val().split(" ").join("\n");
  var newElem = '<textarea />';

  if($elem.hasClass('area')){
    $elem.removeClass('area');
    newElem = '<input />';
    value = value.split("\n").join(" ");
  }
  else
    $elem.addClass('area');

  var $newElem = $(newElem);
  $.each(attrs,function(i,attr){
    $newElem.attr(attr.name, attr.value);
  });

  $input.replaceWith($newElem.val(value));
}

exports.init = function() {
  var $ = jquery.getJQuery();
  
  attachEvents('click',changeTextbox,' > .expand-btn > a');

  return exports;

};