export default function replace() {
  var val = arguments[0];
  // second, through second to last - options is last
  for (var i = 1; i < arguments.length - 1; i++) {
    val = val.replace("{" + (i - 1) + "}", arguments[i]);
  }
  return val;
}
