// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include "common/common_types.h"
#include "enet/enet.h"

namespace Network {

/**
 * Network Optimizations for NWM/ENet
 *
 * These settings improve connection stability, reduce latency, and prevent
 * packet loss in 3DS local wireless multiplayer emulation.
 */
namespace Optimizations {

// Bandwidth limits (bytes per second)
// 3DS local wireless has ~6-7 Mbps theoretical max, but real-world is lower
constexpr u32 INCOMING_BANDWIDTH = 1024 * 1024;  // 1 MB/s (8 Mbps) - generous limit
constexpr u32 OUTGOING_BANDWIDTH = 1024 * 1024;  // 1 MB/s (8 Mbps)

// ENet peer timeout settings (milliseconds)
// Default ENet timeouts are too aggressive for emulated wireless
constexpr u32 PEER_TIMEOUT_LIMIT = 32;      // Time before timeout counter increases
constexpr u32 PEER_TIMEOUT_MINIMUM = 5000;  // Minimum timeout (5 seconds)
constexpr u32 PEER_TIMEOUT_MAXIMUM = 30000; // Maximum timeout (30 seconds)

// Polling interval for network service loop
constexpr u32 SERVICE_TIMEOUT_MS = 50; // 50ms = good balance between latency & CPU usage

// Connection timeout (milliseconds)
constexpr u32 CONNECTION_TIMEOUT_MS = 10000; // 10 seconds (was 5 seconds)

/**
 * Configure an ENet peer with optimized timeout settings
 * Prevents premature disconnections due to temporary lag spikes
 */
inline void ConfigurePeerTimeouts(ENetPeer* peer) {
    if (peer) {
        enet_peer_timeout(peer, PEER_TIMEOUT_LIMIT, PEER_TIMEOUT_MINIMUM, PEER_TIMEOUT_MAXIMUM);
    }
}

/**
 * Enable ENet packet compression
 * Reduces bandwidth usage by ~30-50% for 3DS network packets
 */
inline void EnableCompression(ENetHost* host) {
    if (host && host->compressor.context == nullptr) {
        enet_host_compress_with_range_coder(host);
    }
}

/**
 * Configure ENet host with optimal bandwidth limits
 * Prevents network congestion and packet loss
 */
inline void ConfigureBandwidthLimits(ENetHost* host) {
    if (host) {
        enet_host_bandwidth_limit(host, INCOMING_BANDWIDTH, OUTGOING_BANDWIDTH);
    }
}

/**
 * Apply all network optimizations to an ENet host and peer
 * Call this after creating a connection
 */
inline void ApplyOptimizations(ENetHost* host, ENetPeer* peer) {
    EnableCompression(host);
    ConfigureBandwidthLimits(host);
    ConfigurePeerTimeouts(peer);
}

} // namespace Optimizations
} // namespace Network
