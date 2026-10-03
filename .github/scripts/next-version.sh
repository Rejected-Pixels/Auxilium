#!/usr/bin/env bash
# Works out the next SemVer 2.0 version from Conventional Commits since the last vX.Y.Z tag.
#
# Prints (for $GITHUB_OUTPUT):
#   last_version  latest released version, or 0.0.0 if nothing has been tagged yet
#   bump          major | minor | patch | none
#   next_version  the version to release; equals last_version when bump is none
#   dev_base      base for pre-release builds: next_version, or last_version with the patch bumped when bump is none
#
# Rules: "<type>!:" or a "BREAKING CHANGE:" footer -> major, "feat:" -> minor, "fix:"/"perf:" -> patch,
# anything else (chore, docs, refactor, ...) doesn't trigger a release.
set -euo pipefail

release_tag_pattern='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'

last_tag=$(git tag --list 'v*' --sort=-v:refname | grep -E "$release_tag_pattern" | head -n 1 || true)
if [[ -n "$last_tag" ]]; then
  last_version=${last_tag#v}
  range="$last_tag..HEAD"
else
  last_version=0.0.0
  range=HEAD
fi
IFS=. read -r major minor patch <<< "$last_version"

bump=none
# %x1e (record separator) ends each commit so multi-line bodies can be read whole.
while IFS= read -r -d $'\x1e' message; do
  message=${message#$'\n'}
  subject=${message%%$'\n'*}
  if [[ "$subject" =~ ^[a-zA-Z]+(\([^\)]*\))?!: ]] || grep -qE '^BREAKING[ -]CHANGE:' <<< "$message"; then
    bump=major
    break
  elif [[ "$subject" =~ ^feat(\([^\)]*\))?: ]]; then
    bump=minor
  elif [[ "$subject" =~ ^(fix|perf)(\([^\)]*\))?: && "$bump" == none ]]; then
    bump=patch
  fi
done < <(git log --format='%B%x1e' "$range")

case "$bump" in
  major) next_version="$((major + 1)).0.0" ;;
  minor) next_version="$major.$((minor + 1)).0" ;;
  patch) next_version="$major.$minor.$((patch + 1))" ;;
  none)  next_version="$last_version" ;;
esac

if [[ "$bump" == none ]]; then
  dev_base="$major.$minor.$((patch + 1))"
else
  dev_base="$next_version"
fi

echo "last_version=$last_version"
echo "bump=$bump"
echo "next_version=$next_version"
echo "dev_base=$dev_base"
