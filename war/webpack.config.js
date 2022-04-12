/* eslint no-undef: 0 */

const path = require('path');
const MiniCSSExtractPlugin = require('mini-css-extract-plugin');
const FixStyleOnlyEntriesPlugin = require('webpack-fix-style-only-entries');
const CopyPlugin = require('copy-webpack-plugin');
const { CleanWebpackPlugin: CleanPlugin } = require('clean-webpack-plugin');

module.exports = (env, argv) => ({
  mode: 'development',
  entry: {
    "page-init": [path.join(__dirname, "src/main/js/page-init.js")],
    "pluginSetupWizard": [
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
    "sortable-drag-drop": [path.join(__dirname, "src/main/js/sortable-drag-drop.js")],
    "section-to-tabs": [path.join(__dirname, "src/main/js/section-to-tabs.js")],
    "filter-build-history": [path.join(__dirname, "src/main/js/filter-build-history.js")],

    // New UI CSS files
    "base-styles-v2": [path.join(__dirname, "src/main/less/base-styles-v2.less")],
  },
  output: {
    path: path.join(__dirname, "src/main/webapp/jsbundles"),
  },
  devtool: argv.mode === 'production' ? 'source-map' : 'inline-cheap-module-source-map',
  plugins: [
    new FixStyleOnlyEntriesPlugin(),
    new MiniCSSExtractPlugin({
      filename: "[name].css",
    }),
    new CopyPlugin([
      // Copies fonts to the src/main/webapp/css for compat purposes
      // Some plugins or parts of the UI try to load them from these paths
      {
        context: 'src/main/fonts',
        from: "**/*",
        to: path.join(__dirname, "src/main/webapp/css")
      }
    ]),
    // Clean all assets within the specified output.
    // It will not clean copied fonts
    new CleanPlugin(),
  ],
  module: {
    rules: [
      {
        test: /\.(css|less)$/,
        use: [
          'style-loader',
          {
            loader: MiniCSSExtractPlugin.loader,
            options: {
              sourceMap: true
            }
          },
          {
            loader: 'css-loader',
            options: {
              sourceMap: true,
              url: (url, resourcePath) => {
                // ignore the URLS on the base styles as they are picked
                // from the src/main/webapp/images dir
                if (resourcePath.includes('base-styles-v2.less')) {
                  return false;
                }

                return true;
              }
            }
          },
          {
            loader: 'postcss-loader',
            options: {
              sourceMap: true
            }
          },
          {
            loader: 'less-loader',
            options: {
              sourceMap: true
            }
          }
        ]
      },
      {
        test: /\.(woff(2)?|ttf|eot|svg)(\?v=\d+\.\d+\.\d+)?$/,
        use: [
          {
            loader: 'file-loader',
            options: {
              name: '[name].[ext]',
              outputPath: 'fonts/'
            }
          }
        ]
      },
      {
        test: /\.hbs$/,
        loader: "handlebars-loader",
        options: {
          // The preferred option for adding handlebars helpers is putting them
          // inside this helpers directory
          helperDirs: path.join(__dirname, 'src/main/js/handlebars-helpers'),
          precompileOptions: {
            knownHelpersOnly: false,
            // Helpers registered with Handlebars.registerHelper must be listed so that
            // handlebars-loader will expect them when compiling the templates.
            // This helpers cannot be moved to the helpers directory because they are closures
            knownHelpers: [
              'pluginCountForCategory',
              'totalPluginCount',
              'inSelectedPlugins',
              'dependencyCount',
              'eachDependency',
              'ifVisibleDependency'
            ]
          },
        },
      },
      {
        test: /\.js$/,
        exclude: /node_modules/,
        loader: "babel-loader",
      },
    ]
  },
  optimization: {
    splitChunks: {
       chunks: 'async',
       cacheGroups: {
         commons: {
           test: /[\\/]node_modules[\\/]/,
           name: 'vendors',
           chunks: 'all'
         }
       }
    }
  },
  resolve: {
    alias:{
      // Needed to be able to register helpers at runtime
      handlebars: 'handlebars/runtime',
    },
  },
});
