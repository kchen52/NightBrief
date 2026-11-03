#!/usr/bin/env python3
"""Build the open-water mask (WMSK v1) used by darker-sky search.

The output matches ``GridOpenWater`` in
``core-sites/src/main/kotlin/app/nightbrief/sites/OpenWater.kt`` byte for byte.

Format (big-endian):

* int32  magic ``WMSK`` (0x574D534B)
* int32  version ``1``
* float64 south latitude of the grid (degrees)
* float64 west longitude of the grid (degrees)
* float64 cell size (degrees)
* int32  rows
* int32  cols
* packed bits, row-major from the south-west corner, 8 cells per byte.
  The first cell in a byte is the high bit. ``1`` is open water (ocean or
  lake). ``0`` is land, including islands in lakes.

A cell covers ``[south + r*cell, south + (r+1)*cell)`` by
``[west + c*cell, west + (c+1)*cell)``. Longitude ``+180`` is read as
``-180``. A latitude or longitude exactly on the north or east edge belongs
to the last cell.

Source
------
Natural Earth 1:10 million land and lakes (public domain). Ocean is every
cell whose center is outside the land polygons. Lakes are burned back to
water afterwards; holes in those polygons (islands) stay land. The Great
Lakes are land in the land polygons, which is why the lakes file is required.

The committed file is gzip. Do not commit the uncompressed mask or the
downloaded shapefiles. Requires rasterio, numpy, and pyshp.
"""

from __future__ import annotations

import argparse
import gzip
import math
import struct
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

try:
    import numpy as np
except ImportError as exc:  # pragma: no cover - environment without deps
    raise SystemExit(
        "numpy is required. Install it with: pip install -r tools/requirements.txt"
    ) from exc

MAGIC = 0x574D534B  # 'WMSK'
VERSION = 1
HEADER = struct.Struct(">iidddii")

LAND_URL = "https://naciscdn.org/naturalearth/10m/physical/ne_10m_land.zip"
LAKES_URL = "https://naciscdn.org/naturalearth/10m/physical/ne_10m_lakes.zip"

# Same sphere and destination as DarkSkyFinder.destination.
EARTH_RADIUS_KM = 6371.0
TORONTO = (43.6532, -79.3832)


def destination(latitude: float, longitude: float, distance_km: float, bearing_deg: float) -> tuple[float, float]:
    angular = distance_km / EARTH_RADIUS_KM
    lat1 = math.radians(latitude)
    lon1 = math.radians(longitude)
    bearing = math.radians(bearing_deg)
    lat2 = math.asin(
        math.sin(lat1) * math.cos(angular) + math.cos(lat1) * math.sin(angular) * math.cos(bearing)
    )
    lon2 = lon1 + math.atan2(
        math.sin(bearing) * math.sin(angular) * math.cos(lat1),
        math.cos(angular) - math.sin(lat1) * math.sin(lat2),
    )
    return math.degrees(lat2), math.degrees(lon2)


def index_of(latitude: float, longitude: float, south: float, west: float, cell: float, rows: int, cols: int) -> tuple[int, int] | None:
    """Same cell rule as ``WmskHeader.indexOf``."""
    if not math.isfinite(latitude) or not math.isfinite(longitude):
        return None
    lon = longitude % 360.0
    if lon >= 180.0:
        lon -= 360.0
    if lon < -180.0:
        lon += 360.0
    row = math.floor((latitude - south) / cell)
    col = math.floor((lon - west) / cell)
    if row == rows:
        row = rows - 1
    if col == cols:
        col = cols - 1
    if row < 0 or col < 0 or row >= rows or col >= cols:
        return None
    return int(row), int(col)


def is_water(mask: np.ndarray, latitude: float, longitude: float, south: float, west: float, cell: float) -> bool:
    rows, cols = mask.shape
    found = index_of(latitude, longitude, south, west, cell, rows, cols)
    if found is None:
        return False
    row, col = found
    return bool(mask[row, col])


def load_polygons(shp_path: Path) -> list:
    try:
        import shapefile
    except ImportError as exc:  # pragma: no cover - environment without deps
        raise SystemExit(
            "pyshp is required. Install it with: pip install -r tools/requirements.txt"
        ) from exc
    reader = shapefile.Reader(str(shp_path))
    polygons = []
    for shape in reader.shapes():
        if shape.shapeType not in (
            shapefile.POLYGON,
            shapefile.POLYGONZ,
            shapefile.POLYGONM,
        ):
            continue
        geometry = shape.__geo_interface__
        if geometry.get("coordinates"):
            polygons.append(geometry)
    reader.close()
    return polygons


def download(url: str, cache: Path) -> Path:
    name = url.rsplit("/", 1)[-1]
    dest = cache / name
    if not dest.is_file() or dest.stat().st_size == 0:
        cache.mkdir(parents=True, exist_ok=True)
        print(f"downloading {url}")
        request = urllib.request.Request(url, headers={"User-Agent": "NightBrief/0.1 (water-mask build)"})
        with urllib.request.urlopen(request) as response, dest.open("wb") as out:
            out.write(response.read())
    return dest


def unzip_shp(zip_path: Path, work: Path) -> Path:
    work.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as archive:
        archive.extractall(work)
    shp = next(work.glob("*.shp"))
    return shp


