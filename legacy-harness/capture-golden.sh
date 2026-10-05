#!/usr/bin/env bash
# Regenerates src/test/resources/parity/*.expected by running the unmodified
# legacy ACAS sl055 (compiled from an acas-legacy checkout) against every
# src/test/resources/parity/*.fixture via the sl055h harness.
#
# Usage: ACAS_LEGACY=/path/to/acas-legacy legacy-harness/capture-golden.sh
set -euo pipefail

legacy=${ACAS_LEGACY:?set ACAS_LEGACY to an acas-legacy checkout}
gnucobol=${GNUCOBOL_HOME:-/opt/gnucobol-3.2}
here=$(cd "$(dirname "$0")" && pwd)
parity=$(dirname "$here")/src/test/resources/parity
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

export PATH="$gnucobol/bin:$PATH"
export LD_LIBRARY_PATH="$gnucobol/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export COB_LIBRARY_PATH="$work/bin"
export COB_EXIT_WAIT=0
export TERM=${TERM:-xterm}

mkdir -p "$work/bin"
copy=(-I "$legacy/copybooks")
# Compiled the same way as common/comp-common-no-rdbms.sh and sales/comp-sales-no-rdbms.sh.
(
    cd "$work/bin"
    cobc -m "$legacy/common/dummy-rdbmsMT.cbl" "${copy[@]}" -Wlinkage
    for m in acas013 acas015 acas016 fhlogger; do
        cobc -m "$legacy/common/$m.cbl" "$legacy/common/dummy-rdbmsMT.cbl" "${copy[@]}" -Wlinkage -lz
    done
    cobc -m "$legacy/sales/sl055.cbl" "$legacy/sales/dummy-rdbmsMT.cbl" "${copy[@]}" -Wlinkage -lz
)
cobc -x "$here/sl055h.cbl" "${copy[@]}" -o "$work/bin/sl055h"

for fixture in "$parity"/*.fixture; do
    name=$(basename "$fixture" .fixture)
    run="$work/run-$name"
    mkdir -p "$run"
    cp "$fixture" "$run/in.fixture"
    # sl055 uses screen I/O, so it needs a terminal; script(1) provides one.
    (cd "$run" && script -qec "$work/bin/sl055h in.fixture out.dump" /dev/null > screen.log 2>&1)
    if [[ ! -s "$run/out.dump" ]]; then
        echo "FAILED: $name (no dump produced)" >&2
        cat -v "$run/screen.log" >&2
        exit 1
    fi
    cp "$run/out.dump" "$parity/$name.expected"
    echo "captured $name"
done
