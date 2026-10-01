#!/usr/bin/env python3
"""Synthetic-raster checks for the NBLP v1 writer. No dataset download."""

from __future__ import annotations

import math
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np

import build_bortle_grid as grid

try:
    import rasterio
    from rasterio.transform import from_bounds
except ImportError as exc:  # pragma: no cover
    raise SystemExit(
        "rasterio is required to build the synthetic GeoTIFF. "
        "pip install -r tools/requirements.txt"
    ) from exc

# Independent of the tool's helpers so a swapped threshold fails the test.
# Constants are the ones in Bortle.kt (natural sky 0.171, zero point 12.589).
_THRESHOLDS = (
    (21.99, 1),
    (21.89, 2),
    (21.69, 3),
    (20.49, 4),
    (19.50, 5),
    (18.94, 6),
    (18.38, 7),
    (17.80, 8),
)


def reference_bortle_sqm(sqm: float) -> int:
    for threshold, bortle in _THRESHOLDS:
        if sqm >= threshold:
            return bortle
    return 9


def reference_bortle_mcd(mcd: float) -> int:
    total = 0.171 + max(0.0, mcd)
    sqm = 12.589 - 2.5 * math.log10(total / 1000.0)
    return reference_bortle_sqm(sqm)


def read_nblp(path: Path) -> tuple[dict, bytes]:
    blob = path.read_bytes()
    if len(blob) < 40:
        raise AssertionError(f"header truncated: {len(blob)} bytes")
    magic, version, south, west, cell, rows, cols = struct.unpack(">iidddii", blob[:40])
    payload = blob[40:]
    header = {
        "magic": magic,
        "magic_bytes": blob[:4],
        "version": version,
        "south": south,
        "west": west,
        "cell": cell,
        "rows": rows,
        "cols": cols,
    }
    if len(payload) != rows * cols:
        raise AssertionError(f"payload {len(payload)} != {rows}*{cols}")
    return header, payload


def write_tif(path: Path, data: np.ndarray, south: float, west: float, north: float, east: float, nodata):
    height, width = data.shape
    with rasterio.open(
        path,
        "w",
        driver="GTiff",
        height=height,
        width=width,
        count=1,
        dtype="float32",
        crs="EPSG:4326",
        transform=from_bounds(west, south, east, north, width, height),
        nodata=nodata,
    ) as dst:
        dst.write(data.astype(np.float32), 1)


class BuildBortleGridTest(unittest.TestCase):
    def test_reference_thresholds_match_known_samples(self):
        # Locked to values computed from the Kotlin formula, not the tool.
        self.assertEqual(reference_bortle_mcd(0.0), 1)
        self.assertEqual(reference_bortle_mcd(0.2), 4)
        self.assertEqual(reference_bortle_mcd(1.0), 5)
        self.assertEqual(reference_bortle_mcd(10.0), 9)
        self.assertEqual(reference_bortle_sqm(22.2), 1)
        self.assertEqual(reference_bortle_sqm(18.0), 8)
        self.assertEqual(reference_bortle_sqm(17.0), 9)

    def test_header_and_south_west_layout(self):
        # North-up raster. Row 0 is the northern edge; NBLP row 0 must be the southern edge.
        #   north row: 0.0 mcd (B1), 0.2 mcd (B4)
        #   south row: 1.0 mcd (B5), 10 mcd (B9)
        data = np.array([[0.0, 0.2], [1.0, 10.0]], dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            tif = Path(tmp) / "sky.tif"
            out = Path(tmp) / "sky.nblp"
            write_tif(tif, data, south=10, west=20, north=12, east=22, nodata=None)
            grid.main([str(tif), str(out), "--bbox", "10,20,12,22", "--cell-deg", "1", "--aggregate", "max"])
            header, payload = read_nblp(out)

        self.assertEqual(header["magic_bytes"], b"NBLP")
        self.assertEqual(header["magic"], 0x4E424C50)
        self.assertEqual(header["version"], 1)
        self.assertEqual(header["south"], 10.0)
        self.assertEqual(header["west"], 20.0)
        self.assertEqual(header["cell"], 1.0)
        self.assertEqual((header["rows"], header["cols"]), (2, 2))
        self.assertEqual(list(payload), [5, 9, 1, 4])

    def test_max_pool_is_worst_sky_and_mean_is_softer(self):
        data = np.array([[0.0, 0.2], [1.0, 10.0]], dtype=np.float32)
        samples = [float(v) for v in data.astype(np.float64).ravel()]
        mean_mcd = sum(samples) / len(samples)
        expected_mean = reference_bortle_mcd(mean_mcd)
        self.assertEqual(expected_mean, 7)
        self.assertNotEqual(expected_mean, 9)

        with tempfile.TemporaryDirectory() as tmp:
            tif = Path(tmp) / "sky.tif"
            write_tif(tif, data, south=10, west=20, north=12, east=22, nodata=None)
            worst = Path(tmp) / "worst.nblp"
            mean = Path(tmp) / "mean.nblp"
            grid.main([str(tif), str(worst), "--bbox", "10,20,12,22", "--cell-deg", "2", "--aggregate", "max"])
            grid.main(
                [str(tif), str(mean), "--bbox", "10,20,12,22", "--cell-deg", "2", "--aggregate", "mean"]
            )
            worst_header, worst_bytes = read_nblp(worst)
            mean_header, mean_bytes = read_nblp(mean)

        self.assertEqual((worst_header["rows"], worst_header["cols"]), (1, 1))
        self.assertEqual(list(worst_bytes), [9])
        self.assertEqual(mean_header["cell"], 2.0)
        self.assertEqual(list(mean_bytes), [expected_mean])

    def test_sqm_units_and_nodata_zero(self):
        # south row 18.0 (B8), 17.0 (B9); north row 22.2 (B1), nodata.
        nodata = -9999.0
        data = np.array([[22.2, nodata], [18.0, 17.0]], dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            tif = Path(tmp) / "sqm.tif"
            out = Path(tmp) / "sqm.nblp"
            write_tif(tif, data, south=10, west=20, north=12, east=22, nodata=nodata)
            grid.main(
                [
                    str(tif),
                    str(out),
                    "--bbox",
                    "10,20,12,22",
                    "--cell-deg",
                    "1",
                    "--input-units",
                    "sqm",
                ]
            )
            header, payload = read_nblp(out)
        self.assertEqual(header["version"], 1)
        self.assertEqual(list(payload), [8, 9, 1, 0])


if __name__ == "__main__":
    unittest.main()
