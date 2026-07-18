import { pathToFileURL } from 'node:url'
import { loadWorkerConfig } from './config.js'
import { createWorkerHttpServer } from './http-server.js'
import { PlaywrightDouyinDriver } from './playwright-driver.js'
import { SessionManager } from './session-manager.js'

function listen(server, port, host) {
  return new Promise((resolve, reject) => {
    const onError = error => {
      server.off('listening', onListening)
      reject(error)
    }
    const onListening = () => {
      server.off('error', onError)
      resolve()
    }
    server.once('error', onError)
    server.once('listening', onListening)
    server.listen(port, host)
  })
}

function closeServer(server) {
  return new Promise((resolve, reject) => {
    if (!server.listening) {
      resolve()
      return
    }
    server.close(error => error ? reject(error) : resolve())
  })
}

export async function startWorker({ env = process.env, driver: suppliedDriver } = {}) {
  const config = loadWorkerConfig(env)
  const driver = suppliedDriver ?? new PlaywrightDouyinDriver({ config })
  const manager = new SessionManager({ driver, ttlSeconds: config.sessionTtlSeconds })
  const server = createWorkerHttpServer({ manager, token: config.token })
  const cleanupTimer = setInterval(() => {
    manager.cleanupExpired().catch(error => {
      console.error('Douyin Worker session cleanup failed:', error.message)
    })
  }, config.cleanupIntervalMs)
  cleanupTimer.unref()

  try {
    await listen(server, config.port, config.host)
  } catch (error) {
    clearInterval(cleanupTimer)
    await manager.closeAll()
    await driver.close?.()
    throw error
  }

  let stopping
  const stop = () => {
    stopping ??= (async () => {
      clearInterval(cleanupTimer)
      await closeServer(server)
      await manager.closeAll()
      await driver.close?.()
    })()
    return stopping
  }

  return { config, server, manager, driver, stop }
}

async function main() {
  const worker = await startWorker()
  console.log(`Douyin Worker listening on http://${worker.config.host}:${worker.config.port}`)

  const shutdown = signal => {
    console.log(`Douyin Worker received ${signal}; shutting down`)
    worker.stop()
      .then(() => process.exit(0))
      .catch(error => {
        console.error('Douyin Worker shutdown failed:', error.message)
        process.exit(1)
      })
  }
  process.once('SIGINT', () => shutdown('SIGINT'))
  process.once('SIGTERM', () => shutdown('SIGTERM'))
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => {
    console.error('Douyin Worker failed to start:', error.message)
    process.exitCode = 1
  })
}
