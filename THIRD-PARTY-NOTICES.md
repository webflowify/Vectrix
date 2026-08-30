# Third-Party Notices

This document lists the open-source software components used by the
**PS1 Emulator** Android app (the "Application"), together with their
licenses and copyright holders. It is provided to satisfy attribution and
license-notice requirements (including the GNU GPLv2 under which the
Application as a whole is distributed).

> **Application license:** The Application links the PCSX ReARMed emulation
> core, which is licensed under the **GNU GPLv2** (including the ARM
> dynarec ("Ari64" new_dynarec), which is GPLv2 *only*, not "or later").
> The combined work is therefore distributed under the **GNU GPLv2**. The
> full text of the GPLv2 is in the `LICENSE` file. Because the binary is
> distributed in object-code form, the complete corresponding source is made
> available from the **public source repository** under GPLv2 §3 (see
> Section 1.1 below). Each distributed binary is tagged there for reference.

---

## 1. Components and their licenses (summary)

| Component | Version / Source | License | Used in |
|---|---|---|---|
| PCSX ReARMed (emulation core) | bundled source at `app/src/main/cpp/core` (fork of libretro/pcsx_rearmed, with modifications) | **GPLv2** (dynarec: GPLv2 only) | Native `.so` |
| libchdr | bundled in `core/deps/libchdr` | **BSD-3-Clause** (two copyright holders — see §2 below) | Native (CHD support) |
| LZMA SDK (7-Zip) | bundled in `core/deps/libchdr/deps/lzma-24.05` | **Public Domain** | Native |
| Zstandard (zstd) | bundled in `core/deps/libchdr/deps/zstd-1.5.6` | **BSD-2-Clause** | Native |
| zlib | bundled in `core/deps/libchdr/deps/zlib-1.3.1` | **zlib License** | Native |
| libretro-common | bundled in `core/deps/libretro-common` | **MIT** | Native |
| lightrec | bundled in `core/deps/lightrec` | **LGPL-2.0** (GNU Library GPL v2) | Native (not compiled — see note) |
| GNU Lightning | bundled in `core/deps/lightning` | **GPL-3.0 / LGPL-3.0** | Native (not compiled — see note) |
| TLSF allocator | bundled in `core/deps/lightrec/tlsf/` | **BSD-2-Clause** | Native (not compiled — see note) |
| P.E.Op.S. / Pete GPU & SPU plugins | part of PCSX ReARMed | **GPLv2 / (L)GPL** | Native |
| stb_image_write | v1.16, `app/src/main/cpp/stb_image_write.h` (nothings.org) | **MIT / Public Domain** (dual) | Native (screenshots) |
| Oboe (audio) | `com.google.oboe:oboe:1.10.0` | **Apache-2.0** | Native + Java |
| AndroidX (AppCompat, Material, Core, Activity, Fragment, ConstraintLayout, RecyclerView, ViewPager2, Preference, Lifecycle, DocumentFile) | per `gradle/libs.versions.toml` | **Apache-2.0** | Java |
| Room (persistence) | `androidx.room:room-*` 2.6.1 | **Apache-2.0** | Java |
| Glide (image loading) | `com.github.bumptech.glide:glide:4.16.0` | **Apache-2.0** (gifdecoder sub-component: **BSD-2-Clause**) | Java |
| Apache Commons Compress | `org.apache.commons:commons-compress:1.26.2` | **Apache-2.0** | Java |
| XZ for Java | `org.tukaani:xz:1.9` | **Public Domain** | Java |

### PCSX ReARMed authors (from `core/AUTHORS`)
- (C) 1999-2003 PCSX Team (Linuzappz, Shadow, Pete Bernert, NoComp, Nik3d, Akumax)
- (C) 2005-2009 PCSX-df Team (Ryan Schultz, Andrew Burton, et al.)
- (C) 2009-2011 PCSX-Reloaded Team (Wei Mingzhi, edgbla, shalma, et al.)
- ARM NEON GPU plugin: (C) 2011-2012 Exophase, notaz
- PCSX4ALL GPU plugin: (C) 2010 PCSX4ALL Team, Unai, Franxis, Chui
- P.E.Op.S. GPU/SPU plugins: (C) Pete Bernert and the P.E.Op.S. team
- MIPS->ARM recompiler: (C) 2009-2011 Ari64
- integration/optimization/frontend: (C) 2010-2012 notaz

