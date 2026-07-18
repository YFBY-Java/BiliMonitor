import { readonly, ref, type Ref } from 'vue'

export interface QrPollingStatus {
  status: string
}

interface QrPollingOptions<T extends QrPollingStatus> {
  poll: () => Promise<T>
  intervalMs: number
  onResult?: (result: T) => void | Promise<void>
  onError?: (error: unknown) => void | Promise<void>
  isTerminal?: (result: T) => boolean
}

export interface QrPollingController {
  active: Readonly<Ref<boolean>>
  inFlight: Readonly<Ref<boolean>>
  start: (initialDelayMs?: number) => void
  stop: () => void
  setIntervalMs: (value: number) => void
}

const DEFAULT_TERMINAL_STATES = new Set(['SUCCESS', 'EXPIRED', 'FAILED'])

export function useQrPolling<T extends QrPollingStatus>(options: QrPollingOptions<T>): QrPollingController {
  const active = ref(false)
  const inFlight = ref(false)
  let intervalMs = normalizeDelay(options.intervalMs)
  let timer: ReturnType<typeof setTimeout> | undefined
  let generation = 0
  let requestSequence = 0
  let currentRequest: number | undefined

  const isTerminal = options.isTerminal ?? (result => DEFAULT_TERMINAL_STATES.has(result.status))

  function start(initialDelayMs = 0) {
    stop()
    active.value = true
    schedule(initialDelayMs, generation)
  }

  function stop() {
    generation += 1
    active.value = false
    if (timer !== undefined) {
      clearTimeout(timer)
      timer = undefined
    }
  }

  function setIntervalMs(value: number) {
    intervalMs = normalizeDelay(value)
  }

  function schedule(delay: number, scheduledGeneration: number) {
    if (!active.value || scheduledGeneration !== generation) return
    if (timer !== undefined) clearTimeout(timer)
    timer = setTimeout(() => {
      timer = undefined
      void pollOnce(scheduledGeneration)
    }, normalizeDelay(delay))
  }

  async function pollOnce(requestGeneration: number) {
    if (!active.value || requestGeneration !== generation || currentRequest !== undefined) return

    const requestId = ++requestSequence
    currentRequest = requestId
    inFlight.value = true
    try {
      const result = await options.poll()
      if (!active.value || requestGeneration !== generation) return
      await options.onResult?.(result)
      if (isTerminal(result)) {
        stop()
      } else {
        schedule(intervalMs, requestGeneration)
      }
    } catch (error) {
      if (!active.value || requestGeneration !== generation) return
      await options.onError?.(error)
      schedule(intervalMs, requestGeneration)
    } finally {
      if (currentRequest === requestId) {
        currentRequest = undefined
        inFlight.value = false
      }
    }
  }

  return {
    active: readonly(active),
    inFlight: readonly(inFlight),
    start,
    stop,
    setIntervalMs
  }
}

function normalizeDelay(value: number) {
  return Number.isFinite(value) ? Math.max(0, Math.round(value)) : 0
}
