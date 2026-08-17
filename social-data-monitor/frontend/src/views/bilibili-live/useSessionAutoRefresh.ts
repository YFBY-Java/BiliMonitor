import { readonly, ref, type Ref } from 'vue'

interface SessionAutoRefreshOptions {
  refresh: () => Promise<void>
  intervalSeconds: number
  onError?: (error: unknown) => void | Promise<void>
}

export interface SessionAutoRefreshController {
  active: Readonly<Ref<boolean>>
  inFlight: Readonly<Ref<boolean>>
  start: () => void
  stop: () => void
  setIntervalSeconds: (value: number) => void
  refreshNow: () => Promise<void>
}

export function useSessionAutoRefresh(
  options: SessionAutoRefreshOptions
): SessionAutoRefreshController {
  const active = ref(false)
  const inFlight = ref(false)
  let intervalMs = normalizeIntervalSeconds(options.intervalSeconds) * 1_000
  let timer: ReturnType<typeof setTimeout> | undefined
  let generation = 0
  let currentRequest: Promise<void> | undefined

  function start() {
    stop()
    active.value = true
    schedule(generation)
  }

  function stop() {
    generation += 1
    active.value = false
    clearTimer()
  }

  function setIntervalSeconds(value: number) {
    intervalMs = normalizeIntervalSeconds(value) * 1_000
    if (active.value && currentRequest === undefined) {
      schedule(generation)
    }
  }

  async function refreshNow(): Promise<void> {
    clearTimer()
    if (currentRequest !== undefined) {
      await currentRequest
      clearTimer()
    }
    if (currentRequest !== undefined) return currentRequest
    await execute(generation)
  }

  function schedule(scheduledGeneration: number) {
    if (!active.value || scheduledGeneration !== generation) return
    clearTimer()
    timer = setTimeout(() => {
      timer = undefined
      void execute(scheduledGeneration)
    }, intervalMs)
  }

  function execute(requestGeneration: number): Promise<void> {
    if (currentRequest !== undefined) return currentRequest
    inFlight.value = true
    const request = Promise.resolve()
      .then(options.refresh)
      .catch(async error => {
        await options.onError?.(error)
      })
      .finally(() => {
        if (currentRequest === request) {
          currentRequest = undefined
          inFlight.value = false
          schedule(requestGeneration)
        }
      })
    currentRequest = request
    return request
  }

  function clearTimer() {
    if (timer !== undefined) {
      clearTimeout(timer)
      timer = undefined
    }
  }

  return {
    active: readonly(active),
    inFlight: readonly(inFlight),
    start,
    stop,
    setIntervalSeconds,
    refreshNow
  }
}

function normalizeIntervalSeconds(value: number): number {
  if (!Number.isFinite(value)) return 10
  return Math.min(3_600, Math.max(1, Math.round(value)))
}
