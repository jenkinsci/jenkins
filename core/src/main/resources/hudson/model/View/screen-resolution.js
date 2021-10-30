(function() {
  var selfScript = document.querySelector('#screenResolution-script');
  var secureCookie = selfScript.getAttribute('data-use-secure-cookie');
  YAHOO.util.Cookie.set("screenResolution", screen.width + "x" + screen.height, {
    path: "/",
    secure: secureCookie === 'true'
  });
})();
