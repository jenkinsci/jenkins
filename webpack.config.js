const path = require("path");
const MiniCSSExtractPlugin = require("mini-css-extract-plugin");
const CssMinimizerPlugin = require("css-minimizer-webpack-plugin");
const RemoveEmptyScriptsPlugin = require("webpack-remove-empty-scripts");
const { CleanWebpackPlugin: CleanPlugin } = require("clean-webpack-plugin");

module.exports = (env, argv) => ({
  mode: "development",
  entry: {
    pluginSetupWizard: [
      path.join(__dirname, "src/main/js/pluginSetupWizard.js"),
      path.join(__dirname, "src/main/scss/pluginSetupWizard.scss"),
    ],
    "plugin-manager-ui": [
      path.join(__dirname, "src/main/js/plugin-manager-ui.js"),
    ],
    "add-item": [
      path.join(__dirname, "src/main/js/add-item.js"),
      path.join(__dirname, "src/main/js/add-item.scss"),
    ],
    "pages/computer-set": [
      path.join(__dirname, "src/main/js/pages/computer-set"),
    ],
    "pages/dashboard": [path.join(__dirname, "src/main/js/pages/dashboard")],
    "pages/manage-jenkins/system-information": [
      path.join(
        __dirname,
        "src/main/js/pages/manage-jenkins/system-information",
      ),
    ],
    app: [path.join(__dirname, "src/main/js/app.js")],
    header: [path.join(__dirname, "src/main/js/components/header/index.js")],
    "pages/cloud-set": [
      path.join(__dirname, "src/main/js/pages/cloud-set/index.js"),
      path.join(__dirname, "src/main/js/pages/cloud-set/index.scss"),
    ],
    "pages/manage-jenkins": [
      path.join(__dirname, "src/main/js/pages/manage-jenkins"),
    ],
    "pages/register": [path.join(__dirname, "src/main/js/pages/register")],
    "keyboard-shortcuts": [
      path.join(__dirname, "src/main/js/keyboard-shortcuts.js"),
    ],
    "sortable-drag-drop": [
      path.join(__dirname, "src/main/js/sortable-drag-drop.js"),
    ],
    "section-to-sidebar-items": [
      path.join(__dirname, "src/main/js/section-to-sidebar-items.js"),
    ],
    "section-to-tabs": [path.join(__dirname, "src/main/js/section-to-tabs.js")],
    "components/row-selection-controller": [
      path.join(__dirname, "src/main/js/components/row-selection-controller"),
    ],
    "pages/project/builds-card": [
      path.join(__dirname, "src/main/js/pages/project/builds-card.js"),
    ],
    "simple-page": [path.join(__dirname, "src/main/scss/simple-page.scss")],
    styles: [path.join(__dirname, "src/main/scss/styles.scss")],
  },
  output: {
    path: path.join(__dirname, "war/src/main/webapp/jsbundles"),
  },
  devtool:
    argv.mode === "production"
      ? "source-map"
      : "inline-cheap-module-source-map",
  plugins: [
    new RemoveEmptyScriptsPlugin({}),
    new MiniCSSExtractPlugin({
      filename: "[name].css",
    }),
    // Clean all assets within the specified output.
    // It will not clean copied fonts
    new CleanPlugin(),
  ],
  module: {
    rules: [
      {
        test: /\.(css|scss)$/,
        use: [
          "style-loader",
          {
            loader: MiniCSSExtractPlugin.loader,
            options: {
              esModule: false,
            },
          },
          {
            loader: "css-loader",
            options: {
              sourceMap: true,
              // ignore the URLS on the base styles and mask images as they are picked
              // from the war/src/main/webapp/images dir
              url: {
                filter: (url, resourcePath) => {
                  return (
                    !resourcePath.includes("styles.scss") &&
                    !url.includes("../images/svgs/")
                  );
                },
              },
            },
          },
          {
            loader: "postcss-loader",
            options: {
              sourceMap: true,
            },
          },
          {
            loader: "sass-loader",
            options: {
              sourceMap: true,
            },
          },
        ],
      },
      {
        test: /\.(woff(2)?|ttf|eot|svg)(\?v=\d+\.\d+\.\d+)?$/,
        type: "asset/resource",
        generator: {
          filename: "fonts/[name].[ext]",
        },
      },
      {
        test: /\.hbs$/,
        loader: "handlebars-loader",
        options: {
          // The preferred option for adding handlebars helpers is putting them
          // inside this helpers directory
          helperDirs: path.join(__dirname, "src/main/js/handlebars-helpers"),
          precompileOptions: {
            knownHelpersOnly: false,
            // Helpers registered with Handlebars.registerHelper must be listed so that
            // handlebars-loader will expect them when compiling the templates.
            // This helpers cannot be moved to the helpers directory because they are closures
            knownHelpers: [
              "pluginCountForCategory",
              "totalPluginCount",
              "inSelectedPlugins",
              "dependencyCount",
              "eachDependency",
              "ifVisibleDependency",
            ],
          },
        },
      },
      {
        test: /\.js$/,
        exclude: /node_modules/,
        loader: "babel-loader",
      },
    ],
  },
  optimization: {
    splitChunks: {
      chunks: "async",
      cacheGroups: {
        commons: {
          test: /[\\/]node_modules[\\/]/,
          name: "vendors",
          chunks: "all",
        },
      },
    },
    minimizer: [
      new CssMinimizerPlugin({
        minimizerOptions: {
          preset: [
            "default",
            {
              svgo: { exclude: true },
            },
          ],
        },
      }),
    ],
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src/main/js"),
      // Needed to be able to register helpers at runtime
      handlebars: "handlebars/runtime",
    },
  },
});
