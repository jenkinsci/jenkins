/**
 * @typedef DropdownItem
 * @type {object}
 * @property {'DISABLED' | 'SEPARATOR' | 'HEADER' | 'CUSTOM'} type
 * @property {string} id
 * @property {string} displayName
 * @property {{order: number}} group
 * @property {string} icon
 * @property {string} iconXml
 * @property {{text: string, tooltip: string, severity: string}} badge
 * @property {{url: string} | {title: string, description: string, postTo: string}, {attributes: {[key: string]: string}, javascriptUrl: string} | {actions: DropdownItem[]}} event
 * @property {string} semantic
 * @property {string} contents - TODO
 * @property {string} clazz - TODO ??? not sure if this is staying
 * @property {() => {}} onClick - TODO ??? not sure if this is staying
 * */
