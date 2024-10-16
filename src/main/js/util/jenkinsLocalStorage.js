import { getWindow } from "window-handle";
import storage from "./localStorage";

/**
 * Store a Jenkins globally scoped value.
 */
function setGlobalItem(name, value) {
  storage.setItem("jenkins:" + name, value);
}

/**
 * Get a Jenkins globally scoped value.
 */
function getGlobalItem(name, defaultVal) {
  return storage.getItem("jenkins:" + name, defaultVal);
}

/**
 * Store a Jenkins page scoped value.
 */
function setPageItem(name, value) {
  name = "jenkins:" + name + ":" + getWindow().location.href;
  storage.setItem(name, value);
}

/**
 * Get a Jenkins page scoped value.
 */
function getPageItem(name, defaultVal) {
  name = "jenkins:" + name + ":" + getWindow().location.href;
  return storage.getItem(name, defaultVal);
}

export default {
  setGlobalItem,
  getGlobalItem,
  setPageItem,
  getPageItem,
};
