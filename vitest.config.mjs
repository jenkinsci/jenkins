import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src/main/js"),
    },
  },
  test: {
    environment: "jsdom",
    include: ["src/test/js/**/*.test.js"],
    // published by the Jenkinsfile; kept out of */target/surefire-reports so
    // that Launchable does not try to parse it as Maven output
    reporters: ["default", "junit"],
    outputFile: { junit: "target/vitest-reports/junit.xml" },
  },
});
