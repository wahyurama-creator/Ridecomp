#!/usr/bin/env python3
"""Lists touch targets smaller than 56 dp (UI-07) in a capture.sh run (T13.2). Standard library only (R2).

    python3 -I scripts/screens/targets.py build/screens/<label>/320dp

Reads every <name>.xml (uiautomator dump) and device.txt (density) of the folder. uiautomator reports the
VISIBLE bounds: an element cut by the edge of a scrolling list looks smaller than it is (marked "edge").
"""
import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

MIN_TARGET_DP = 56  # UI-07, 08-CODING-RULES §6
DP_PER_INCH = 160
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def density_of(folder):
    values = dict(line.split("=", 1) for line in (folder / "device.txt").read_text().split())
    return int(values["density"])


def bounds_of(node):
    return tuple(map(int, BOUNDS.match(node.get("bounds")).groups()))


def small_targets(dump, density):
    tree = ElementTree.parse(dump)  # our own uiautomator dump: no DOCTYPE or entities

    def walk(node, viewport):
        if node.get("scrollable") == "true":
            viewport = bounds_of(node)
        if node.get("clickable") == "true" or node.get("checkable") == "true":
            yield node, viewport
        for child in node.findall("node"):
            yield from walk(child, viewport)

    root = tree.getroot().find("node")
    for node, viewport in walk(root, bounds_of(root)):
        x1, y1, x2, y2 = bounds_of(node)
        width_dp, height_dp = (x2 - x1) * DP_PER_INCH / density, (y2 - y1) * DP_PER_INCH / density
        if min(width_dp, height_dp) >= MIN_TARGET_DP:
            continue
        label = node.get("content-desc") or node.get("text") or node.get("resource-id") or node.get("class")
        # Cut by the edge of the screen or of the scrolling list it sits in: only partly visible.
        edge = " edge" if y1 <= viewport[1] or y2 >= viewport[3] else ""
        yield f"{width_dp:.0f} x {height_dp:.0f} dp  {label!r} at {node.get('bounds')}{edge}"


def main(folder):
    density, found = density_of(folder), 0
    for dump in sorted(folder.glob("*.xml")):
        for line in small_targets(dump, density):
            print(f"{dump.stem}: {line}")
            found += 1
    print(f"{found} target(s) under {MIN_TARGET_DP} dp")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(Path(sys.argv[1]))
