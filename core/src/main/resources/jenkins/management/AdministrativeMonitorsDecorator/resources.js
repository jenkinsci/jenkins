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
}
function secAmCloser(e) {
    var list = document.getElementById('visible-sec-am-list');
    var el = e.target;
    while (el) {
        if (el === list) {
            return; // clicked in the list
        }
        el = el.parentElement;
    }
    hideVisibleSecAmList();
}
function amEscCloser(e) {
    if (e.keyCode === 27) {
        amCloser(e);
    }
}
function secAmEscCloser(e) {
    if (e.keyCode === 27) {
        secAmCloser(e);
    }
}
function amContainer() {
    return document.getElementById('visible-am-container');
}
function secAmContainer() {
    return document.getElementById('visible-sec-am-container');
}
function hideVisibleAmList(e) {
    amContainer().classList.remove('visible');
    document.removeEventListener('click', amCloser);
    document.removeEventListener('keydown', amEscCloser);
}
function hideVisibleSecAmList(e) {
    secAmContainer().classList.remove('visible');
    document.removeEventListener('click', secAmCloser);
    document.removeEventListener('keydown', secAmEscCloser);
}
function showVisibleAmList(e) {
    amContainer().classList.add('visible');
    setTimeout(function() {
        document.addEventListener('click', amCloser);
        document.addEventListener('keydown', amEscCloser);
    }, 1);
}
function showVisibleSecAmList(e) {
    secAmContainer().classList.add('visible');
    setTimeout(function() {
        document.addEventListener('click', secAmCloser);
        document.addEventListener('keydown', secAmEscCloser);
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
function toggleVisibleSecAmList(e) {
    if (secAmContainer().classList.contains('visible')) {
        hideVisibleSecAmList(e);
    } else {
        showVisibleSecAmList(e);
    }
    e.preventDefault();
}
