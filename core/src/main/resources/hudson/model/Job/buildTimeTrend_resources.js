/**
 * Public method to be called by progressiveRendering's callback
 */
function buildTimeTrend_displayBuilds(data) {
	var p = document.getElementById('trend');
	var isMasterSlaveEnabled = 'true' === p.getAttribute("data-is-master-slave-enabled");
	var imagesURL = document.head.getAttribute('data-imagesurl');
	var rootURL = document.head.getAttribute('data-rooturl');
	
	for (var x = 0; data.length > x; x++) {
		var e = data[x];
		var tr = new Element('tr');
		tr.insert(new Element('td', {data: e.iconColorOrdinal}).
		insert(new Element('a', {href: e.number + '/console'}).
		insert(new Element('img', {width: 16, height: 16, src: imagesURL+ '/16x16/' + e.buildStatusUrl, alt: e.iconColorDescription}))));
		tr.insert(new Element('td', {data: e.number}).
		insert(new Element('a', {href: e.number + '/', 'class': 'model-link inside'}).
		update(e.displayName.escapeHTML())));
		tr.insert(new Element('td', {data: e.duration}).
		update(e.durationString.escapeHTML()));
		if (isMasterSlaveEnabled) {
			var buildInfo = null;
			var buildInfoStr = (e.builtOnStr || '').escapeHTML();
			if(e.builtOn) {
				buildInfo = new Element('a', {href: rootURL + '/computer/' + e.builtOn, 'class': 'model-link inside'}).update(buildInfoStr);
			} else {
				buildInfo = buildInfoStr;
			}
			tr.insert(new Element('td').update(buildInfo));
		}
		p.insert(tr);
		Behaviour.applySubtree(tr);
	}
	ts_refresh(p);
}
