console.log('section/index.js');
var jquery = require('jquery-detached-2.1.4');

var tagName = '[data-tagName="section"]'; //what DOM element am I attached to...
var scope = 'form'; //how much of the DOM do I need to watch for changes...

// Events to attach...
exports.openCloseEvent = function(e){
  var $ = jquery.getJQuery();
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
};

exports.redrawEvent = function(e){
  var $ = jquery.getJQuery();
  var $input = (e.currentTarget)? $(e.currentTarget) : e;
  var $body  = $input.closest('.panel-collapse').addClass('collapse in').height('auto');
  var orgHeight = $body.height();
  
  return {$elem:$body,event:e};
};

exports.init = function() {
  var $ = jquery.getJQuery();
  var thisObj = this;
  var openCloseEvent = thisObj.openCloseEvent;
  var redrawEvent = thisObj.redrawEvent;
  
  $(scope).on('click',tagName + '> .panel-section-header',openCloseEvent);
  $(scope).on('click',(tagName + '> .panel-collapse button'),redrawEvent);
  $(scope).on('click',(tagName + '> .panel-collapse a'),redrawEvent);
  $(scope).on('click',(tagName + '> .panel-collapse input'),redrawEvent);
  $(scope).on('change',(tagName + '> .panel-collapse input'),redrawEvent);
  $(scope).on('change',(tagName + '> .panel-collapse select'),redrawEvent);
  
  $(function(){
    $(scope).on('DOMNodeInserted',$.proxy(thisObj.findElems,thisObj));
  });
  return thisObj;

};

exports.findElems = function() {
  var $ = jquery.getJQuery();
  var thisObj = this;

  var $elems = $(tagName)
    .each(function(i,section){
      var $section = $(section);
      var $body  = $section.children('.panel-collapse').addClass('collapse in');
      
      thisObj.redrawEvent($body);
      
      // color code child nested nodes so that they box up correctly when nested...
      $body.find('.setting-main').each(function(i){
        var $main = $(this);
        var $repeated = $main.children('.repeated-container');
        if($repeated && $repeated.length > 0)
          $main.addClass('fill');
      });

    });      
  
  return $elems;
};