#!/bin/sh

case "$CONDA_PREFIX" in
  */appose-dev)
    ;;
  *)
    echo "Please run 'make setup' and then 'mamba activate appose-dev' first."
    exit 1
    ;;
esac
