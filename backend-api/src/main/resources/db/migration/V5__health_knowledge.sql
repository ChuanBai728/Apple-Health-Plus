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

-- Sleep (expanded)
insert into health_knowledge values ('sleep-5', '睡眠', '失眠的识别与应对','慢性失眠诊断标准：入睡困难或睡眠维持困难≥3次/周，持续≥3个月，且影响日间功能。认知行为疗法(CBT-I)是首选治疗，效果优于药物且无副作用。', 'sleep,失眠,insomnia,治疗,CBT');
insert into health_knowledge values ('sleep-6', '睡眠', '午睡的科学建议','20-30分钟午睡可提升警觉性和表现，避免>60分钟以免进入深度睡眠导致醒后昏沉。最佳午睡时间在下午1-3点，与人体昼夜节律低谷一致。', 'sleep,午睡,nap,建议');
insert into health_knowledge values ('sleep-7', '睡眠', '昼夜节律与睡眠质量','人体生物钟由视交叉上核调控，受光照强烈影响。早晨接触自然光15-30分钟可同步昼夜节律。夜班工作者心血管疾病和代谢综合征风险增加。', 'sleep,节律,circadian,生物钟,光照');
insert into health_knowledge values ('sleep-8', '睡眠', '睡眠与免疫系统','睡眠期间T细胞和NK细胞活性增强。睡眠<6h导致感染风险增加4倍。深度睡眠阶段生长激素分泌达到高峰，促进组织修复。', 'sleep,免疫,immune,深度睡眠,infection');

-- Heart (expanded)
insert into health_knowledge values ('heart-5', '心脏', '心电图基础与房颤','正常窦性心律60-100bpm。房颤是最常见心律失常，表现为不规则心跳，中风风险增加5倍。Apple Watch ECG可筛查房颤，阳性预测值约84%。', 'heart,ECG,房颤,afib,arrhythmia');
insert into health_knowledge values ('heart-6', '心脏', '血压分类与管理','正常血压<120/80mmHg，理想<115/75。每降低10mmHg收缩压，心血管事件风险降低20%。DASH饮食（低钠高钾）可使收缩压降低8-14mmHg。', 'heart,血压,hypertension,DASH,钠');
insert into health_knowledge values ('heart-7', '心脏', '胆固醇与动脉健康','LDL-C<100mg/dL为理想，>160mg/dL为高危。HDL-C>60mg/dL有保护作用。有氧运动可提升HDL-C 5-10%。动脉硬化是心血管事件的根本原因。', 'heart,胆固醇,cholesterol,LDL,HDL,动脉');
insert into health_knowledge values ('heart-8', '心脏', '运动心电图与心脏应激','运动时心率上升反映心脏负荷。运动后心率恢复(HRR)是心血管健康指标：运动停止1分钟后心率下降>12bpm为正常，下降<12bpm预示风险增加。', 'heart,运动心电图,HRR,恢复,stress test');

-- Activity (expanded)
insert into health_knowledge values ('activity-5', '运动', '心率区间训练','Zone 1(50-60%HRmax)：恢复/热身；Zone 2(60-70%)：脂肪燃烧/耐力基础；Zone 3(70-80%)：有氧能力提升；Zone 4(80-90%)：乳酸阈值；Zone 5(90-100%)：无氧冲刺。每周80%训练应在Zone 1-2。', 'activity,心率区间,zone,训练,cardio');
insert into health_knowledge values ('activity-6', '运动', '力量训练指南','每周≥2次力量训练覆盖主要肌群。每组8-12次重复，2-3组，组间休息60-90秒。渐进超负荷原则：每1-2周增加重量或次数5-10%。力量训练可提升基础代谢率。', 'activity,力量,strength,增肌,训练');
insert into health_knowledge values ('activity-7', '运动', '柔韧性与活动度','静态拉伸每动作保持15-60秒，每周≥3次。动态拉伸适合运动前热身。瑜伽和普拉提可改善柔韧性和核心力量。关节活动度随年龄下降约10%/10年。', 'activity,柔韧性,flexibility,拉伸,瑜伽');
insert into health_knowledge values ('activity-8', '运动', '跑步步态与效率','理想步频约170-180步/分钟。步幅过大增加着地冲击力。前脚掌着地可减少膝关节压力，后跟着地适合初学者。跑步经济性可通过plyometric训练改善。', 'activity,跑步,running,步频,cadence,步幅');
insert into health_knowledge values ('activity-9', '运动', '游泳与交叉训练','游泳是全身性低冲击运动，适合关节康复和交叉训练。30分钟自由泳消耗约250-400kcal。水的浮力减少关节受力90%，特别适合超重和关节炎患者。', 'activity,游泳,swimming,交叉训练,cross');
insert into health_knowledge values ('activity-10', '运动', '高原训练与适应','海拔>1500m时血氧饱和度开始下降。高原训练2-4周可提升红细胞数量和最大摄氧量。回到平原后效果持续约3-4周。急性高原反应：头痛、疲劳、呼吸困难。', 'activity,高原,altitude,血氧,适应');

