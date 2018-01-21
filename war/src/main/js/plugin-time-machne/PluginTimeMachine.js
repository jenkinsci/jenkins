var $ = require('bootstrap-detached').getBootstrap();
var api = require('../api/pluginTimeMachine');
var snapshotListTemplate = require('./snapshotList.hbs');
var snapshotDiffTemplate = require('./snapshotDiff.hbs');
var doRollbackTemplate = require('./doRollback.hbs');
var Modal = require('../widgets/Modal');

function PluginTimeMachine(target, snapshotIds) {
    this.target = target;
    this.snapshotList = toSnapshotList(snapshotIds);
}
PluginTimeMachine.prototype = {

    render: function() {
        this.target.empty();
        if (this.snapshotList.length > 0) {
            // Remove the first entry for the template. It's the latest/current, so
            // nothing to change for it and so don't display it.
            this.target.append(snapshotListTemplate({snapshots: this.snapshotList.slice(1)}));
        }

        this.collapseAll();
        this.onSelect();
    },

    collapseAll: function() {
        $('#snapshot-list').collapse();
        $('#snapshot-list .panel-collapse.in').collapse('hide');
    },

    onSelect: function() {
        var timeMachine = this;

        $("#snapshot-list").on('show.bs.collapse', function(e) {
            var panel = $(e.target);
            var snapshotId = panel.attr('data-snapshot-id');
            var panelBody = $('.panel-body', panel);

            panelBody.empty();
            panelBody.text('Loading ...');

            timeMachine.loadSnapshotDiff(snapshotId, panelBody);
        });

        $("#snapshot-list").on('click', '.rollback', function(e) {
            var rollbackButton = $(e.target);
            var snapshotId = parseInt(rollbackButton.attr('data-snapshot-id'));

            var modal = new Modal('Plugin Rollback');
            modal.body(doRollbackTemplate({takenAt: new Date(snapshotId)}));
            modal.yes(function() {
                api.setSnapshotRollback(snapshotId);
            });
            modal.render();
        });
    },

    loadSnapshotDiff: function(snapshotId, panelBody) {
        var latestSnapshotId = this.snapshotList[0].id;
        api.getSnapshotDiff(latestSnapshotId, snapshotId, function(data) {
            panelBody.empty();
            if (!this.isError) {
                if (data.length === 0) {
                    panelBody.append('<div class="same-as-latest">This snapshot matches the current plugin state. Rolling back to this snapshot will have no effect.</div>');
                } else {
                    panelBody.append(snapshotDiffTemplate({
                        snapshotId: snapshotId,
                        changes: data
                    }));
                }
            } else {
                panelBody.text('Error loading snapshot diff: ' + JSON.stringify(data, undefined, 4));
            }
        });
    }
};

function toSnapshotList(snapshotIds) {
    var snapshotList = [];
    for (var i = 0; i < snapshotIds.length; i++) {
        var snapshot = {};

        snapshotList.push(snapshot);

        snapshot.idx = i;
        snapshot.id = snapshotIds[i];
        snapshot.takenAt = new Date(snapshot.id).toString();
        snapshot.takenAtShort = new Date(snapshot.id).toLocaleDateString();
        snapshot.latest = (i === 0);
    }
    return snapshotList;
}

module.exports = PluginTimeMachine;