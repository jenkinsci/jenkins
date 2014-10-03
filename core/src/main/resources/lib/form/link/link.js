Behaviour.specify('A.post', 'link.post', 0, function(element) {
	element.onclick = function(evt) {
		var form = document.createElement('form');
		form.setAttribute('method', 'POST');
        form.setAttribute('action', element.getAttribute('href'));
        crumb.appendToForm(form);
        document.body.appendChild(form);
		form.submit();
        return false;
    }
});

Behaviour.specify('A.post-async', 'link.post-async', 0, function(element) {
	element.onclick = function(evt) {
		new Ajax.Request(element.getAttribute('href'));
		return false;
	}
});

Behaviour.specify('A.async', 'link.async', 0, function(element) {
	element.onclick = function(evt) {
		new Ajax.Request(element.getAttribute('href'), {
            method : 'get'
        });
		return false;
	}
});