-- Body (expanded)
insert into health_knowledge values ('body-4', '身体测量', '腰围与内脏脂肪','腰围是内脏脂肪最简易的预测指标。男性>90cm、女性>85cm为腹型肥胖，心血管和代谢风险显著升高。每减少1cm腰围，代谢综合征风险降低约3%。', 'body,腰围,waist,内脏脂肪,visceral');
insert into health_knowledge values ('body-5', '身体测量', '去脂体重与基础代谢','去脂体重（瘦体重）是基础代谢率的主要决定因素。每增加1kg肌肉，基础代谢增加约13-20kcal/天。30岁后若不进行力量训练，每10年损失3-8%肌肉量。', 'body,瘦体重,lean mass,肌肉,代谢');
insert into health_knowledge values ('body-6', '身体测量', '骨密度与骨质疏松','骨密度T值>-1.0SD为正常，-1.0至-2.5为骨量减少，<-2.5为骨质疏松。负重运动和充足钙/维生素D摄入是预防关键。50岁以上女性骨质疏松患病率约30%。', 'body,骨密度,bone,osteoporosis,钙');
insert into health_knowledge values ('body-7', '身体测量', '身体水分率','正常成人身体水分率男性50-65%，女性45-60%。随年龄增长水分率下降。生物电阻抗法(BIA)测量受饮水、进食、运动状态影响，建议同一条件下重复测量。', 'body,水分,hydration,hydration_level,体水');
insert into health_knowledge values ('body-8', '身体测量', '代谢年龄评估','代谢年龄基于体脂率、肌肉量、内脏脂肪等综合计算。代谢年龄<实际年龄提示身体状况优于同龄平均水平。通过运动和饮食改善可在3-6个月内降低代谢年龄。', 'body,代谢年龄,metabolic,age,aging');

-- Vitals (expanded)
insert into health_knowledge values ('vital-3', '生命体征', '体温调节与健康','正常核心体温36.1-37.2°C，存在昼夜波动（清晨最低、傍晚最高）。Apple Watch腕温反映外周皮肤温度，受环境和佩戴方式影响，比核心体温低1-2°C。', 'vital,体温,temperature,发热,fever');
insert into health_knowledge values ('vital-4', '生命体征', '血糖与代谢健康','空腹血糖正常<5.6mmol/L(100mg/dL)。5.6-6.9为空腹血糖受损，≥7.0为糖尿病。规律运动可使胰岛素敏感性提升24-48小时。连续血糖监测可揭示饮食对血糖的个体化影响。', 'vital,血糖,glucose,糖尿病,diabetes,insulin');
insert into health_knowledge values ('vital-5', '生命体征', '心率恢复能力','运动后心率恢复速度反映副交感神经再激活能力。1分钟恢复>25bpm为优秀，12-25bpm为正常，<12bpm需关注。心率恢复慢与全因死亡率增加相关。', 'vital,心率恢复,HRR,recovery,副交感');
insert into health_knowledge values ('vital-6', '生命体征', '双乘积与心肌氧耗','双乘积(RPP)=心率×收缩压÷1000，正常静息值7-10。运动时RPP显著升高反映心肌氧需求。RPP过高与心肌缺血风险相关。定期有氧运动可降低静息和运动RPP。', 'vital,RPP,心肌,氧耗,rate pressure');

