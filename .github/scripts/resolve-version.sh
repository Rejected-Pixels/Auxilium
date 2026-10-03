#!/usr/bin/env bash
# Decides whether a release workflow run should release, and with which SemVer 2.0 version.
#
# Reads the GitHub event from the environment:
#   EVENT          pull_request | push | workflow_dispatch
#   PR_MERGED      "true" for a merged pull request
#   PR_BASE        base branch of the pull request
#   REF            full ref, e.g. refs/heads/main or refs/tags/v1.2.0
#   REF_TYPE       branch | tag
#   REF_NAME       short ref name, e.g. main or v1.2.0
#   INPUT_CHANNEL  manual runs: auto | alpha | beta | release
#   INPUT_VERSION  manual runs: optional exact version
#
# Writes version, release and create_tag to $GITHUB_OUTPUT (stdout when unset).
#
# Rules:
#   - PR merged into main: the next version from its Conventional Commits; nothing if only chore/docs/ci-style commits.
#   - v* tag pushed: the tag's version.
#   - manual run: channel auto releases on main and makes an alpha elsewhere; alpha/beta make X.Y.Z-<channel>.N;
#     release is only allowed on main; an explicit version overrides the channel.
#   - anything released from a commit not on main must be an alpha or beta pre-release.
set -euo pipefail

script_dir=$(dirname "${BASH_SOURCE[0]}")
source <(bash "$script_dir/next-version.sh")

output=${GITHUB_OUTPUT:-/dev/stdout}
summary=${GITHUB_STEP_SUMMARY:-/dev/null}

fail() {
  echo "::error::$1"
  exit 1
}

# Whether the commit being built is already part of main.
on_main=false
if git rev-parse -q --verify refs/remotes/origin/main > /dev/null && git merge-base --is-ancestor HEAD origin/main; then
  on_main=true
fi

is_alpha_or_beta() { [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+-(alpha|beta)([.+]|$) ]]; }

require_prerelease_off_main() {
  if [[ "$on_main" != "true" ]] && ! is_alpha_or_beta "$1"; then
    fail "'$1' is being released from outside main, so it must be an alpha or beta pre-release (e.g. $dev_base-alpha.1)"
  fi
}

# Next X.Y.Z-<channel>.N, counting up from any existing tags for that version and channel.
next_prerelease() {
  local base="$1" channel="$2" last
  last=$(git tag --list "v$base-$channel.*" | sed -nE "s/^v${base//./\\.}-$channel\.(0|[1-9][0-9]*)$/\1/p" | sort -n | tail -n 1)
  echo "$base-$channel.$(( ${last:-0} + 1 ))"
}

release=false
create_tag=false
version=""

case "$EVENT" in
  pull_request)
    if [[ "${PR_MERGED:-false}" == "true" && "${PR_BASE:-}" == "main" && "$bump" != "none" ]]; then
      version="$next_version"; release=true; create_tag=true
    fi
    ;;
  push)
    [[ "$REF_TYPE" == "tag" ]] || fail "Release runs on push only handle v* tags"
    version="${REF_NAME#v}"; release=true
    require_prerelease_off_main "$version"
    ;;
  workflow_dispatch)
    release=true; create_tag=true
    channel="${INPUT_CHANNEL:-auto}"
    if [[ "$channel" == "auto" ]]; then
      [[ "$on_main" == "true" ]] && channel=release || channel=alpha
    fi

    if [[ -n "${INPUT_VERSION:-}" ]]; then
      version="${INPUT_VERSION#v}"
      require_prerelease_off_main "$version"
    elif [[ "$channel" == "release" ]]; then
      [[ "$on_main" == "true" ]] || fail "Full releases can only be made from main. Pick the alpha or beta channel for $REF_NAME."
      [[ "$bump" != "none" ]] || fail "No feat/fix/perf or breaking commits since v$last_version, so there is nothing to release. Enter a version to release anyway."
      version="$next_version"
    else
      version=$(next_prerelease "$dev_base" "$channel")
    fi
    ;;
  *)
    fail "Unsupported event '$EVENT'"
    ;;
esac

if [[ "$release" == "true" ]]; then
  # Full SemVer 2.0 check (https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string).
  semver='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-((0|[1-9][0-9]*|[0-9]*[a-zA-Z-][0-9a-zA-Z-]*)(\.(0|[1-9][0-9]*|[0-9]*[a-zA-Z-][0-9a-zA-Z-]*))*))?(\+([0-9a-zA-Z-]+(\.[0-9a-zA-Z-]+)*))?$'
  [[ "$version" =~ $semver ]] || fail "'$version' is not a valid SemVer 2.0 version"
  if [[ "$create_tag" == "true" ]] && git rev-parse -q --verify "refs/tags/v$version" > /dev/null; then
    fail "Tag v$version already exists"
  fi
fi

mod_name=$(grep -E '^mod_name=' gradle.properties | cut -d= -f2-)

{
  echo "version=$version"
  echo "release=$release"
  echo "create_tag=$create_tag"
} >> "$output"

if [[ "$release" == "true" ]]; then
  echo "### Releasing $mod_name $version" >> "$summary"
else
  echo "### Nothing to release (only non-releasing commits since v$last_version)" >> "$summary"
fi
echo "Bump since v$last_version: **$bump** · tag: **$create_tag**" >> "$summary"
