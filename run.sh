#!/usr/bin/env bash
# NetTransfer launcher — resolves JavaFX modules from Maven local repo
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/target/nettransfer-1.0.jar"

M2="$HOME/.m2/repository/org/openjfx"
VER="21.0.4"

FX_MODS="\
$M2/javafx-base/$VER/javafx-base-$VER-linux.jar:\
$M2/javafx-base/$VER/javafx-base-$VER.jar:\
$M2/javafx-controls/$VER/javafx-controls-$VER-linux.jar:\
$M2/javafx-controls/$VER/javafx-controls-$VER.jar:\
$M2/javafx-fxml/$VER/javafx-fxml-$VER-linux.jar:\
$M2/javafx-fxml/$VER/javafx-fxml-$VER.jar:\
$M2/javafx-graphics/$VER/javafx-graphics-$VER-linux.jar:\
$M2/javafx-graphics/$VER/javafx-graphics-$VER.jar"

exec java \
  --module-path "$FX_MODS" \
  --add-modules javafx.base,javafx.controls,javafx.fxml,javafx.graphics \
  --enable-native-access=javafx.graphics \
  -jar "$JAR" "$@"