-- Nutrition (expanded)
insert into health_knowledge values ('nutrition-3', '营养', '蛋白质摄入指南','成人蛋白质推荐量0.8g/kg/天。运动人群1.2-2.0g/kg/天。优质蛋白来源：鸡蛋、鱼、鸡胸、豆制品、乳清蛋白。每餐摄入25-30g蛋白质最大化肌肉蛋白合成。', 'nutrition,蛋白质,protein,氨基酸,mass');
insert into health_knowledge values ('nutrition-4', '营养', '碳水化合物与运动','碳水化合物是运动主要燃料。中等强度运动每小时需30-60g碳水，高强度60-90g。运动后30分钟内补充碳水+蛋白（4:1比例）可加速糖原恢复。', 'nutrition,碳水,carbs,运动营养,glycogen');
insert into health_knowledge values ('nutrition-5', '营养', '脂肪与必需脂肪酸','脂肪供能占每日热量20-35%。Omega-3（鱼油、亚麻籽）有抗炎和心血管保护作用。反式脂肪和过量饱和脂肪增加动脉硬化风险。橄榄油和坚果富含健康单不饱和脂肪。', 'nutrition,脂肪,fat,omega3,脂肪酸');
insert into health_knowledge values ('nutrition-6', '营养', '微量元素与运动表现','铁缺乏导致运动耐量下降（尤其女性运动员）。镁参与肌肉收缩和能量代谢。锌缺乏影响免疫功能。钙和维生素D对骨骼健康和肌肉功能至关重要。', 'nutrition,微量元素,mineral,铁,锌,镁,钙');
insert into health_knowledge values ('nutrition-7', '营养', '咖啡因与运动','3-6mg/kg咖啡因可提升耐力表现3-5%。峰值效果在摄入后30-60分钟。对力量、爆发力和认知功能也有提升作用。长期使用不产生耐受的运动表现提升效果。', 'nutrition,咖啡因,caffeine,运动,performance');

-- Mental Health (new category)
insert into health_knowledge values ('mental-1', '心理健康', '压力与健康影响','慢性压力激活下丘脑-垂体-肾上腺轴(HPA)，导致皮质醇升高。长期高皮质醇与免疫抑制、腹部脂肪堆积、海马体萎缩、HRV降低相关。正念冥想8周可显著降低皮质醇水平。', 'mental,压力,stress,cortisol,皮质醇,HPA');
insert into health_knowledge values ('mental-2', '心理健康', '运动对心理健康的益处','规律运动可提升BDNF(脑源性神经营养因子)，促进神经新生。30分钟中等强度有氧运动可立即改善情绪并持续数小时。运动治疗轻中度抑郁的效果与药物相当(APA指南)。', 'mental,运动,脑健康,BDNF,抑郁,anxiety');
insert into health_knowledge values ('mental-3', '心理健康', '正念与冥想','正念冥想(MBSR)8周课程可降低焦虑34%、抑郁33%。每天10-15分钟冥想即可显著改善注意力和情绪调节。冥想通过增强前额叶-杏仁核连接降低情绪反应性。', 'mental,正念,mindfulness,meditation,冥想');
insert into health_knowledge values ('mental-4', '心理健康', '睡眠与心理健康','慢性失眠使抑郁症风险增加2倍，焦虑症风险增加3倍。REM睡眠对情绪调节和记忆巩固至关重要。失眠认知行为疗法(CBT-I)可同时改善睡眠和抑郁症状。', 'mental,睡眠,抑郁,焦虑,insomnia,CBT');
insert into health_knowledge values ('mental-5', '心理健康', '心率变异性(HRV)与心理健康','HRV是自主神经功能的窗口。低HRV与抑郁、焦虑、PTSD显著相关。高HRV反映更好的情绪调节能力。生物反馈训练可提升HRV并改善焦虑症状。', 'mental,HRV,情绪,自主神经,biofeedback');
insert into health_knowledge values ('mental-6', '心理健康', '社会联系与健康','孤独感使全因死亡风险增加26%，效果相当于每天吸15支烟。高质量社会联系降低压力激素水平、增强免疫功能。每周至少2-3次有意义的社交互动。', 'mental,社交,孤独,loneliness,social,健康');

-- Recovery
insert into health_knowledge values ('recovery-1', '恢复', '运动后最佳恢复策略','运动后30分钟内摄入蛋白质20-30g+碳水60-80g可最大化恢复效果。睡眠是恢复的首要驱动力。轻度活动（散步、拉伸）比完全休息更有利于消除肌肉酸痛。', 'recovery,恢复,post-exercise,营养,睡眠');
insert into health_knowledge values ('recovery-2', '恢复', '冷热疗法与恢复','冷水浸泡(10-15°C)5-15分钟可减轻运动后肌肉酸痛和炎症。热疗(桑拿、热敷)促进血液循环和放松。冷热交替效果更佳。注意：冷水浸泡可能减弱力量训练的适应性。', 'recovery,冷疗,热疗,ice bath,sauna,按摩');
insert into health_knowledge values ('recovery-3', '恢复', 'HRV指导训练','通过每日晨起HRV监测指导训练强度。HRV在正常范围→正常训练。HRV>基线10%→可增加强度。HRV<基线10%→减量或休息。这种方法可显著减少过度训练风险。', 'recovery,HRV,训练,指导,overtraining,readiness');
insert into health_knowledge values ('recovery-4', '恢复', '酒精对恢复的影响','酒精摄入>2标准杯会显著降低HRV、增加静息心率、减少REM睡眠。酒精抑制蛋白质合成，损害运动后肌肉修复。建议运动日和恢复日限制饮酒。', 'recovery,酒精,alcohol,HRV,睡眠,肌肉');

