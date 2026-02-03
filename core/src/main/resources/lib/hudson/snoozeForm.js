Behaviour.specify(".snooze-trigger", "snooze-open", 0, function (btn) {
    btn.addEventListener('click', function () {
        var monitorId = btn.getAttribute('data-monitor-id');
        var dialog = document.getElementById('snooze-dialog-' + monitorId);
        var select = document.getElementById('snooze-duration-' + monitorId);
        var customInput = document.getElementById('snooze-minutes-' + monitorId);

        if (dialog && dialog.showModal) {
            dialog.showModal();
            // Reset
            if (select) select.value = "60";
            if (customInput) {
                customInput.style.display = 'none';
                customInput.required = false;
            }
        } else {
            console.warn("Dialog API not supported");
        }
    });
});

Behaviour.specify(".snooze-cancel-btn", "snooze-close", 0, function (btn) {
    btn.addEventListener('click', function () {
        var dialog = btn.closest('dialog');
        if (dialog) dialog.close();
    });
});

Behaviour.specify(".snooze-select", "snooze-select", 0, function (select) {
    select.addEventListener('change', function () {
        var monitorId = select.getAttribute('data-monitor-id');
        var customInput = document.getElementById('snooze-minutes-' + monitorId);
        if (customInput) {
            var isCustom = (select.value === 'custom');
            customInput.style.display = isCustom ? 'block' : 'none';
            customInput.required = isCustom;
            if (isCustom) customInput.focus();
        }
    });
});

Behaviour.specify("form.snooze-form", "snooze-form", 0, function (form) {
    form.addEventListener('submit', function (e) {
        e.preventDefault();

        // Find CSRF crumb
        var crumbName = 'Jenkins-Crumb';
        var crumbValue = '';
        var crumbInput = document.querySelector('input[name="Jenkins-Crumb"]');
        if (crumbInput) {
            crumbValue = crumbInput.value;
        } else if (window.crumb) {
            crumbName = window.crumb.fieldName;
            crumbValue = window.crumb.value;
        } else {
            // Try to look in head meta tags as last resort
            var meta = document.head.querySelector('meta[name="crumb-name"]');
            if (meta) {
                crumbName = meta.content;
                var metaValue = document.head.querySelector('meta[name="crumb-value"]');
                if (metaValue) crumbValue = metaValue.content;
            }
        }

        var headers = { 'X-Requested-With': 'XMLHttpRequest' };
        if (crumbValue) {
            headers[crumbName] = crumbValue;
        }

        var submitBtn = form.querySelector('button[type="submit"]');
        var originalText = submitBtn.innerText;
        submitBtn.disabled = true;
        submitBtn.innerText = '...';

        fetch(form.action, {
            method: 'POST',
            body: new FormData(form),
            headers: headers
        }).then(function (response) {
            if (response.ok) {
                var dialog = form.closest('dialog');
                if (dialog) dialog.close();
                var monitorId = form.getAttribute('data-monitor-id');
                var trigger = document.querySelector('.snooze-trigger[data-monitor-id="' + monitorId + '"]');
                var monitor = trigger ? trigger.closest('.jenkins-alert') : null;
                if (monitor) {
                    monitor.style.transition = 'opacity 0.3s ease';
                    monitor.style.opacity = '0';
                    setTimeout(function () { monitor.remove(); }, 300);
                }
            } else {
                response.text().then(function (text) { alert('Error: ' + text); });
            }
        }).catch(function (error) {
            console.error(error);
            alert('Error submitting snooze form');
        }).finally(function () {
            submitBtn.disabled = false;
            submitBtn.innerText = originalText;
        });
    });
});
