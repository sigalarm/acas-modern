#!/usr/bin/env bash
# Regenerates src/test/resources/parity-pl080/*.expected by running the
# unmodified legacy ACAS pl080 (compiled from an acas-legacy checkout) against
# every src/test/resources/parity-pl080/*.fixture via the pl080h harness.
# pl080 is interactive: drive-pl080.py types each fixture's KEY records.
#
# Usage: ACAS_LEGACY=/path/to/acas-legacy legacy-harness/capture-golden-pl080.sh
# Needs GnuCOBOL 3.2 and python3 with pexpect.
set -euo pipefail

legacy=${ACAS_LEGACY:?set ACAS_LEGACY to an acas-legacy checkout}
gnucobol=${GNUCOBOL_HOME:-/opt/gnucobol-3.2}
here=$(cd "$(dirname "$0")" && pwd)
parity=$(dirname "$here")/src/test/resources/parity-pl080
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

export PATH="$gnucobol/bin:$PATH"
export LD_LIBRARY_PATH="$gnucobol/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export COB_LIBRARY_PATH="$work/bin"
export COB_EXIT_WAIT=0
export TERM=xterm

mkdir -p "$work/bin"
copy=(-I "$legacy/copybooks")
# Compiled the same way as common/comp-common-no-rdbms.sh and purchase/comp-purchase-no-rdbms.sh.
(
    cd "$work/bin"
    cobc -m "$legacy/common/dummy-rdbmsMT.cbl" "${copy[@]}" -Wlinkage
    for m in acas022 acas029 fhlogger; do
        cobc -m "$legacy/common/$m.cbl" "$legacy/common/dummy-rdbmsMT.cbl" "${copy[@]}" -Wlinkage -lz
    done
    cobc -m "$legacy/common/maps04.cbl" "${copy[@]}"
    cobc -m "$legacy/purchase/pl080.cbl" "$legacy/purchase/dummy-rdbmsMT.cbl" "${copy[@]}" -Wlinkage -lz
)
cobc -x "$here/pl080h.cbl" "${copy[@]}" -o "$work/bin/pl080h"

for fixture in "$parity"/*.fixture; do
    name=$(basename "$fixture" .fixture)
    run="$work/run-$name"
    mkdir -p "$run"
    cp "$fixture" "$run/in.fixture"
    if ! python3 "$here/drive-pl080.py" "$work/bin/pl080h" "$run" || [[ ! -s "$run/out.dump" ]]; then
        echo "FAILED: $name" >&2
        cat -v "$run/screen.log" >&2
        exit 1
    fi
    cp "$run/out.dump" "$parity/$name.expected"
    echo "captured $name"
done
