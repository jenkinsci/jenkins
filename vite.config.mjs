import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import Handlebars from "handlebars";
import { createLogger, defineConfig, minify } from "vite";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const js = (p) => path.join(__dirname, "src/main/js", p);
const scss = (p) => path.join(__dirname, "src/main/scss", p);

export const entries = {
  pluginSetupWizard: [
    js("pluginSetupWizard.js"),
    scss("pluginSetupWizard.scss"),
  ],
  "plugin-manager-ui": [js("plugin-manager-ui.js")],
  "add-item": [js("add-item.js"), js("add-item.scss")],
  "pages/computer-set": [js("pages/computer-set/index.js")],
  "pages/dashboard": [js("pages/dashboard/index.js")],
  "pages/manage-jenkins/system-information": [
    js("pages/manage-jenkins/system-information/index.js"),
  ],
  app: [js("app.js")],
  header: [js("components/header/index.js")],
  "pages/cloud-set": [
    js("pages/cloud-set/index.js"),
    js("pages/cloud-set/index.scss"),
  ],
  "pages/manage-jenkins": [js("pages/manage-jenkins/index.js")],
  "pages/register": [js("pages/register/index.js")],
  "keyboard-shortcuts": [js("keyboard-shortcuts.js")],
  "sortable-drag-drop": [js("sortable-drag-drop.js")],
  "section-to-sidebar-items": [js("section-to-sidebar-items.js")],
  "section-to-tabs": [js("section-to-tabs.js")],
  "components/row-selection-controller": [
    js("components/row-selection-controller/index.js"),
  ],
  "pages/project/builds-card": [js("pages/project/builds-card.js")],
  "pages/project/builds-card-new": [js("pages/project/builds-card-new.js")],
  "pages/project/build": [js("pages/project/build.js")],
  "simple-page": [scss("simple-page.scss")],
  styles: [scss("styles.scss")],
};

const helperDir = js("handlebars-helpers");
const helpers = fs
  .readdirSync(helperDir)
  .filter((f) => f.endsWith(".js"))
  .map((f) => path.basename(f, ".js"));

const runtimeHelpers = [
  "pluginCountForCategory",
  "totalPluginCount",
  "inSelectedPlugins",
  "dependencyCount",
  "eachDependency",
  "ifVisibleDependency",
];

function handlebarsPlugin() {
  return {
    name: "jenkins-handlebars",
    transform(source, id) {
      if (!id.endsWith(".hbs")) {
        return null;
      }
      const used = helpers.filter((h) =>
        new RegExp(`{{[#^/]?\\s*${h}\\b`).test(source),
      );
      const spec = Handlebars.precompile(source, {
        knownHelpersOnly: false,
        knownHelpers: Object.fromEntries(
          [...used, ...runtimeHelpers].map((h) => [h, true]),
        ),
      });
      const imports = used
        .map(
          (h, i) =>
            `import helper${i} from ${JSON.stringify(path.join(helperDir, `${h}.js`))};\nHandlebars.registerHelper(${JSON.stringify(h)}, helper${i});`,
        )
        .join("\n");
      return {
        code: `import Handlebars from "handlebars/runtime";\n${imports}\nexport default Handlebars.template(${spec});\n`,
        map: null,
      };
    },
  };
}

function entryPlugin(name) {
  const id = `\0jenkins-entry:${name}`;
  return {
    name: "jenkins-entry",
    resolveId(source) {
      return source === id ? id : null;
    },
    load(source) {
      if (source !== id) {
        return null;
      }
      return entries[name]
        .map((f) => `import ${JSON.stringify(f)};`)
        .join("\n");
    },
  };
}

function removeEmptyScriptsPlugin() {
  return {
    name: "jenkins-remove-empty-scripts",
    enforce: "post",
    generateBundle(_, bundle) {
      for (const [file, chunk] of Object.entries(bundle)) {
        if (chunk.type === "chunk" && chunk.isEntry && !chunk.code.trim()) {
          delete bundle[file];
        }
      }
    },
  };
}

const logger = createLogger("warn");
for (const method of ["warn", "warnOnce"]) {
  const original = logger[method];
  logger[method] = (msg, options) => {
    if (msg.includes("didn't resolve at build time")) {
      return;
    }
    original(msg, options);
  };
}

const legacyScripts = ["behavior.js", "hudson-behavior.js", "sortable.js"].map(
  (file) => path.join(__dirname, "war/src/main/webapp/scripts", file),
);

function identityMap(file, code) {
  const lines = code.split("\n").length;
  return {
    version: 3,
    sources: [file],
    sourcesContent: [code],
    names: [],
    mappings: ["AAAA", ...Array(lines - 1).fill("AACA")].join(";"),
  };
}

function legacyScriptsPlugin(mode) {
  return {
    name: "jenkins-legacy-scripts",
    buildStart() {
      legacyScripts.forEach((file) => this.addWatchFile(file));
    },
    async generateBundle() {
      const parts = await Promise.all(
        legacyScripts.map(async (file) => {
          const source = path.relative(__dirname, file);
          const code = fs.readFileSync(file, "utf8");
          if (mode !== "production") {
            return { code, map: identityMap(source, code) };
          }
          const result = await minify(source, code, {
            module: false,
            sourcemap: true,
          });
          if (result.errors.length) {
            this.error(`${source}: ${result.errors[0].message}`);
          }
          return {
            code: result.code,
            map: { ...result.map, sources: [source], sourcesContent: [code] },
          };
        }),
      );
      let line = 0;
      const sections = parts.map((part) => {
        const section = { offset: { line, column: 0 }, map: part.map };
        line += part.code.split("\n").length;
        return section;
      });
      const map = JSON.stringify({ version: 3, file: "legacy.js", sections });
      const code = parts.map((part) => part.code).join("\n");
      if (mode !== "production") {
        this.emitFile({
          type: "asset",
          fileName: "legacy.js",
          source: `${code}\n//# sourceMappingURL=data:application/json;base64,${Buffer.from(map).toString("base64")}\n`,
        });
        return;
      }
      this.emitFile({ type: "asset", fileName: "legacy.js.map", source: map });
      this.emitFile({
        type: "asset",
        fileName: "legacy.js",
        source: `${code}\n//# sourceMappingURL=legacy.js.map\n`,
      });
    },
  };
}

export function entryConfig(name, mode) {
  return defineConfig({
    configFile: false,
    mode,
    customLogger: logger,
    logLevel: "warn",
    publicDir: false,
    plugins: [
      entryPlugin(name),
      handlebarsPlugin(),
      removeEmptyScriptsPlugin(),
      name === "app" && legacyScriptsPlugin(mode),
    ],
    resolve: {
      alias: [
        { find: "@", replacement: js("") },
        { find: /^handlebars$/, replacement: "handlebars/runtime" },
      ],
    },
    css: {
      devSourcemap: true,
      preprocessorOptions: {
        scss: {
          importer: { findFileUrl: () => null },
        },
      },
    },
    build: {
      outDir: path.join(__dirname, "war/src/main/webapp/jsbundles"),
      emptyOutDir: false,
      sourcemap: mode === "production" ? true : "inline",
      minify: mode === "production",
      cssMinify: mode === "production",
      cssCodeSplit: false,
      reportCompressedSize: false,
      rolldownOptions: {
        input: { [name]: `\0jenkins-entry:${name}` },
        output: {
          format: "iife",
          entryFileNames: "[name].js",
          assetFileNames: (asset) =>
            asset.names?.some((n) => n.endsWith(".css"))
              ? `${name}.css`
              : "fonts/[name][extname]",
        },
      },
    },
  });
}
