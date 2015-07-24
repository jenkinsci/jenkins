console.log('section/index.js');
var jquery = require('jquery-detached-2.1.4');
var booty = require('bootstrap-detached-3.3');

exports.init = function() {

  var $ = jquery.getJQuery();
  $(this.findElem);
  
  return this;

};

exports.findElem = function() {
  var $ = jquery.getJQuery();
  
  var $sections = $('.section.panel')
  .each(function(i,section){
    var $section = $(section);
    var $header = $section.children('.panel-heading');
    var $body  = $section.children('.panel-collapse').addClass('collapse in');
    var orgHeight = $body.height();    

    $header.click(function(e){
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
      
    });
    
    // if the section needs to change height because of content, it should redraw, which can be called by child elements...
    $section.bind('redraw',function(e,param){
      setTimeout(function(){
        $body.removeAttr('style');
        orgHeight = $body.height();
        $body.height(orgHeight);
      },1);
    });
    
    // chanage of radio, checkboxes and selects are common causes of size change needs...
    $body.find('input, select').change(function(e){
      $section.trigger('redraw',e)
    }); 
    
    // color code child nested nodes so that they box up correctly when nested...
    $body.find('.setting-main').each(function(i){
      var $main = $(this);
      var $repeated = $main.children('.repeated-container');
      if($repeated && $repeated.length > 0)
        $main.addClass('fill');
    });

  });      
  
  return $sections;
};