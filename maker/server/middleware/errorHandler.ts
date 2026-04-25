import type { Request, Response, NextFunction } from 'express'

/**
 * Global error handler. Returns JSON to API callers and logs details
 * server-side. NEVER leaks stack traces or internal state to the client.
 *
 * Mirrors NeoMud-Platform/src/middleware/errorHandler.ts (without the
 * Zod special-case since the maker doesn't currently use Zod). Without
 * this middleware, any route that calls `next(err)` falls through to
 * Express's default HTML error page, which the maker UI can't surface
 * usefully — playtest 500s rendered as `<pre>Internal Server Error</pre>`
 * forced the client to show a generic "Something went wrong" toast with
 * no actionable detail.
 */
export function errorHandler(
  err: Error & { status?: number },
  req: Request,
  res: Response,
  _next: NextFunction
): void {
  const status = err.status || 500

  // Log full detail server-side so docker logs / Better Stack can capture
  // the stack and the route that triggered it.
  console.error('[error]', {
    method: req.method,
    path: req.path,
    status,
    message: err.message,
    stack: err.stack,
  })

  // Caller-facing 4xx errors carry intentional messages; pass them through.
  // 5xx errors are generic to avoid leaking internals.
  res.status(status).json({
    error: status >= 400 && status < 500 ? err.message : 'An unexpected error occurred',
  })
}
