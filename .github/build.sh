#!/bin/sh
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/main/ci-build.sh
sh ci-build.sh
result=$?

case "$(uname)" in
  MINGW*|MSYS*) EXE=.exe ;;
  *) EXE= ;;
esac

uv=$(find "$HOME/.local/share/appose" -name uv$EXE -type f | head -n1)
if [ "$uv" ]; then (set -x; "$uv" --version; "$uv" cache dir)
else
  echo "No uv executable found!"
fi

pixi=$(find "$HOME/.local/share/appose" -name pixi$EXE -type f | head -n1)
if [ "$pixi" ]; then (set -x; "$pixi" info)
else
  echo "No pixi executable found!"
fi

mamba=$(find "$HOME/.local/share/appose" -name micromamba$EXE -type f | head -n1)
if [ "$mamba" ]; then (set -x; "$mamba" info)
else
  echo "No micromamba executable found!"
fi

exit $result
