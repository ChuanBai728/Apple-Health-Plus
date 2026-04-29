// API response types
export interface CreateUploadResponse {
  uploadId: string;
  storageKey: string;
  uploadUrl: string;
  status: string;
}

export interface UploadStatusResponse {
  uploadId: string;
  status: string;
  fileName: string;
  createdAt: string;
  lastError: string | null;
}

export interface OverviewCard {
  metricKey: string;
  label: string;
  latest: number | null;
  unit: string;
  trend30d: number | null;
  anomaly: boolean;
  recentPoints: MetricPoint[];
}

export interface OverviewResponse {
  reportId: string;
  status: string;
  coverage?: {
    totalRecords: number;
    metricCount: number;
    sourceCount: number;
    startDate: string | null;
    endDate: string | null;
  };
  cards: OverviewCard[];
  headline: string;
}

export interface MetricPoint {
  date: string;
  value: number | null;
}

export interface MetricSeriesResponse {
  metricKey: string;
  label: string;
  granularity: string;
  points: MetricPoint[];
}

export interface MetricPoint {
  date: string;
  value: number | null;
  baselineAvg30d?: number | null;
  trendDelta7d?: number | null;
  trendDelta30d?: number | null;
  anomaly?: boolean;
}

export interface ChatMessageRequest {
  question: string;
  uploadId?: string;
}

export interface ChatMessageResponse {
  intent: string;
  conclusion: string;
  evidence: string[];
  advice: string[];
  disclaimer: string;
}

export interface ChatHistoryMessage {
  role: string;
  content: string;
  intent: string | null;
  evidence: string[];
  advice: string[];
  disclaimer: string | null;
  createdAt: string;
}

export interface ChatSessionResponse {
  sessionId: string;
  uploadId: string | null;
  title: string;
  createdAt: string;
  messages: ChatHistoryMessage[];
}

// Upload status flow
export type UploadStatus =
  | 'created'
  | 'uploading'
  | 'uploaded'
  | 'queued'
  | 'parsing'
  | 'parsed'
  | 'aggregating'
  | 'ready'
  | 'failed'
  | 'deleted';

export const STATUS_LABELS: Record<string, string> = {
  created: '已创建',
  uploading: '上传中',
  uploaded: '已上传',
  queued: '排队中',
  parsing: '解析中',
  parsed: '已解析',
  aggregating: '聚合中',
  ready: '就绪',
  failed: '失败',
  deleted: '已删除',
};

export const METRIC_LABELS: Record<string, string> = {
  active_energy_burned: '活动能量',
  apple_exercise_time: '锻炼时长',
  apple_sleeping_wrist_temperature: '睡眠腕温',
  apple_stand_time: '站立时长',
  apple_walking_steadiness: '步行稳定性',
  basal_energy_burned: '基础代谢',
  body_fat_percentage: '体脂率',
  body_mass: '体重',
  body_mass_index: 'BMI',
  dietary_energy_consumed: '膳食能量',
  dietary_water: '饮水量',
  distance_cycling: '骑行距离',
  distance_walking_running: '步行/跑步距离',
  environmental_audio_exposure: '环境噪音',
  environmental_sound_reduction: '降噪暴露',
  flights_climbed: '爬楼层数',
  headphone_audio_exposure: '耳机音量',
  heart_rate: '心率',
  heart_rate_recovery_one_minute: '心率恢复',
  heart_rate_variability: '心率变异性',
  heart_rate_variability_sdnn: 'HRV (SDNN)',
  height: '身高',
  hkdata_type_sleep_duration_goal: '睡眠目标',
  lean_body_mass: '去脂体重',
  mindful_minutes: '正念时长',
  oxygen_saturation: '血氧饱和度',
  physical_effort: '体力负荷',
  respiratory_rate: '呼吸频率',
  resting_heart_rate: '静息心率',
  running_ground_contact_time: '触地时间',
  running_power: '跑步功率',
  running_speed: '跑步速度',
  running_stride_length: '步幅',
  running_vertical_oscillation: '垂直振幅',
  six_minute_walk_test_distance: '6分钟步行',
  sleep_duration: '睡眠时长',
  stair_ascent_speed: '上楼速度',
  stair_descent_speed: '下楼速度',
  step_count: '步数',
  time_in_daylight: '日照时长',
  vo2max: '最大摄氧量',
  waist_circumference: '腰围',
  walking_asymmetry_percentage: '步态不对称',
  walking_double_support_percentage: '双支撑占比',
  walking_heart_rate_average: '步行平均心率',
  walking_speed: '步行速度',
  walking_step_length: '步长',
  workout: '运动时长',
  blood_pressure_systolic: '收缩压',
  blood_pressure_diastolic: '舒张压',
  blood_oxygen_saturation: '血氧饱和度',
  walking_running_distance: '步行/跑步距离',
};

