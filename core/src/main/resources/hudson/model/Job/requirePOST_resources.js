(function () {
    const form = document.getElementById('post-form');
    form.addEventListener('submit', function (e) {
        e.preventDefault();
        new Ajax.Request(window.location.href, {
            method: 'POST',
            onSuccess: function (r) {
                if (r.status === 201) {
                    window.location.reload();
                }
            }
        })
    })
})();
