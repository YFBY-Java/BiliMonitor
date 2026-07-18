export interface RequestGeneration {
  next: () => number
  invalidate: () => void
  current: () => number
  isCurrent: (token: number) => boolean
}

export function createRequestGeneration(): RequestGeneration {
  let generation = 0

  return {
    next() {
      generation += 1
      return generation
    },
    invalidate() {
      generation += 1
    },
    current() {
      return generation
    },
    isCurrent(token: number) {
      return token === generation
    }
  }
}
