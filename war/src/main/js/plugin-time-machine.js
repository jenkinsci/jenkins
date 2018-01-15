var $ = require('jquery-detached').getJQuery();
var api = require('./api/pluginTimeMachine');
var PluginTimeMachine = require('./plugin-time-machne/PluginTimeMachine');

$(function() {
    var timeMachineDiv = $('#plugin-time-machine');

    // We'll be using bootstrap 3 so add the class so the
    // styles get applied.
    timeMachineDiv.addClass('bootstrap-3');

    api.getSnapshotList(function(data) {
        if (!this.isError) {
            var pluginTimeMachine = new PluginTimeMachine(timeMachineDiv, data);
            pluginTimeMachine.render();
        } else {
            timeMachineDiv.text('Error loading snapshots: ' + timeMachineDiv.text(JSON.stringify(data, undefined, 4)));
        }
    });
});
