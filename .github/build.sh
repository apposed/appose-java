#!/bin/sh
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/main/ci-build.sh
sh ci-build.sh
result=$?

case "$(uname)" in
  MINGW*|MSYS*) EXE=.exe ;;
  *) EXE= ;;
esac
echo
echo "===== uv ====="
~/.local/share/appose/.uv/bin/uv${EXE} --version
~/.local/share/appose/.uv/bin/uv${EXE} cache dir
echo
echo "===== pixi ====="
~/.local/share/appose/.pixi/bin/pixi${EXE} info
echo
echo "===== mamba ====="
~/.local/share/appose/.mamba/bin/micromamba${EXE} info

exit $result
