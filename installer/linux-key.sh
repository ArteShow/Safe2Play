#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

echo "================================"
echo "   Safe2Play Public Key Export"
echo "================================"
echo
echo "Launch Minecraft with Safe2Play first."
echo

DEFAULT_PROFILE="$HOME/.minecraft"
read -r -p "Minecraft game directory [$DEFAULT_PROFILE]: " PROFILE
PROFILE="${PROFILE:-$DEFAULT_PROFILE}"

if [[ "$PROFILE" == "~" ]]; then
    PROFILE="$HOME"
elif [[ "$PROFILE" == "~/"* ]]; then
    PROFILE="$HOME/${PROFILE#~/}"
fi

KEY="$PROFILE/config/safe2play/public.key"
OUT="$SCRIPT_DIR/public-key.txt"

echo
echo "Waiting up to 10 minutes for the public key..."
echo "Expected location: $KEY"

for ((i = 0; i < 120; i++)); do
    if [[ -s "$KEY" ]]; then
        break
    fi
    sleep 5
done

if [[ ! -s "$KEY" ]]; then
    echo
    echo "ERROR: Public key not found."
    echo "Check the game directory and confirm Safe2Play loaded."
    exit 1
fi

cp -- "$KEY" "$OUT"

echo
echo "Public key exported to:"
echo "  $OUT"
echo
echo "Send public-key.txt to the server owner for registration."
echo "NEVER share privat.key. It is your private key."
read -r -p "Press Enter to exit..."