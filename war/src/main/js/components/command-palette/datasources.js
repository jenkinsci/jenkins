import { LinkResult } from "./models";
import Search from "@/api/search";
import * as Symbols from "./symbols";

export const JenkinsSearchSource = {
  async execute(query) {
    const rootUrl = document.head.dataset.rooturl;
    const response = await Search.search(query);

    function correctAddress(url) {
      if (url.startsWith("/")) {
        url = url.substring(1);
      }

      return rootUrl + "/" + url;
    }

    return await response.json().then((data) => {
      return data["suggestions"].slice().map((e) =>
        LinkResult({
          icon: Symbols.SEARCH,
          label: e.name,
          url: correctAddress(e.url),
        }),
      );
    });
  },
};
