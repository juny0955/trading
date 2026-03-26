// k6 duration 형식: "30s", "1m", "1h30m", "300ms", "1.5h" 등
const DURATION_RE = /^(\d+(\.\d+)?(ms|s|m|h))+$/;

// duration 문자열을 밀리초로 변환한다. "1m30s" → 90000, "300ms" → 300, "0s" → 0
export function parseDurationToMs(duration) {
  const units = { ms: 1, s: 1000, m: 60000, h: 3600000 };
  let total = 0;
  const re = /(\d+(?:\.\d+)?)(ms|s|m|h)/g;
  let match;
  while ((match = re.exec(duration)) !== null) {
    total += parseFloat(match[1]) * units[match[2]];
  }
  return total;
}

function parsePositiveInt(rawValue, fallbackValue) {
  const value = Number(rawValue);
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : fallbackValue;
}

function parseDuration(rawValue, fallbackValue) {
  if (!rawValue) return fallbackValue;
  if (!DURATION_RE.test(rawValue)) {
    throw new Error(
      `Invalid k6 duration: "${rawValue}". Expected format like "30s", "1m", "1h30m", "300ms".`
    );
  }
  return rawValue;
}

export function buildPhasedOptions({
  env = __ENV,
  defaultVus,
  defaultMeasureDuration,
  thresholds,
}) {
  const vus = parsePositiveInt(env.VUS, defaultVus);
  const warmupDuration = parseDuration(env.WARMUP_DURATION, "1m");
  const measureDuration = parseDuration(
    env.MEASURE_DURATION || env.DURATION,
    defaultMeasureDuration
  );
  const cooldownDuration = parseDuration(env.COOLDOWN_DURATION, "1m");
  const gracefulRampDown = parseDuration(env.GRACEFUL_RAMP_DOWN, "5s");

  const result = {
    scenarios: {
      default: {
        executor: "ramping-vus",
        startVUs: 0,
        stages: [
          { duration: warmupDuration, target: vus },
          { duration: measureDuration, target: vus },
          { duration: cooldownDuration, target: 0 },
        ],
        gracefulRampDown,
      },
    },
    thresholds,
  };

  // k6이 무시하는 필드 — buildPhaseReporter가 재파싱 없이 재사용
  result._phaseDurations = {
    warmupMs: parseDurationToMs(warmupDuration),
    measureMs: parseDurationToMs(measureDuration),
    cooldownMs: parseDurationToMs(cooldownDuration),
  };

  return result;
}
