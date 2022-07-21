(function() {
  var element = document.getElementById('search-box-completion');
  if (element) {
    createSearchBox(element.getAttribute('data-search-url'));
  }
})();
