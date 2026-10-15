"""Tests for ``external.py`` — the external daily feature builders.

These cover the three behaviours the refactor depends on:

1. Weather is aggregated over *trading hours only* (weekday open 15:00,
   weekend 12:00, close 23:00), so pre-open hours are dropped.
2. A public holiday derives its eve (day before) and day-after automatically.
3. When an external source fails, ``build_external_features`` still returns
   every column (NA for the degraded sources) rather than raising.

None of these tests touch the network: aggregation and derivation are pure
functions, and the outage test swaps the fetchers for stubs that raise.
"""

import pandas as pd
import pytest

import external as external_module


def _hourly_frame(day: str, hours) -> pd.DataFrame:
    """Hourly weather rows for ``day`` spanning the given hours (06:00-23:00).

    Values are chosen so pre-open inclusion is unmistakable:

    * temperature == hour, so the mean reveals the service window;
    * rain falls only at 06:00 (always pre-open);
    * a thunderstorm code sits at 08:00 (pre-open on weekdays);
    * wind gust == hour.
    """
    base = pd.Timestamp(day).normalize()
    rows = []
    for hour in hours:
        rows.append(
            {
                "time": base + pd.Timedelta(hours=hour),
                "date": base,
                "hour": hour,
                "temperature_2m": float(hour),
                "precipitation": 10.0 if hour == 6 else 0.0,
                "cloud_cover": 50.0,
                "wind_gusts_10m": float(hour),
                "weather_code": 95 if hour == 8 else 3,
                "precipitation_probability": float(hour),
                "weather_source": "observed",
            }
        )
    return pd.DataFrame(rows)


def test_weather_aggregates_over_trading_hours_only():
    config = external_module.Config()

    # Monday (dayofweek 0) -> weekday open 15:00, close 23:00.
    weekday = _hourly_frame("2026-08-31", range(6, 24))
    result = external_module.aggregate_service_weather(config, weekday)
    row = result.iloc[0]

    # Mean of hours 15..23 == 19.0 (hours 6..14 dropped). Max is the last hour.
    assert row["service_temp_mean_c"] == 19.0
    assert row["service_temp_max_c"] == 23.0
    # Rain only at 06:00, which is pre-open, so it is excluded.
    assert row["service_rain_mm"] == 0.0
    # Thunderstorm code at 08:00 is pre-open on a weekday, so excluded.
    assert row["service_storm"] == 0
    # Wind gust peak is over service hours only (a 08:00 spike would be 50).
    assert row["service_wind_gust_max_kmh"] == 23.0

    # Sunday (dayofweek 6) -> weekend open 12:00, so hours 12..23 are kept.
    weekend = _hourly_frame("2026-09-06", range(6, 24))
    row = external_module.aggregate_service_weather(config, weekend).iloc[0]
    assert row["service_temp_mean_c"] == 17.5  # mean(12..23)
    assert row["service_temp_max_c"] == 23.0

    # Friday (dayofweek 4) -> weekend-style early open 12:00 (Fri-Sun).
    friday = _hourly_frame("2026-09-04", range(6, 24))
    row = external_module.aggregate_service_weather(config, friday).iloc[0]
    assert row["service_temp_mean_c"] == 17.5  # mean(12..23), not 19.0 (15:00 open)
    assert row["service_temp_max_c"] == 23.0
    # Rain only at 06:00 is pre-open on a Friday too, so still excluded.
    assert row["service_rain_mm"] == 0.0


def test_holiday_eve_and_day_after_are_derived():
    dates = pd.DataFrame(
        [
            {
                "_date": pd.Timestamp("2026-11-03"),
                "_type": "PUBLIC_HOLIDAY",
                "_name": "Melbourne Cup",
            }
        ]
    )
    frame = pd.DataFrame({"date": pd.date_range("2026-11-01", "2026-11-05", freq="D")})

    result = external_module.build_public_holidays(dates, frame)
    by_date = result.set_index("date")

    assert by_date.loc[pd.Timestamp("2026-11-03"), "public_holiday"] == 1
    assert by_date.loc[pd.Timestamp("2026-11-03"), "public_holiday_name"] == "Melbourne Cup"
    # Eve is the day before; day-after is the day after.
    assert by_date.loc[pd.Timestamp("2026-11-02"), "public_holiday_eve"] == 1
    assert by_date.loc[pd.Timestamp("2026-11-04"), "day_after_public_holiday"] == 1
    # Days not adjacent to the holiday stay clear.
    assert by_date.loc[pd.Timestamp("2026-11-01"), "public_holiday_eve"] == 0
    assert by_date.loc[pd.Timestamp("2026-11-05"), "day_after_public_holiday"] == 0


