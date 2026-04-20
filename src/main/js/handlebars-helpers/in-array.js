export default function inArray(arr, val, options) {
  if (arr.indexOf(val) >= 0) {
    return options.fn();
  }
}
