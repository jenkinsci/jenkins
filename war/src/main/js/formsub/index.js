var jquery = require('jquery-detached-2.1.4');

exports.doSubmit = function(selector) {
    var $ = jquery.getJQuery(); // Always get $ this way i.e. don't cache it at the module level. Allows for testing.
    var $form = $(selector);

    // Etc ....

    return $form.text();
};

console.log('*** initialise form submission ...');
