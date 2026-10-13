#!/usr/bin/env bash
set -Eeuo pipefail

MC_VERSION="26.1"
LOADER_VERSION="0.19.5"
PROFILE_NAME="Lesson"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MOD_JAR="$SCRIPT_DIR/safe2play-mod-1.0.0.jar"
FABRIC_API="$SCRIPT_DIR/fabric-api.jar"
INSTALLER="$SCRIPT_DIR/fabric-installer.jar"

echo "======================================"
echo "       Safe2Play Linux Installer"
echo "======================================"
echo "Minecraft: $MC_VERSION"
echo "Fabric:    $LOADER_VERSION"
echo "Profile:   $PROFILE_NAME"
echo

# Check required files first.
for file in "$MOD_JAR" "$FABRIC_API"; do
    if [[ ! -f "$file" ]]; then
        echo "ERROR: Required file is missing: $file"
        exit 1
    fi
done

for cmd in java python3 curl install; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "ERROR: Required command '$cmd' is not installed."
        exit 1
    fi
done

DEFAULT_DIR="$HOME/.minecraft"
read -r -p "TLauncher game directory [$DEFAULT_DIR]: " MC_DIR
MC_DIR="${MC_DIR:-$DEFAULT_DIR}"

if [[ "$MC_DIR" == "~" ]]; then
    MC_DIR="$HOME"
elif [[ "$MC_DIR" == "~/"* ]]; then
    MC_DIR="$HOME/${MC_DIR#~/}"
fi

mkdir -p "$MC_DIR"

MODS_DIR="$MC_DIR/mods"
VERSIONS_DIR="$MC_DIR/versions"
GENERATED="fabric-loader-${LOADER_VERSION}-${MC_VERSION}"
VERSION_DIR="$VERSIONS_DIR/$GENERATED"
LESSON_DIR="$VERSIONS_DIR/$PROFILE_NAME"

# Install mods FIRST, so a profile setup problem won't prevent copying them.
echo
echo "Installing mods..."
mkdir -p "$MODS_DIR"

install -m 644 "$MOD_JAR" "$MODS_DIR/safe2play-mod-1.0.0.jar"
echo "Safe2Play mod installed."

install -m 644 "$FABRIC_API" "$MODS_DIR/fabric-api.jar"
echo "Fabric API installed."

echo "Mods copied successfully."

# If Lesson already exists and has metadata, keep it and skip Fabric setup.
if [[ -f "$LESSON_DIR/$PROFILE_NAME.json" ]]; then
    echo
    echo "The '$PROFILE_NAME' profile already exists. Skipping profile creation."

elif [[ -e "$LESSON_DIR" ]]; then
    echo
    echo "WARNING: $LESSON_DIR already exists but has no Lesson.json."
    echo "Skipping profile creation to avoid overwriting existing files."
    echo "The mods have still been installed."

else
    # Download Fabric installer only if needed.
    if [[ ! -f "$INSTALLER" ]]; then
        echo
        echo "Downloading the official Fabric installer..."
        curl -fL --retry 2 \
            "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.jar" \
            -o "$INSTALLER"
    fi

    # Reuse an existing Fabric installation if it is already present.
    if [[ ! -f "$VERSION_DIR/$GENERATED.json" ]]; then
        echo
        echo "Installing Fabric into: $MC_DIR"

        java -jar "$INSTALLER" client \
            -dir "$MC_DIR" \
            -mcversion "$MC_VERSION" \
            -loader "$LOADER_VERSION"
    else
        echo
        echo "Fabric $LOADER_VERSION for Minecraft $MC_VERSION already exists."
        echo "Skipping Fabric installation."
    fi

    if [[ -f "$VERSION_DIR/$GENERATED.json" ]]; then
        # Move the whole directory so any additional files are preserved.
        mv "$VERSION_DIR" "$LESSON_DIR"

        if [[ -f "$LESSON_DIR/$GENERATED.json" ]]; then
            mv "$LESSON_DIR/$GENERATED.json" \
               "$LESSON_DIR/$PROFILE_NAME.json"
        fi

        if [[ -f "$LESSON_DIR/$GENERATED.jar" ]]; then
            mv "$LESSON_DIR/$GENERATED.jar" \
               "$LESSON_DIR/$PROFILE_NAME.jar"
        fi

        python3 - "$LESSON_DIR/$PROFILE_NAME.json" "$PROFILE_NAME" <<'PY'
import json
import sys

path, name = sys.argv[1], sys.argv[2]

with open(path, encoding="utf-8") as f:
    data = json.load(f)

data["id"] = name

with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, indent=2)
    f.write("\n")
PY

        echo "Created the '$PROFILE_NAME' version."
    else
        echo
        echo "WARNING: Fabric version metadata was not found."
        echo "The mods are installed, but the Lesson profile may need fixing."
    fi
fi

# Remove the Fabric installer JAR after successful installation.
if [[ -f "$INSTALLER" ]]; then
    rm -- "$INSTALLER"
    echo "Removed temporary Fabric installer."
fi

echo
echo "======================================"
echo "Installation finished."
echo "======================================"
echo "Game directory: $MC_DIR"
echo "Mods directory: $MODS_DIR"
echo "Safe2Play:      $MODS_DIR/safe2play-mod-1.0.0.jar"
echo "Fabric API:     $MODS_DIR/fabric-api.jar"
echo
echo "If Lesson was created successfully, open TLauncher and select it."
echo "After launching the game, run ./linux-key.sh to export your public key."
read -r -p "Press Enter to exit..."