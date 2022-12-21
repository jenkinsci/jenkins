import jenkins from "@/util/jenkins";

export function getSymbol(symbol, handler) {
  jenkins.get(
    "/symbols?symbol=" + symbol,
    function (res) {
      handler(res.data["symbol"]);
    },
    { async: false }
  );
}
