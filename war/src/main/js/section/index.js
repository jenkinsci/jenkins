console.log('section/index.js');
var jquery = require('jquery-detached-2.1.4');
var booty = require('bootstrap-detached-3.3');
exports.init = function() {
  var $ = jquery.getJQuery();
  
  $(function(){
  //find my dom element....
 
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

      $body.find('.shown .chk-name > label').each(
          function(){
            var $this = $(this);
            if($.trim($this.text()) === 'None')
              $this.closest('.radio-group-box').removeClass('shown').addClass('none');
          }
      );

    });  
  });
};