---

## 1.1 Source code availability — GPLv2 §3 (public repository)

The Application is distributed in object-code (compiled) form. The complete
corresponding source code — sufficient to build the exact distributed binary —
is published at the public source repository under GPLv2 §3:

> **Public source repository:** <https://github.com/webflowify/Vectrix>
>
> The repository contains all application source files, the JNI bridge, native
> glue code, build scripts, and the PCSX ReARMed core (git submodule) required
> to build the shipped binary. The source is provided under the terms of the
> GNU General Public License, version 2.

This satisfies GPLv2 §3: the binary is distributed from a designated place
(the app store / website) and equivalent access to the corresponding source is
available from the same place via the URL above (also shown in the app's
**Licenses** screen). Each released binary is tagged in the repository.

- **Vectrix v1.0.5** (versionName `1.0.5`, versionCode 6) — the corresponding
  source for this released binary is tagged **`v1.0.5`** in this repository.

Notes for the distributor:
- Keep the matching source available at the repository for as long as the
  corresponding binary is distributed.
- If you ever change the build, keep the source that matches **each** binary you
  distributed.

---

## 2. Full license texts

### GNU GPLv2
See the `LICENSE` file at the repository root. A standalone copy of the full
GPLv2 text is also bundled with the app at
`app/src/main/assets/licenses/gpl-2.0.txt` for offline access.

### LGPL-2.0 (lightrec — bundled, not compiled)
The lightrec dynamic recompiler is licensed under the GNU Library General
Public License v2 (LGPL-2.0). The full text is at
<https://www.gnu.org/licenses/old-licenses/lgpl-2.0.html>. It is present in
the source tree but is **not** compiled into the shipped Android native
library — the Android build uses the GPLv2 "Ari64" dynarec instead.

The lightrec source tree also bundles the **TLSF allocator** (Two-Level
Segregated Fit), which is licensed under the **BSD-2-Clause License**:
```
Copyright (c) 2006-2016, Nick Johnson
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
  * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

### GPL-3.0 / LGPL-3.0 (GNU Lightning — bundled, not compiled)
GNU Lightning is licensed under the **GNU General Public License v3.0** and
the **GNU Lesser General Public License v3.0**. The full texts are at:
- <https://www.gnu.org/licenses/gpl-3.0.html>
- <https://www.gnu.org/licenses/lgpl-3.0.html>

It is present in the source tree but is **not** compiled into the shipped
Android native library. GNU Lightning is a dependency of lightrec, which
itself is also not compiled.

### BSD 3-Clause License (libchdr)

libchdr contains source code from two separate copyright holders. Both
notices must be reproduced in binary distributions.

**Library wrapper — `core/deps/libchdr/LICENSE.txt`**
```
Copyright Romain Tisserand
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the <organization> nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

**Original MAME CHD code — `chd.c`, `chd.h`, `bitstream.c`, `cdrom.c`, `flac.c`, `huffman.c`**
```
Copyright Aaron Giles
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in
      the documentation and/or other materials provided with the
      distribution.
    * Neither the name 'MAME' nor the names of its contributors may be
      used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY AARON GILES ''AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL AARON GILES BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
```

### BSD 2-Clause License (Zstandard / zstd)
```
BSD License

For Zstandard software

Copyright (c) Meta Platforms, Inc. and affiliates. All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.
 * Neither the name Facebook, nor Meta, nor the names of its contributors may
   be used to endorse or promote products derived from this software without
   specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

### MIT License (libretro-common)
```
Copyright (c) libretro contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### zlib License (zlib)
```
zlib.h -- interface of the 'zlib' general purpose compression library
version 1.3.1, January 22nd, 2024

Copyright (C) 1995-2024 Jean-loup Gailly and Mark Adler

This software is provided 'as-is', without any express or implied
warranty.  In no event will the authors be held liable for any damages
arising from the use of this software.

Permission is granted to anyone to use this software for any purpose,
including commercial applications, and to alter it and redistribute it
freely, subject to the following restrictions:

1. The origin of this software must not be misrepresented; you must not
   claim that you wrote the original software. If you use this software
   in a product, an acknowledgment in the product documentation would be
   appreciated but is not required.
2. Altered source versions must be plainly marked as such, and must not be
   misrepresented as being the original software.
3. This notice may not be removed or altered from any source distribution.
```

