// Initialize all modules by requiring them. Also makes sure they get bundled (see gulpfile.js).
var $ = require('jquery-detached').getJQuery();

var getItems = function(root){
  var d = $.Deferred();
  $.get(root+'/categories?depth=3').done(
      function(data){
        d.resolve(data);
      }
  );
  return d.promise();
}; 

var jRoot = $('head').attr('data-rooturl');

$.when(getItems(jRoot)).done(function(data){
  $(function() {
    
    //////////////////////////////
    // helpful reference DOM

    var defaultMinToShow = 2;
    var $root = $('#main-panel');
    var $form = $root.find('form[name="createItem"]').addClass('jenkins-config new-view');
    var $newView = $('<div class="new-view" />')
      .attr('name','createItem')
      .attr('action','craetItem')
      .prependTo($form);
    var $tabs = $('<div class="jenkins-config-widgets" />').appendTo($newView);
    var $categories = $('<div class="categories" />').appendTo($newView);
    var sectionsToShow = [];    

    
    ////////////////////////////////
    // scroll action......
    
    function watchScroll(){
      var $window = $(window);
      var $jenkTools = $('#breadcrumbBar');
      var winScoll = $window.scrollTop();
      var jenkToolOffset = $jenkTools.height() + $jenkTools.offset().top + 15;

      $tabs.find('.active').removeClass('active');
      $.each(data.categories,function(i,cat){
        var domId = '#j-add-item-type-'+cat.id;
        var $cat = $(domId);
        var catHeight = ($cat.length > 0)?
            $cat.offset().top + $cat.outerHeight() - (jenkToolOffset + 100):
              0;

        if(winScoll < catHeight){
          var $thisTab = $tabs.find(['[href="',cleanHref(domId),'"]'].join(''));
          resetActiveTab($thisTab);
          return false;
        }
      });
      
      if(winScoll > $('#page-head').height() - 5 ){  
        $tabs.width($tabs.width()).css({
          'position':'fixed',
          'top':($jenkTools.height() - 5 )+'px'});
        $categories.css({'margin-top':$tabs.outerHeight()+'px'});   
      }
      else{
        $tabs.add($categories).removeAttr('style');
      }
    }

    //////////////////////////
    // helper functions...
    
    function addCopyOption(data){
      var $copy = $('#copy').closest('tr');
      if($copy.length === 0) {return data;} // exit if copy should not be added to page. Jelly page holds that logic.
      var copyTitle = $copy.find('label').text();
      var copyDom = $copy.next().find('.setting-main').html();
      var copy = {
          name:'Copy',
          id:'copy',
          minToShow:0,
          items:[
            {
              class:"copy",
              description:copyDom,
              displayName:copyTitle,
            }
          ]
      };
      var newData = [];
      
      $.each(data,function(i,elem){
        if(elem.id !== "category-id-copy")
          { newData.push(elem); }
      });
      
      newData.push(copy);

      
      return newData;
    }
    
    function sortItemsByOrder(itemTypes) {
      function sortByOrder(a, b) {
        var aOrder = a.weight;
        var bOrder = b.weight;
        return ( (aOrder < bOrder) ? -1 : ( (aOrder === bOrder) ? 0 : 1));
      }
      return itemTypes.sort(sortByOrder);
    }
    
    function hideAllTabsIfUnnecesary(sectionsToShow){
      if(sectionsToShow.length < 2){
        $tabs.find('.tab').hide();
        $categories.find('.category-header').hide();
      }        
    }
    
    function checkCatCount(elem){
      var minToShow = (typeof elem.minToShow === 'number')? elem.minToShow : 9999999;
      return ($.isArray(elem.items) && elem.items.length >= Math.min(minToShow,defaultMinToShow));
    }
    
    function cleanClassName(className){
      return className.replace(/\./g,'_');
    }
    
    function cleanHref(id,reverse){
      if(reverse){
        var gotHash = (id.indexOf('#') === 0)? 
           '#j-add-item-type-'+ id.substring(1):
             'j-add-item-type-'+ id;
        return gotHash;
      }
      else{
        return id.replace('j-add-item-type-','');
      }
    }
    
    function cleanLayout(){
      // Do a little shimmy-hack to force legacy code to resize correctly and set tab state.
      $('html,body').animate({scrollTop: 1}, 1);
      $('html,body').animate({scrollTop: 0}, 10);

      setTimeout(fireBottomStickerAdjustEvent,410);
    }
    
    function resetActiveTab($this){
      var $nav = $this.closest('.nav');
      $nav.find('.active').removeClass('active');
      $this.addClass('active');
    } 
    
    //////////////////////////////////
    // Draw functions
    
    function drawName() {
      var $name = $('<div class="j-add-item-name" />');

      var $input = $('<input type="text" name="name" class="name" id="name" placeholder="New item name..." />')
        .change(function(){
          $form.find('input[name="name"]').val($(this).val());
          window.updateOk($form[0]);
        })
        .appendTo($name);
      
      $tabs.prepend($name);
      setTimeout(function(){$input.focus();},100);
    }    
    
    function drawTabs(data){
      $('body').addClass('add-item');
      setTimeout(function(){$('body').addClass('hide-side j-hide-left');},200);
      $('#main-panel').addClass('container');
      var $navBox = $('<nav class="navbar navbar-default navbar-static form-config tabBarFrame"/>');
      var $nav = $('<ul class="nav navbar-nav tabBar config-section-activators" />');
      
      $.each(data,function(i,elem){
        if(!checkCatCount(elem)) {return;}
        // little bit hacky here... need to keep track if I have tabs to show, so if there is just 1, I can hide it later....
        else if (elem.minToShow !== 0) {sectionsToShow.push(elem.id);}
        
        var $tab = drawTab(elem);
        var $items = drawCategory(elem);
        var $cat = $items.parent();
        
        $.each(elem.items,function(i,elem){
          var $item = drawItem(elem);
          $items.append($item);
        });
        
        $nav.append($tab);
        $categories.append($cat);

      });
      $(window).on('scroll',watchScroll);
      
      if(sectionsToShow.length > 1){
        $navBox.append($nav);
        $tabs.prepend($navBox);
      }else{
        $categories.find('.category-header').hide();
      }
      drawName();
      cleanLayout();
    }

    function drawTab(i,elem){
      if(!elem) {elem = i;}
      var $tab = $(['<li><a class="tab" href="#',cleanHref(elem.id),'">',elem.name,'</a></li>'].join(''))
        .click(function(){
          //e.preventDefault(e);
          var $this = $(this).children('a');
          
          var tab = $this.attr('href');
          var scrollTop = $(cleanHref(tab,true)).offset().top - ($newView.children('.jenkins-config-widgets').height() + 15);
          
          setTimeout(function(){resetActiveTab($this);},510);
          
          $('html,body').animate({
            scrollTop: scrollTop
          }, 500);
        });
      return $tab;
    }

    function drawCategory(i,elem){
      if (!elem) elem = i;
      var $category = $('<div/>').addClass('category jenkins-config hide-cat').attr('id', 'j-add-item-type-'+elem.id);
      var $items = $('<ul/>').addClass('j-item-options').appendTo($category);
      var $newTarget;
      
      if(checkCatCount(elem)){
        var $catHeader = $('<div class="category-header" />').prependTo($category);
        var catDom = (elem.minToShow > 0)?
            ['<h2>', elem.name, '</h2>'].join(''):
              '';
        $(catDom).appendTo($catHeader);
        $(['<p>', elem.description, '</p>'].join('')).appendTo($catHeader);
        
        $category.removeClass('hide-cat');
      }
      else if(elem.remainders){
        $newTarget = $('#'+cleanHref(elem.remainders,true)).find('.j-item-options');
      }

      return $items;
    }
    
    function drawItem(elem){
      var $item = $([
          '<li class="',cleanClassName(elem.class),'"><label><input name="mode" value="',elem.class,'" type="radio" /> <span class="label">', elem.displayName, '</span></label></li>'
      ].join('')).append([
          '<div class="desc">', elem.description, '</div>'
      ].join('')).append([
          '<div class="icn"><span class="img" style="background:url(',jRoot,'/images/items/',cleanClassName(elem.class),'.png)"></span></div>'
      ].join(''));

      function setSelectState(){
        var $this = $(this).closest('li');
        //if this is a hyperlink, don't move the selection.
        if($this.find('a:focus').length === 1) {return false;}
        $this.closest('.categories').find('.active').removeClass('active');
        $this.addClass('active');
        $this.find('input[type="radio"]').attr('checked', 'checked');
        window.updateOk($form[0]);
        
        $('html, body').animate({
          scrollTop:$this.offset().top - 200
        },50);
        
      }
      
      $item.click(setSelectState);
      
      return $item;
    }
    
    // initialize
    
    var sortedDCategories = sortItemsByOrder(data.categories);
    var sortedDCategoriesWithCopy = addCopyOption(sortedDCategories);
    drawTabs(sortedDCategoriesWithCopy);
    
  });
});

function fireBottomStickerAdjustEvent() {
  Event.fire(window, 'jenkins:bottom-sticker-adjust'); // jshint ignore:line
}