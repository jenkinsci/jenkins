/* eslint no-undef: 0 */

const path = require("path");
const MiniCSSExtractPlugin = require("mini-css-extract-plugin");
const CssMinimizerPlugin = require("css-minimizer-webpack-plugin");
const RemoveEmptyScriptsPlugin = require("webpack-remove-empty-scripts");
const CopyPlugin = require("copy-webpack-plugin");
const { CleanWebpackPlugin: CleanPlugin } = require("clean-webpack-plugin");

module.exports = (env, argv) => ({
  mode: "development",
  entry: {
    "page-init": [path.join(__dirname, "src/main/js/page-init.js")],
    pluginSetupWizard: [
      path.join(__dirname, "src/main/js/pluginSetupWizard.js"),
      path.join(__dirname, "src/main/less/pluginSetupWizard.less"),
    ],
    "plugin-manager-ui": [
      path.join(__dirname, "src/main/js/plugin-manager-ui.js"),
    ],
    "add-item": [
      path.join(__dirname, "src/main/js/add-item.js"),
      path.join(__dirname, "src/main/js/add-item.less"),
    ],
    "config-scrollspy": [
      path.join(__dirname, "src/main/js/config-scrollspy.js"),
      path.join(__dirname, "src/main/js/config-scrollspy.less"),
    ],
    "config-tabbar": [
      path.join(__dirname, "src/main/js/config-tabbar.js"),
      path.join(__dirname, "src/main/js/config-tabbar.less"),
    ],
    app: [path.join(__dirname, "src/main/js/app.js")],
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
    "filter-build-history": [
      path.join(__dirname, "src/main/js/filter-build-history.js"),
    ],
    "simple-page": [path.join(__dirname, "src/main/less/simple-page.less")],
    styles: [path.join(__dirname, "src/main/less/styles.less")],
  },
  output: {
    path: path.join(__dirname, "src/main/webapp/jsbundles"),
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
    new CopyPlugin({
      // Copies fonts to the src/main/webapp/css for compat purposes
      // Some plugins or parts of the UI try to load them from these paths
      patterns: [
        {
          context: "src/main/fonts",
          from: "**/*",
          to: path.join(__dirname, "src/main/webapp/css"),
        },
      ],
    }),
    // Clean all assets within the specified output.
    // It will not clean copied fonts
    new CleanPlugin(),
  ],
  module: {
    rules: [
      {
        test: /\.(css|less)$/,
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
              // ignore the URLS on the base styles as they are picked
              // from the src/main/webapp/images dir
              url: {
                filter: (url, resourcePath) => {
                  return !resourcePath.includes("styles.less");
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
            loader: "less-loader",
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
