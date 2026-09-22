// Copyright Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

// NEON-accelerated versions of the "converted" (source format -> RGBA8)
// linear decode paths used by LINEAR_DECODE_TABLE_CONVERTED in
// texture_codec.h. These are pure, drop-in replacements for the equivalent
// scalar LinearCopy<true, format, true> instantiations: given the same
// input bytes they produce byte-identical output, just faster.
//
// Scope: only the 4 uncompressed "converted" linear formats are covered
// here (RGBA8, RGB565, RGB5A1, RGBA4). These are the pixel formats used by
// framebuffers and linear (non-tiled) surfaces. Tiled/Morton-swizzled
// texture uploads (the more common path for game textures stored in PICA
// VRAM) are not touched by this file and still go through the scalar
// per-pixel path in MortonCopyTile.
//
// Every formula below was verified against the scalar reference in
// common/color.h by brute-force comparison over all 65536 possible 16-bit
// pixel values before being ported to NEON intrinsics (RGBA8 is a trivial
// byte-swap so wasn't included in that check). See the accompanying patch
// notes for the verification harness. Still: this file has not been
// compiled or run on real arm64 hardware, since this environment has no
// Android/NDK toolchain. Build and test it (ideally with a game/texture
// that actually uses each of these 4 formats) before trusting it.

#include "common/arch.h"

#if CITRA_ARCH(arm64)

#include <arm_neon.h>
#include <cstddef>
#include <cstring>
#include <span>
#include "common/common_types.h"

