# jenkins-web-components


# UI 

The UI is ready to be consumed in a jenkins plugin and will be automatic created when you run `mvn install`. 
However, you can use different environments for development.

This webcomponent follows the [open-wc](https://github.com/open-wc/open-wc) recommendation.

Our components are developed with [lit](https://lit.dev/docs/)

## Prepare

Run `yarn` if you have not run `mvn install`.

## Development 

For rapid development, we recommend to use the local demo environment and 
as soon you want to test on the server run the `build`

### Local Demo with `parcel`

```bash
yarn start

Server running at http://localhost:1234
```

To run a local development server that serves the basic demo located in `index.html`

## Build for jenkins 
You can build the project with the `yarn build` command which will invoke `yarn css:build` and then will use `parcel` to package the build into one file `dist/index.ts` which contains all the necessary information for css and js (as well registering new web components).

This is the file your main `jelly` should import and normally a hard refresh on the jenkins plugin should update the view after you have invoked the build again.

```jelly
<script type="module" src="${resURL}/plugin/$PLUGIN~NAME/js/index.js" />
<link rel="stylesheet" href="${resURL}/plugin/$PLUGIN~NAME/js/index.css" type="text/css" />
```

### Create a new component

```bash
yarn :add newcomponent [ -t componentType || 'components' ]
```

This will create a new component (or the `componentType` you have chosen) and link it in the project hierarchy. 
If you do not define it we will fallback to `process.env.SWC_PREFIX` or `components`

For example if you want to create a new view you can do:

```bash
yarn :add myview -t views
```

### Styles

We use [tailwind](https://tailwindcss.com/docs) as underlying framework, you can either use it in your html/js or use it in the css file of the component.
We need the css file to exist since it will trigger the generation of the style definition file (it is heavily bootstrapped to only include the styles we need for the component). All components are using shadow DOM hence need to encapsulate their styles, since there is no leakage between the overall page and the component itself. e.g. you can have 2 classes with the same name defined but they are only in the scope of themselves not changing the other web component.

### husky integration

In case your project is in the top level you can activate husky support by adding the following to the script section of your `package.json`:

```package.json
"prepare": "husky install",
```
