Behaviour.specify('#URICheckEncodingMonitor-message', 'URICheckEncodingMonitor', 0, function(element) {
    var url = element.getAttribute('data-url');
    var params = {value : '\u57f7\u4e8b'};
    var checkAjax = new Ajax.Updater(
        'URICheckEncodingMonitor-message', url,
        {
            method: 'get', parameters: params
        }
    );
});