namespace VideoCore::NeonDecode {

// RGBA8 -> RGBA8: the scalar reference (Common::Color::DecodeRGBA8) just
// reverses the 4 bytes of each pixel. vrev32q_u8 does exactly that, 4
// pixels (16 bytes) per instruction.
inline void DecodeRGBA8(std::span<const u8> src, std::span<u8> dst) {
    const std::size_t n = std::min(src.size(), dst.size()) / 4;
    const u8* s = src.data();
    u8* d = dst.data();

    std::size_t i = 0;
    for (; i + 4 <= n; i += 4) {
        const uint8x16_t v = vld1q_u8(s + i * 4);
        vst1q_u8(d + i * 4, vrev32q_u8(v));
    }
    for (; i < n; i++) {
        d[i * 4 + 0] = s[i * 4 + 3];
        d[i * 4 + 1] = s[i * 4 + 2];
        d[i * 4 + 2] = s[i * 4 + 1];
        d[i * 4 + 3] = s[i * 4 + 0];
    }
}

// RGB565 -> RGBA8. 8 pixels (16 src bytes -> 32 dst bytes) per iteration.
inline void DecodeRGB565(std::span<const u8> src, std::span<u8> dst) {
    const std::size_t n = std::min(src.size() / 2, dst.size() / 4);
    const u8* s = src.data();
    u8* d = dst.data();

    std::size_t i = 0;
    for (; i + 8 <= n; i += 8) {
        const uint16x8_t px = vld1q_u16(reinterpret_cast<const uint16_t*>(s + i * 2));

        const uint16x8_t r5 = vandq_u16(vshrq_n_u16(px, 11), vdupq_n_u16(0x1F));
        const uint16x8_t g6 = vandq_u16(vshrq_n_u16(px, 5), vdupq_n_u16(0x3F));
        const uint16x8_t b5 = vandq_u16(px, vdupq_n_u16(0x1F));

        const uint16x8_t r8 = vorrq_u16(vshlq_n_u16(r5, 3), vshrq_n_u16(r5, 2));
        const uint16x8_t g8 = vorrq_u16(vshlq_n_u16(g6, 2), vshrq_n_u16(g6, 4));
        const uint16x8_t b8 = vorrq_u16(vshlq_n_u16(b5, 3), vshrq_n_u16(b5, 2));

        const uint8x8x4_t out = {vmovn_u16(r8), vmovn_u16(g8), vmovn_u16(b8), vdup_n_u8(255)};
        vst4_u8(d + i * 4, out);
    }
    for (; i < n; i++) {
        u16 pixel;
        std::memcpy(&pixel, s + i * 2, 2);
        const u8 r5 = (pixel >> 11) & 0x1F;
        const u8 g6 = (pixel >> 5) & 0x3F;
        const u8 b5 = pixel & 0x1F;
        d[i * 4 + 0] = (r5 << 3) | (r5 >> 2);
        d[i * 4 + 1] = (g6 << 2) | (g6 >> 4);
        d[i * 4 + 2] = (b5 << 3) | (b5 >> 2);
        d[i * 4 + 3] = 255;
    }
}

// RGB5A1 -> RGBA8.
inline void DecodeRGB5A1(std::span<const u8> src, std::span<u8> dst) {
    const std::size_t n = std::min(src.size() / 2, dst.size() / 4);
    const u8* s = src.data();
    u8* d = dst.data();

    std::size_t i = 0;
    for (; i + 8 <= n; i += 8) {
        const uint16x8_t px = vld1q_u16(reinterpret_cast<const uint16_t*>(s + i * 2));

        const uint16x8_t r5 = vandq_u16(vshrq_n_u16(px, 11), vdupq_n_u16(0x1F));
        const uint16x8_t g5 = vandq_u16(vshrq_n_u16(px, 6), vdupq_n_u16(0x1F));
        const uint16x8_t b5 = vandq_u16(vshrq_n_u16(px, 1), vdupq_n_u16(0x1F));
        const uint16x8_t a1 = vandq_u16(px, vdupq_n_u16(0x1));

        const uint16x8_t r8 = vorrq_u16(vshlq_n_u16(r5, 3), vshrq_n_u16(r5, 2));
        const uint16x8_t g8 = vorrq_u16(vshlq_n_u16(g5, 3), vshrq_n_u16(g5, 2));
        const uint16x8_t b8 = vorrq_u16(vshlq_n_u16(b5, 3), vshrq_n_u16(b5, 2));
        // Convert1To8(v) == v * 255 for v in {0,1}. 0 - a1 gives 0x0000 or
        // 0xFFFF (mod 2^16); the low byte after narrowing is 0 or 0xFF.
        const uint16x8_t a8 = vsubq_u16(vdupq_n_u16(0), a1);

        const uint8x8x4_t out = {vmovn_u16(r8), vmovn_u16(g8), vmovn_u16(b8), vmovn_u16(a8)};
        vst4_u8(d + i * 4, out);
    }
    for (; i < n; i++) {
        u16 pixel;
        std::memcpy(&pixel, s + i * 2, 2);
        const u8 r5 = (pixel >> 11) & 0x1F;
        const u8 g5 = (pixel >> 6) & 0x1F;
        const u8 b5 = (pixel >> 1) & 0x1F;
        const u8 a1 = pixel & 0x1;
        d[i * 4 + 0] = (r5 << 3) | (r5 >> 2);
        d[i * 4 + 1] = (g5 << 3) | (g5 >> 2);
        d[i * 4 + 2] = (b5 << 3) | (b5 >> 2);
        d[i * 4 + 3] = a1 * 255;
    }
}

// RGBA4 -> RGBA8.
inline void DecodeRGBA4(std::span<const u8> src, std::span<u8> dst) {
    const std::size_t n = std::min(src.size() / 2, dst.size() / 4);
    const u8* s = src.data();
    u8* d = dst.data();

    std::size_t i = 0;
    for (; i + 8 <= n; i += 8) {
        const uint16x8_t px = vld1q_u16(reinterpret_cast<const uint16_t*>(s + i * 2));

        const uint16x8_t r4 = vandq_u16(vshrq_n_u16(px, 12), vdupq_n_u16(0xF));
        const uint16x8_t g4 = vandq_u16(vshrq_n_u16(px, 8), vdupq_n_u16(0xF));
        const uint16x8_t b4 = vandq_u16(vshrq_n_u16(px, 4), vdupq_n_u16(0xF));
        const uint16x8_t a4 = vandq_u16(px, vdupq_n_u16(0xF));

        const uint16x8_t r8 = vorrq_u16(vshlq_n_u16(r4, 4), r4);
        const uint16x8_t g8 = vorrq_u16(vshlq_n_u16(g4, 4), g4);
        const uint16x8_t b8 = vorrq_u16(vshlq_n_u16(b4, 4), b4);
        const uint16x8_t a8 = vorrq_u16(vshlq_n_u16(a4, 4), a4);

        const uint8x8x4_t out = {vmovn_u16(r8), vmovn_u16(g8), vmovn_u16(b8), vmovn_u16(a8)};
        vst4_u8(d + i * 4, out);
    }
    for (; i < n; i++) {
        u16 pixel;
        std::memcpy(&pixel, s + i * 2, 2);
        const u8 r4 = (pixel >> 12) & 0xF;
        const u8 g4 = (pixel >> 8) & 0xF;
        const u8 b4 = (pixel >> 4) & 0xF;
        const u8 a4 = pixel & 0xF;
        d[i * 4 + 0] = (r4 << 4) | r4;
        d[i * 4 + 1] = (g4 << 4) | g4;
        d[i * 4 + 2] = (b4 << 4) | b4;
        d[i * 4 + 3] = (a4 << 4) | a4;
    }
}

} // namespace VideoCore::NeonDecode

#endif // CITRA_ARCH(arm64)
