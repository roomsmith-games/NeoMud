/**
 * Structured JSON logger with optional Better Stack HTTP ingestion.
 *
 * Always writes a JSON line to stdout (picked up by `docker logs`). If
 * BETTER_STACK_TOKEN and BETTER_STACK_HOST are both set, also ships the
 * same payload to Better Stack's HTTP source via fire-and-forget POST.
 *
 * Fire-and-forget on purpose: a log call must never block a request.
 * Ingestion errors land in stdout (never re-ingested, to avoid loops).
 *
 * Env vars arrive via the staging compose file's env_file: .env.
 * deploy.yml upserts them from GitHub Actions secrets on every deploy.
 */

type LogLevel = 'debug' | 'info' | 'warn' | 'error'

// Abort Better Stack requests after 5s so a hung ingest endpoint doesn't
// silently pile up open sockets. 5s is generous for a single small POST
// against Better Stack's CDN-fronted endpoint.
const INGEST_TIMEOUT_MS = 5000

// Read env lazily so tests can toggle ingestion on/off and so config
// changes at runtime (e.g. a rotating secret) are picked up without a
// process restart.
function getIngestionConfig(): { url: string; token: string } | null {
  const token = process.env.BETTER_STACK_TOKEN
  const host = process.env.BETTER_STACK_HOST
  if (!token || !host) return null
  return { url: `https://${host}`, token }
}

interface LogEventPayload {
  [key: string]: unknown
}

function emit(level: LogLevel, message: string, extras: LogEventPayload): void {
  const ts = new Date().toISOString()
  const line = JSON.stringify({ ts, level, message, ...extras })
  // Stdout first — the contract is "stdout never lies". docker logs
  // remains the authoritative view if Better Stack drops events.
  if (level === 'error' || level === 'warn') {
    // eslint-disable-next-line no-console
    console.error(line)
  } else {
    // eslint-disable-next-line no-console
    console.log(line)
  }
  const ingest = getIngestionConfig()
  if (ingest) {
    shipToBetterStack(ingest.url, ingest.token, level, message, extras, ts)
  }
}

function shipToBetterStack(
  url: string,
  token: string,
  level: LogLevel,
  message: string,
  extras: LogEventPayload,
  ts: string,
): void {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), INGEST_TIMEOUT_MS)
  fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      dt: ts,
      level,
      message,
      ...extras,
    }),
    signal: controller.signal,
  })
    .then((res) => {
      if (!res.ok) {
        // Don't re-enter the logger — stdout only to avoid recursion.
        // eslint-disable-next-line no-console
        console.error(
          `[logger] Better Stack responded ${res.status} ${res.statusText}`,
        )
      }
    })
    .catch((err: unknown) => {
      // eslint-disable-next-line no-console
      console.error(
        '[logger] Better Stack ingestion failed:',
        err instanceof Error ? err.message : String(err),
      )
    })
    .finally(() => clearTimeout(timer))
}

export const logger = {
  debug(message: string, extras: LogEventPayload = {}) {
    emit('debug', message, extras)
  },
  info(message: string, extras: LogEventPayload = {}) {
    emit('info', message, extras)
  },
  warn(message: string, extras: LogEventPayload = {}) {
    emit('warn', message, extras)
  },
  error(message: string, extras: LogEventPayload = {}) {
    emit('error', message, extras)
  },

  /**
   * Emit a metric data point as a structured log event. Better Stack
   * treats logs+metrics as the same stream; queries filter on `metric`
   * and aggregate on `value` + label fields.
   *
   * For counters: pass value=1 and label dimensions in `labels`.
   * For gauges: pass the current value with labels.
   * For histograms: emit one event per observation with value=observed.
   *
   * Example:
   *   logger.metric('publish_attempts', 1, { outcome: 'allowed', plan: 'FREE' })
   *   logger.metric('api_request_ms', 42, { route: '/worlds/:id/publish', status: 200 })
   */
  metric(name: string, value: number, labels: LogEventPayload = {}) {
    emit('info', `metric:${name}`, { metric: name, value, ...labels })
  },

  /** Exposed for tests — do not use in production code. */
  _isIngestionEnabled(): boolean {
    return getIngestionConfig() !== null
  },
}
