export default function id(str) {
  return ("" + str).replace(/\W+/g, "_");
}
