function amCloser(e) {
    var list = document.getElementById('visible-am-list');
    var el = e.target;
    while (el) {
        if (el === list) {
            return; // clicked in the list
        }
        el = el.parentElement;
    }
    hideVisibleAmList();
};
function amEscCloser(e) {
    if (e.keyCode == 27) {
        amCloser(e);
    }
};
function amContainer() {
    return document.getElementById('visible-am-container');
};
function hideVisibleAmList(e) {
    amContainer().classList.remove('visible');
    document.removeEventListener('click', amCloser);
    document.removeEventListener('keydown', amEscCloser);
}
function showVisibleAmList(e) {
    amContainer().classList.add('visible');
    setTimeout(function() {
        document.addEventListener('click', amCloser);
        document.addEventListener('keydown', amEscCloser);
    }, 1);
}
function toggleVisibleAmList(e) {
    if (amContainer().classList.contains('visible')) {
        hideVisibleAmList(e);
    } else {
        showVisibleAmList(e);
    }
    e.preventDefault();
}