#!/bin/sh
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/main/ci-setup-github-actions.sh
sh ci-setup-github-actions.sh

# Install latest development version of appose-python.
echo "==> Installing appose-python..."
python -m pip install git+https://github.com/apposed/appose-python.git#egg=appose

# Let the Linux build handle artifact deployment.
if [ "$(uname)" != Linux ]
then
  echo "No deploy -- non-Linux build"
  echo "NO_DEPLOY=1" >> $GITHUB_ENV
fi
