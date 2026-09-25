#!/bin/sh
# Cai JDK 17 vao ./.jdk (trong thu muc repo) cho Render native runtime.
#
# Vi sao can: Render khong co native runtime cho JVM (chi Node/Python/Ruby/Go/Rust/Elixir),
# nen phai tu tai JDK. Tai lieu Render noi chi noi dung trong thu muc repo moi duoc giu
# lai tu buoc build sang buoc runtime, va runtime chi can JDK (Maven chi dung luc build)
# -> giai nen JDK ngay trong thu muc repo chu khong vao $HOME.
#
# Goi tu buildCommand:   sh ../../render-jdk.sh
# Goi tu startCommand:   sh ../../render-jdk.sh   (chi tai neu .jdk con thieu)
#
# Idempotent: da co .jdk/bin/java thi thoat ngay.
set -eu

JAVA_DIR=".jdk"
if [ -x "$JAVA_DIR/bin/java" ]; then
    exit 0
fi

# Adoptium API: redirect sang tarball JDK Temurin 17 (linux x64). URL on dinh, khong can
# cap nhat theo tung ban build; muon ghim ban cu the thi doi thanh URL release truc tiep.
URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"

echo "render-jdk: dang tai JDK 17 (~190MB) vao $JAVA_DIR ..."
curl -fsSL -o jdk.tgz "$URL"
mkdir -p "$JAVA_DIR"
tar -xzf jdk.tgz -C "$JAVA_DIR" --strip-components=1
rm -f jdk.tgz
"$JAVA_DIR/bin/java" -version
