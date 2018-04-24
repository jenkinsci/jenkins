const Fetch = {
    get: function (url, callback) {
        const xhr = new XMLHttpRequest();
        xhr.open('GET', url, true);
        xhr.onload = function () {
            const status = xhr.status;
            if(callback) {
                callback(null, status);
            }
        };
        xhr.onerror = function(){
            const message = xhr.responseText;
            const status = xhr.status;
            if(callback) {
                callback(message, status);
            }
        };
        xhr.send();
    }
};

function safeRedirector(url) {
    const timeout = 5000;
    window.setTimeout(function() {
        const statusChecker = arguments.callee;
        Fetch.get(url, function(error, status) {
            if(error || status !== 200) {
                window.setTimeout(statusChecker, timeout)
            } else {
                window.location.replace(url);
            }
        })
    }, timeout);
}
