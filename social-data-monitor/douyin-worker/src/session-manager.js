import { randomUUID } from 'node:crypto'

const TERMINAL_STATUSES = new Set(['SUCCESS', 'EXPIRED', 'FAILED'])

export class WorkerError extends Error {
  constructor(code, message, statusCode = 400) {
    super(message)
    this.name = 'WorkerError'
    this.code = code
    this.statusCode = statusCode
  }
}

export class SessionManager {
  constructor({ driver, ttlSeconds = 180, now = Date.now }) {
    if (!driver) {
      throw new TypeError('driver is required')
    }
    this.driver = driver
    this.ttlSeconds = ttlSeconds
    this.now = now
    this.sessions = new Map()
  }

  async create({ expiresInSeconds } = {}) {
    const requestedTtl = Number(expiresInSeconds)
    const effectiveTtl = Number.isFinite(requestedTtl) && requestedTtl > 0
      ? Math.min(requestedTtl, this.ttlSeconds)
      : this.ttlSeconds
    const workerSessionId = randomUUID()
    const createdAtMs = this.now()
    const expiresAtMs = createdAtMs + effectiveTtl * 1_000
    const session = {
      handle: undefined,
      initialization: undefined,
      createdAtMs,
      expiresAtMs,
      status: { status: 'STARTING', message: 'Browser session is starting' },
      bundle: undefined,
      closed: false,
      closePromise: undefined
    }
    this.sessions.set(workerSessionId, session)
    session.initialization = this.#initialize(session, {
      workerSessionId,
      createdAt: new Date(createdAtMs).toISOString(),
      expiresAt: new Date(expiresAtMs).toISOString()
    })

    return {
      workerSessionId,
      status: 'STARTING',
      createdAt: new Date(createdAtMs).toISOString(),
      expiresAt: new Date(expiresAtMs).toISOString()
    }
  }

  async qr(workerSessionId) {
    const session = await this.#activeSession(workerSessionId)
    if (!session.handle) {
      throw new WorkerError('SESSION_NOT_READY', 'The login session is still initializing', 409)
    }
    return session.handle.qr()
  }

  async status(workerSessionId) {
    const session = this.#requireSession(workerSessionId)
    if (await this.#expireIfNecessary(session)) {
      return session.status
    }
    if (TERMINAL_STATUSES.has(session.status.status)) {
      return session.status
    }
    if (!session.handle) {
      return session.status
    }

    const observed = await session.handle.status()
    if (observed?.status === 'SUCCESS') {
      if (!observed.bundle) {
        session.status = { status: 'FAILED', message: 'Login completed without a credential bundle' }
        await this.#close(session)
        return session.status
      }

      const validation = await this.driver.validateBundle(observed.bundle)
      if (!validation?.valid) {
        session.status = {
          status: 'FAILED',
          message: validation?.message ?? 'The captured login state failed validation'
        }
        await this.#close(session)
        return session.status
      }

      session.bundle = validation.bundle ?? observed.bundle
      session.status = {
        ...observed,
        status: 'SUCCESS',
        bundle: undefined,
        validation: validation.details
      }
      await this.#close(session)
      return session.status
    }

    session.status = observed ?? { status: 'STARTING', message: 'Waiting for browser state' }
    if (TERMINAL_STATUSES.has(session.status.status)) {
      await this.#close(session)
    }
    return session.status
  }

  async consume(workerSessionId) {
    const session = this.#requireSession(workerSessionId)
    await this.#expireIfNecessary(session)
    if (session.status.status !== 'SUCCESS' || !session.bundle) {
      throw new WorkerError('SESSION_NOT_READY', 'The login session has not produced a validated credential', 409)
    }
    return { bundle: session.bundle }
  }

  async validate(bundle) {
    if (!bundle || typeof bundle !== 'object') {
      throw new WorkerError('INVALID_BUNDLE', 'A credential bundle is required', 400)
    }
    return this.driver.validateBundle(bundle)
  }

  async remove(workerSessionId) {
    const session = this.#requireSession(workerSessionId)
    await this.#close(session)
    this.sessions.delete(workerSessionId)
  }

  async cleanupExpired() {
    for (const [workerSessionId, session] of this.sessions) {
      if (this.now() > session.expiresAtMs) {
        await this.#expireIfNecessary(session)
        this.sessions.delete(workerSessionId)
      }
    }
  }

  async closeAll() {
    const sessions = [...this.sessions.values()]
    this.sessions.clear()
    await Promise.allSettled(sessions.map(session => this.#close(session)))
  }

  #requireSession(workerSessionId) {
    const session = this.sessions.get(workerSessionId)
    if (!session) {
      throw new WorkerError('SESSION_NOT_FOUND', 'The login session does not exist', 404)
    }
    return session
  }

  async #activeSession(workerSessionId) {
    const session = this.#requireSession(workerSessionId)
    if (await this.#expireIfNecessary(session)) {
      throw new WorkerError('SESSION_EXPIRED', 'The login session has expired', 410)
    }
    return session
  }

  async #expireIfNecessary(session) {
    if (this.now() <= session.expiresAtMs || session.status.status === 'SUCCESS') {
      return false
    }
    session.status = { status: 'EXPIRED', message: 'The login session has expired' }
    await this.#close(session)
    return true
  }

  async #initialize(session, options) {
    try {
      const handle = await this.driver.createSession(options)
      if (session.closed) {
        await handle.close()
        return
      }
      session.handle = handle
    } catch (error) {
      if (!session.closed) {
        session.status = {
          status: 'FAILED',
          message: error instanceof Error ? error.message : String(error)
        }
      }
    }
  }

  async #close(session) {
    if (session.closePromise) {
      return session.closePromise
    }
    session.closed = true
    const handle = session.handle
    session.closePromise = handle
      ? Promise.resolve().then(() => handle.close())
      : Promise.resolve()
    return session.closePromise
  }
}
