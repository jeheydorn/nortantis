# Flatpak packaging (self-distributed)

Builds a self-contained Flatpak of Nortantis that you publish as a single `.flatpak` bundle on
jandjheydorn.com — the Linux analog of the `.deb` / Arch files. It is **not** set up for Flathub:
it builds with network access (no offline dependency vendoring) and ships no AppStream metainfo.
If you ever decide on Flathub, that path needs an offline build and metainfo added then.

## Files

| File | Purpose |
| --- | --- |
| `com.jandjheydorn.Nortantis.yaml` | The manifest (build + runtime permissions). |
| `nortantis.sh` | Launcher; runs the bundled JRE with the same JVM options as the `.deb`. |
| `com.jandjheydorn.Nortantis.desktop` | Menu entry + `.nort` file association (double-click to open). |
| `com.jandjheydorn.Nortantis.mimetype.xml` | Declares `application/x-nortantis` for the `*.nort` glob. |

## Prerequisites (Linux build machine)

```sh
# flatpak + flatpak-builder from your distro, then the runtime, SDK, and JDK extension:
flatpak install flathub org.freedesktop.Platform//24.08 org.freedesktop.Sdk//24.08 \
    org.freedesktop.Sdk.Extension.openjdk//24.08
```

## Build, bundle, publish

```sh
# 1. Build and install locally to test it end to end.
flatpak-builder --user --install --force-clean build-dir flatpak/com.jandjheydorn.Nortantis.yaml
flatpak run com.jandjheydorn.Nortantis

# 2. Export to a local repo, then package a single-file bundle.
flatpak-builder --repo=repo --force-clean build-dir flatpak/com.jandjheydorn.Nortantis.yaml
flatpak build-bundle repo Nortantis.flatpak com.jandjheydorn.Nortantis \
    --runtime-repo=https://flathub.org/repo/flathub.flatpakrepo

# 3. Upload Nortantis.flatpak to your site (one file, roughly app-sized, like the .deb).
```

Users install with:

```sh
flatpak install ./Nortantis.flatpak
```

The `--runtime-repo` flag points their machine at Flathub for the freedesktop **runtime**, so you
only host the app bundle — your Linux bandwidth stays close to what a `.deb` costs today.

## Verify on first run (Java/Swing papercuts)

- **Font chooser** (`JFontChooser`): confirm the user's installed fonts appear. Flatpak mounts host
  fonts read-only, but Java's fontconfig enumeration can be finicky.
- **Taskbar icon**: if the window shows a generic Java icon, run `xprop WM_CLASS` on the window and
  align `StartupWMClass` in the `.desktop` file (see the note there).
- **Double-click a `.nort`**: opens via the document portal even without a filesystem grant, but the
  app receives a remapped `/run/user/<uid>/doc/...` path; external references inside the map (overlay
  image, File-source background texture, custom-images folder) rely on `--filesystem=host`.

## Notes

- **No auto-update.** A single bundle does not self-update; users re-download to upgrade. If you ever
  want updates, host an OSTree repo + a `.flatpakrepo` file instead of a bundle (more infrastructure).
- **`--filesystem=host`** draws a "Potentially unsafe" note in GNOME Software's install dialog. Switch
  to `--filesystem=home` (same safety tier) if you don't need files on external/removable mounts.
