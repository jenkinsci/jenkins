Behaviour.specify("#recurse", 'ListView', 0, function (e) {
    var nestedElements = $$('SPAN.nested')
    e.onclick = function () {
        nestedElements.each(function (el) {
            e.checked ? el.show() : el.hide();
        });
    }
});
