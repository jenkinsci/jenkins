import { getWindow } from "window-handle";

let storage = getWindow().localStorage;

function setMock() {
  storage = {
    storage: {},
    setItem: function (name, value) {
      this.storage[name] = value;
    },
    getItem: function (name) {
      return this.storage[name];
    },
    removeItem: function (name) {
      delete this.storage[name];
    },
  };
}

function setItem(name, value) {
  storage.setItem(name, value);
}

function getItem(name, defaultVal) {
  var value = storage.getItem(name);
  if (!value) {
    value = defaultVal;
  }
  return value;
}

function removeItem(name) {
  return storage.removeItem(name);
}

if (typeof storage === "undefined") {
  console.warn("HTML5 localStorage not supported by this browser.");
  // mock it...
  setMock();
}

export default {
  setMock,
  setItem,
  getItem,
  removeItem,
};
