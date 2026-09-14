import { defineConfig } from "vitest/config";
import { fileURLToPath } from "node:url";

export default defineConfig({
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src/main/js", import.meta.url)),
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