### Public Domain (LZMA SDK / 7-Zip; XZ for Java)
The LZMA SDK (used by libchdr) and the XZ for Java library are released into
the public domain:

> LZMA SDK: "This code is placed in the public domain." (Igor Pavlov, 7-Zip)
> XZ for Java: "This software is released into the public domain." (Lasse Collin,
> Igor Pavlov; org.tukaani:xz)

### Apache License 2.0 (Oboe, AndroidX, Room, Glide, Apache Commons Compress)
These components are licensed under the Apache License, Version 2.0. The full
text is available at <https://www.apache.org/licenses/LICENSE-2.0>. You may
obtain a copy of the license at:

    http://www.apache.org/licenses/LICENSE-2.0

A summary of the Apache 2.0 conditions: redistribution must retain the
copyright notice and the NOTICE file (if any); modified source must be marked;
derivative works must carry the Apache 2.0 license; the license does not grant
trademark rights; and the software is provided without warranty.

**Apache NOTICE files (§4.1 of Apache-2.0):**

The Apache-2.0 license requires that redistribution must include the NOTICE
file if one exists. Of the Apache-2.0-licensed dependencies in this project,
only Apache Commons Compress ships a NOTICE file:

*Apache Commons Compress — NOTICE.txt*
```
Apache Commons Compress
Copyright 2002-2026 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (https://www.apache.org/).
```

The following Apache-2.0-licensed dependencies do **not** ship a NOTICE file
in their source repositories: Oboe, AndroidX (AppCompat, Material, Core,
Activity, Fragment, ConstraintLayout, RecyclerView, ViewPager2, Preference,
Lifecycle, DocumentFile), Room, Glide. No separate NOTICE attribution is
required for these libraries.

**Note — Glide BSD-2-Clause sub-component (gifdecoder):**
Glide bundles `gifdecoder`, which is licensed under the BSD 2-Clause License:
```
Copyright 2014 Google, Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
1. Redistributions of source code must retain the above copyright notice,
   this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

### MIT / Public Domain (stb_image_write v1.16)
Written by Sean T. Barrett — http://nothings.org/stb  
Compiled into `libps1emulatorcore.so` via `app/src/main/cpp/screenshot.h`.

This library is dual-licensed — you may use either:

**ALTERNATIVE A — MIT License**
```
Copyright (c) 2017 Sean Barrett
Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

**ALTERNATIVE B — Public Domain (www.unlicense.org)**
```
This is free and unencumbered software released into the public domain.
Anyone is free to copy, modify, publish, use, compile, sell, or distribute this
software, either in source code form or as a compiled binary, for any purpose,
commercial or non-commercial, and by any means.
In jurisdictions that recognize copyright laws, the author or authors of this
software dedicate any and all copyright interest in the software to the public
domain. We make this dedication for the benefit of the public at large and to
the detriment of our heirs and successors. We intend this dedication to be an
overt act of relinquishment in perpetuity of all present and future rights to
this software under copyright law.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

---

## 3. Copyright / trademark notice

- This Application is **not** affiliated with, endorsed by, or sponsored by
  Sony Interactive Entertainment or any other rights holder.
- "PlayStation", "PSX", "PS1", "Sony" and related logos are trademarks of
  their respective owners. The Application does not bundle or link to any
  BIOS, ROM, or game files; users must supply their own, from hardware they
  legally own.
- The Application's own source code is Copyright (C) the PS1 Emulator authors
  and is distributed under the GPLv2 (see `LICENSE`).
