#!/bin/sh

dir=$(dirname "$0")
cd "$dir/.."

# Python
black src/main/python src/test/python
isort src/main/python src/test/python
python -m flake8 src/main/python src/test/python
validate-pyproject pyproject.toml

# Java
mvn \
  license:update-project-license \
  license:update-file-header \
  formatter:format \
  impsort:sort

# JavaScript
#TODO