export const METRIC_UNITS: Record<string, string> = {
  active_energy_burned: 'kcal',
  apple_exercise_time: 'min',
  apple_sleeping_wrist_temperature: '°C',
  apple_stand_time: 'min',
  apple_walking_steadiness: '%',
  basal_energy_burned: 'kcal',
  body_fat_percentage: '%',
  body_mass: 'kg',
  body_mass_index: 'kg/m²',
  dietary_energy_consumed: 'kcal',
  dietary_water: 'mL',
  distance_cycling: 'km',
  distance_walking_running: 'km',
  environmental_audio_exposure: 'dB',
  environmental_sound_reduction: 'dB',
  flights_climbed: '层',
  headphone_audio_exposure: 'dB',
  heart_rate: 'bpm',
  heart_rate_recovery_one_minute: 'bpm',
  heart_rate_variability: 'ms',
  heart_rate_variability_sdnn: 'ms',
  height: 'cm',
  hkdata_type_sleep_duration_goal: 'h',
  lean_body_mass: 'kg',
  mindful_minutes: 'min',
  oxygen_saturation: '%',
  physical_effort: 'MET',
  respiratory_rate: '次/分',
  resting_heart_rate: 'bpm',
  running_ground_contact_time: 'ms',
  running_power: 'W',
  running_speed: 'km/h',
  running_stride_length: 'm',
  running_vertical_oscillation: 'cm',
  six_minute_walk_test_distance: 'm',
  sleep_duration: 'h',
  stair_ascent_speed: 'm/s',
  stair_descent_speed: 'm/s',
  step_count: '步',
  time_in_daylight: 'min',
  vo2max: 'mL/kg/min',
  waist_circumference: 'cm',
  walking_asymmetry_percentage: '%',
  walking_double_support_percentage: '%',
  walking_heart_rate_average: 'bpm',
  walking_speed: 'km/h',
  walking_step_length: 'cm',
  workout: 'min',
  blood_pressure_systolic: 'mmHg',
  blood_pressure_diastolic: 'mmHg',
  blood_oxygen_saturation: '%',
};

// Health direction: true = higher is good, false = higher is bad, null = neutral
const HIGHER_IS_GOOD = new Set(['sleep_duration','heart_rate_variability_sdnn','heart_rate_variability','step_count','active_energy_burned','vo2max','walking_speed','apple_walking_steadiness','oxygen_saturation','blood_oxygen_saturation','walking_running_distance','distance_walking_running','flights_climbed','lean_body_mass','time_in_daylight','six_minute_walk_test_distance','apple_exercise_time','apple_stand_time','running_speed','running_power','distance_cycling','stair_ascent_speed','walking_step_length']);
const LOWER_IS_GOOD = new Set(['body_mass','body_fat_percentage','resting_heart_rate','heart_rate','body_mass_index','waist_circumference','respiratory_rate','walking_heart_rate_average','headphone_audio_exposure','environmental_audio_exposure','walking_asymmetry_percentage','walking_double_support_percentage','physical_effort','running_vertical_oscillation','running_ground_contact_time','heart_rate_recovery_one_minute']);

export function getTrendColor(metricKey: string, trend: number): string {
  if (HIGHER_IS_GOOD.has(metricKey)) return trend > 0 ? 'text-emerald-500' : 'text-rose-500';
  if (LOWER_IS_GOOD.has(metricKey)) return trend > 0 ? 'text-rose-500' : 'text-emerald-500';
  return 'text-gray-400';
}

// Category groupings for overview display
export const METRIC_CATEGORIES: Record<string, { label: string; icon: string }> = {
  activity: { label: '🏃 活动与健身', icon: '🏃' },
  heart: { label: '🫀 心脏与生命体征', icon: '🫀' },
  body: { label: '⚖️ 身体测量', icon: '⚖️' },
  sleep_env: { label: '💤 睡眠与环境', icon: '💤' },
};

