/** @type {YAHOO.widget.Menu} */
var oMenu;

var xhr;

/**
 * @param {HTMLElement} e
 *      The LI tag that the mouse has wondered into.
 */
function foo(e) {
  function showMenu(items) {
    oMenu.hide();
    oMenu.cfg.setProperty("context",[e,"tl","bl"]);
    oMenu.clearContent();
    oMenu.addItems(items);
    oMenu.render("rendertarget");
    oMenu.show();
  }

  if (xhr)
    xhr.options.onComplete = function() {};   // ignore the currently pending call

  if (e.items) {// use what's already loaded
    showMenu(e.items);
  } else {// fetch menu on demand
    xhr = new Ajax.Request(e.firstChild.getAttribute("href")+"contextMenu", {
      onComplete : function (x) {
        var a = x.responseText.evalJSON().items;
        a.each(function(e) {
          e.text = "<img src='"+e.icon+"' width=24 height=24 style='margin: 2px;' alt=''> "+e.displayName;
        });
        e.items = a;
        showMenu(a);
      }
    });
  }

  return false;
}
window.addEventListener("load",function(){
  oMenu = new YAHOO.widget.Menu("breadcrumb-menu", {position:"dynamic", hidedelay:1000});
})
