document.addEventListener('DOMContentLoaded', function() {
  document.querySelectorAll('input.optional-block-event-item').forEach(function(el) {
    el.addEventListener('click', function(e) {
      updateOptionalBlock(el, true);
    });
  });
});