const CATEGORY_MAP: Record<string, string> = {
  step_count: 'activity', active_energy_burned: 'activity', basal_energy_burned: 'activity',
  distance_walking_running: 'activity', walking_running_distance: 'activity',
  flights_climbed: 'activity', apple_exercise_time: 'activity', apple_stand_time: 'activity',
  workout: 'activity', physical_effort: 'activity', distance_cycling: 'activity',
  walking_speed: 'activity', walking_step_length: 'activity', walking_heart_rate_average: 'activity',
  walking_double_support_percentage: 'activity', walking_asymmetry_percentage: 'activity',
  apple_walking_steadiness: 'activity', running_speed: 'activity', running_power: 'activity',
  running_stride_length: 'activity', running_vertical_oscillation: 'activity',
  running_ground_contact_time: 'activity', stair_ascent_speed: 'activity',
  stair_descent_speed: 'activity', six_minute_walk_test_distance: 'activity',

  heart_rate: 'heart', resting_heart_rate: 'heart', heart_rate_variability_sdnn: 'heart',
  heart_rate_variability: 'heart', heart_rate_recovery_one_minute: 'heart',
  oxygen_saturation: 'heart', blood_oxygen_saturation: 'heart', respiratory_rate: 'heart',
  vo2max: 'heart',

  body_mass: 'body', body_fat_percentage: 'body', body_mass_index: 'body',
  lean_body_mass: 'body', waist_circumference: 'body', height: 'body',
  dietary_water: 'body', dietary_energy_consumed: 'body',

  sleep_duration: 'sleep_env', hkdata_type_sleep_duration_goal: 'sleep_env',
  apple_sleeping_wrist_temperature: 'sleep_env', time_in_daylight: 'sleep_env',
  environmental_audio_exposure: 'sleep_env', environmental_sound_reduction: 'sleep_env',
  headphone_audio_exposure: 'sleep_env', mindful_minutes: 'sleep_env',
};

export interface MetricReference {
  description: string;
  healthyRange: string;
  advice: string;
}

