/*
 * PS1 Emulator for Android
 * Copyright (C) 2024-2026 MST. ROKIEA SULTANA
 * 2 No. College Gate Bylane, Mymensingh - 2207, Bangladesh (BD)
*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
#pragma once

// --- MemFile: in-memory buffer for PCSX freeze functions ---------------------
// The PCSX freeze functions (sioFreeze, cdrFreeze, psxRcntFreeze, mdecFreeze,
// ndrc_freeze, padFreeze) use SaveFuncs.read/write/seek to serialize/deserialize
// state. We redirect SaveFuncs to a MemFile to capture/write data in memory.
//
// Save format for extra state section (v5):
//   [sio_size  (4)] [sio_data]
//   [cdr_size  (4)] [cdr_data]
//   [rcnt_size (4)] [rcnt_data]
//   [mdec_size (4)] [mdec_data]
//   [ndrc_size (4)] [ndrc_data]
//   [pad_size  (4)] [pad_data]

#include <cstdint>
#include <cstring>

// psxcommon.h provides SaveFuncs, PcsxSaveFuncs, and gzfreeze macro.
// This header must be included AFTER psxcommon.h (or any header that includes it).
// The PcsxSaveFuncs struct and SaveFuncs extern are provided by psxcommon.h.

struct MemFile {
    uint8_t *buf;
    size_t pos;
    size_t size;     // write: high-water mark; read: total data available
    size_t capacity;
};

static inline void *memfile_open(const char *name, const char *mode) {
    (void)mode;
    MemFile *mf = (MemFile *)(uintptr_t)name;
    return mf;
}

static inline int memfile_read(void *file, void *buf, unsigned int len) {
    MemFile *mf = (MemFile *)file;
    if (!mf || mf->pos + len > mf->size) return -1;
    memcpy(buf, mf->buf + mf->pos, len);
    mf->pos += len;
    return (int)len;
}

static inline int memfile_write(void *file, const void *buf, unsigned int len) {
    MemFile *mf = (MemFile *)file;
    if (!mf || mf->pos + len > mf->capacity) return -1;
    memcpy(mf->buf + mf->pos, buf, len);
    mf->pos += len;
    if (mf->pos > mf->size) mf->size = mf->pos;
    return (int)len;
}

static inline long memfile_seek(void *file, long offs, int whence) {
    MemFile *mf = (MemFile *)file;
    if (!mf) return -1;
    switch (whence) {
    case SEEK_SET: mf->pos = offs; break;
    case SEEK_CUR: mf->pos += offs; break;
    case SEEK_END: mf->pos = mf->size + offs; break;
    }
    return (long)mf->pos;
}

static inline void memfile_close(void *file) {
    (void)file;
}

// RAII guard: redirects SaveFuncs to MemFile functions, restores on destruction
struct SaveFuncsGuard {
    struct PcsxSaveFuncs orig;
    SaveFuncsGuard() : orig(SaveFuncs) {
        SaveFuncs.open  = memfile_open;
        SaveFuncs.read  = memfile_read;
        SaveFuncs.write = memfile_write;
        SaveFuncs.seek  = memfile_seek;
        SaveFuncs.close = memfile_close;
    }
    ~SaveFuncsGuard() { SaveFuncs = orig; }
};

// --- Macros for saving/loading individual freeze sections ---------------------

// Save one freeze section: writes [size(4)] [data] to the MemFile
// The freeze function is called with Mode=1 (save) via SaveFuncs redirect.
#define FREEZE_SAVE_SECTION(mf, freeze_func) do {                          \
    uint32_t _size_pos = (uint32_t)(mf).pos;                               \
    uint32_t _zero = 0;                                                    \
    memfile_write(&(mf), &_zero, 4);                                       \
    size_t _data_start = (mf).pos;                                         \
    void *_f = memfile_open((const char *)&(mf), "wb");                    \
    freeze_func(_f, 1);                                                    \
    memfile_close(_f);                                                     \
    uint32_t _size = (uint32_t)((mf).pos - _data_start);                   \
    size_t _saved = (mf).pos;                                              \
    (mf).pos = _size_pos;                                                  \
    memfile_write(&(mf), &_size, 4);                                       \
    (mf).pos = _saved;                                                     \
} while(0)

// Load one freeze section: reads [size(4)] [data] from a buffer
// The freeze function is called with Mode=0 (load) via SaveFuncs redirect.
#define FREEZE_LOAD_SECTION(in_buf, pos_var, total_size, freeze_func) do { \
    uint32_t _sec_size = 0;                                                \
    if ((pos_var) + 4 > (total_size)) break;                               \
    memcpy(&_sec_size, (in_buf) + (pos_var), 4);                           \
    (pos_var) += 4;                                                        \
    if (_sec_size == 0 || (pos_var) + _sec_size > (total_size)) break;     \
    MemFile _smf = { (uint8_t *)(in_buf) + (pos_var), 0,                   \
                     _sec_size, _sec_size };                                \
    void *_f = memfile_open((const char *)&_smf, "rb");                    \
    freeze_func(_f, 0);                                                    \
    memfile_close(_f);                                                     \
    (pos_var) += _sec_size;                                                \
} while(0)

static constexpr uint32_t EXTRA_STATE_MAGIC = 0x45585452; // "EXTR"
static constexpr size_t EXTRA_STATE_BUF_SIZE = 128 * 1024; // 128 KB
