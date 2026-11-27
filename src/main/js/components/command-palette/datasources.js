import { LinkResult } from "./models";
import Search from "@/api/search";

export const JenkinsSearchSource = {
  execute(query, scope) {
    const rootUrl = document.head.dataset.rooturl;

    function correctAddress(url) {
      if (url.startsWith("/")) {
        url = url.substring(1);
      }

      return rootUrl + "/" + url;
    }

    return Search.search(query, scope?.url).then((rsp) => {
      if (!rsp.ok) {
        return [];
      }

      return rsp.json().then((data) => {
        return data["suggestions"].slice().map((e) =>
          LinkResult({
            icon: e.icon,
            type: e.type,
            label: e.name,
            description: e.description,
            url: correctAddress(e.url),
            group: e.group,
            searchScope: e.searchScope,
            parent: e.parent,
          }),
        );
      });
    });
  },
};
