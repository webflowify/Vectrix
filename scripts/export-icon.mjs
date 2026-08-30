import { Resvg } from "@resvg/resvg-js";
import { writeFileSync, mkdirSync } from "fs";
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
  if (c.a === 0) return 'none';
  if (c.a === 1) return `rgb(${c.r},${c.g},${c.b})`;
  return `rgba(${c.r},${c.g},${c.b},${c.a.toFixed(2)})`;
}

function stroke(hex) {
  return fill(hex);
}

const SIZE = 108;
const OUTPUT_SIZE = 512;

const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${OUTPUT_SIZE}" height="${OUTPUT_SIZE}" viewBox="0 0 ${SIZE} ${SIZE}">
  <defs>
    <clipPath id="safe-zone">
      <circle cx="54" cy="54" r="54"/>
    </clipPath>
  </defs>

  <g clip-path="url(#safe-zone)">
    <!-- === BACKGROUND LAYER === -->
    <!-- Deep space background -->
    <rect x="0" y="0" width="${SIZE}" height="${SIZE}" fill="${fill('#FF0A0A1A')}"/>

    <!-- Subtle grid lines -->
    <line x1="0" y1="27" x2="108" y2="27" stroke="${stroke('#0D7C3AED')}" stroke-width="0.3"/>
    <line x1="0" y1="54" x2="108" y2="54" stroke="${stroke('#0D7C3AED')}" stroke-width="0.3"/>
    <line x1="0" y1="81" x2="108" y2="81" stroke="${stroke('#0D7C3AED')}" stroke-width="0.3"/>
    <line x1="27" y1="0" x2="27" y2="108" stroke="${stroke('#0D7C3AED')}" stroke-width="0.3"/>
    <line x1="54" y1="0" x2="54" y2="108" stroke="${stroke('#0D7C3AED')}" stroke-width="0.3"/>
    <line x1="81" y1="0" x2="81" y2="108" stroke="${stroke('#0D7C3AED')}" stroke-width="0.3"/>

    <!-- Corner accent lines -->
    <line x1="0" y1="0" x2="20" y2="20" stroke="${stroke('#337C3AED')}" stroke-width="0.5"/>
    <line x1="108" y1="0" x2="88" y2="20" stroke="${stroke('#3306B6D4')}" stroke-width="0.5"/>
    <line x1="0" y1="108" x2="20" y2="88" stroke="${stroke('#33E11D48')}" stroke-width="0.5"/>
    <line x1="108" y1="108" x2="88" y2="88" stroke="${stroke('#337C3AED')}" stroke-width="0.5"/>

    <!-- === FOREGROUND LAYER === -->
    <!-- Gradient disc outer ring -->
    <circle cx="54" cy="54" r="36" fill="${fill('#FF7C3AED')}"/>

    <!-- Dark disc body -->
    <circle cx="54" cy="54" r="32" fill="${fill('#FF0A0A1A')}"/>

    <!-- Inner disc surface -->
    <circle cx="54" cy="54" r="26" fill="${fill('#FF1E1E3F')}"/>

    <!-- Disc shine arc -->
    <path d="M30,40 Q54,20 78,40" fill="none" stroke="${stroke('#33FFFFFF')}" stroke-width="1.5"/>

    <!-- Data rings on disc -->
    <circle cx="54" cy="54" r="22" fill="none" stroke="${stroke('#1A7C3AED')}" stroke-width="0.4"/>
    <circle cx="54" cy="54" r="18" fill="none" stroke="${stroke('#1A7C3AED')}" stroke-width="0.4"/>
    <circle cx="54" cy="54" r="14" fill="none" stroke="${stroke('#1A7C3AED')}" stroke-width="0.4"/>

    <!-- Center spindle hole -->
    <circle cx="54" cy="54" r="7" fill="${fill('#FF0A0A1A')}"/>
    <circle cx="54" cy="54" r="5" fill="none" stroke="${stroke('#FF7C3AED')}" stroke-width="1"/>

    <!-- Enlarged controller body -->
    <path d="M34,46 C34,38 42,36 50,36 L58,36 C66,36 74,38 74,46 L74,54 C74,64 70,70 64,72 L60,72 C58,72 56,68 56,64 L52,64 C52,68 50,72 48,72 L44,72 C40,70 34,64 34,54 Z"
          fill="${fill('#FF12122A')}" stroke="${stroke('#FF7C3AED')}" stroke-width="1.5"/>

    <!-- D-pad -->
    <line x1="44" y1="45" x2="44" y2="55" stroke="${stroke('#FFFFFFFF')}" stroke-width="3" stroke-linecap="round"/>
    <line x1="39" y1="50" x2="49" y2="50" stroke="${stroke('#FFFFFFFF')}" stroke-width="3" stroke-linecap="round"/>

    <!-- Face buttons -->
    <circle cx="64" cy="46" r="2.2" fill="${fill('#FF22C55E')}"/>
    <circle cx="68" cy="50" r="2.2" fill="${fill('#FFE11D48')}"/>
    <circle cx="64" cy="54" r="2.2" fill="${fill('#FF3B82F6')}"/>
    <circle cx="60" cy="50" r="2.2" fill="${fill('#FFFFC107')}"/>

    <!-- Analog sticks -->
    <circle cx="46" cy="60" r="3" fill="${fill('#FF2A2A3E')}" stroke="${stroke('#FF555577')}" stroke-width="1"/>
    <circle cx="62" cy="60" r="3" fill="${fill('#FF2A2A3E')}" stroke="${stroke('#FF555577')}" stroke-width="1"/>
  </g>
</svg>`;

const outDir = join(process.cwd(), "app", "src", "main", "res", "drawable");
mkdirSync(outDir, { recursive: true });

const pngPath = join(outDir, "ic_launcher_512.png");
const jpegPath = join(outDir, "ic_launcher_512.jpg");
const svgPath = join(outDir, "ic_launcher_512.svg");

// Write SVG for reference (kept out of res/ because Android drawable
// resources only allow .xml/.png, not .svg).
const refDir = join(process.cwd(), "assets", "icon-src");
mkdirSync(refDir, { recursive: true });
const refSvgPath = join(refDir, "ic_launcher_512.svg");
writeFileSync(refSvgPath, svg, "utf-8");
console.log(`SVG reference written to ${refSvgPath}`);

// Render PNG
const resvg = new Resvg(svg, {
  fitTo: { mode: "width", value: OUTPUT_SIZE },
  background: "rgba(10,10,26,1)",
});
const pngData = resvg.render();
const pngBuffer = pngData.asPng();
writeFileSync(pngPath, pngBuffer);
console.log(`PNG (${OUTPUT_SIZE}x${OUTPUT_SIZE}) written to ${pngPath}`);

// Render JPEG (use PNG data, convert via canvas-like approach)
// Since resvg only outputs PNG, we'll save the PNG and note that
// JPEG conversion requires additional tooling
// For Play Store, PNG is the preferred format anyway
console.log(`\nDone! Play Store high-res icon: ${pngPath}`);
console.log(`SVG source: ${svgPath}`);
