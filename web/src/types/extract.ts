export type SleepStage = 'core' | 'deep' | 'rem' | 'unspecified' | 'awake' | 'inbed'

export interface SleepSegment {
  start: string
  end: string
  minutes: number
  stage: SleepStage
  source: string
}

export type WorkoutActivity = 'strength' | 'walk' | 'cycling' | 'running' | 'stairs' | 'other'

export interface Workout {
  activity: WorkoutActivity
  start: string
  end: string
  minutes: number
  source: string
}

/** day -> source -> value */
export interface DailyBySource {
  [day: string]: { [source: string]: number }
}

/** day -> { sum, n }：均值型指标（心率、HRV），多来源样本合并求均值 */
export interface DailyAvg {
  [day: string]: { sum: number; n: number }
}

export interface RingDay {
  kcal: number
  exerciseMin: number
  standHours: number
}

export interface RawExtract {
  stepCount: DailyBySource
  distanceKm: DailyBySource
  flightsClimbed: DailyBySource
  daylightMin: DailyBySource
  rings: { [day: string]: RingDay }
  restingHr: DailyAvg
  hrv: DailyAvg
  sleepSegments: SleepSegment[]
  workouts: Workout[]
}

export function emptyExtract(): RawExtract {
  return {
    stepCount: {},
    distanceKm: {},
    flightsClimbed: {},
    daylightMin: {},
    rings: {},
    restingHr: {},
    hrv: {},
    sleepSegments: [],
    workouts: [],
  }
}

export type WorkerRequest =
  | { type: 'parse'; file: File }
  | { type: 'demo' }
  | { type: 'cancel' }

export type WorkerResponse =
  | { type: 'phase'; phase: 'zip' | 'inflate' | 'scan' }
  | { type: 'progress'; ratio: number; bytesDone: number; bytesTotal: number; lines: number }
  | { type: 'done'; data: RawExtract; warnings: string[]; ignored: number }
  | { type: 'error'; message: string }