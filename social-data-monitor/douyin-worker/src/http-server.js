import { createServer } from 'node:http'
import { timingSafeEqual } from 'node:crypto'
import { WorkerError } from './session-manager.js'

const JSON_CONTENT_TYPE = 'application/json; charset=utf-8'

function sendJson(response, statusCode, data) {
  const bytes = Buffer.from(JSON.stringify(data))
  response.writeHead(statusCode, {
    'content-type': JSON_CONTENT_TYPE,
    'content-length': bytes.length
  })
  response.end(bytes)
}

function sendSuccess(response, statusCode, data) {
  sendJson(response, statusCode, { success: true, data })
}

function tokenMatches(actual, expected) {
  if (!expected) {
    return true
  }
  const actualBytes = Buffer.from(actual ?? '')
  const expectedBytes = Buffer.from(expected)
  return actualBytes.length === expectedBytes.length && timingSafeEqual(actualBytes, expectedBytes)
}

async function readJson(request, maxBodyBytes) {
  const chunks = []
  let size = 0
  for await (const chunk of request) {
    size += chunk.length
    if (size > maxBodyBytes) {
      throw new WorkerError('REQUEST_TOO_LARGE', 'Request body is too large', 413)
    }
    chunks.push(chunk)
  }
  if (chunks.length === 0) {
    return {}
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'))
  } catch {
    throw new WorkerError('INVALID_JSON', 'Request body must be valid JSON', 400)
  }
}

function loginSessionRoute(pathname) {
  const match = pathname.match(/^\/internal\/v1\/login-sessions\/([^/]+)(?:\/(qr|status|consume))?$/u)
  return match ? { workerSessionId: decodeURIComponent(match[1]), action: match[2] } : undefined
}

export function createWorkerHttpServer({ manager, token = '', maxBodyBytes = 64 * 1024 * 1024 }) {
  if (!manager) {
    throw new TypeError('manager is required')
  }

  return createServer(async (request, response) => {
    try {
      const url = new URL(request.url ?? '/', 'http://worker.local')

      if (request.method === 'GET' && url.pathname === '/internal/v1/health') {
        sendSuccess(response, 200, { status: 'UP' })
        return
      }

      if (!tokenMatches(request.headers['x-worker-token'], token)) {
        throw new WorkerError('UNAUTHORIZED', 'Worker token is missing or invalid', 401)
      }

      if (request.method === 'POST' && url.pathname === '/internal/v1/login-sessions') {
        const body = await readJson(request, maxBodyBytes)
        sendSuccess(response, 201, await manager.create(body))
        return
      }

      if (request.method === 'POST' && url.pathname === '/internal/v1/web-sessions/validate') {
        const body = await readJson(request, maxBodyBytes)
        sendSuccess(response, 200, await manager.validate(body.bundle))
        return
      }

      const route = loginSessionRoute(url.pathname)
      if (route && request.method === 'GET' && route.action === 'qr') {
        const image = await manager.qr(route.workerSessionId)
        const bytes = Buffer.isBuffer(image) ? image : image.bytes
        const contentType = Buffer.isBuffer(image) ? 'image/png' : image.contentType
        response.writeHead(200, {
          'content-type': contentType ?? 'image/png',
          'content-length': bytes.length,
          'cache-control': 'no-store'
        })
        response.end(bytes)
        return
      }
      if (route && request.method === 'GET' && route.action === 'status') {
        sendSuccess(response, 200, await manager.status(route.workerSessionId))
        return
      }
      if (route && request.method === 'POST' && route.action === 'consume') {
        sendSuccess(response, 200, await manager.consume(route.workerSessionId))
        return
      }
      if (route && request.method === 'DELETE' && route.action === undefined) {
        await manager.remove(route.workerSessionId)
        response.writeHead(204)
        response.end()
        return
      }

      throw new WorkerError('NOT_FOUND', 'Worker endpoint not found', 404)
    } catch (error) {
      const statusCode = error instanceof WorkerError ? error.statusCode : 500
      const code = error instanceof WorkerError ? error.code : 'INTERNAL_ERROR'
      const message = error instanceof WorkerError ? error.message : 'Unexpected worker error'
      sendJson(response, statusCode, { success: false, error: { code, message } })
    }
  })
}
