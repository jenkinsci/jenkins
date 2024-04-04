function xmlEscape(str) {
  if (str == null) {
    return "";
  }

  return str.replace(/[<>&'"]/g, (match) => {
    switch (match) {
      case "<":
        return "&lt;";
      case ">":
        return "&gt;";
      case "&":
        return "&amp;";
      case "'":
        return "&apos;";
      case '"':
        return "&quot;";
    }
  });
}

export { xmlEscape };
