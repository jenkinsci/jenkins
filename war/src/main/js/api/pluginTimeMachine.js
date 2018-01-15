/**
 * Plugin Time Machine API.
 */

var jenkins = require('../util/jenkins');
var HTTP_TIMEOUT = 10 * 1000; // 10 seconds

exports.getSnapshotList = function (handler) {
    jenkins.get('/pluginManager/timeMachine/snapshots', function (response) {
        handler.call({isError: (response.status !== 'ok')}, response.data);
    }, {
        timeout: HTTP_TIMEOUT,
        error: function (xhr, textStatus, errorThrown) {
            handler.call({isError: true}, errorThrown);
        }
    });
};