export const METRIC_REFERENCE: Record<string, MetricReference> = {
  heart_rate: {
    description: '心率指心脏每分钟跳动的次数，是反映心脏健康和身体状态的核心指标。运动、情绪、咖啡因等都会影响心率。',
    healthyRange: '静息状态 60-100 bpm；运动员可低至 40 bpm；运动时随强度升高，最大心率 ≈ 220-年龄',
    advice: '持续静息心率偏高（>85 bpm）可能与压力、缺乏运动或潜在健康问题有关，建议增加有氧运动。',
  },
  resting_heart_rate: {
    description: '静息心率是身体完全放松时的心跳速度，通常在早晨刚醒来时测量。较低的静息心率通常意味着更高效的心脏功能。',
    healthyRange: '成人健康范围 60-80 bpm；经常运动者 40-60 bpm；>85 bpm 需关注',
    advice: '有氧运动、充足睡眠和管理压力是降低静息心率的有效方法。如果短期内突然升高 10 bpm 以上，可能预示过度训练或疾病。',
  },
  heart_rate_variability_sdnn: {
    description: '心率变异性（HRV SDNN）衡量连续心跳之间时间间隔的微小变化。高 HRV 表示自主神经系统调节能力强，身体恢复好。',
    healthyRange: '成人一般 20-70 ms，健康年轻人通常 40-100 ms，运动员可 >100 ms',
    advice: 'HRV 短期下降是正常压力反应；如果长期显著下降（低于个人基线 30%），建议减少训练量、增加休息和睡眠。',
  },
  step_count: {
    description: '每日步数是衡量日常活动量的最直观指标，包含走路、跑步等所有步态活动。',
    healthyRange: '健康目标 ≥8,000 步/天；活跃目标 ≥10,000 步/天；<5,000 步/天为久坐生活方式',
    advice: '循序渐进增加步数，每天比前一天多走 500 步。分散在全天完成比一次走完更有利于关节健康。',
  },
  active_energy_burned: {
    description: '活动能量指通过身体活动消耗的热量（基础代谢除外）。数值越高说明日常活动越活跃。',
    healthyRange: '因人而异，一般建议每日 1.5-3.0 kcal；具体取决于年龄、性别、体重和活动水平',
    advice: '结合有氧和力量训练能更有效地提高日常能量消耗。不要只关注单日数值，关注周趋势更有意义。',
  },
  sleep_duration: {
    description: '睡眠时长指每夜实际睡眠的总时长。充足的睡眠是身体修复、记忆巩固和免疫系统的基石。',
    healthyRange: '成人 7-9 小时，青少年 8-10 小时，<6 小时或 >10 小时均需关注',
    advice: '固定入睡和起床时间为改善睡眠最有效的方法。睡前 1 小时减少屏幕蓝光暴露，卧室温度控制在 18-22°C。',
  },
  body_mass: {
    description: '体重是身体质量的基本指标，反映骨骼、肌肉、脂肪和水分等组织的总和。单次数值意义有限，长期趋势更重要。',
    healthyRange: '结合身高计算 BMI：18.5-24.9 为健康范围',
    advice: '每周同一时间（如周一早晨空腹）测量更具可比性。体重的正常日波动约 0.5-2 kg，不必为短期波动焦虑。',
  },
  body_fat_percentage: {
    description: '体脂率是身体脂肪占总体重的百分比，比单看体重更能反映身体成分健康状况。',
    healthyRange: '男性 10-20%，女性 18-28%；运动员偏低，>30%（男）/>35%（女）需关注',
    advice: '通过力量训练增加肌肉量可改善体脂率。极端低体脂（男<5%，女<12%）可能影响内分泌和免疫。',
  },
  oxygen_saturation: {
    description: '血氧饱和度反映血液中红细胞携带氧气的比例，是衡量呼吸系统和循环系统功能的关键指标。',
    healthyRange: '正常 ≥95%，90-94% 需注意，<90% 建议就医',
    advice: '持续低血氧可能与睡眠呼吸暂停、肺功能或高海拔环境有关。运动时血氧短暂下降属正常现象。',
  },
  respiratory_rate: {
    description: '呼吸频率是每分钟的呼吸次数。异常升高可能是压力、疾病或过度训练的信号。',
    healthyRange: '成人静息 12-20 次/分，运动员可低至 8-12 次/分',
    advice: '运动中呼吸加快属正常。静息呼吸率持续 >20 次/分建议关注。腹式深呼吸练习可帮助降低呼吸频率。',
  },
  vo2max: {
    description: '最大摄氧量（VO₂max）是衡量心肺耐力的黄金标准，指身体在高强度运动时能利用的最大氧气量。',
    healthyRange: '男性 35-55，女性 30-45 mL/kg/min；数值越高心肺功能越好，随年龄自然下降',
    advice: '高强度间歇训练（HIIT）和持续有氧运动是提高 VO₂max 最有效的方法。每年约以 1% 速度自然衰减。',
  },
  workout: {
    description: '运动时长记录各类训练（跑步、骑行、力量等）的持续时间。规律运动是维持整体健康的基础。',
    healthyRange: 'WHO 建议每周 ≥150 分钟中等强度或 ≥75 分钟高强度运动',
    advice: '多样化的训练类型有助于全面发展身体能力。建议将有氧、力量和柔韧训练均衡搭配。',
  },
  body_mass_index: {
    description: 'BMI（身体质量指数）是体重与身高平方的比值，广泛用于评估体重是否在健康范围内。',
    healthyRange: '18.5-24.9 健康，25-29.9 超重，≥30 肥胖，<18.5 偏瘦',
    advice: 'BMI 不区分肌肉和脂肪，运动员可能偏高。结合体脂率和腰围一同评估更为准确。',
  },
  basal_energy_burned: {
    description: '基础代谢能量是身体在完全休息状态下维持生命所需的最少热量，占总能量消耗的 60-75%。',
    healthyRange: '因人而异，与年龄、性别、体重和肌肉量相关，无明显"健康范围"',
    advice: '增加肌肉量是提高基础代谢最有效的方法。极端节食反而会降低基础代谢率。',
  },
  walking_speed: {
    description: '步行速度是日常行走的快慢程度，被多项研究证明是预测健康和寿命的重要指标。',
    healthyRange: '日常步行 3-5 km/h，<2.5 km/h 提示活动能力下降',
    advice: '刻意加快日常步行速度可以提升心肺耐力。尝试间歇性快走（快走 1 分钟+慢走 2 分钟循环）。',
  },
  apple_walking_steadiness: {
    description: '步行稳定性评估步态的对称性和平稳程度，反映平衡能力和跌倒风险。',
    healthyRange: 'OK 级别及以上为正常，Low/Very Low 提示跌倒风险增加',
    advice: '平衡训练（如单腿站立、太极、瑜伽）可以改善步行稳定性。稳定性在 Low 级别时建议咨询医生。',
  },
  flights_climbed: {
    description: '爬楼层数记录每天攀爬的楼梯高度，是下肢力量和心肺耐力的良好指标。',
    healthyRange: '无固定标准，日均 5-10 层为活跃水平',
    advice: '爬楼梯是高效率的有氧和力量结合训练。膝盖不适者可选择电梯上行、步行下行的方式。',
  },
  environmental_audio_exposure: {
    description: '环境噪音暴露记录周围环境的平均声音分贝水平。长期高分贝暴露会损害听力并增加压力。',
    healthyRange: '日间 50-70 dB 为正常环境，持续 >85 dB 可能损伤听力',
    advice: '在嘈杂环境中佩戴降噪耳塞或用降噪耳机播放白噪音。长期高噪音暴露与高血压和睡眠障碍相关。',
  },
  headphone_audio_exposure: {
    description: '耳机音量暴露记录通过耳机接收的音频分贝水平。长期高分贝听音是不可逆听力损伤的主要原因。',
    healthyRange: 'WHO 建议耳机音量 <80 dB，每日累计 <40 小时',
    advice: '遵循 60/60 原则：音量不超过最大音量的 60%，单次不超过 60 分钟。选择具有通透模式的耳机更安全。',
  },
  time_in_daylight: {
    description: '日照时长记录每天在户外自然光下的时间。充足日照对维生素 D 合成、情绪调节和昼夜节律至关重要。',
    healthyRange: '每日 15-30 分钟为基本需求，早晨日光对调节睡眠周期最有效',
    advice: '早晨起床后 30 分钟内接触自然光 10-15 分钟，有助于改善情绪和夜间入睡质量。',
  },
  dietary_water: {
    description: '饮水量记录每日通过饮水摄入的液体量。充足的水分对代谢、体温调节和认知功能至关重要。',
    healthyRange: '一般成人每日 1.5-2.5 L，运动或高温环境需增加',
    advice: '不要等到口渴才喝水。尿液颜色浅黄为水分充足的表现，深黄提示需要补水。',
  },
  apple_sleeping_wrist_temperature: {
    description: '睡眠腕温是夜间睡眠时手腕皮肤的基础温度，反映身体核心温度调节和代谢状态。',
    healthyRange: '个体差异较大，通常比基础体温低 1-2°C，月经周期后半段女性可升高 0.3-0.5°C',
    advice: '睡眠腕温异常升高可能提示感染、饮酒过量或睡眠环境温度过高。保持卧室凉爽有利于深睡眠。',
  },
  walking_heart_rate_average: {
    description: '步行平均心率记录步行期间的心率水平。较低的水平意味着步行对心血管系统负担较轻。',
    healthyRange: '因人而异，一般步行心率在最大心率的 50-70% 为宜',
    advice: '如果步行心率持续偏高，可通过每周 3-4 次的有氧训练来提升心肺功能。',
  },
  physical_effort: {
    description: '体力负荷（METs）衡量身体活动相对于静息状态的代谢水平。1 MET = 静息代谢率。',
    healthyRange: '中强度活动 3-6 METs，高强度活动 >6 METs。日均有 30 分钟中高强度活动为宜',
    advice: '散步 ≈ 3 METs，慢跑 ≈ 7 METs。每天累计 30 分钟中高强度活动对健康有显著益处。',
  },
};

