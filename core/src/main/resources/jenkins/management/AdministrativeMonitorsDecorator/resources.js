function initializeAmMonitor(amMonitorRoot, options) {
    var button = amMonitorRoot.querySelector('.am-button');
    var url = button.getAttribute('data-href');
    var amList = amMonitorRoot.querySelector('.am-list');

    function onClose(e) {
        var list = amList;
        var el = e.target;
        while (el) {
            if (el === list) {
                return; // clicked in the list
            }
            el = el.parentElement;
        }
        close();
    }
    function onEscClose(e) {
        if (e.keyCode === 27) {
            close();
        }
    }

    function show() {
        if (options.closeAll) options.closeAll();

        new Ajax.Request(url, {
            method: "GET",
            onSuccess: function(rsp) {
                var popupContent = rsp.responseText;
                amList.innerHTML = popupContent;
                amMonitorRoot.classList.add('visible');
                document.addEventListener('click', onClose);
                document.addEventListener('keydown', onEscClose);
            }
        })
    }

    function close() {
        amMonitorRoot.classList.remove('visible');
        amList.innerHTML = '';
        document.removeEventListener('click', onClose);
        document.removeEventListener('keydown', onEscClose);
    }

    function toggle(e) {
        if (amMonitorRoot.classList.contains('visible')) {
            close();
        } else {
            show();
        }
        e.preventDefault();
    }


    return {
        button: button,
        toggle: toggle,
        close: close,
    }
}

document.addEventListener('DOMContentLoaded', function () {
    var monitorWidgets;

    function closeAll() {
        monitorWidgets.forEach(function (widget) {
            widget.close();
        })
    }

    var normalMonitors = initializeAmMonitor(document.getElementById('visible-am-container'), {
        closeAll: closeAll,
    });
    var securityMonitors = initializeAmMonitor(document.getElementById('visible-sec-am-container'), {
        closeAll: closeAll,
    });
    monitorWidgets = [normalMonitors, securityMonitors];

    monitorWidgets.forEach(function (widget) {
        widget.button.addEventListener('click', widget.toggle);
    });
});
