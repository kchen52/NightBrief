#!/usr/bin/env python3
"""Build a NightBrief light-pollution grid (NBLP v1) from a GeoTIFF.

The output matches ``GridBortleLookup`` in
``core-sites/src/main/kotlin/app/nightbrief/sites/Bortle.kt`` byte for byte.

Format (big-endian):

* int32  magic ``NBLP`` (0x4E424C50)
* int32  version ``1``
* float64 south latitude of the grid (degrees)
* float64 west longitude of the grid (degrees)
* float64 cell size (degrees)
* int32  rows
* int32  cols
* ``rows * cols`` uint8 values, row-major, starting at the south-west corner.
  ``0`` is no data; ``1``..``9`` are Bortle classes.

A cell covers ``[south + r*cell, south + (r+1)*cell)`` by
``[west + c*cell, west + (c+1)*cell)``, which is how
``GridBortleLookup.lookup`` indexes the raster.

Input units
-----------
Default ``--input-units mcd`` is artificial zenith brightness in mcd/m², the
unit of the 2015 World Atlas of Artificial Night Sky Brightness. Natural sky
of 0.171 mcd/m² is added, then converted with the same constant as
``BortleClass.fromArtificialBrightness``::

    SQM = 12.589 - 2.5 * log10(total_mcd / 1000)

(12.58 is that constant rounded; the grid uses 12.589 so it agrees with the
Kotlin classifier.) ``--input-units sqm`` reads total sky brightness in
mag/arcsec² and maps it with ``BortleClass.fromSqm`` (no natural-sky term).

Downsampling
------------
``--cell-deg`` bins source pixels into the output cells. Each source sample is
converted to linear luminance first (artificial mcd/m², or total mcd/m² when
the raster is SQM). The default aggregate is ``max``: the brightest sample in
the cell, i.e. the worst sky an observer in that cell could be standing under.
``--aggregate mean`` uses the mean luminance instead, then classifies once.
Pixels flagged nodata (and non-finite values) are skipped. A cell with no
valid samples is written as 0.

The grid is aligned to the south-west corner of ``--bbox`` (default: the
raster bounds). Dimensions are rounded up so the bbox is fully covered; the
header stores that corner and the cell size, not the original north/east edge.
Requires a geographic CRS in degrees (EPSG:4326 or similar). rasterio is
imported only when a raster is opened; if it is missing the tool exits with
an install hint.
"""

from __future__ import annotations

import argparse
import math
import struct
import sys
from typing import Iterable

try:
    import numpy as np
except ImportError as exc:  # pragma: no cover - environment without deps
    raise SystemExit(
        "numpy is required. Install it with: pip install -r tools/requirements.txt"
    ) from exc

MAGIC = 0x4E424C50  # 'NBLP'
VERSION = 1
HEADER = struct.Struct(">iidddii")  # magic, version, south, west, cell, rows, cols

# Identical to BortleClass.fromArtificialBrightness / fromSqm.
NATURAL_SKY_MCD = 0.171
SQM_ZERO_POINT = 12.589
SQM_THRESHOLDS = (
    (21.99, 1),
    (21.89, 2),
    (21.69, 3),
    (20.49, 4),
    (19.50, 5),
    (18.94, 6),
    (18.38, 7),
    (17.80, 8),
)


def bortle_from_sqm(sqm: float) -> int:
    for threshold, bortle in SQM_THRESHOLDS:
        if sqm >= threshold:
            return bortle
    return 9


def bortle_from_artificial_mcd(mcd: float) -> int:
    total = NATURAL_SKY_MCD + max(0.0, mcd)
    sqm = SQM_ZERO_POINT - 2.5 * math.log10(total / 1000.0)
    return bortle_from_sqm(sqm)


def _classify(luminance: np.ndarray, valid: np.ndarray, units: str) -> np.ndarray:
    """Classify pooled linear luminance. ``mcd`` values are artificial; ``sqm`` values are total."""
    out = np.zeros(luminance.shape, dtype=np.uint8)
    if not np.any(valid):
        return out
    vals = luminance[valid]
    if units == "mcd":
        total = NATURAL_SKY_MCD + np.maximum(vals, 0.0)
    else:
        total = vals
    total = np.maximum(total, 1e-12)
    sqm = SQM_ZERO_POINT - 2.5 * np.log10(total / 1000.0)
    classes = np.select(
        [sqm >= t for t, _ in SQM_THRESHOLDS],
        [b for _, b in SQM_THRESHOLDS],
        default=9,
    ).astype(np.uint8)
    out[valid] = classes
    return out


