const path = require('path');
const MiniCSSExtractPlugin = require('mini-css-extract-plugin');
const FixStyleOnlyEntriesPlugin = require("webpack-fix-style-only-entries");

module.exports = {
  mode: 'development',
  entry: {
    "page-init": [path.join(__dirname, "src/main/js/page-init.js")],
    "pluginSetupWizard": [
      path.join(__dirname, "src/main/js/pluginSetupWizard.js"),
      path.join(__dirname, "src/main/less/pluginSetupWizard.less"),
    ],
    "upgradeWizard": [path.join(__dirname, "src/main/js/upgradeWizard.js")],
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
  },
  output: {
    path: path.join(__dirname, "src/main/webapp/jsbundles"),
  },
  plugins: [
    new FixStyleOnlyEntriesPlugin(),
    new MiniCSSExtractPlugin({
      filename: "[name].css",
    }),
  ],
  module: {
    rules: [
      {
        test: /\.(css|less)$/,
        loader: [MiniCSSExtractPlugin.loader, "css-loader", "less-loader"]
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
          helperDirs: path.join(__dirname, 'src/main/js/handlebars-helpers'),
          precompileOptions: {
            knownHelpersOnly: false,
            // Helpers registered with Handlebars.registerHelper must be listed so that
            // handlebars-loader will expect them when compiling the templates
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
}