def rasterize(land, lakes, south: float, west: float, cell: float, rows: int, cols: int) -> np.ndarray:
    try:
        from rasterio.features import rasterize as rio_rasterize
        from rasterio.transform import Affine
    except ImportError as exc:  # pragma: no cover - environment without deps
        raise SystemExit(
            "rasterio is required. Install it with: pip install -r tools/requirements.txt"
        ) from exc
    # Row 0 is south, column 0 is west. Matches WmskHeader.indexOf.
    transform = Affine(cell, 0.0, west, 0.0, cell, south)
    mask = np.ones((rows, cols), dtype=np.uint8)
    rio_rasterize(land, out=mask, transform=transform, default_value=0)
    if lakes:
        rio_rasterize(lakes, out=mask, transform=transform, default_value=1)
    return mask


def pack_bits(mask: np.ndarray) -> bytes:
    flat = np.ascontiguousarray(mask, dtype=np.uint8).reshape(-1)
    if flat.size % 8 != 0:
        raise SystemExit(f"cell count {flat.size} is not a multiple of 8")
    packed = np.packbits(flat, bitorder="big")
    return packed.tobytes()


def write_gzip(path: Path, south: float, west: float, cell: float, rows: int, cols: int, payload: bytes) -> None:
    header = HEADER.pack(MAGIC, VERSION, south, west, cell, rows, cols)
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = header + payload
    with gzip.GzipFile(filename="", mode="wb", fileobj=path.open("wb"), compresslevel=9, mtime=0) as out:
        out.write(raw)


def required_probes() -> list[tuple[str, float, float, bool]]:
    """Points the shipped mask must classify, including the Lake Ontario bug."""
    probes: list[tuple[str, float, float, bool]] = [
        ("Toronto inland north", 43.80, -79.40, False),
        ("Lake Ontario center", 43.60, -77.80, True),
        ("Manitoulin Island", 45.80, -82.15, False),
        ("Kansas", 39.0, -98.0, False),
        ("Sahara", 25.0, 10.0, False),
        ("Pacific", 0.0, -150.0, True),
        ("Atlantic", 30.0, -40.0, True),
        ("Hudson Bay", 60.0, -85.0, True),
    ]
    # Bearings that issue #53 plotted offshore from downtown Toronto.
    for distance, bearing, name in (
        (30.0, 90.0, "Toronto 30 km E"),
        (30.0, 135.0, "Toronto 30 km SE"),
        (30.0, 180.0, "Toronto 30 km S"),
        (45.0, 90.0, "Toronto 45 km E"),
        (45.0, 135.0, "Toronto 45 km SE"),
    ):
        lat, lon = destination(TORONTO[0], TORONTO[1], distance, bearing)
        probes.append((name, lat, lon, True))
    # Directions that should still be places to set up a telescope.
    for distance, bearing, name in (
        (30.0, 0.0, "Toronto 30 km N"),
        (30.0, 315.0, "Toronto 30 km NW"),
        (60.0, 0.0, "Toronto 60 km N"),
    ):
        lat, lon = destination(TORONTO[0], TORONTO[1], distance, bearing)
        probes.append((name, lat, lon, False))
    return probes


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build the NightBrief open-water mask.")
    parser.add_argument(
        "output",
        nargs="?",
        default="data/src/main/assets/water_mask.wmsk.gzip",
        help="gzip WMSK path to write",
    )
    parser.add_argument("--cell-deg", type=float, default=0.05)
    parser.add_argument(
        "--cache",
        default=str(Path(tempfile.gettempdir()) / "nightbrief-natural-earth"),
        help="where to keep the downloaded zips",
    )
    args = parser.parse_args(argv)

    cell = args.cell_deg
    if cell <= 0:
        raise SystemExit("--cell-deg must be positive")
    south, west = -90.0, -180.0
    rows = int(round(180.0 / cell))
    cols = int(round(360.0 / cell))
    if abs(rows * cell - 180.0) > 1e-9 or abs(cols * cell - 360.0) > 1e-9:
        raise SystemExit(f"--cell-deg {cell} does not divide 180 and 360 exactly")
    if (rows * cols) % 8 != 0:
        raise SystemExit("grid size is not a multiple of 8 bits")

    cache = Path(args.cache)
    land_zip = download(LAND_URL, cache)
    lakes_zip = download(LAKES_URL, cache)
    land_shp = unzip_shp(land_zip, cache / "ne_10m_land")
    lakes_shp = unzip_shp(lakes_zip, cache / "ne_10m_lakes")
    print(f"reading {land_shp.name} and {lakes_shp.name}")
    land = load_polygons(land_shp)
    lakes = load_polygons(lakes_shp)
    print(f"land polygons {len(land)}, lake polygons {len(lakes)}")

    print(f"rasterizing {cols} x {rows} at {cell}°")
    mask = rasterize(land, lakes, south, west, cell, rows, cols)
    water_cells = int(mask.sum())
    print(f"open-water cells {water_cells} / {mask.size} ({100.0 * water_cells / mask.size:.1f}%)")

    failed = False
    for name, lat, lon, expect in required_probes():
        got = is_water(mask, lat, lon, south, west, cell)
        mark = "ok" if got == expect else "FAIL"
        if got != expect:
            failed = True
        print(f"  {mark:4} {name}: water={got} expected={expect} ({lat:.4f}, {lon:.4f})")
    if failed:
        print("refusing to write a mask that misses a required probe", file=sys.stderr)
        return 1

    payload = pack_bits(mask)
    output = Path(args.output)
    write_gzip(output, south, west, cell, rows, cols, payload)
    print(f"wrote {output} ({output.stat().st_size} bytes gzip, {len(payload) + HEADER.size} bytes raw)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