def cell_count(span: float, cell: float) -> int:
    """Whole cells needed to cover ``span``, without an extra cell on exact multiples."""
    if span <= 0 or cell <= 0:
        raise SystemExit(f"invalid span {span} or cell {cell}")
    ratio = span / cell
    nearest = int(round(ratio))
    if nearest >= 1 and abs(nearest - ratio) <= 1e-6:
        return nearest
    count = int(math.ceil(ratio - 1e-12))
    if count < 1:
        raise SystemExit(f"bbox span {span} is smaller than one cell of {cell} degrees")
    return count


def _open_raster(path: str):
    try:
        import rasterio
    except ImportError as exc:
        raise SystemExit(
            "rasterio is required to read a GeoTIFF. "
            "Install it with: pip install -r tools/requirements.txt"
        ) from exc
    try:
        return rasterio.open(path)
    except Exception as exc:
        raise SystemExit(f"could not open {path}: {exc}") from exc


def _bbox_from_dataset(src) -> tuple[float, float, float, float]:
    bounds = src.bounds
    return float(bounds.bottom), float(bounds.left), float(bounds.top), float(bounds.right)


def _span_east(west: float, east: float) -> float:
    if east > west:
        return east - west
    # Dateline-crossing bbox: west=170, east=-170 covers 20 degrees.
    return east + 360.0 - west


def _require_geographic(src) -> None:
    crs = src.crs
    if crs is None:
        print(
            "warning: raster has no CRS; assuming longitude/latitude in degrees",
            file=sys.stderr,
        )
        return
    if not crs.is_geographic:
        raise SystemExit(
            f"raster CRS {crs} is projected. Reproject to a geographic CRS "
            "(EPSG:4326, degrees of longitude/latitude) and run again."
        )


def _default_cell(src) -> float:
    pixel_x, pixel_y = (abs(float(v)) for v in src.res)
    if pixel_x <= 0 or pixel_y <= 0:
        raise SystemExit("raster pixel size is not positive; pass --cell-deg")
    if abs(pixel_x - pixel_y) / max(pixel_x, pixel_y) > 0.01:
        raise SystemExit(
            f"raster pixels are {pixel_x:.6f} by {pixel_y:.6f} degrees; "
            "pass --cell-deg to choose the output cell"
        )
    return max(pixel_x, pixel_y)


def convert(
    dataset,
    output_path: str,
    *,
    bbox: tuple[float, float, float, float] | None = None,
    cell_deg: float | None = None,
    input_units: str = "mcd",
    aggregate: str = "max",
) -> tuple[int, int]:
    """Bin ``dataset`` into an NBLP file. Returns ``(rows, cols)``."""
    if input_units not in ("mcd", "sqm"):
        raise SystemExit("--input-units must be mcd or sqm")
    if aggregate not in ("max", "mean"):
        raise SystemExit("--aggregate must be max (worst sky) or mean")
    _require_geographic(dataset)

    south, west, north, east = bbox if bbox is not None else _bbox_from_dataset(dataset)
    if not (-90.0 <= south < north <= 90.0):
        raise SystemExit(f"latitudes must satisfy -90 <= south < north <= 90 (got {south}, {north})")
    lon_span = _span_east(west, east)
    if lon_span <= 0 or lon_span > 360.0:
        raise SystemExit(f"longitude span must be within (0, 360] degrees (got {lon_span})")

    cell = float(cell_deg) if cell_deg is not None else _default_cell(dataset)
    if cell <= 0:
        raise SystemExit("--cell-deg must be positive")
    rows = cell_count(north - south, cell)
    cols = cell_count(lon_span, cell)

    pooled_max = np.full((rows, cols), -np.inf, dtype=np.float64)
    pooled_sum = np.zeros((rows, cols), dtype=np.float64)
    pooled_count = np.zeros((rows, cols), dtype=np.int64)

    transform = dataset.transform
    for _, window in dataset.block_windows(1):
        row_off = int(window.row_off)
        col_off = int(window.col_off)
        height = int(window.height)
        width = int(window.width)
        if height == 0 or width == 0:
            continue
        block = dataset.read(1, window=window, masked=True)
        brightness = _block_brightness(block, input_units)
        row_centers = np.arange(row_off, row_off + height, dtype=np.float64) + 0.5
        col_centers = np.arange(col_off, col_off + width, dtype=np.float64) + 0.5
        lon = transform.a * col_centers[np.newaxis, :] + transform.b * row_centers[:, np.newaxis] + transform.c
        lat = transform.d * col_centers[np.newaxis, :] + transform.e * row_centers[:, np.newaxis] + transform.f
        # Shift into [west, west+360) so a dateline-crossing bbox still bins.
        lon = west + np.mod(lon - west, 360.0)
        finite = np.isfinite(brightness)
        r = np.floor((lat - south) / cell).astype(np.int64)
        c = np.floor((lon - west) / cell).astype(np.int64)
        inside = finite & (r >= 0) & (r < rows) & (c >= 0) & (c < cols)
        if not np.any(inside):
            continue
        rr = r[inside]
        cc = c[inside]
        vals = brightness[inside]
        if aggregate == "max":
            np.maximum.at(pooled_max, (rr, cc), vals)
        else:
            np.add.at(pooled_sum, (rr, cc), vals)
            np.add.at(pooled_count, (rr, cc), 1)

    if aggregate == "max":
        valid = np.isfinite(pooled_max)
        luminance = pooled_max
    else:
        valid = pooled_count > 0
        luminance = np.zeros_like(pooled_sum)
        np.divide(pooled_sum, pooled_count, out=luminance, where=valid)

    grid = _classify(luminance, valid, input_units)
    write_nblp(output_path, south, west, cell, grid)
    return rows, cols


