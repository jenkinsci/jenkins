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

        this.collapseAll();
        this.onSelect();
    },

    collapseAll: function() {
        $('#snapshot-list').collapse();
        $('#snapshot-list .panel-collapse.in').collapse('hide');
    },

    onSelect: function() {
        $("#snapshot-list").on('show.bs.collapse', function(e) {
            var panel = $(e.target);
            var snapshotId = panel.attr('data-snapshot-id');
            var isLatest = (panel.attr('data-snapshot-latest') === 'true');
            var previousSnapshotId = panel.attr('data-snapshot-previous');
            var panelBody = $('.panel-body', panel);

            panelBody.empty();
            panelBody.text('snapshotId: ' + snapshotId);
        });
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