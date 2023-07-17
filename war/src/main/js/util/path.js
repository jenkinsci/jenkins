function combinePath(pathOne, pathTwo) {
  let queryParams;
  let i = pathOne.indexOf("?");
  if (i >= 0) {
    queryParams = pathOne.substring(i);
  } else {
    queryParams = "";
  }

  i = pathOne.indexOf("#");
  if (i >= 0) {
    pathOne = pathOne.substring(0, i);
  }

  if (pathOne.endsWith("/")) {
    return pathOne + pathTwo + queryParams;
  }
  return pathOne + "/" + pathTwo + queryParams;
}

export default { combinePath };
