#!/usr/bin/env node
// Zips the extension into dist/safephone-chrome-extension.zip ready for upload.
// Refuses to run if src/generated/config.js is missing, since loading an
// extension without baked credentials would silently fail every gist pull.
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const root = path.resolve(__dirname, "..");
const distDir = path.join(root, "dist");
const outZip = path.join(distDir, "safephone-chrome-extension.zip");

const configPath = path.join(root, "src", "generated", "config.js");
if (!fs.existsSync(configPath)) {
  console.error(
    `Missing ${path.relative(root, configPath)}.\n` +
      `Run \`npm run build:config\` first (or set the SAFEPHONE_CLOUD_SYNC_DEFAULT_* env vars).`,
  );
  process.exit(1);
}

fs.mkdirSync(distDir, { recursive: true });
if (fs.existsSync(outZip)) fs.rmSync(outZip);

// Mirror the structure of an unpacked extension. Anything here will be visible
// inside the installed extension: keep test/ and node_modules/ out.
const include = ["manifest.json", "src", "icons"];
const args = ["-r", outZip, ...include, "-x", "src/generated/config.example.js"];

execFileSync("zip", args, { cwd: root, stdio: "inherit" });
console.log(`Wrote ${path.relative(process.cwd(), outZip)}`);
