import { LinkResult } from "./models";
import Search from "@/api/search";

export const JenkinsSearchSource = {
  execute(query) {
    const rootUrl = document.head.dataset.rooturl;

    function correctAddress(url) {
      if (url.startsWith("/")) {
        url = url.substring(1);
      }

      return rootUrl + "/" + url;
    }

    return Search.search(query).then((rsp) =>
      rsp.json().then((data) => {
        return data["suggestions"].slice().map((e) =>
          LinkResult({
            icon: e.iconXml,
            type: e.type,
            label: e.name,
            url: correctAddress(e.url),
            group: e.group,
          }),
        );
      }),
    );
  },
};
