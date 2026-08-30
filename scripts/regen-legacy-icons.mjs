import { Resvg } from "@resvg/resvg-js";
import { writeFileSync, mkdirSync, unlinkSync, readdirSync } from "fs";
import { join } from "path";

function androidColor(hex) {
  const a = parseInt(hex.slice(1, 3), 16) / 255;
  const r = parseInt(hex.slice(3, 5), 16);
  const g = parseInt(hex.slice(5, 7), 16);
  const b = parseInt(hex.slice(7, 9), 16);
  return { r, g, b, a };
}
function fill(hex) {
  const c = androidColor(hex);
  if (c.a === 0) return "none";
  if (c.a === 1) return `rgb(${c.r},${c.g},${c.b})`;
  return `rgba(${c.r},${c.g},${c.b},${c.a.toFixed(2)})`;
}
function stroke(hex) {
  return fill(hex);
}

// Full launcher icon (background + foreground) WITHOUT any trademarked
// PlayStation (Sony) button glyphs. Source of truth for the launcher icon.
function buildSvg(size) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 108 108">
  <defs>
    <clipPath id="safe-zone"><circle cx="54" cy="54" r="54"/></clipPath>
  </defs>
  <g clip-path="url(#safe-zone)">
    <rect x="0" y="0" width="108" height="108" fill="${fill("#FF0A0A1A")}"/>
    <line x1="0" y1="27" x2="108" y2="27" stroke="${stroke("#0D7C3AED")}" stroke-width="0.3"/>
    <line x1="0" y1="54" x2="108" y2="54" stroke="${stroke("#0D7C3AED")}" stroke-width="0.3"/>
    <line x1="0" y1="81" x2="108" y2="81" stroke="${stroke("#0D7C3AED")}" stroke-width="0.3"/>
    <line x1="27" y1="0" x2="27" y2="108" stroke="${stroke("#0D7C3AED")}" stroke-width="0.3"/>
    <line x1="54" y1="0" x2="54" y2="108" stroke="${stroke("#0D7C3AED")}" stroke-width="0.3"/>
    <line x1="81" y1="0" x2="81" y2="108" stroke="${stroke("#0D7C3AED")}" stroke-width="0.3"/>
    <line x1="0" y1="0" x2="20" y2="20" stroke="${stroke("#337C3AED")}" stroke-width="0.5"/>
    <line x1="108" y1="0" x2="88" y2="20" stroke="${stroke("#3306B6D4")}" stroke-width="0.5"/>
    <line x1="0" y1="108" x2="20" y2="88" stroke="${stroke("#33E11D48")}" stroke-width="0.5"/>
    <line x1="108" y1="108" x2="88" y2="88" stroke="${stroke("#337C3AED")}" stroke-width="0.5"/>
    <circle cx="54" cy="54" r="36" fill="${fill("#FF7C3AED")}"/>
    <circle cx="54" cy="54" r="32" fill="${fill("#FF0A0A1A")}"/>
    <circle cx="54" cy="54" r="26" fill="${fill("#FF1E1E3F")}"/>
    <path d="M30,40 Q54,20 78,40" fill="none" stroke="${stroke("#33FFFFFF")}" stroke-width="1.5"/>
    <circle cx="54" cy="54" r="22" fill="none" stroke="${stroke("#1A7C3AED")}" stroke-width="0.4"/>
    <circle cx="54" cy="54" r="18" fill="none" stroke="${stroke("#1A7C3AED")}" stroke-width="0.4"/>
    <circle cx="54" cy="54" r="14" fill="none" stroke="${stroke("#1A7C3AED")}" stroke-width="0.4"/>
    <circle cx="54" cy="54" r="7" fill="${fill("#FF0A0A1A")}"/>
    <circle cx="54" cy="54" r="5" fill="none" stroke="${stroke("#FF7C3AED")}" stroke-width="1"/>
    <path d="M34,46 C34,38 42,36 50,36 L58,36 C66,36 74,38 74,46 L74,54 C74,64 70,70 64,72 L60,72 C58,72 56,68 56,64 L52,64 C52,68 50,72 48,72 L44,72 C40,70 34,64 34,54 Z"
          fill="${fill("#FF12122A")}" stroke="${stroke("#FF7C3AED")}" stroke-width="1.5"/>
    <line x1="44" y1="45" x2="44" y2="55" stroke="${stroke("#FFFFFFFF")}" stroke-width="3" stroke-linecap="round"/>
    <line x1="39" y1="50" x2="49" y2="50" stroke="${stroke("#FFFFFFFF")}" stroke-width="3" stroke-linecap="round"/>
    <circle cx="64" cy="46" r="2.2" fill="${fill("#FF22C55E")}"/>
    <circle cx="68" cy="50" r="2.2" fill="${fill("#FFE11D48")}"/>
    <circle cx="64" cy="54" r="2.2" fill="${fill("#FF3B82F6")}"/>
    <circle cx="60" cy="50" r="2.2" fill="${fill("#FFFFC107")}"/>
    <circle cx="46" cy="60" r="3" fill="${fill("#FF2A2A3E")}" stroke="${stroke("#FF555577")}" stroke-width="1"/>
    <circle cx="62" cy="60" r="3" fill="${fill("#FF2A2A3E")}" stroke="${stroke("#FF555577")}" stroke-width="1"/>
  </g>
</svg>`;
}

const densities = [
  { dir: "mipmap-mdpi", size: 48 },
  { dir: "mipmap-hdpi", size: 72 },
  { dir: "mipmap-xhdpi", size: 96 },
  { dir: "mipmap-xxhdpi", size: 144 },
  { dir: "mipmap-xxxhdpi", size: 192 },
];

const resDir = join(process.cwd(), "app", "src", "main", "res");

for (const d of densities) {
  const folder = join(resDir, d.dir);
  mkdirSync(folder, { recursive: true });
  // Remove the old (trademarked) webp icons so only the corrected PNG is used.
  for (const name of ["ic_launcher", "ic_launcher_round"]) {
    const webp = join(folder, name + ".webp");
    try { unlinkSync(webp); } catch (e) { /* not present */ }
    const resvg = new Resvg(buildSvg(d.size), {
      fitTo: { mode: "width", value: d.size },
      background: "rgba(10,10,26,1)",
    });
    const png = resvg.render().asPng();
    writeFileSync(join(folder, name + ".png"), png);
    console.log(`Wrote ${join(folder, name + ".png")}`);
  }
}

console.log("Legacy launcher icons regenerated (PlayStation symbols removed).");
