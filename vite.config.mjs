import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import Handlebars from "handlebars";
import { createLogger, defineConfig } from "vite";

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

const entryPrefix = "\0jenkins-entry:";

function entryPlugin() {
  return {
    name: "jenkins-entry",
    resolveId(source) {
      return source.startsWith(entryPrefix) ? source : null;
    },
    load(source) {
      if (!source.startsWith(entryPrefix)) {
        return null;
      }
      return entries[source.slice(entryPrefix.length)]
        .map((f) => `import ${JSON.stringify(f)};`)
        .join("\n");
    },
  };
}

function vendorChunkName(id) {
  const match = id.match(/node_modules\/((?:@[^/]+\/)?[^/]+)/);
  if (!match) {
    return null;
  }
  if (match[1] === "@popperjs/core") {
    return "tippy";
  }
  return match[1].replace(/\.js$/, "");
}

function hoistTransitiveImportsPlugin() {
  return {
    name: "jenkins-hoist-transitive-imports",
    renderChunk(code, chunk, _, meta) {
      if (!chunk.isEntry) {
        return null;
      }
      const seen = new Set(chunk.imports);
      const queue = [...chunk.imports];
      while (queue.length) {
        for (const file of meta.chunks[queue.shift()]?.imports ?? []) {
          if (!seen.has(file)) {
            seen.add(file);
            queue.push(file);
          }
        }
      }
      const extra = [...seen]
        .filter((file) => !chunk.imports.includes(file))
        .map((file) => {
          const relative = path.posix.relative(
            path.posix.dirname(chunk.fileName),
            file,
          );
          return `import ${JSON.stringify(relative.startsWith(".") ? relative : `./${relative}`)};`;
        });
      if (!extra.length) {
        return null;
      }
      return { code: `${code}\n${extra.join("\n")}\n`, map: null };
    },
  };
}

function removeEmptyScriptsPlugin() {
  return {
    name: "jenkins-remove-empty-scripts",
    enforce: "post",
    generateBundle(_, bundle) {
      for (const [file, chunk] of Object.entries(bundle)) {
        if (chunk.type !== "chunk" || !chunk.isEntry) {
          continue;
        }
        for (const css of chunk.viteMetadata?.importedCss ?? []) {
          bundle[css].fileName = `${chunk.name}.css`;
        }
        if (!chunk.code.trim()) {
          delete bundle[file];
        }
      }
    },
  };
}

const logger = createLogger("info");
for (const method of ["warn", "warnOnce"]) {
  const original = logger[method];
  logger[method] = (msg, options) => {
    if (msg.includes("didn't resolve at build time")) {
      return;
    }
    original(msg, options);
  };
}

export default defineConfig(({ mode }) => ({
  customLogger: logger,
  publicDir: false,
  plugins: [
    entryPlugin(),
    handlebarsPlugin(),
    hoistTransitiveImportsPlugin(),
    removeEmptyScriptsPlugin(),
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
    emptyOutDir: true,
    sourcemap: mode === "production" ? true : "inline",
    minify: mode === "production",
    cssMinify: mode === "production",
    reportCompressedSize: false,
    rolldownOptions: {
      input: Object.fromEntries(
        Object.keys(entries).map((name) => [name, entryPrefix + name]),
      ),
      output: {
        format: "es",
        entryFileNames: "[name].js",
        chunkFileNames: "chunks/[name].js",
        assetFileNames: "[name][extname]",
        codeSplitting: {
          groups: [
            {
              name: vendorChunkName,
              debugName: "vendor",
              test: /node_modules/,
            },
          ],
        },
      },
    },
  },
}));
