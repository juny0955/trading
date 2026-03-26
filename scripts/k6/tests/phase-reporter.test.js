import test from "node:test";
import assert from "node:assert/strict";

import { parseDurationToMs } from "../lib/phases.js";

// ─── parseDurationToMs ──────────────────────────────────────────────────────

test("parseDurationToMs: 0s → 0", () => {
  assert.equal(parseDurationToMs("0s"), 0);
});

test("parseDurationToMs: 30s → 30000", () => {
  assert.equal(parseDurationToMs("30s"), 30000);
});

test("parseDurationToMs: 1m → 60000", () => {
  assert.equal(parseDurationToMs("1m"), 60000);
});

test("parseDurationToMs: 1h → 3600000", () => {
  assert.equal(parseDurationToMs("1h"), 3600000);
});

test("parseDurationToMs: 300ms → 300", () => {
  assert.equal(parseDurationToMs("300ms"), 300);
});

test("parseDurationToMs: compound 1m30s → 90000", () => {
  assert.equal(parseDurationToMs("1m30s"), 90000);
});

test("parseDurationToMs: compound 1h30m → 5400000", () => {
  assert.equal(parseDurationToMs("1h30m"), 5400000);
});

test("parseDurationToMs: float 1.5m → 90000", () => {
  assert.equal(parseDurationToMs("1.5m"), 90000);
});

// ─── buildPhaseReporter (phase 경계 계산) ────────────────────────────────────
// phase-reporter.js는 k6 전용 모듈(k6/metrics, k6/execution 등)을 import하므로
// Node.js에서 직접 실행할 수 없다. 대신 핵심 계산 로직을 인라인으로 검증한다.

function calcPhases(startMs, warmupMs, measureMs, cooldownMs) {
  const measureStartMs = startMs + warmupMs;
  const measureEndMs = measureStartMs + measureMs;
  const cooldownEndMs = measureEndMs + cooldownMs;
  return {
    warmup: { startMs, endMs: measureStartMs, durationMs: warmupMs },
    measure: { startMs: measureStartMs, endMs: measureEndMs, durationMs: measureMs },
    cooldown: { startMs: measureEndMs, endMs: cooldownEndMs, durationMs: cooldownMs },
  };
}

test("phase 경계: warmup=30s measure=2m cooldown=10s", () => {
  const start = 1000000;
  const phases = calcPhases(
    start,
    parseDurationToMs("30s"),
    parseDurationToMs("2m"),
    parseDurationToMs("10s")
  );

  assert.equal(phases.warmup.startMs, 1000000);
  assert.equal(phases.warmup.endMs, 1000000 + 30000);
  assert.equal(phases.warmup.durationMs, 30000);

  assert.equal(phases.measure.startMs, 1000000 + 30000);
  assert.equal(phases.measure.endMs, 1000000 + 30000 + 120000);
  assert.equal(phases.measure.durationMs, 120000);

  assert.equal(phases.cooldown.startMs, 1000000 + 150000);
  assert.equal(phases.cooldown.endMs, 1000000 + 160000);
  assert.equal(phases.cooldown.durationMs, 10000);
});

test("phase 경계: warmup=0s measure=1m cooldown=0s (기본값)", () => {
  const start = 0;
  const phases = calcPhases(
    start,
    parseDurationToMs("0s"),
    parseDurationToMs("1m"),
    parseDurationToMs("0s")
  );

  assert.equal(phases.warmup.durationMs, 0);
  assert.equal(phases.warmup.startMs, phases.warmup.endMs); // 구간 없음
  assert.equal(phases.measure.startMs, 0);
  assert.equal(phases.measure.endMs, 60000);
  assert.equal(phases.cooldown.durationMs, 0);
});