def test_missing_rain_probability_column_degrades_to_na():
    config = external_module.Config()
    hourly = _hourly_frame("2026-08-31", range(6, 24)).drop(
        columns=["precipitation_probability"]
    )

    # A frame without the precipitation_probability column must still
    # aggregate without raising, and the probability feature degrades to NA.
    result = external_module.aggregate_service_weather(config, hourly)
    row = result.iloc[0]

    assert pd.isna(row["service_rain_probability_max_pct"])
    # Everything else aggregates normally.
    assert row["service_temp_mean_c"] == 19.0


def test_source_outage_degrades_to_na(monkeypatch):
    config = external_module.Config()

    def boom(*args, **kwargs):
        raise RuntimeError("source unavailable")

    monkeypatch.setattr(external_module, "fetch_datavic_dates", boom)
    monkeypatch.setattr(external_module, "fetch_weather_hourly", boom)
    monkeypatch.setattr(external_module, "build_university_dates", boom)

    # Must not raise, and must still return the full schema.
    result = external_module.build_external_features(
        config, pd.Timestamp("2026-09-01"), pd.Timestamp("2026-09-07")
    )

    expected = set(
        external_module.PUBLIC_HOLIDAY_COLUMNS
        + external_module.SCHOOL_COLUMNS
        + external_module.UNIVERSITY_COLUMNS
        + external_module.WEATHER_COLUMNS
    )
    assert expected <= set(result.columns)
    assert len(result) == 7

    # Sources that degraded stay NA across the whole window.
    for column in (
        external_module.SCHOOL_COLUMNS
        + external_module.UNIVERSITY_COLUMNS
        + external_module.WEATHER_COLUMNS
    ):
        assert result[column].isna().all()


def test_feature_config_env_map(monkeypatch):
    """The advertised feature-selection overrides are env-reachable through
    ``Config``, not just the venue/weather/school/university toggles."""
    monkeypatch.setenv("FORECAST_INCLUDE_BOOKED_COVERS", "force")
    monkeypatch.setenv("FORECAST_BOOKED_COVERS_MIN_COVERAGE", "0.6")
    monkeypatch.setenv("FORECAST_REVENUE_LAGS", "3,7,14")
    monkeypatch.setenv("FORECAST_REVENUE_ROLLING_WINDOWS", "7,14")
    monkeypatch.setenv("FORECAST_FEATURE_EXCLUDE", "is_friday,service_rain_mm")
    monkeypatch.setenv("FORECAST_HISTORY_START", "2024-06-01")

    config = external_module.Config()

    assert config.get("features.include_booked_covers") == "force"
    assert config.get("features.booked_covers_min_coverage") == 0.6
    assert config.get("features.revenue_lags") == [3, 7, 14]
    assert config.get("features.revenue_rolling_windows") == [7, 14]
    assert config.get("features.exclude") == ["is_friday", "service_rain_mm"]
    assert config.get("data.history_start") == "2024-06-01"


def test_include_booked_covers_env_accepts_bool_and_force(monkeypatch):
    for value in ("true", "1", "yes", "on", "TRUE"):
        monkeypatch.setenv("FORECAST_INCLUDE_BOOKED_COVERS", value)
        assert external_module.Config().get("features.include_booked_covers") is True

    for value in ("false", "0", "no", "off"):
        monkeypatch.setenv("FORECAST_INCLUDE_BOOKED_COVERS", value)
        assert external_module.Config().get("features.include_booked_covers") is False

    monkeypatch.setenv("FORECAST_INCLUDE_BOOKED_COVERS", "force")
    assert external_module.Config().get("features.include_booked_covers") == "force"
