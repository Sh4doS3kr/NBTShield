# Changelog

All notable changes to NBTShield will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] - 2026-03-24

### Added

- **Book Ban Protection** — New `BookListener` that blocks oversized books, limits page count and page length
- **Sign Protection** — Prevents oversized sign text via `SignChangeEvent`
- **Entity Protection** — New `EntityListener` that protects item frames and armor stands from oversized NBT items
- **Packet-Level Protection** — Netty `ChannelDuplexHandler` injected per player to catch:
  - `PacketTooLargeException`
  - `VarInt too big` / `VarLong too big`
  - `Badly compressed packet`
  - `Length wider than 21-bit`
  - `Unable to fit X into 3`
  - `Payload may not be larger than 1048576/32767 bytes`
  - `Tried to read NBT tag that was too big`
  - `Packet over maximum protocol size`
  - `Connection timed out`
- **Container Protection** — Now scans chests, barrels, hoppers, droppers, dispensers (not just shulker boxes)
- **Creative Mode Protection** — Intercepts `InventoryCreativeEvent` to block arbitrary NBT injection
- **Ender Chest Scanning** — Player ender chests are now scanned and cleaned
- **Chunk Entity Scanning** — Item frames and armor stands in chunks are scanned on load
- **Configurable limits** for all new protections in `config.yml`
- New config options: `book-protection`, `sign-protection`, `entity-protection`, `packet-protection`, `scan-creative-actions`, `scan-entity-nbt-on-chunk-load`

### Changed

- **Renamed plugin** from `AntiChunkBan` to `NBTShield`
- **Package** renamed from `com.antichunkban` to `com.nbtshield`
- **Main class** renamed from `AntiChunkBan` to `NBTShield`
- **Commands** changed from `/acb` to `/nbs` (alias `/nbtshield`)
- **Permissions** changed from `antichunkban.*` to `nbtshield.*`
- `removeAllShulkers()` upgraded to `removeAllDangerousItems()` — now removes all oversized items, not just shulkers
- `ChunkListener` now scans ALL container types, not just shulker boxes
- `ItemListener.onBlockPlace()` now blocks any oversized item placement, not just shulkers
- Strike action now also cleans ender chest
- Updated default NBT limits to more secure values

### Improved

- Better container detection with `isContainer()` utility method
- Better book detection with `isBook()` utility method
- More detailed logging with categorized prefixes (`[ChunkScan]`, `[EntityLoad]`, `[BookProtection]`, etc.)
- Netty handler names use `nbtshield_protection` identifier

---

## [1.0.0] - 2026-03-24

### Added

- Initial release
- **Shulker Box NBT Protection** — Detects and removes shulker boxes with oversized NBT data
- **Item NBT Scanning** — Checks serialized byte size of all items
- **Chunk Load Scanning** — Scans tile entities when chunks load
- **Inventory Scanning** — Scans on join, inventory click, item pickup, item drop, item spawn
- **Block Place Protection** — Prevents placing oversized shulker boxes
- **Strike System** — Tracks repeat offenders and escalates (removes all shulkers)
- **Admin Notifications** — Real-time alerts to admins with `antichunkban.admin` permission
- **Commands** — `/acb reload`, `/acb scan`, `/acb scanplayer <name>`
- **Configurable** — All limits and toggles in `config.yml`
- **Paper 1.21.4 support**
