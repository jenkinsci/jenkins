console.log('optionBlock/index.js');
var jquery = require('jquery-detached-2.1.4');
var booty = require('bootstrap-detached-3.3');

exports.init = function() {

  var $ = jquery.getJQuery();
  $(this.findElem);
  //$('body').on('DOMNodeInserted',findElem);
};

exports.findElem = function(){
  var $ = jquery.getJQuery();

  var $elems = $('[data-tagName="optionalBlock"]')
    .each(function(i,elem){
      var $elem = $(elem);
      var $groupBox = $elem;
      var $group = $groupBox.children('.option-group').first();
      var $labelBox = $elem.children('.option-group-label');
      var $label = $labelBox.find('label');
      var $chk = $labelBox.find('.chk-name > input');
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