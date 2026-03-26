import test from "node:test";
import assert from "node:assert/strict";

import { buildPhasedOptions } from "../lib/phases.js";

test("buildPhasedOptions uses explicit warmup, measure, cooldown, and gracefulRampDown durations", () => {
  const options = buildPhasedOptions({
    env: {
      VUS: "25",
      WARMUP_DURATION: "20s",
      MEASURE_DURATION: "2m",
      COOLDOWN_DURATION: "15s",
      GRACEFUL_RAMP_DOWN: "0s",
    },
    defaultVus: 10,
    defaultMeasureDuration: "1m",
    thresholds: {
      http_req_failed: ["rate<0.05"],
    },
  });

  assert.deepEqual(options, {
    scenarios: {
      default: {
        executor: "ramping-vus",
        startVUs: 0,
        stages: [
          { duration: "20s", target: 25 },
          { duration: "2m", target: 25 },
          { duration: "15s", target: 0 },
        ],
        gracefulRampDown: "0s",
      },
    },
    thresholds: {
      http_req_failed: ["rate<0.05"],
    },
    _phaseDurations: {
      warmupMs: 20000,
      measureMs: 120000,
      cooldownMs: 15000,
    },
  });
});

test("buildPhasedOptions keeps backward compatibility with DURATION and zero-length warmup/cooldown", () => {
  const options = buildPhasedOptions({
    env: {
      VUS: "7",
      DURATION: "45s",
    },
    defaultVus: 20,
    defaultMeasureDuration: "1m",
    thresholds: {
      http_req_duration: ["p(95)<1000"],
    },
  });

  assert.deepEqual(options.scenarios.default.stages, [
    { duration: "0s", target: 7 },
    { duration: "45s", target: 7 },
    { duration: "0s", target: 0 },
  ]);
  assert.equal(options.scenarios.default.executor, "ramping-vus");
  assert.equal(options.scenarios.default.startVUs, 0);
  assert.equal(options.scenarios.default.gracefulRampDown, "5s");
  assert.deepEqual(options.thresholds, {
    http_req_duration: ["p(95)<1000"],
  });
});

test("parsePositiveInt: VUS=0 falls back to defaultVus", () => {
  const options = buildPhasedOptions({
    env: { VUS: "0", DURATION: "30s" },
    defaultVus: 10,
    defaultMeasureDuration: "1m",
    thresholds: {},
  });
  assert.equal(options.scenarios.default.stages[1].target, 10);
});

test("parsePositiveInt: VUS=-5 falls back to defaultVus", () => {
  const options = buildPhasedOptions({
    env: { VUS: "-5", DURATION: "30s" },
    defaultVus: 10,
    defaultMeasureDuration: "1m",
    thresholds: {},
  });
  assert.equal(options.scenarios.default.stages[1].target, 10);
});

test("parsePositiveInt: VUS=abc falls back to defaultVus", () => {
  const options = buildPhasedOptions({
    env: { VUS: "abc", DURATION: "30s" },
    defaultVus: 10,
    defaultMeasureDuration: "1m",
    thresholds: {},
  });
  assert.equal(options.scenarios.default.stages[1].target, 10);
});

test("parsePositiveInt: VUS=2.7 is floored to 2", () => {
  const options = buildPhasedOptions({
    env: { VUS: "2.7", DURATION: "30s" },
    defaultVus: 10,
    defaultMeasureDuration: "1m",
    thresholds: {},
  });
  assert.equal(options.scenarios.default.stages[1].target, 2);
});

test("parseDuration: MEASURE_DURATION not set uses defaultMeasureDuration", () => {
  const options = buildPhasedOptions({
    env: { VUS: "5" },
    defaultVus: 10,
    defaultMeasureDuration: "2m",
    thresholds: {},
  });
  assert.equal(options.scenarios.default.stages[1].duration, "2m");
});

test("parseDuration: invalid duration throws descriptive error", () => {
  assert.throws(
    () =>
      buildPhasedOptions({
        env: { VUS: "5", MEASURE_DURATION: "30" },
        defaultVus: 10,
        defaultMeasureDuration: "1m",
        thresholds: {},
      }),
    /Invalid k6 duration/
  );
});

test("parseDuration: garbage duration string throws descriptive error", () => {
  assert.throws(
    () =>
      buildPhasedOptions({
        env: { VUS: "5", WARMUP_DURATION: "abc" },
        defaultVus: 10,
        defaultMeasureDuration: "1m",
        thresholds: {},
      }),
    /Invalid k6 duration/
  );
});
