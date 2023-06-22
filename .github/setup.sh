#!/bin/sh
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/main/ci-setup-github-actions.sh
sh ci-setup-github-actions.sh

# Install latest development version of appose-python.
echo "==> Installing appose-python..."
python -m pip install git+https://github.com/apposed/appose-python.git#egg=appose
