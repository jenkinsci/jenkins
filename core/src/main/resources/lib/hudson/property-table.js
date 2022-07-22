(function(){
  document.addEventListener("DOMContentLoaded", function() {
    document.querySelectorAll('.hidden-info .jenkins-button').forEach(function(elem) {
      elem.addEventListener('click', function() {
        elem.parentElement.style.display = 'none';
        elem.parentElement.nextSibling.classList.remove('hidden')
      });
    });

    document.querySelectorAll('.all-hidden-info').forEach(function(elem) {
      elem.addEventListener('click', function() {
        elem.parentElement.style.display = 'none';
        let tableId = elem.getAttribute('data-table-id');
        document.getElementById(tableId).querySelectorAll('.hidden-info .jenkins-button').forEach(function(elem) {
          elem.parentElement.style.display = 'none';
          elem.parentElement.nextSibling.classList.remove('hidden')
        });
      });
    });
  });
})();
