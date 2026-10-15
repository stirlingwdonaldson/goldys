"""External daily features: public holidays, Victorian school terms,
university teaching calendars and trading-hours weather.

Every fetcher degrades gracefully: if a source is unreachable the feature
columns are still created (as NA) so the downstream schema never changes.

Configuration comes from environment variables (``FORECAST_*``) rather than a
``config.yaml``, but the behaviour mirrors the reference pipeline:

* ``FORECAST_VENUE_LAT`` / ``FORECAST_VENUE_LON`` — venue coordinates;
* ``FORECAST_TIMEZONE`` — Open-Meteo localisation;
* ``FORECAST_OPEN_WEEKDAY`` / ``FORECAST_OPEN_WEEKEND`` / ``FORECAST_CLOSE`` —
  the trading-hours window used to aggregate weather;
* ``FORECAST_INCLUDE_WEATHER`` / ``FORECAST_INCLUDE_SCHOOL`` /
  ``FORECAST_INCLUDE_UNIVERSITY`` — feature toggles;
* ``FORECAST_INCLUDE_BOOKED_COVERS`` (``true``/``false``/``force``) and
  ``FORECAST_BOOKED_COVERS_MIN_COVERAGE`` — the booked-covers coverage gate;
* ``FORECAST_REVENUE_LAGS`` / ``FORECAST_REVENUE_ROLLING_WINDOWS`` /
  ``FORECAST_FEATURE_EXCLUDE`` — comma-separated feature-selection overrides;
* ``FORECAST_HISTORY_START`` — the earliest history date;
* ``FORECAST_CACHE_DIR`` — where HTTP responses are cached on disk.
"""

from __future__ import annotations

import io
import json
import logging
import os
import re
import time
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any

import httpx
import pandas as pd
import yaml

LOGGER = logging.getLogger(__name__)

OPEN_METEO_ARCHIVE = "https://archive-api.open-meteo.com/v1/archive"
OPEN_METEO_FORECAST = "https://api.open-meteo.com/v1/forecast"
DATAVIC_PACKAGE_URL = (
    "https://discover.data.vic.gov.au/api/3/action/package_show"
    "?id=government-dates-api-data"
)

WEATHER_FIELDS = [
    "temperature_2m",
    "precipitation",
    "cloud_cover",
    "wind_gusts_10m",
    "weather_code",
]

PUBLIC_HOLIDAY_COLUMNS = [
    "public_holiday",
    "public_holiday_name",
    "public_holiday_eve",
    "day_after_public_holiday",
]
SCHOOL_COLUMNS = ["school_term", "school_holiday"]
UNIVERSITY_COLUMNS = [
    "uni_in_session",
    "uni_teaching_weeks",
    "uni_assessment",
    "uni_break",
]
WEATHER_COLUMNS = [
    "service_temp_mean_c",
    "service_temp_max_c",
    "service_rain_mm",
    "service_rain_probability_max_pct",
    "service_sunny_hours",
    "service_storm",
    "service_wind_gust_max_kmh",
    "weather_source",
]


# ---------------------------------------------------------------------------
# Environment-based configuration
# ---------------------------------------------------------------------------


class ConfigError(RuntimeError):
    """Raised when configuration is missing something the pipeline needs."""


def _as_bool(value: str) -> bool:
    return value.strip().lower() in ("1", "true", "yes", "on")


def _as_include_booked_covers(value: str) -> bool | str:
    """``true``/``false`` as a bool, ``force`` as the string the coverage gate
    treats as "always use, however sparse"."""
    stripped = value.strip().lower()
    if stripped == "force":
        return "force"
    return stripped in ("1", "true", "yes", "on")


def _as_int_list(value: str) -> list[int]:
    return [int(part) for part in value.split(",") if part.strip()]


def _as_str_list(value: str) -> list[str]:
    return [part.strip() for part in value.split(",") if part.strip()]


# Defaults match the reference repo's ``config.yaml`` venue block.
_CONFIG_DEFAULTS: dict[str, Any] = {
    "venue.latitude": -37.79653,
    "venue.longitude": 144.98986,
    "venue.timezone": "Australia/Melbourne",
    "venue.opening_hour.weekday": 15,
    "venue.opening_hour.weekend": 12,
    "venue.closing_hour": 23,
    "features.include_weather": True,
    "features.include_school": True,
    "features.include_university": True,
}

