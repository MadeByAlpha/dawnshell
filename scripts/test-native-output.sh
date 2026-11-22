#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf -- "$work_dir"' EXIT

"${CC:-cc}" -std=c17 -Os -Wall -Wextra -Werror -Wformat=2 \
    -I "$repo_dir/app/src/main/cpp" \
    "$repo_dir/app/src/test/cpp/dawnshell_json_test.c" \
    -o "$work_dir/dawnshell_json_test"
"$work_dir/dawnshell_json_test"
