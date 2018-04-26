/**
 * Plugin Time Machine API.
 */

var jenkins = require('../util/jenkins');
var HTTP_TIMEOUT = 10 * 1000; // 10 seconds
var $ = require('jquery-detached').getJQuery();

exports.getSnapshotList = function (handler) {
    doGET('/pluginManager/timeMachine/snapshots', handler);
};

exports.getSnapshotDiff = function (from, to, handler) {
    doGET('/pluginManager/timeMachine/snapshotChanges?from=' + from + '&to=' + to, handler);
};

exports.setSnapshotRollback = function (to) {
    doPOST('/pluginManager/timeMachine/setRollback?toSnapshotTakenAt=' + to);
    doPOST('/restart');
    location.replace(jenkins.baseUrl());
};

function doGET(url, handler) {
    jenkins.get(url, function (response) {
        handler.call({isError: (response.status !== 'ok')}, response.data);
    }, {
        timeout: HTTP_TIMEOUT,
        error: function (xhr, textStatus, errorThrown) {
            handler.call({isError: true}, errorThrown);
        }
    });
}

function doPOST(url) {
    $.ajax({
        url: jenkins.baseUrl() + url,
        type: 'POST',
        cache: false,
        dataType: 'json',
        contentType: "application/json"
    });
}
