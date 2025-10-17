/**
 * @typedef MenuItemDropdownItem
 * @type {object}
 * @property {{order: number}} group
 *
 * @property {"ITEM"} type
 * @property {string} [id]
 * @property {string} displayName
 * @property {string} [icon]
 * @property {string} [iconXml]
 * @property {{text: string, tooltip: string, severity: string}} badge
 * @property {{
 *   url: string, type?: 'GET' | 'POST'
 * } | {
 *   title: string, description: string, postTo: string
 * } | {
 *   attributes: {[key: string]: string}, javascriptUrl: string
 * } | {
 *   actions: DropdownItem[]
 * }} event
 * @property {string} semantic
 * @property {string} contents - TODO
 * @property {string} clazz - TODO ??? not sure if this is staying
 * @property {() => {}} onClick - TODO ??? not sure if this is staying
 */

/**
 * @typedef SubmenuDropdownItem
 * @type {{
 *   type: "SUBMENU";
 * }}
 */

/**
 * @typedef SeparatorDropdownItem
 * @type {{
 *   type: "SEPARATOR";
 * }}
 */

/**
 * @typedef HeaderDropdownItem
 * @type {{
 *   type: "HEADER";
 *   displayName: string;
 * }}
 */

/**
 * @typedef CustomDropdownItem
 * @type {{
 *   type: "CUSTOM";
 *   displayName: string;
 * }}
 */

/**
 * @typedef DropdownItem
 * @type {
 *   MenuItemDropdownItem |
 *   SubmenuDropdownItem |
 *   SeparatorDropdownItem |
 *   HeaderDropdownItem |
 *   CustomDropdownItem
 * }
 */

/**
 * @typedef {"ITEM" | "SUBMENU" | "SEPARATOR" | "HEADER" | "CUSTOM"} DropdownItemType
 * */
