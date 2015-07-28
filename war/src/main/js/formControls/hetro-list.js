console.log('formControls/section.js');
var jquery = require('jquery-detached-2.1.4');

var tagName = '[data-tagName="section"]'; //what DOM element am I attached to...
var scope = 'form'; //how much of the DOM do I need to watch for changes...

function attachEvents(event, eventHandler, target, targetScope ){
  var $ = jquery.getJQuery();
  targetScope = targetScope || ' > .panel-collapse ';
  target = target || '';
  // This 'on' syntax is from JQuery: http://api.jquery.com/on/
  // scope allows for a sub portion of the document to be watched for matching event selectors
  $(scope).on(event,tagName + targetScope + target ,eventHandler);
}

function watchDomModification(){
  var $ = jquery.getJQuery();  
  $(scope).on('DOMNodeInserted', addDomDecorations);
}

function addDomDecorations(){
  var $ = jquery.getJQuery();
  var $elems = $(tagName)
    .each(function(i,section){
      var $section = $(section);
      var $body  = $section.children('.panel-collapse').addClass('collapse in');
      
      exports.resize($body);
      
      // color code child nested nodes so that they box up correctly when nested...
      $body.find('.setting-main').each(function(i){
        var $main = $(this);
        var $repeated = $main.children('.repeated-container');
        if($repeated && $repeated.length > 0)
          $main.addClass('fill');
      });

    });      
  
  return $elems;
}

//Event handlers to attach...
// This is very similar to the Bootstrap Collapse, 
// but unlike BS Collapse, the panels have no knowledge of the parent elements

function openCloseEventHandler(e){
  var $ = jquery.getJQuery();
  console.log(tagName);
  var $header = $(this);
  var $section = $header.closest(tagName);
  var $body  = $section.children('.panel-collapse').addClass('collapse in').height('auto');
  var orgHeight = $body.height(); 
  
  e.preventDefault(); 
  
  if(!$section.hasClass('not-shown')){
    $body.removeAttr('style');
    orgHeight = $body.height();
    $body.height(orgHeight);
    $section.removeClass('shown').addClass('not-shown');
    $body.height(0);
  }
  else{
    $section.addClass('shown').removeClass('not-shown');
    $body.height(orgHeight);
  }
  
  return {$elem:$section,event:e};
}

exports.resize = function(e){
  // this function takes both events and JQuery target elements, 
  // allowing it to be used as an event handler internally 
  // or to be triggered by other elements who may need to inject DOM elements by some other means.
  var $ = jquery.getJQuery();
  var $input = (e.currentTarget)? $(e.currentTarget) : e;
  var $body  = $input.closest('.panel-collapse').addClass('collapse in').height('auto');
  var orgHeight = $body.height();
  
  return {$elem:$body,event:e};
}; 

exports.init = function() {
  var $ = jquery.getJQuery();

  // attaching the main event to the element...
  attachEvents('click',openCloseEventHandler,'', '> .panel-section-header');
  
  //attaching the children's events that are most likely to require element resizing
  attachEvents('click',exports.resize, 'button');
  attachEvents('click',exports.resize, 'a');
  attachEvents('click',exports.resize, 'input');
  attachEvents('change',exports.resize, 'input');
  attachEvents('change',exports.resize, 'select');
  
  watchDomModification();
  
  addDomDecorations();
  
  return exports;
};
