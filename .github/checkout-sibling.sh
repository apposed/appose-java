#!/bin/sh

# Checks out a sibling Appose repository (e.g. appose-java) beside this one,
# so that the tests run against its latest code. Uses the branch with the same
# name as this build's, if the sibling has one, so that changes spanning both
# repositories can be tested together; otherwise, uses the main branch.
#
# Usage: .github/checkout-sibling.sh <repository-name>

set -e

repo=$1
url="https://github.com/apposed/$repo"

# NB: For pull requests, GITHUB_HEAD_REF is the source branch;
# for pushes, GITHUB_REF_NAME is the branch (or tag) pushed.
branch=${GITHUB_HEAD_REF:-$GITHUB_REF_NAME}
if [ -z "$branch" ] || ! git ls-remote --exit-code --heads "$url" "$branch" >/dev/null 2>&1
then
  branch=main
fi

echo "==> Checking out $repo@$branch"
git clone --depth 1 --branch "$branch" "$url" "../$repo"
git -C "../$repo" log -1 --oneline
