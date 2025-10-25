#!/bin/sh
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/main/ci-build.sh
sh ci-build.sh
result=$?

echo
echo "===== uv ====="
~/.local/share/appose/.uv/bin/uv self version
~/.local/share/appose/.uv/bin/uv cache dir
echo
echo "===== pixi ====="
~/.local/share/appose/.pixi/bin/pixi info
echo
echo "===== mamba ====="
~/.local/share/appose/.mamba/bin/micromamba info

exit $result
