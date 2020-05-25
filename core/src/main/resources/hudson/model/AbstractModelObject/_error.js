(function initializeShowDetailsButton(){
	Behaviour.specify(".js-click-to-show-details", 'showDetails', 0, function(element) {
		element.onclick = function(evt) {
			var showQuery = element.getAttribute('data-show');
			var hideQuery = element.getAttribute('data-hide');
			
			if (showQuery) {
				document.querySelectorAll(showQuery).forEach(r => r.style.display = "block");
			}
			if (hideQuery) {
				document.querySelectorAll(hideQuery).forEach(r => r.style.display = "none");
			}
		}
	});
})();
