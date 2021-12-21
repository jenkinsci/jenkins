(function () {
    const form = document.getElementById('post-form');
    form.addEventListener('submit', function (e) {
        e.preventDefault();
        const currentUrl = window.location.href;
        new Ajax.Request(currentUrl, {
            method: 'POST',
            onSuccess: function (r) {
                if (r.status === 201) {
                    // Redirect to the job page after the request posts successfully
                    window.location.href = currentUrl.substring(0, currentUrl.lastIndexOf('/'));
                }
            }
        })
    })
})();