-- Women's Health
insert into health_knowledge values ('women-1', '女性健康', '月经周期与运动表现','卵泡期(第1-14天)：雌激素上升，力量训练适应性更好，耐力表现可能提升。黄体期(第15-28天)：核心体温升高0.3-0.5°C，可能影响高温环境运动表现，血糖利用效率略有下降。', 'women,月经,周期,menstrual,运动,performance');
insert into health_knowledge values ('women-2', '女性健康', '基础体温与排卵','排卵后基础体温升高0.3-0.5°C并维持至下次月经。Apple Watch腕温可在月经周期中反映这一变化。持续体温不升可能提示无排卵。', 'women,基础体温,排卵,腕温,BBT');
insert into health_knowledge values ('women-3', '女性健康', '女性心率特点','女性平均静息心率比男性高3-5bpm。女性QT间期较长，心律失常风险类型与男性不同。妊娠期心输出量增加30-50%，静息心率升高10-15bpm。', 'women,心率,女性,心率特性,QT,pregnancy');
insert into health_knowledge values ('women-4', '女性健康', '更年期与心血管健康','绝经后雌激素下降导致心血管保护减弱。更年期女性应加强有氧和力量训练，关注血脂和血压变化。HRV在更年期可能下降。', 'women,更年期,menopause,心血管,雌激素');

-- Aging & Longevity
insert into health_knowledge values ('aging-1', '健康长寿', '步速与寿命预测','步速是老年人健康和寿命的强预测指标。自然步速>1.0m/s提示健康老龄。步速<0.6m/s提示衰弱和摔倒风险增加。步速每降低0.1m/s，5年死亡率增加约12%。', 'aging,步速,gait speed,寿命,longevity');
insert into health_knowledge values ('aging-2', '健康长寿', '肌少症预防','40岁后肌肉量每10年减少3-8%，70岁后加速。肌少症诊断：低肌量+低肌力+低体能。抗阻训练是预防和逆转肌少症最有效的手段，任何时候开始都不晚。', 'aging,肌少症,sarcopenia,肌肉,抗阻训练');
insert into health_knowledge values ('aging-3', '健康长寿', '握力与全因死亡','握力是全身肌力的简单替代指标。握力每降低5kg，全因死亡风险增加16%。中老年男性握力>30kg、女性>20kg为健康范围。保持握力需要持续上肢力量训练。', 'aging,握力,grip,strength,死亡风险');
insert into health_knowledge values ('aging-4', '健康长寿', '认知衰退预防','地中海饮食、规律运动、认知刺激（学习、阅读、社交）、充足睡眠是预防认知衰退的四大支柱。中年期运动量与老年认知功能显著正相关。', 'aging,认知,cognitive,痴呆,dementia,预防');
insert into health_knowledge values ('aging-5', '健康长寿', 'VO2max与长寿','VO2max是心肺健康的金标准，也是长寿最强预测指标之一。VO2max每增加3.5mL/kg/min(1MET)，全因死亡率降低12-15%。中年人保持VO2max>30-35可显著延长健康寿命。', 'aging,vo2max,长寿,心肺,cardio');

