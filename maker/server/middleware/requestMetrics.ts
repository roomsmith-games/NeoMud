import type { Request, Response, NextFunction } from 'express'
import { logger } from '../lib/logger.js'

/**
 * Emits a `metric:api_request_ms` event on every response — a histogram
 * of request durations labeled by method, matched route, and status.
 * Skips the root health check to keep label cardinality clean.
 *
 * Counterpart to the platform's requestLogger middleware; kept as a
 * dedicated middleware here so we don't also duplicate the platform's
 * console logging (maker request logs go through the logger directly
 * in the handlers that care about them).
 */
export function requestMetrics(req: Request, res: Response, next: NextFunction): void {
  const start = Date.now()
  res.on('finish', () => {
    if (req.path === '/' || req.path === '/health') return
    const duration = Date.now() - start
    logger.metric('api_request_ms', duration, {
      method: req.method,
      route: (req.route as { path?: string } | undefined)?.path || req.path,
      status: res.statusCode,
    })
  })
  next()
}
