console.log('radioBlock/index.js');
var jquery = require('jquery-detached-2.1.4');
var booty = require('bootstrap-detached-3.3');

var tagName = '[data-tagName="radioBlock"]'; //what DOM element am I attached to...
var scope = 'form'; //how much of the DOM do I need to watch for changes...


exports.init = function() {

  var $ = jquery.getJQuery();
  var thisObj = this;
  
  $(function(){
    $(scope).on('DOMNodeInserted',thisObj.findElems);
  });
  return thisObj;
  
  
};

exports.findElems = function(){
  var $ = jquery.getJQuery();
  var $elems = $(tagName)
    .each(function(i,elem){
      var $elem = $(elem);
      var $groupBox = $elem;
      var $group = $groupBox.children('.radio-group').first();
      var $labelBox = $elem.children('.radio-group-label');
      var $label = $labelBox.find('label');
      var $chk = $labelBox.find('.chk-name input');
      var checked = $chk.is(':checked');

      if($group.children().length === 0)
        $groupBox.addClass('none');
      else
        $groupBox.removeClass('none');
      
      if(checked){ 
        $group.show();
        $groupBox.addClass('shown');
      }
      else{
        $group.hide();
        $groupBox.removeClass('shown');
      }
    });        

}