#!/usr/bin/env python3
"""Runs the pl080h harness in a pseudo terminal and types the fixture's KEY records.

Usage: drive-pl080.py <pl080h-binary> <run-dir>

<run-dir> must contain in.fixture. Each "KEY|text" record is typed as text
followed by Enter, in order. The harness writes out.dump; the terminal
output is saved as screen.log.
"""
import sys
import time

import pexpect

KEY_DELAY_SECONDS = 0.4


def keys(fixture_path):
    with open(fixture_path, encoding="ascii") as fixture:
        for line in fixture:
            line = line.rstrip("\n")
            if line.startswith("KEY|"):
                yield line[len("KEY|"):]


def main():
    binary, run_dir = sys.argv[1], sys.argv[2]
    child = pexpect.spawn(binary, ["in.fixture", "out.dump"], cwd=run_dir,
                          dimensions=(24, 80), encoding="latin-1", timeout=20)
    with open(f"{run_dir}/screen.log", "w", encoding="latin-1") as log:
        child.logfile_read = log
        time.sleep(1.0)
        for key in keys(f"{run_dir}/in.fixture"):
            child.send(key + "\r")
            time.sleep(KEY_DELAY_SECONDS)
        child.expect(pexpect.EOF)
    child.close()
    if child.exitstatus != 0:
        sys.exit(f"pl080h exited with status {child.exitstatus}")


if __name__ == "__main__":
    main()
