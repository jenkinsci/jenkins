import { LitElement, html } from 'lit';
import { customElement, property } from 'lit/decorators'
import Styles from './Checkbox.styles';

@customElement('jenkins-checkbox')
export class Checkbox extends LitElement {
  static styles = [ Styles ];

  @property({ type: String }) field: string;
  @property({ type: String }) id: string;
  @property({ type: String }) name: string;
  @property({ type: String }) title: string;
  @property({ type: String }) tooltip: string;
  @property({ type: String }) value: string;
  @property({ type: Boolean }) checked: boolean;
  @property({ type: Boolean }) readonly: boolean;
  @property({ type: Boolean }) default: boolean;
  @property({ type: Boolean }) json: boolean;


  render() {
    return html`<span class="jenkins-checkbox">
      <input type="checkbox"
        name="${this.name}"
        value="${this.value}"
        title="${this.tooltip}"
        id="${this.id}"
        json="${this.json}"
        ?disabled="${this.readonly}"
        ?checked="${this.checked}"/>
      <label class="attach-previous ${this.title == null ? 'js-checkbox-label-empty' : ''}"
        title="${this.tooltip}">${this.title}</label>
  </span>`;
  }
}
