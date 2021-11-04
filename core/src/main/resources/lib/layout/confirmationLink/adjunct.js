Behaviour.specify("A.confirmation-link", 'confirmation-link', 0, function (element) {
    element.onclick = function(event) {
        var post = element.getAttribute('data-post');
        var href = element.getAttribute('data-url');
        var message = element.getAttribute('data-message');
        if (confirm(message)) {
            var form = document.createElement('form');
            form.setAttribute('method', (post === "true") ? 'POST' : 'GET');
            form.setAttribute('action', href);
            if (post) {
                crumb.appendToForm(form);
            }
            document.body.appendChild(form);
            form.submit();
        }
        return false;
    };
});