_ENV_MAP: dict[str, tuple[str, Any]] = {
    "FORECAST_VENUE_LAT": ("venue.latitude", float),
    "FORECAST_VENUE_LON": ("venue.longitude", float),
    "FORECAST_TIMEZONE": ("venue.timezone", str),
    "FORECAST_OPEN_WEEKDAY": ("venue.opening_hour.weekday", int),
    "FORECAST_OPEN_WEEKEND": ("venue.opening_hour.weekend", int),
    "FORECAST_CLOSE": ("venue.closing_hour", int),
    "FORECAST_INCLUDE_WEATHER": ("features.include_weather", _as_bool),
    "FORECAST_INCLUDE_SCHOOL": ("features.include_school", _as_bool),
    "FORECAST_INCLUDE_UNIVERSITY": ("features.include_university", _as_bool),
    "FORECAST_INCLUDE_BOOKED_COVERS": ("features.include_booked_covers", _as_include_booked_covers),
    "FORECAST_BOOKED_COVERS_MIN_COVERAGE": ("features.booked_covers_min_coverage", float),
    "FORECAST_REVENUE_LAGS": ("features.revenue_lags", _as_int_list),
    "FORECAST_REVENUE_ROLLING_WINDOWS": ("features.revenue_rolling_windows", _as_int_list),
    "FORECAST_FEATURE_EXCLUDE": ("features.exclude", _as_str_list),
    "FORECAST_HISTORY_START": ("data.history_start", str),
}

DATA_DIR = Path(__file__).resolve().parent / "data"
DEFAULT_CACHE_DIR = Path(__file__).resolve().parent / ".cache"


class Config:
    """Small env-backed stand-in for the reference ``pipeline.config.Config``.

    Keeps the same ``get``/``require``/``resolve``/``cache_dir`` surface so the
    ported functions are unchanged, but the values come from ``FORECAST_*`` env
    vars (with the reference's defaults) instead of a YAML file.
    """

    def __init__(self, *, cache_dir: str | Path | None = None) -> None:
        self.raw: dict[str, Any] = dict(_CONFIG_DEFAULTS)
        for variable, (dotted, cast) in _ENV_MAP.items():
            value = os.environ.get(variable)
            if value is None or value == "":
                continue
            self.raw[dotted] = cast(value)
        self._cache_dir = Path(
            cache_dir or os.environ.get("FORECAST_CACHE_DIR") or DEFAULT_CACHE_DIR
        )

    def get(self, dotted: str, default: Any = None) -> Any:
        return self.raw.get(dotted, default)

    def require(self, dotted: str) -> Any:
        if dotted not in self.raw:
            raise ConfigError(f"missing required config key: {dotted}")
        return self.raw[dotted]

    def resolve(self, dotted: str) -> Path:
        if dotted == "paths.manual_events":
            return DATA_DIR / "manual_events.yaml"
        if dotted == "paths.university_calendar":
            return DATA_DIR / "university_dates.yaml"
        if dotted == "paths.cache_dir":
            return self._cache_dir
        raise ConfigError(f"unknown path key: {dotted}")

    @property
    def cache_dir(self) -> Path:
        self._cache_dir.mkdir(parents=True, exist_ok=True)
        return self._cache_dir


# ---------------------------------------------------------------------------
# Cached HTTP (urllib-free: httpx is already a service dependency)
# ---------------------------------------------------------------------------


_REQUEST_TIMEOUT = 60
_MAX_RETRIES = 4
_HTTP_HEADERS = {"User-Agent": "goldys-forecast/1.0"}


@dataclass
class CachedResponse:
    """Thin stand-in for ``requests.Response`` exposing ``.json()``/``.content``."""

    content: bytes
    url: str = ""
    status_code: int = 200
    encoding: str = "utf-8"

    def json(self) -> Any:
        return json.loads(self.content.decode(self.encoding or "utf-8"))


