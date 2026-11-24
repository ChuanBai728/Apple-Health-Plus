export const MARATHON_KM = 42.195
export const EVEREST_M = 8848.49
/** Apple 把 1 层记为约 10 英尺 */
export const FLOOR_M = 3.048
export const EQUATOR_KM = 40075

export const everestCount = (floors: number) => (floors * FLOOR_M) / EVEREST_M
export const marathonCount = (km: number) => km / MARATHON_KM
export const equatorPct = (km: number) => (km / EQUATOR_KM) * 100