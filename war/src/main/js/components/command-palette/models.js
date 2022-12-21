import * as Symbols from "./symbols";

export class LinkResult {
  constructor(icon, label, description, category, url, isExternal) {
    this.icon = icon;
    this.label = label;
    this.description = description;
    this.category = category;
    this.url = url;
    this.isExternal = isExternal;
  }
  render() {
    return `<a class="jenkins-command-palette__results__item" href="${
      this.url
    }">
        <div class="jenkins-command-palette__results__item__icon">${this.icon}</div>
        ${this.label}
        ${
          this.description
            ? `<span class="jenkins-command-palette__results__item__description">${this.description}</span>`
            : ``
        }
        ${this.isExternal ? Symbols.EXTERNAL_LINK : ""}
    </a>`;
  }
}
