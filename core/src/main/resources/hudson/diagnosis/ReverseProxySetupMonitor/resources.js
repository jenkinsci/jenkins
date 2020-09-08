(function () {
	var redirectForm = document.getElementById('redirect-error');
	if (!redirectForm) {
		console.warn('This script expects to have an element with id="redirect-error" to be working.');
		return;
	}

	var urlToTest = redirectForm.getAttribute('data-url');
	var callUrlToTest = function(testWithContext, callback) {
		var options = {
			onComplete: callback
		};
		if (testWithContext === true) {
			options.parameters = { testWithContext: true };
		}
		new Ajax.Request(urlToTest, options);
	};
	
	var displayWarningMessage = function(withContextMessage) {
		redirectForm.classList.remove('reverse-proxy__hidden');
		if (withContextMessage === true) {
			redirectForm.querySelectorAll('.js-context-message').forEach(function (node) {
				return node.classList.remove('reverse-proxy__hidden');
			});
		}
	};

	callUrlToTest( false, function(response) {
		if (response.status !== 200) {
			var context = redirectForm.getAttribute('data-context');
			// to cover the case where the JenkinsRootUrl is configured without the context
			if (context) {
				callUrlToTest(true, function(response2) {
					if (response2.status === 200) {
						// this means the root URL was configured but without the contextPath, 
						// so different message to display
						displayWarningMessage(true);
					} else {
						displayWarningMessage(false);
					}
				});
			} else {
				// redirect failed. Unfortunately, according to the spec http://www.w3.org/TR/XMLHttpRequest/
				// in case of error, we can either get 0 or a failure code
				displayWarningMessage(false);
			}
		}
	});
})();
