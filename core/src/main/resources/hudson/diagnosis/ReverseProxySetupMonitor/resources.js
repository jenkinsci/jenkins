(() => {
	var redirectForm = document.getElementById('redirect-error');
	if (!redirectForm) {
		console.warn('This script expects to have an element with id="redirect-error" to be working.');
	} else {
		var urlToTest = redirectForm.getAttribute('data-url');
		new Ajax.Request(urlToTest, {
			onComplete : function(response) {
				if (response.status !== 200) {
					var context = redirectForm.getAttribute('data-context');
					// to cover the case where the JenkinsRootUrl is configured without the context
					if (context) {
						new Ajax.Request(urlToTest, {
							parameters: { testWithContext:true },
							onComplete: function (response) {
								if (response.status === 200) {
									// this means the root URL was configured but without the contextPath, 
									// so different message to display
									redirectForm.style.display = "block";
									redirectForm.querySelectorAll('.context-message').forEach(node => node.style.display = "block");
								} else {
									redirectForm.style.display = "block";
								}
							}
						});
					} else {
						// redirect failed. Unfortunately, according to the spec http://www.w3.org/TR/XMLHttpRequest/
						// in case of error, we can either get 0 or a failure code
						redirectForm.style.display = "block";
					}
				}
			}
		});
	}
})();
