
/**
 * A backward compatibility hack to expose functions that
 * existing JS code may look for in the global scope.
 * @param module The module containing functions to be exported.
 * @param functions An array of function names.
 */
exports.globalize = function(module, functions) {
    if (typeof window !== 'undefined') {
        function apply(funcNum) {
            var functionInfo = functions[funcNum];
            var globalFuncName;
            var moduleFuncName;

            if (typeof functionInfo === 'object') {
                moduleFuncName = functionInfo.from;
                globalFuncName = functionInfo.to;
            } else {
                moduleFuncName = functionInfo;
                globalFuncName = functionInfo;
            }

            window[globalFuncName] = function () {
                // TODO: Come up with a way of gather this info so we can track code using non modularized code.
                return module.exports[moduleFuncName].apply(window, arguments);
            };
        }
        for (var i = 0; i < functions.length; i++) {
            apply(i);
        }
    }
};