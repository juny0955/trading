import { Gauge } from "k6/metrics";
import exec from "k6/execution";
import http from "k6/http";
import encoding from "k6/encoding";

// handleSummary에서 data.metrics[METRIC_NAME].values.value 로 시나리오 시작 시각을 읽는다.
// 모든 VU가 동일한 exec.scenario.startTime을 기록하므로 Gauge 최종값 = 정확한 시작 시각.
const METRIC_NAME = "k6_scenario_start_epoch_ms";
const _startGauge = new Gauge(METRIC_NAME, false);
let _startRecorded = false;

export function buildPhaseReporter({ warmupMs, measureMs, cooldownMs }) {
  const scenarioName = __ENV.SCENARIO_NAME || "default";
  const grafanaUrl = __ENV.GRAFANA_URL || "http://localhost:3000";
  const grafanaUser = __ENV.GRAFANA_USER || "admin";
  const grafanaPass = __ENV.GRAFANA_PASSWORD || "admin";

  function recordStart() {
    if (!_startRecorded) {
      _startRecorded = true;
      _startGauge.add(exec.scenario.startTime);
    }
  }

  function buildHandleSummary() {
    return function handleSummary(data) {
      const startMs = data.metrics[METRIC_NAME]?.values?.value;
      if (!startMs) {
        console.warn(
          "[phase-reporter] 시나리오 시작 시각 미기록 — " +
            "default function에서 reporter.recordStart() 호출 여부 확인"
        );
        return {};
      }

      const measureStartMs = startMs + warmupMs;
      const measureEndMs = measureStartMs + measureMs;
      const cooldownEndMs = measureEndMs + cooldownMs;

      const phases = {
        warmup: {
          start: iso(startMs),
          end: iso(measureStartMs),
          durationMs: warmupMs,
        },
        measure: {
          start: iso(measureStartMs),
          end: iso(measureEndMs),
          durationMs: measureMs,
        },
        cooldown: {
          start: iso(measureEndMs),
          end: iso(cooldownEndMs),
          durationMs: cooldownMs,
        },
      };

      const ts = new Date(startMs).toISOString().replace(/[-:]/g, "").slice(0, 15) + "Z";
      const filename = `phase-report-${scenarioName}-${ts}.json`;
      const report = JSON.stringify({ scenario: scenarioName, phases }, null, 2);

      sendAnnotation(grafanaUrl, grafanaUser, grafanaPass, {
        text: `k6 measure: ${scenarioName}`,
        tags: ["k6", "phase:measure", scenarioName],
        time: measureStartMs,
        timeEnd: measureEndMs,
        isRegion: true,
      });

      console.log(
        `[phase-reporter] measure 구간: ${iso(measureStartMs)} ~ ${iso(measureEndMs)}`
      );

      return { [filename]: report };
    };
  }

  return { recordStart, buildHandleSummary };
}

function iso(ms) {
  return new Date(ms).toISOString();
}

function sendAnnotation(grafanaUrl, user, pass, annotation) {
  const auth = encoding.b64encode(`${user}:${pass}`);
  const res = http.post(
    `${grafanaUrl}/api/annotations`,
    JSON.stringify(annotation),
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Basic ${auth}`,
      },
    }
  );
  if (res.status !== 200) {
    console.warn(
      `[phase-reporter] Grafana annotation 전송 실패: status=${res.status} body=${res.body}`
    );
  }
}
