export default function ifneq(o1, o2, options) {
  if (o1 !== o2) {
    return options.fn();
  }
}
