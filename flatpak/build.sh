#!/usr/bin/env bash
# Builds a single-file Nortantis.flatpak bundle from the manifest in this directory.
# Idempotent: safe to run repeatedly, locally or in CI (e.g. a GitHub Action).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MANIFEST="$SCRIPT_DIR/com.jandjheydorn.Nortantis.yaml"
APP_ID="com.jandjheydorn.Nortantis"
RUNTIME_VERSION="24.08"
FLATHUB_REPO_URL="https://flathub.org/repo/flathub.flatpakrepo"
BUILD_DIR="$REPO_ROOT/build-dir"
EXPORT_REPO_DIR="$REPO_ROOT/repo"
OUT_BUNDLE="$REPO_ROOT/Nortantis.flatpak"

command -v flatpak >/dev/null || { echo "flatpak is not installed." >&2; exit 1; }
command -v flatpak-builder >/dev/null || { echo "flatpak-builder is not installed." >&2; exit 1; }

flatpak remote-add --user --if-not-exists flathub "$FLATHUB_REPO_URL"

flatpak install --user --noninteractive --or-update flathub \
    "org.freedesktop.Platform//$RUNTIME_VERSION" \
    "org.freedesktop.Sdk//$RUNTIME_VERSION" \
    "org.freedesktop.Sdk.Extension.openjdk//$RUNTIME_VERSION"

flatpak-builder --force-clean --repo="$EXPORT_REPO_DIR" "$BUILD_DIR" "$MANIFEST"

flatpak build-bundle "$EXPORT_REPO_DIR" "$OUT_BUNDLE" "$APP_ID" \
    --runtime-repo="$FLATHUB_REPO_URL"

echo "Built $OUT_BUNDLE"
