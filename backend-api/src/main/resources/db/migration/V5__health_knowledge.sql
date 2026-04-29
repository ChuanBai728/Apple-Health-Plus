create table health_knowledge (
    id          varchar(32) primary key,
    category    varchar(32) not null,
    title       varchar(128) not null,
    content     text not null,
    keywords    text not null
);

-- Sleep
insert into health_knowledge values ('sleep-1', '睡眠', '睡眠时长与健康',
    '成人推荐睡眠时长7-9小时（美国睡眠医学会AASM指南）。长期<6小时与心血管疾病风险增加48%、2型糖尿病风险增加37%相关。>9小时也需关注潜在健康问题。',
    'sleep,睡眠,时长,duration,hours');
insert into health_knowledge values ('sleep-2', '睡眠', '睡眠周期与深度睡眠',
    '正常睡眠周期约90分钟，每夜4-6个周期。深度睡眠（N3期）占总睡眠15-25%，是身体修复和免疫增强的关键阶段。深度睡眠不足与认知功能下降、免疫力降低相关。',
    'sleep,深度,deep,周期,cycle');
insert into health_knowledge values ('sleep-3', '睡眠', '睡眠与HRV的关系',
    '充足睡眠（≥7h）通常伴随HRV升高，反映副交感神经活跃。睡眠不足时HRV通常降低15-30%，静息心率升高3-5bpm，表明自主神经系统失衡，恢复不充分。',
    'sleep,HRV,recovery,恢复,variability');
insert into health_knowledge values ('sleep-4', '睡眠', '改善睡眠的建议',
    '固定作息时间是最有效的睡眠改善策略。睡前1小时避免蓝光（手机/电脑），卧室温度控制在18-22°C。若卧床20分钟无法入睡，应起身进行放松活动，待有睡意再回床。',
    'sleep,改善,建议,advice,tips,失眠,insomnia');

-- Heart
insert into health_knowledge values ('heart-1', '心脏', '静息心率参考范围',
    '成人静息心率正常范围60-100 bpm。经常运动者40-60 bpm为良好体能标志。持续RHR>85 bpm与全因死亡率增加相关。短期内RHR升高>10 bpm需警惕过度训练或感染。',
    'heart,心率,rhr,resting,静息');
insert into health_knowledge values ('heart-2', '心脏', 'HRV心率变异性',
    'HRV衡量心跳间期微变化，SDNN是最常用指标。成人SDNN一般20-70ms，健康年轻人40-100ms。HRV高=自主神经调节能力强。HRV短期下降是正常应激反应，长期显著下降（>30%基线）需关注。',
    'heart,HRV,variability,变异性,sdnn');
insert into health_knowledge values ('heart-3', '心脏', '运动中的心率响应',
    '最大心率≈220-年龄。中等强度运动对应50-70%最大心率，高强度70-85%最大心率。运动后1分钟心率恢复>12bpm为正常心血管反应，<12bpm可能预示心血管风险。',
    'heart,exercise,运动,心率,max,training');
insert into health_knowledge values ('heart-4', '心脏', '血氧饱和度',
    '正常静息SpO2≥95%。90-94%为轻度低氧血症，<90%需立即就医。高原环境、睡眠呼吸暂停、COPD可导致SpO2下降。Apple Watch测量受佩戴松紧、皮肤血流影响。',
    'heart,血氧,oxygen,spo2,saturation');

-- Activity & Exercise
insert into health_knowledge values ('activity-1', '运动', '每日步数与健康',
    '>10,000步/天为理想目标，>8,000步/天已能显著降低全因死亡率。5,000-7,500步/天为基础健康水平。<5,000步/天为久坐生活方式。',
    'activity,步数,steps,步行,walking');
insert into health_knowledge values ('activity-2', '运动', 'WHO运动建议',
    'WHO建议成人每周至少150分钟中等强度或75分钟高强度有氧运动，加2次以上力量训练。每周300分钟以上中等强度运动可获得额外健康收益。',
    'activity,运动,exercise,WHO,建议,训练');
