import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { build } from "vite";
import { entries, entryConfig } from "./vite.config.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const mode = args.includes("--production") ? "production" : "development";
const watch = args.includes("--watch");

fs.rmSync(path.join(__dirname, "war/src/main/webapp/jsbundles"), {
  recursive: true,
  force: true,
});

const names = Object.keys(entries);
const start = performance.now();
const time = () => new Date().toLocaleTimeString();

if (!watch) {
  await Promise.all(names.map((name) => build(entryConfig(name, mode))));
  console.log(
    `Built ${names.length} bundles in ${Math.round(performance.now() - start)}ms`,
  );
  process.exit(0);
}

const pending = new Set(names);

await Promise.all(
  names.map(async (name) => {
    const config = entryConfig(name, mode);
    config.build.watch = {};
    const watcher = await build(config);
    watcher.on("event", (event) => {
      if (event.code === "ERROR") {
        console.error(`${time()} ${name} failed: ${event.error.message}`);
        return;
      }
      if (event.code !== "BUNDLE_END") {
        return;
      }
      if (!pending.has(name)) {
        console.log(`${time()} Rebuilt ${name} in ${event.duration}ms`);
        return;
      }
      pending.delete(name);
      if (pending.size === 0) {
        console.log(
          `${time()} Built ${names.length} bundles in ${Math.round(performance.now() - start)}ms, watching for changes`,
        );
      }
    });
  }),
);
