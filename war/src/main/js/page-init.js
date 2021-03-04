import $ from 'jquery';
import jsModules from 'jenkins-js-modules';

/*
 * Page initialisation tasks.
 */

$(function() {
    loadScripts();
    loadCSS();
    reformatDates();
});

function loadScripts() {
    $('.jenkins-js-load').each(function () {
        var scriptUrl = $(this).attr('data-src');
        if (scriptUrl) {
            // jsModules.addScript will ensure that the script is
            // loaded once and once only. So, this can be considered
            // analogous to a client-side adjunct.
            jsModules.addScript(scriptUrl);
            $(this).remove();
        }
    });
}

function loadCSS() {
    $('.jenkins-css-load').each(function () {
        var cssUrl = $(this).attr('data-src');
        if (cssUrl) {
            // jsModules.addCSSToPage will ensure that the CSS is
            // loaded once and once only. So, this can be considered
            // analogous to a client-side adjunct.
            jsModules.addCSSToPage(cssUrl);
            $(this).remove();
        }
    });
}

function reformatDates() {
    const formatDateElements = document.getElementsByTagName('formatDate');
    for (let x of formatDateElements) {
        const dateValue = x.getAttribute('value');
        const dateStyle = x.getAttribute('dateStyle');
        const timeStyle = x.getAttribute('timeStyle');

        const dt = new Date(parseInt(dateValue));

        const options = {dateStyle, timeStyle};
        let dateTimeFormat = new Intl.DateTimeFormat(navigator.language, options);
        x.innerHTML = dateTimeFormat.format(dt);
    }
}