insert into health_knowledge values ('activity-3', '运动', 'VO2max与心肺耐力',
    'VO2max是衡量心肺耐力的金标准。成年男性35-55、女性30-45 mL/kg/min为正常范围。每增加1 MET的VO2max，全因死亡率降低约15%。高强度间歇训练对提升VO2max最有效。',
    'activity,vo2max,心肺,cardio,fitness,耐力');
insert into health_knowledge values ('activity-4', '运动', '运动恢复与过度训练',
    '过度训练表现为：静息心率持续升高>5bpm、HRV持续下降>20%、睡眠质量变差、情绪低落。出现2项以上需减少训练量50%并增加休息。完全恢复通常需要1-4周。',
    'activity,恢复,recovery,overtraining,过度,训练');

-- Body
insert into health_knowledge values ('body-1', '身体测量', 'BMI与健康风险',
    'BMI 18.5-24.9为健康范围。BMI不区分肌肉与脂肪，运动员BMI>25可能正常。腰围（男>90cm，女>85cm）比BMI更能预测代谢风险。',
    'body,BMI,体重,weight,body mass');
insert into health_knowledge values ('body-2', '身体测量', '体脂率参考范围',
    '男性健康体脂率10-20%，女性18-28%。必需脂肪：男性3-5%，女性8-12%。体脂率<5%（男）或<12%（女）可能影响内分泌。腹部脂肪比皮下脂肪更有害。',
    'body,体脂,fat,percentage');
insert into health_knowledge values ('body-3', '身体测量', '体重波动解读',
    '正常日间体重波动0.5-2kg，主要来自水分和食物摄入。每周固定时间（晨起空腹）称重更有可比性。持续2周以上单向变化>0.5kg/周可视为真实趋势。',
    'body,体重,weight,波动,trend');

-- Vitals
insert into health_knowledge values ('vital-1', '生命体征', '呼吸频率参考',
    '成人静息呼吸频率12-20次/分为正常。运动员可低至8-12次/分。>20次/分（气促）可能与焦虑、贫血、心肺疾病或感染相关。',
    'vital,呼吸,respiratory,rate,breathing');
insert into health_knowledge values ('vital-2', '生命体征', '血压参考',
    '理想血压<120/80 mmHg。120-139/80-89为高血压前期。>140/90为高血压。Apple Watch不直接测量血压，但可通过HRV和静息心率间接反映心血管压力。',
    'vital,血压,blood pressure,hypertension');

-- Nutrition
insert into health_knowledge values ('nutrition-1', '营养', '水分摄入建议',
    '成人日均需水1.5-2.5L（含食物水分）。尿液颜色浅黄为水分充足。运动每小时额外补充400-800ml水。脱水（>2%体重）会降低运动表现和认知功能。',
    'nutrition,水,water,hydration,饮水,脱水');
insert into health_knowledge values ('nutrition-2', '营养', '能量平衡',
    '1kg体脂≈7700kcal。每日500kcal负平衡约每周减重0.5kg。基础代谢占60-75%总能耗，活动代谢15-30%。极低热量饮食（<1200kcal/天）会降低基础代谢。',
    'nutrition,能量,energy,calories,减肥,代谢');

-- General
insert into health_knowledge values ('general-1', '通用', 'Apple Health数据解读',
    'Apple Watch/iPhone数据为消费级健康监测，非医疗级诊断设备。数据趋势比单点数值更有意义。持续异常应向医生咨询。系统根据手腕检测推算，在皮肤血流灌注差时精度下降。',
    'general,Apple,Watch,数据,datasource,精度');
insert into health_knowledge values ('general-2', '通用', '医疗免责声明',
    '本分析基于用户上传的Apple Health数据，仅供健康参考，不构成医疗诊断。任何健康问题请咨询执业医师。AI建议不应替代专业医疗意见。',
    'general,disclaimer,免责,诊断');