def _block_brightness(block, units: str) -> np.ndarray:
    """Linear luminance for pooling. Nodata and non-finite samples are NaN.

    ``mcd`` → artificial brightness (mcd/m²), negatives clamped to 0.
    ``sqm`` → total brightness (mcd/m²) inverted from mag/arcsec².
    """
    if np.ma.isMaskedArray(block):
        mask = np.ma.getmaskarray(block)
        values = np.asarray(block.filled(np.nan), dtype=np.float64)
    else:
        values = np.asarray(block, dtype=np.float64)
        mask = np.zeros(values.shape, dtype=bool)
    mask = mask | ~np.isfinite(values)
    brightness = np.full(values.shape, np.nan, dtype=np.float64)
    valid = ~mask
    if units == "mcd":
        brightness[valid] = np.maximum(values[valid], 0.0)
    else:
        brightness[valid] = 1000.0 * np.power(10.0, (SQM_ZERO_POINT - values[valid]) / 2.5)
    return brightness


def write_nblp(path: str, south: float, west: float, cell: float, grid: np.ndarray) -> None:
    if grid.ndim != 2 or grid.dtype != np.uint8:
        grid = np.asarray(grid, dtype=np.uint8)
    rows, cols = grid.shape
    if rows <= 0 or cols <= 0 or rows > 2**31 - 1 or cols > 2**31 - 1:
        raise SystemExit(f"grid dimensions {rows}x{cols} do not fit the NBLP header")
    payload = np.ascontiguousarray(grid, dtype=np.uint8).tobytes(order="C")
    if len(payload) != rows * cols:
        raise SystemExit("internal error: NBLP payload size does not match rows*cols")
    with open(path, "wb") as fh:
        fh.write(HEADER.pack(MAGIC, VERSION, float(south), float(west), float(cell), int(rows), int(cols)))
        fh.write(payload)


def parse_bbox(text: str) -> tuple[float, float, float, float]:
    parts = [p.strip() for p in text.split(",")]
    if len(parts) != 4:
        raise SystemExit("--bbox must be south,west,north,east in degrees")
    try:
        south, west, north, east = (float(p) for p in parts)
    except ValueError as exc:
        raise SystemExit(f"--bbox values must be numbers: {text}") from exc
    return south, west, north, east


def parse_args(argv: Iterable[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Convert a light-pollution GeoTIFF into a NightBrief NBLP v1 grid. "
            "Cells are max-pooled by default (worst sky in the cell); "
            "pass --aggregate mean for the mean luminance."
        )
    )
    parser.add_argument("input", help="GeoTIFF of artificial brightness (mcd/m²) or SQM (mag/arcsec²)")
    parser.add_argument("output", help="output .nblp path")
    parser.add_argument(
        "--bbox",
        help="south,west,north,east in degrees (default: raster bounds). "
        "west > east means the box crosses the antimeridian",
    )
    parser.add_argument(
        "--cell-deg",
        type=float,
        help="output cell size in degrees (default: the source pixel size). "
        "Source samples inside a cell are pooled before classification",
    )
    parser.add_argument(
        "--input-units",
        choices=("mcd", "sqm"),
        default="mcd",
        help="mcd: artificial brightness in mcd/m² (default). sqm: total sky brightness in mag/arcsec²",
    )
    parser.add_argument(
        "--aggregate",
        choices=("max", "mean"),
        default="max",
        help="max: brightest sample in the cell, the worst-case sky (default). mean: mean luminance",
    )
    return parser.parse_args(list(argv) if argv is not None else None)


def main(argv: Iterable[str] | None = None) -> None:
    args = parse_args(argv)
    bbox = parse_bbox(args.bbox) if args.bbox else None
    dataset = _open_raster(args.input)
    try:
        rows, cols = convert(
            dataset,
            args.output,
            bbox=bbox,
            cell_deg=args.cell_deg,
            input_units=args.input_units,
            aggregate=args.aggregate,
        )
    finally:
        dataset.close()
    print(
        f"wrote {args.output}: {rows}x{cols} cells, {args.aggregate} luminance, units={args.input_units}",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