-- Environment
insert into health_knowledge values ('env-1', '环境', '噪音暴露与健康','环境噪音>55dB(日间)/>45dB(夜间)开始影响健康。长期>65dB增加高血压风险。夜间噪音>40dB干扰睡眠结构。Apple Watch环境噪音监测可帮助识别高风险环境。', 'environment,噪音,noise,听力,血压');
insert into health_knowledge values ('env-2', '环境', '日照与维生素D','每日15-30分钟日照（面部+手臂）可合成足够维生素D。冬季高纬度地区日照不足需考虑补充。维生素D缺乏与骨质疏松、免疫功能下降、情绪障碍相关。', 'environment,日照,维生素D,vitamin D,阳光,sun');
insert into health_knowledge values ('env-3', '环境', '空气质量与运动','PM2.5>35μg/m³时户外运动收益开始被空气污染抵消。避开高峰交通时段和工业园区运动。室内HEPA过滤可降低室内PM2.5 50-80%。', 'environment,空气,污染,PM2.5,运动,肺部');
insert into health_knowledge values ('env-4', '环境', '温湿度与运动安全','运动适宜温度10-25°C。>30°C时体温调节压力显著增加，每升高1°C心率增加5-10bpm。高湿度(>70%)阻止汗液蒸发增加中暑风险。寒冷(<0°C)影响肌肉功能。', 'environment,温度,湿度,heat,运动安全');

-- Chronic Conditions
insert into health_knowledge values ('chronic-1', '慢病管理', '运动是良药','规律运动可预防和治疗26+种慢性疾病。运动对2型糖尿病、冠心病、高血压、抑郁症、骨关节炎有循证疗效。每周150分钟中等强度有氧运动是基础推荐剂量。', 'chronic,运动,慢病,exercise medicine,预防');
insert into health_knowledge values ('chronic-2', '慢病管理', '代谢综合征','诊断标准（满足3项）：腰围超标+血压≥130/85+空腹血糖≥5.6+甘油三酯≥1.7+HDL-C降低。每周≥150分钟运动+减重5-10%可逆转代谢综合征。', 'chronic,代谢综合征,metabolic,肥胖,血糖,血压');
insert into health_knowledge values ('chronic-3', '慢病管理', '2型糖尿病运动处方','有氧运动+抗阻训练组合效果优于单一方式。餐后15-20分钟散步可降低餐后血糖峰值。高强度间歇训练(HIIT)改善胰岛素敏感性效果显著。运动前后监测血糖防低血糖。', 'chronic,糖尿病,diabetes,运动,血糖,胰岛素');
insert into health_knowledge values ('chronic-4', '慢病管理', '冠心病二级预防','稳定型冠心病患者规律运动可使全因死亡降低26-31%。运动强度以Borg RPE 11-14为宜。需避免憋气和爆发力动作。运动前应进行心肺运动试验评估。', 'chronic,冠心病,CAD,心脏康复,cardiac,康复');
insert into health_knowledge values ('chronic-5', '慢病管理', '高血压生活方式干预','DASH饮食+低钠(<5g盐/天)+规律运动可降低收缩压8-14mmHg。减重每减1kg收缩压降低约1mmHg。限制饮酒（男<2杯/天，女<1杯）也可降低血压。', 'chronic,高血压,生活方式,减盐,运动,DASH');
insert into health_knowledge values ('chronic-6', '慢病管理', '骨关节炎运动指南','水中运动减少关节负重90%，是理想初始运动方式。强化股四头肌可减少膝关节疼痛40-50%。避免高冲击活动(跑步、跳跃)。适度运动不加速关节退变反而促进修复。', 'chronic,骨关节炎,osteoarthritis,关节,运动,水疗');

-- General (expanded)
insert into health_knowledge values ('general-3', '通用', '可穿戴设备的局限性','光学心率传感器（PPG）在运动、低温、肤色较深时精度下降。步数计数在推车、握扶手时可能不准确。能量消耗估算误差达20-40%。数据用于趋势观察而非绝对精确测量。', 'general,可穿戴,wearable,精度,limitation,PPG');
insert into health_knowledge values ('general-4', '通用', '健康数据的长期价值','单日数据波动正常，关注周/月趋势更有意义。多个指标交叉验证比单一指标更可靠（如HRV+RHR+睡眠同时恶化=需要关注）。建议每季度回顾健康数据趋势。', 'general,数据,趋势,trend,价值,analytics');
insert into health_knowledge values ('general-5', '通用', '设定SMART健康目标','Specific（具体化）：每天8000步而非多走路。Measurable（可量化）：用数据追踪。Achievable（可实现）：循序渐进。Relevant（相关性）：与个人健康需求匹配。Time-bound（有时限）：4周内达成。', 'general,目标,goal,SMART,计划,健康');
insert into health_knowledge values ('general-6', '通用', '何时应咨询医生','持续静息心率>100bpm或<50bpm（非运动员）；不明原因体重下降>5%/月；持续SpO2<92%；HRV持续显著下降>30%个人基线；胸痛、晕厥、严重呼吸困难。', 'general,医生,就医,警告,red flag,咨询');
