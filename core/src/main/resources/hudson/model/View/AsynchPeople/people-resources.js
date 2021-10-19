function display(data) {
    var p = document.getElementById('people');
    for (var x = 0; data.length > x; x++) {
        var e = data[x];
        var id = 'person-' + e.id;
        var r = document.getElementById(id);
        if (r == null) {
            r = document.createElement('tr');
            r.id = id;
            p.appendChild(r);
        } else {
            while (r.firstChild) {
                r.removeChild(r.firstChild);
            }
        }

        var d = document.createElement('td');
        var a = document.createElement('a');
        a.href = '${rootURL}/' + e.url;
        a.className = 'model-link inside';
        var i = document.createElement('img');
        i.src = e.avatar;
        i.className = 'icon${iconSize}';
        a.appendChild(i);
        d.appendChild(a);
        r.appendChild(d);

        d = document.createElement('td');
        var a = document.createElement('a');
        a.href = '${rootURL}/' + e.url;
        a.appendChild(document.createTextNode(e.id));
        d.appendChild(a);
        r.appendChild(d);

        d = document.createElement('td');
        var a = document.createElement('a');
        a.href = '${rootURL}/' + e.url;
        a.appendChild(document.createTextNode(e.fullName));
        d.appendChild(a);
        r.appendChild(d);

        d = document.createElement('td');
        d.setAttribute('data', e.timeSortKey);
        d.appendChild(document.createTextNode(e.lastChangeTimeString));
        r.appendChild(d);

        d = document.createElement('td');
        if (e.projectUrl != null) {
            a = document.createElement('a');
            a.href = '${rootURL}/' + e.projectUrl;
            a.className = 'model-link inside';
            a.appendChild(document.createTextNode(e.projectFullDisplayName));
            d.appendChild(a);
        }
        r.appendChild(d);

        ts_refresh(p);
    }
}