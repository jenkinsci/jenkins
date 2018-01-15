var $ = require('bootstrap-detached').getBootstrap();
var snapshotListTemplate = require('./snapshotList.hbs');

function PluginTimeMachine(target, snapshotIds) {
    this.target = target;
    this.snapshotList = toSnapshotList(snapshotIds);
}
PluginTimeMachine.prototype = {
    render: function() {
        this.target.empty();
        this.target.append(snapshotListTemplate({snapshots: this.snapshotList}));

        $('#snapshot-list').collapse();

        this.collapseAll();
    },
    collapseAll: function() {
        $('#snapshot-list .panel-collapse.in').collapse('hide');
    }
};

function toSnapshotList(snapshotIds) {
    var snapshotList = [];
    for (var i = 0; i < snapshotIds.length; i++) {
        var previousIdx = i + 1;

        if (previousIdx < snapshotIds.length) {
            var snapshot = {};

            snapshotList.push(snapshot);

            snapshot.idx = i;
            snapshot.id = snapshotIds[i];
            snapshot.takenAt = new Date(snapshot.id).toLocaleDateString();

            snapshot.latest = (i === 0);
            snapshot.previous = snapshotIds[previousIdx];
            snapshot.previousTakenAt = new Date(snapshot.previous).toLocaleDateString();
        }
    }
    return snapshotList;
}

module.exports = PluginTimeMachine;