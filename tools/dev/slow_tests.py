#!/usr/bin/env python3
"""Which game tests hold a run: the "vnap test:" lines of a GameTest log (GameTestServerMixin).

  python3 tools/dev/slow_tests.py [/tmp/vl-gts.log] [N]

The tests of a batch start together and the next batch waits for the slowest, so a batch costs its longest test;
the batches are listed by that, each with the test that held it, then the N slowest tests.
"""
import re
import sys
from collections import defaultdict

LINE = re.compile(r"vnap test: (\S+) (\S+) (\d+)t (\d+)ms(?: own (\d+)ms)?( FAILED)?")


def main(args: list[str]) -> int:
    path = args[0] if args else "/tmp/vl-gts.log"
    top = int(args[1]) if len(args) > 1 else 10
    tests = [(m.group(1), m.group(2), int(m.group(3)), int(m.group(4)), bool(m.group(6)), int(m.group(5) or 0))
             for m in map(LINE.search, open(path, encoding="utf-8", errors="replace")) if m]
    if not tests:
        print("no 'vnap test:' lines (a log from before the timing, or the run did not start)")
        return 1
    batches = defaultdict(list)
    for test in tests:
        batches[test[0]].append(test)
    total = sum(max(t[3] for t in group) for group in batches.values())
    print(f"{len(tests)} tests in {len(batches)} batches, the batches' longest tests add up to {total / 1000:.0f} s")
    for batch, group in sorted(batches.items(), key=lambda item: -max(t[3] for t in item[1]))[:top]:
        slowest = max(group, key=lambda t: t[3])
        print(f"  {max(t[3] for t in group) / 1000:6.1f} s  {batch} ({len(group)} tests): {slowest[1]} {slowest[2]} ticks")
    print(f"the {top} slowest tests:")
    for batch, name, ticks, ms, failed, own in sorted(tests, key=lambda t: -t[3])[:top]:
        print(f"  {ms / 1000:6.1f} s  {ticks:5d} ticks  {name} ({batch}){' FAILED' if failed else ''}")
    print(f"the {top} heaviest starts (the test's own setup; its batch waits for it):")
    for batch, name, ticks, ms, failed, own in sorted(tests, key=lambda t: -t[5])[:top]:
        print(f"  {own / 1000:6.1f} s  {name} ({batch})")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