// Metrics to hide from overview (noisy / uninteresting for general health overview)
const HIDDEN_METRICS = new Set([
  'distance_cycling', 'flights_climbed', 'running_power', 'running_speed',
  'running_stride_length', 'running_vertical_oscillation', 'running_ground_contact_time',
  'dietary_water', 'dietary_energy_consumed', 'hkdata_type_sleep_duration_goal',
  'apple_sleeping_wrist_temperature', 'environmental_sound_reduction',
  'stair_ascent_speed', 'stair_descent_speed', 'six_minute_walk_test_distance',
  'waist_circumference', 'heart_rate_recovery_one_minute', 'height',
  'apple_exercise_time', // merged into workout
]);

export function isHiddenMetric(key: string): boolean { return HIDDEN_METRICS.has(key); }

export const CATEGORY_LABELS: Record<string, string> = {
  heart_cardio: '🫀 心脏与循环', body_metrics: '⚖ 身体测量', daily_activity: '🏃 活动与健身',
  sleep_recovery: '💤 睡眠与恢复', vitals_respiratory: '🫁 生命体征', workouts_training: '🏋 训练表现',
  mindfulness_mental: '🧘 心理与正念',
  other: '📌 其他', activity: '🏃 活动与健身',
  // Legacy short keys from earlier parser versions
  heart: '🫀 心脏与循环', body: '⚖ 身体测量', sleep_env: '💤 睡眠与恢复',
  vitals: '🫁 生命体征', fitness: '🏋 训练表现', mind: '🧘 心理与正念',
};
export function catLabel(cat: string): string { return CATEGORY_LABELS[cat] || `📌 ${cat}`; }

export function getCategory(key: string): string { return CATEGORY_MAP[key] || 'other'; }
export function isImportantMetric(key: string): boolean { return true; }
