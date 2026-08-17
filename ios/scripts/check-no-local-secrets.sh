#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(git rev-parse --show-toplevel)"
failed=0

for variable_name in MOODLE_TEST_URL MOODLE_TEST_USERNAME MOODLE_TEST_PASSWORD; do
  value="${!variable_name:-}"
  if [[ -n "$value" ]] && git -C "$repo_dir" grep --quiet --fixed-strings -- "$value"; then
    printf 'Tracked files contain the value supplied through %s.\n' "$variable_name" >&2
    failed=1
  fi
done

if command -v gitleaks >/dev/null 2>&1; then
  gitleaks detect --source "$repo_dir" --no-banner --redact
fi

exit "$failed"
