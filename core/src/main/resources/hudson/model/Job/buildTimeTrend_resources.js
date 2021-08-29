/**
 * Public method to be called by progressiveRendering's callback
 */
function buildTimeTrend_displayBuilds(data) {
	var p = document.getElementById('trend');
	var isDistributedBuildsEnabled = 'true' === p.getAttribute("data-is-distributed-build-enabled");
	var rootURL = document.head.getAttribute('data-rooturl');
	
	for (var x = 0; data.length > x; x++) {
		var e = data[x];
		var tr = new Element('tr');
		tr.insert(new Element('td', {data: e.iconColorOrdinal}).
		insert(new Element('a', {class: 'build-status-link', href: e.number + '/console'}).
		insert(generateSVGIcon(e.iconName))));
		tr.insert(new Element('td', {data: e.number}).
		insert(new Element('a', {href: e.number + '/', 'class': 'model-link inside'}).
		update(e.displayName.escapeHTML())));
		tr.insert(new Element('td', {data: e.duration}).
		update(e.durationString.escapeHTML()));
		if (isDistributedBuildsEnabled) {
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

/**
 * Generate SVG Icon
 */
function generateSVGIcon(iconName) {

	const imagesURL = document.head.getAttribute('data-imagesurl');

	const isInProgress = iconName.endsWith("anime");
	let buildStatus = 'never-built';
	switch (iconName) {
		case 'red':
		case 'red-anime':
			buildStatus = 'last-failed';
			break;
		case 'yellow':
		case 'yellow-anime':
			buildStatus = 'last-unstable';
			break;
		case 'blue':
		case 'blue-anime':
			buildStatus = 'last-successful';
			break;
		case 'grey':
		case 'grey-anime':
		case 'disabled':
		case 'disabled-anime':
			buildStatus = 'last-disabled';
			break;
		case 'aborted':
		case 'aborted-anime':
			buildStatus = 'last-aborted';
			break;
		case 'nobuilt':
		case 'nobuilt-anime':
			buildStatus = 'never-built';
			break
	}

	const svg1 = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
	svg1.setAttribute('class', 'svg-icon');
	svg1.setAttribute('viewBox', "0 0 24 24");
	const use1 = document.createElementNS('http://www.w3.org/2000/svg', 'use');
	use1.setAttribute('href', imagesURL + '/build-status/build-status-sprite.svg#build-status-' + (isInProgress ? 'in-progress' : 'static'))
	svg1.appendChild(use1);

	const svg2 = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
	svg2.setAttribute('class', 'svg-icon icon-' + iconName + ' icon-sm');
	svg2.setAttribute('viewBox', "0 0 24 24");
	const use2 = document.createElementNS('http://www.w3.org/2000/svg', 'use');
	use2.setAttribute('href', imagesURL + '/build-status/build-status-sprite.svg#' + buildStatus)
	svg2.appendChild(use2);

	const span = new Element('span', {class: 'build-status-icon__wrapper icon-' + iconName}).
	insert(new Element('span', {class: 'build-status-icon__outer'}).
	insert(svg1)).
	insert(svg2);

	return span;
}