def _safe_filename(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", value)[:180]


def cached_get(
    url: str,
    *,
    cache_dir: Path,
    cache_key: str,
    params: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    force_refresh: bool = False,
) -> CachedResponse:
    """GET ``url`` with retries, cached on disk keyed by ``cache_key``."""
    cache_dir = Path(cache_dir)
    cache_dir.mkdir(parents=True, exist_ok=True)
    body_path = cache_dir / f"{_safe_filename(cache_key)}.bin"
    meta_path = cache_dir / f"{_safe_filename(cache_key)}.json"

    if body_path.exists() and meta_path.exists() and not force_refresh:
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
        LOGGER.debug("cache hit: %s", cache_key)
        return CachedResponse(
            content=body_path.read_bytes(),
            url=meta.get("url", url),
            encoding=meta.get("encoding", "utf-8"),
        )

    last_error: Exception | None = None
    for attempt in range(_MAX_RETRIES):
        try:
            response = httpx.get(
                url,
                params=params,
                headers={**_HTTP_HEADERS, **(headers or {})},
                timeout=_REQUEST_TIMEOUT,
                follow_redirects=True,
            )
            response.raise_for_status()
            body_path.write_bytes(response.content)
            meta_path.write_text(
                json.dumps(
                    {
                        "url": str(response.url),
                        "encoding": response.encoding,
                        "headers": dict(response.headers),
                    }
                ),
                encoding="utf-8",
            )
            return CachedResponse(
                content=response.content,
                url=str(response.url),
                encoding=response.encoding or "utf-8",
            )
        except Exception as exc:  # noqa: BLE001 - retried and re-raised below
            last_error = exc
            LOGGER.debug("GET attempt %s failed for %s: %s", attempt + 1, url, exc)
            time.sleep(2**attempt)

    raise RuntimeError(f"GET failed after {_MAX_RETRIES} attempts: {url}") from last_error


# ---------------------------------------------------------------------------
# DataVic important dates (public holidays + school terms)
# ---------------------------------------------------------------------------


def _normalise_columns(frame: pd.DataFrame) -> pd.DataFrame:
    result = frame.copy()
    result.columns = [
        re.sub(r"[^a-z0-9]+", "_", str(column).lower()).strip("_")
        for column in result.columns
    ]
    return result


def fetch_datavic_dates(config: Config, *, force_refresh: bool) -> pd.DataFrame:
    """Download the Victorian Government important-dates dataset."""
    package = cached_get(
        DATAVIC_PACKAGE_URL,
        cache_dir=config.cache_dir,
        cache_key="datavic_dates_package",
        force_refresh=force_refresh,
    ).json()

    resources = package.get("result", {}).get("resources", [])
    candidates: list[tuple[int, str]] = []
    for resource in resources:
        url = str(resource.get("url", ""))
        fmt = str(resource.get("format", "")).lower()
        if fmt == "csv" or ".csv" in url.lower():
            years = [int(value) for value in re.findall(r"20\d{2}", url)]
            candidates.append((max(years, default=0), url))
    if not candidates:
        raise RuntimeError("DataVic did not expose an important-dates CSV")

    csv_url = max(candidates)[1]
    response = cached_get(
        csv_url,
        cache_dir=config.cache_dir,
        cache_key=f"datavic_dates_{Path(csv_url).name}",
        force_refresh=force_refresh,
    )
    frame = _normalise_columns(pd.read_csv(io.BytesIO(response.content)))

    date_col = next(
        (c for c in ("important_date", "date", "start_date") if c in frame.columns), None
    )
    type_col = next(
        (c for c in ("datetype", "date_type", "type", "category") if c in frame.columns),
        None,
    )
    if not date_col or not type_col:
        raise RuntimeError(f"Unexpected DataVic columns: {list(frame.columns)}")

    frame["_date"] = pd.to_datetime(frame[date_col], dayfirst=True, errors="coerce")
    frame["_type"] = frame[type_col].fillna("").astype(str).str.upper()
    frame["_name"] = (
        frame["name"].fillna("").astype(str) if "name" in frame.columns else ""
    )
    return frame.dropna(subset=["_date"])


def melbourne_cup_day(year: int) -> pd.Timestamp:
    """First Tuesday of November — fixed by statute, so it can be computed
    rather than looked up. The DataVic feed does not publish it in advance."""
    first = pd.Timestamp(year=year, month=11, day=1)
    return first + pd.Timedelta(days=(1 - first.dayofweek) % 7)


def load_manual_events(config: Config, years: list[int]) -> dict[pd.Timestamp, str]:
    """Holidays the DataVic feed omits: Melbourne Cup (computed) and AFL
    Grand Final Friday (fixture-dependent, so listed in manual_events.yaml)."""
    events: dict[pd.Timestamp, str] = {}

    for year in years:
        events[melbourne_cup_day(year)] = "Melbourne Cup Day"

    try:
        path = config.resolve("paths.manual_events")
    except Exception:
        return events

    if not path.exists():
        LOGGER.warning(
            "Manual events file not found at %s — Melbourne Cup is still "
            "computed, but AFL Grand Final Friday will be missing",
            path,
        )
        return events

    with path.open(encoding="utf-8") as handle:
        payload = yaml.safe_load(handle) or {}

    listed_years = set()
    for raw_date, entry in (payload.get("afl_grand_final_friday") or {}).items():
        day = pd.Timestamp(str(raw_date)).normalize()
        listed_years.add(day.year)
        events[day] = "AFL Grand Final Friday"
        if not (entry or {}).get("verified", False):
            LOGGER.warning(
                "AFL Grand Final Friday %s is unverified — check the AFL "
                "fixture and set verified: true in manual_events.yaml",
                day.date(),
            )

    for raw_date, entry in (payload.get("additional_holidays") or {}).items():
        day = pd.Timestamp(str(raw_date)).normalize()
        events[day] = (entry or {}).get("name") or "Manual holiday"

    missing = sorted(set(years) - listed_years)
    if missing:
        LOGGER.warning(
            "No AFL Grand Final Friday listed for %s — add it to "
            "data/manual_events.yaml once the AFL finals fixture is out. "
            "It is one of the biggest trading days of the year.",
            ", ".join(str(y) for y in missing),
        )
    return events


def build_public_holidays(
    dates: pd.DataFrame, frame: pd.DataFrame, config: Config | None = None
) -> pd.DataFrame:
    holidays = dates[dates["_type"].str.contains("PUBLIC_HOLIDAY", na=False)]
    lookup = {
        pd.Timestamp(row["_date"]).normalize(): (row["_name"] or "Public holiday")
        for _, row in holidays.iterrows()
    }

    if config is not None and not frame.empty:
        years = sorted({int(y) for y in frame["date"].dt.year.unique()})
        manual = load_manual_events(config, years)
        added = [d for d in manual if d not in lookup]
        # The feed wins where it has the date; manual entries only fill gaps.
        for day in added:
            lookup[day] = manual[day]
        in_range = [d for d in added if frame["date"].min() <= d <= frame["date"].max()]
        if in_range:
            LOGGER.info(
                "Added %d holiday(s) missing from DataVic: %s",
                len(in_range),
                ", ".join(f"{d.date()} {manual[d]}" for d in sorted(in_range)),
            )

    result = frame.copy()
    result["public_holiday"] = result["date"].isin(lookup).astype("int8")
    result["public_holiday_name"] = result["date"].map(lookup).fillna("")
    result["public_holiday_eve"] = (
        (result["date"] + pd.Timedelta(days=1)).isin(lookup).astype("int8")
    )
    result["day_after_public_holiday"] = (
        (result["date"] - pd.Timedelta(days=1)).isin(lookup).astype("int8")
    )
    return result


def _school_term_intervals(
    dates: pd.DataFrame,
) -> list[tuple[pd.Timestamp, pd.Timestamp]]:
    """Pair 'Term N YYYY - Start date' rows with their matching end rows.

    The dataset publishes term boundaries out to 2030 but only publishes
    explicit SCHOOL_HOLIDAY rows to 2025, so holidays are derived as the
    gaps between terms instead.
    """
    terms = dates[dates["_type"].str.contains("SCHOOL_TERM", na=False)]
    starts: dict[tuple[int, int], pd.Timestamp] = {}
    ends: dict[tuple[int, int], pd.Timestamp] = {}

    for _, row in terms.iterrows():
        name = str(row["_name"])
        match = re.search(r"term\s*(\d)\s*(\d{4})", name, re.IGNORECASE)
        if not match:
            continue
        key = (int(match.group(2)), int(match.group(1)))
        day = pd.Timestamp(row["_date"]).normalize()
        if re.search(r"end", name, re.IGNORECASE):
            ends[key] = day
        elif re.search(r"start", name, re.IGNORECASE):
            starts[key] = day

    intervals = []
    for key, start in sorted(starts.items()):
        end = ends.get(key)
        if end is not None and end >= start:
            intervals.append((start, end))
    return intervals


def build_school_dates(dates: pd.DataFrame, frame: pd.DataFrame) -> pd.DataFrame:
    intervals = _school_term_intervals(dates)
    result = frame.copy()

    if not intervals:
        LOGGER.warning("No school term intervals parsed from DataVic")
        result["school_term"] = pd.NA
        result["school_holiday"] = pd.NA
        return result

    in_term = pd.Series(False, index=result.index)
    for start, end in intervals:
        in_term |= result["date"].between(start, end)

    # Only mark holidays inside the span the dataset actually covers,
    # so dates outside coverage stay NA rather than looking like holidays.
    covered = result["date"].between(
        min(start for start, _ in intervals), max(end for _, end in intervals)
    )
    result["school_term"] = in_term.astype("int8")
    result["school_holiday"] = (covered & ~in_term).astype("int8")
    result.loc[~covered, SCHOOL_COLUMNS] = pd.NA
    return result


# ---------------------------------------------------------------------------
# University calendars
# ---------------------------------------------------------------------------


_WARNED: set[str] = set()


def _warn_once(message: str, *args: Any) -> None:
    """Avoid repeating the same calendar warning on every pipeline stage."""
    rendered = message % args if args else message
    if rendered not in _WARNED:
        _WARNED.add(rendered)
        LOGGER.warning(rendered)


def _semesters_for_year(calendar: dict[str, Any], key: str, year: int) -> list[dict]:
    years = calendar["universities"][key].get("years", {}) or {}
    entry = years.get(year) or years.get(str(year))
    if entry:
        if not entry.get("verified", False):
            _warn_once(
                "University dates for %s %s are unverified — "
                "check the official calendar and set verified: true",
                key,
                year,
            )
        return entry.get("semesters", [])

    _warn_once("No %s calendar for %s; using approximate fallback", key, year)
    semesters = []
    for template in calendar.get("fallback", {}).get("semesters", []):
        assessment = template.get("assessment_md")
        semesters.append(
            {
                "name": template.get("name", "fallback"),
                "teaching_start": f"{year}-{template['teaching_start_md']}",
                "teaching_end": f"{year}-{template['teaching_end_md']}",
                "breaks": [],
                "assessment": (
                    [f"{year}-{assessment[0]}", f"{year}-{assessment[1]}"]
                    if assessment
                    else None
                ),
            }
        )
    return semesters


def build_university_dates(config: Config, frame: pd.DataFrame) -> pd.DataFrame:
    result = frame.copy()
    path = config.resolve("paths.university_calendar")
    if not path.exists():
        LOGGER.warning("University calendar not found at %s", path)
        for column in UNIVERSITY_COLUMNS:
            result[column] = pd.NA
        return result

    with path.open(encoding="utf-8") as handle:
        calendar = yaml.safe_load(handle) or {}

    universities = calendar.get("universities", {})
    years = sorted({int(value) for value in result["date"].dt.year.unique()})

    teaching_count = pd.Series(0.0, index=result.index)
    assessment = pd.Series(False, index=result.index)
    on_break = pd.Series(False, index=result.index)
    total_weight = sum(
        float(entry.get("weight", 1.0)) for entry in universities.values()
    ) or 1.0

    for key, entry in universities.items():
        weight = float(entry.get("weight", 1.0))
        for year in years:
            for semester in _semesters_for_year(calendar, key, year):
                start = pd.Timestamp(semester["teaching_start"])
                end = pd.Timestamp(semester["teaching_end"])
                within = result["date"].between(start, end)

                breaks = pd.Series(False, index=result.index)
                for window in semester.get("breaks") or []:
                    breaks |= result["date"].between(
                        pd.Timestamp(window[0]), pd.Timestamp(window[1])
                    )

                teaching_count += (within & ~breaks).astype(float) * weight
                on_break |= within & breaks

                window = semester.get("assessment")
                if window:
                    assessment |= result["date"].between(
                        pd.Timestamp(window[0]), pd.Timestamp(window[1])
                    )

    # Fraction of nearby campuses actively teaching — a smoother, more
    # informative signal than a single on/off flag.
    result["uni_teaching_weeks"] = (teaching_count / total_weight).round(3)
    result["uni_in_session"] = (teaching_count > 0).astype("int8")
    result["uni_assessment"] = assessment.astype("int8")
    result["uni_break"] = on_break.astype("int8")
    return result


# ---------------------------------------------------------------------------
# Weather
# ---------------------------------------------------------------------------


def _month_chunks(
    start: pd.Timestamp, end: pd.Timestamp
) -> list[tuple[pd.Timestamp, pd.Timestamp]]:
    chunks = []
    cursor = start.normalize()
    while cursor <= end:
        month_end = (cursor + pd.offsets.MonthEnd(0)).normalize()
        chunks.append((cursor, min(month_end, end)))
        cursor = month_end + pd.Timedelta(days=1)
    return chunks


def fetch_weather_hourly(
    config: Config,
    start: pd.Timestamp,
    end: pd.Timestamp,
    *,
    force_refresh: bool,
) -> pd.DataFrame:
    latitude = config.require("venue.latitude")
    longitude = config.require("venue.longitude")
    timezone = config.require("venue.timezone")

    today = pd.Timestamp(date.today())
    pieces: list[pd.DataFrame] = []

    # Observations are always fetched through yesterday regardless of the
    # requested end date, and chunked by calendar month. Over-fetching is
    # free once cached, and it keeps cache keys stable so a narrower request
    # is a cache hit instead of a full re-download of every month of history.
    historical_end = today - pd.Timedelta(days=1)
    if start <= historical_end:
        for chunk_start, chunk_end in _month_chunks(start, historical_end):
            complete = chunk_end < today.to_period("M").start_time
            cache_key = (
                f"weather_archive_{chunk_start:%Y-%m}"
                if complete
                else f"weather_archive_{chunk_start:%Y-%m}_to_{chunk_end.date()}"
            )
            response = cached_get(
                OPEN_METEO_ARCHIVE,
                cache_dir=config.cache_dir,
                cache_key=cache_key,
                params={
                    "latitude": latitude,
                    "longitude": longitude,
                    "start_date": chunk_start.date().isoformat(),
                    "end_date": chunk_end.date().isoformat(),
                    "hourly": ",".join(WEATHER_FIELDS),
                    "timezone": timezone,
                },
                force_refresh=force_refresh,
            )
            historical = pd.DataFrame(response.json().get("hourly", {}))
            if not historical.empty:
                historical["weather_source"] = "observed"
                historical["precipitation_probability"] = pd.NA
                pieces.append(historical)

    forecast_start = max(start, today)
    forecast_end = min(end, today + pd.Timedelta(days=15))
    if forecast_start <= forecast_end:
        response = cached_get(
            OPEN_METEO_FORECAST,
            cache_dir=config.cache_dir,
            cache_key=f"weather_forecast_{today.date()}",
            params={
                "latitude": latitude,
                "longitude": longitude,
                "forecast_days": 16,
                "hourly": ",".join(WEATHER_FIELDS + ["precipitation_probability"]),
                "timezone": timezone,
            },
            force_refresh=force_refresh,
        )
        forecast = pd.DataFrame(response.json().get("hourly", {}))
        if not forecast.empty:
            forecast["time"] = pd.to_datetime(forecast["time"], errors="coerce")
            mask = forecast["time"].dt.normalize().between(forecast_start, forecast_end)
            forecast = forecast.loc[mask].copy()
            forecast["weather_source"] = "forecast"
            pieces.append(forecast)

    if not pieces:
        return pd.DataFrame()

    hourly = pd.concat(pieces, ignore_index=True)
    hourly["time"] = pd.to_datetime(hourly["time"], errors="coerce")
    hourly = hourly.dropna(subset=["time"])
    hourly["date"] = hourly["time"].dt.normalize()
    hourly["hour"] = hourly["time"].dt.hour
    # Observed rows win where archive and forecast windows overlap.
    hourly = hourly.drop_duplicates(subset=["time"], keep="first")
    # Trim the over-fetched observations back to what the caller asked for.
    return hourly[hourly["date"].between(start, end)]


def _is_thunderstorm(code: Any) -> int:
    try:
        return int(int(float(code)) in {95, 96, 99})
    except (TypeError, ValueError):
        return 0


def aggregate_service_weather(config: Config, hourly: pd.DataFrame) -> pd.DataFrame:
    """Collapse hourly weather to one row per day, over trading hours only.

    Rain at 6am is irrelevant to a pub. Rain at 7pm on a Friday is not.

    Trading hours are venue-specific: Fri-Sun are weekend-style early-open
    days (``weekend_open``, 12:00), while Mon-Thu open later (``weekday_open``,
    15:00); every day closes at ``closing`` (23:00).
    """
    if hourly.empty:
        return pd.DataFrame(columns=["date", *WEATHER_COLUMNS])

    weekday_open = int(config.get("venue.opening_hour.weekday", 15))
    weekend_open = int(config.get("venue.opening_hour.weekend", 12))
    closing = int(config.get("venue.closing_hour", 23))

    rows: list[dict[str, Any]] = []
    for day, group in hourly.groupby("date"):
        # dayofweek 4 == Friday, so Fri-Sun (>= 4) get the weekend early open.
        opening = weekend_open if day.dayofweek >= 4 else weekday_open
        service = group[(group["hour"] >= opening) & (group["hour"] <= closing)]
        if service.empty:
            continue

        temperature = pd.to_numeric(service["temperature_2m"], errors="coerce")
        rain = pd.to_numeric(service["precipitation"], errors="coerce")
        cloud = pd.to_numeric(service["cloud_cover"], errors="coerce")
        gusts = pd.to_numeric(service["wind_gusts_10m"], errors="coerce")

        # precipitation_probability is forecast-only; observed archive rows
        # carry it as all-NA and some callers omit it entirely. A missing
        # column must degrade to NA, not crash `pd.to_numeric(None)`.
        if "precipitation_probability" in service.columns:
            rain_probability = pd.to_numeric(
                service["precipitation_probability"], errors="coerce"
            )
            rain_probability_max = (
                rain_probability.max() if rain_probability.notna().any() else pd.NA
            )
        else:
            rain_probability_max = pd.NA

        rows.append(
            {
                "date": day,
                "service_temp_mean_c": temperature.mean(),
                "service_temp_max_c": temperature.max(),
                "service_rain_mm": rain.sum(min_count=1),
                "service_rain_probability_max_pct": rain_probability_max,
                "service_sunny_hours": (
                    int((cloud <= 30).sum()) if cloud.notna().any() else pd.NA
                ),
                "service_storm": int(service["weather_code"].map(_is_thunderstorm).max()),
                "service_wind_gust_max_kmh": gusts.max(),
                "weather_source": service["weather_source"].iloc[0],
            }
        )

    return pd.DataFrame(rows)


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------


def build_external_features(
    config: Config,
    start: pd.Timestamp,
    end: pd.Timestamp,
    *,
    force_refresh: bool = False,
) -> pd.DataFrame:
    """One row per date between start and end, with all external features."""
    frame = pd.DataFrame({"date": pd.date_range(start, end, freq="D")})

    try:
        dates = fetch_datavic_dates(config, force_refresh=force_refresh)
        frame = build_public_holidays(dates, frame, config)
        if config.get("features.include_school", True):
            frame = build_school_dates(dates, frame)
    except Exception as exc:  # noqa: BLE001 - source is optional, schema is not
        LOGGER.warning("DataVic dates unavailable: %s", exc)
        for column in SCHOOL_COLUMNS:
            frame[column] = pd.NA
        # Manual events don't need the feed, so still apply them rather than
        # blanking the holiday columns entirely. Object dtypes keep the empty
        # columns string-accessible so `_type.str.contains` does not fail.
        empty = pd.DataFrame(columns=["_type", "_date", "_name"], dtype="object")
        frame = build_public_holidays(empty, frame, config)

    if config.get("features.include_university", True):
        try:
            frame = build_university_dates(config, frame)
        except Exception as exc:  # noqa: BLE001
            LOGGER.warning("University dates unavailable: %s", exc)
            for column in UNIVERSITY_COLUMNS:
                frame[column] = pd.NA

    if config.get("features.include_weather", True):
        try:
            hourly = fetch_weather_hourly(config, start, end, force_refresh=force_refresh)
            weather = aggregate_service_weather(config, hourly)
            frame = frame.merge(weather, on="date", how="left")
        except Exception as exc:  # noqa: BLE001
            LOGGER.warning("Weather unavailable: %s", exc)
            for column in WEATHER_COLUMNS:
                frame[column] = pd.NA

    for column in (
        PUBLIC_HOLIDAY_COLUMNS
        + SCHOOL_COLUMNS
        + UNIVERSITY_COLUMNS
        + WEATHER_COLUMNS
    ):
        if column not in frame.columns:
            frame[column] = pd.NA

    return frame.sort_values("date").reset_index(drop=True)
