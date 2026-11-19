#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成《国家、资本与产业 · 28 本阅读计划》plan JSON。"""
import json
import hashlib
from pathlib import Path

STAGES = [
    ("第一阶段 · 地方与国家治理", "第 1 组", "地方财政与治理"),
    ("第二阶段 · 权力与制度", "第 2 组", "权力与制度"),
    ("第三阶段 · 金融与资本", "第 3 组", "金融与资本"),
    ("第四阶段 · 产业与竞争", "第 4 组", "产业与竞争"),
    ("第五阶段 · 历史与体系", "第 5 组", "历史与体系"),
    ("第六阶段 · 文学中的现实", "第 6 组", "文学与现实"),
]

BOOKS = [
    # 第一组
    dict(title="转型中的地方政府：官员激励与治理（第二版）", author="周黎安", stage=0,
         mode="精读", days=20, pages=500,
         background="用政治经济学与组织经济学分析中国地方政府的官员激励、晋升竞争与区域竞争，解释地方政府为何深度介入招商引资、基础设施建设和土地开发。",
         prerequisite="了解改革开放以来中央与地方关系的基本脉络；不需要博弈论基础，但要对激励、竞争这类概念有耐心。"),
    dict(title="中国国家治理的制度逻辑：一个组织学研究", author="周雪光", stage=0,
         mode="精读", days=18, pages=400,
         background="从组织社会学出发，讨论正式制度与实际运行之间的张力，分析中央政策如何在多层科层组织中传导、变通，并出现执行偏差。",
         prerequisite="对中国行政层级（中央—省—市—县—乡）有基本概念；不需要社会学背景。"),
    dict(title="县乡中国：县域治理现代化", author="杨华", stage=0,
         mode="通读", days=10, pages=300,
         background="基于县乡基层调研，呈现县域治理、乡镇干部、村级组织与农民日常互动的真实运行，补充宏观制度分析看不到的基层细节。",
         prerequisite="无；如果先了解一点“条块关系”，会更容易对上书里的场景。"),
    dict(title="大国大城：当代中国的统一、发展与平衡", author="陆铭", stage=0,
         mode="通读", days=10, pages=320,
         background="从空间经济学讨论城市化、人口流动、土地指标与区域平衡，解释人口和产业为何向大城市集聚，以及限制流动的代价。",
         prerequisite="供给、需求、规模经济等基础经济学概念会有帮助，但没有也能读。"),
    dict(title="中国经济改革进程（第2版）", author="吴敬琏", stage=0,
         mode="精读", days=18, pages=450,
         background="以亲历者视角梳理改革开放以来市场化改革的阶段、争论与制度变迁，帮助建立理解中国经济的历史坐标。",
         prerequisite="了解1978年以来的主要改革节点：包产到户、价格双轨、国企改革、加入WTO。"),
    # 第二组
    dict(title="国家的视角：那些试图改善人类状况的项目是如何失败的", author="詹姆斯·C. 斯科特", stage=1,
         mode="精读", days=16, pages=380,
         background="分析国家为何偏爱标准化、统计、规划与“可读性”，以及宏大工程为何常在执行中失败；提出“国家的可读性”和“米提斯”等概念。",
         prerequisite="无；读时可结合中国的扶贫、城市化、土地整治案例对照。"),
    dict(title="独裁者手册：为什么坏行为几乎总是好政治", author="布鲁斯·布埃诺·德·梅斯基塔、阿拉斯泰尔·史密斯", stage=1,
         mode="通读", days=10, pages=300,
         background="用理性选择模型把政治简化为支持者联盟的维持问题，解释税收、资源分配与公共品供给为何常常偏离公共利益。",
         prerequisite="接受“模型是对现实的简化”这一前提；对形式化推理不排斥即可。"),
    dict(title="国家为什么会失败：权力、繁荣与贫穷的根源", author="达龙·阿西莫格鲁、詹姆斯·A. 罗宾逊", stage=1,
         mode="精读", days=16, pages=450,
         background="以包容性制度和攫取性制度解释国家间的发展差异，强调政治制度与经济制度的相互作用，是制度经济学最流行的公共表达之一。",
         prerequisite="无；注意作者把制度作为主解释变量，读时可与地理、文化、技术等解释对照。"),
    dict(title="狭窄的走廊：国家、社会与自由的命运", author="达龙·阿西莫格鲁、詹姆斯·A. 罗宾逊", stage=1,
         mode="选读", days=12, pages=500,
         background="讨论国家能力与社会力量之间的平衡：国家太弱无法提供秩序，国家太强则吞噬社会；只有两者相互制衡的“狭窄走廊”才能容纳自由。",
         prerequisite="最好先读《国家为什么会失败》，否则概念密度偏高。"),
    dict(title="政治秩序的起源：从前人类时代到法国大革命", author="弗朗西斯·福山", stage=1,
         mode="精读", days=20, pages=600,
         background="从生物演化、部落社会、国家建构、法治与责任制政府的长时段比较，解释现代政治秩序的组件如何在不同文明中形成。",
         prerequisite="世界史基本脉络；篇幅大，建议按“国家—法治—问责”三条线做笔记。"),
    # 第三组
    dict(title="时间的代价：利息的真实故事", author="爱德华·钱斯勒", stage=2,
         mode="精读", days=16, pages=400,
         background="以利息观念史为主线，讨论低利率如何扭曲资本配置、推高资产价格、鼓励债务与冒险，连接货币史与金融危机。",
         prerequisite="理解名义利率、实际利率、复利与通胀的基本含义。"),
    dict(title="疯狂、惊恐和崩溃：金融危机史", author="查尔斯·金德尔伯格、罗伯特·阿利伯", stage=2,
         mode="精读", days=14, pages=350,
         background="经典金融危机史，分析信贷扩张、投机狂热、恐慌与崩溃的阶段性机制，以及最后贷款人角色的争议。",
         prerequisite="无；知道银行、债券、股票的基本功能即可。"),
    dict(title="货币崛起：世界金融史", author="尼尔·弗格森", stage=2,
         mode="选读", days=10, pages=350,
         background="从货币、债券、股票、保险到房地产与全球化，讲述金融工具如何塑造国家兴衰与日常生活。",
         prerequisite="无；可作为金融史的入门叙事。"),
    dict(title="大空头", author="迈克尔·刘易斯", stage=2,
         mode="通读", days=8, pages=300,
         background="以几位做空者为主线还原美国次贷危机，展示评级机构、投行、投资者与监管之间的激励错位。",
         prerequisite="了解房贷证券化（MBS、CDO）的基本概念会更顺。"),
    dict(title="大转型：我们时代的政治与经济起源", author="卡尔·波兰尼", stage=2,
         mode="选读", days=14, pages=350,
         background="论证市场经济不是自然产物，而是国家干预与社会制度共同建构的结果；提出“双向运动”与“社会保护”概念。",
         prerequisite="对19世纪欧洲资本主义史有粗略了解；概念较抽象，适合慢读。"),
    # 第四组
    dict(title="芯片战争：世界最关键技术的争夺战", author="克里斯·米勒", stage=3,
         mode="精读", days=12, pages=350,
         background="以半导体产业链为主线，讲述美国、日本、韩国、中国台湾地区与欧洲的产业政策、企业竞争与地缘博弈，解释芯片为何成为战略物资。",
         prerequisite="无；知道晶圆制造、芯片设计、封装测试的基本分工即可。"),
    dict(title="创业型国家：公共部门如何创造奇迹", author="玛丽安娜·马祖卡托", stage=3,
         mode="通读", days=12, pages=300,
         background="反驳“政府只会修补市场”的观点，用互联网、GPS、医药等案例论证公共投资在创新中的关键作用，讨论产业政策的合理性。",
         prerequisite="基础经济学；对创新政策、政府采购有粗略了解更好。"),
    dict(title="交易的世界：金钱、权力与全球大宗商品交易商", author="哈维尔·布拉斯、杰基·法奇", stage=3,
         mode="选读", days=10, pages=350,
         background="追踪全球大宗商品交易商的兴衰，展示石油、金属、粮食贸易背后的资本、政治与信息优势。",
         prerequisite="无；了解期货、现货与大宗商品的基本概念会有帮助。"),
    dict(title="新工业国", author="约翰·肯尼思·加尔布雷思", stage=3,
         mode="选读", days=14, pages=400,
         background="分析大型企业如何通过技术结构、计划与广告影响市场与社会，提出“技术结构阶层”的权力转移。",
         prerequisite="对产业组织、企业理论有基本了解；成书于1967年，需结合当代平台企业重读。"),
    dict(title="门口的野蛮人：史上最强悍的资本收购", author="布赖恩·伯勒、约翰·希利亚尔", stage=3,
         mode="通读", days=10, pages=400,
         background="还原RJR Nabisco杠杆收购案，展示管理层、董事会、投行、私募基金之间的利益博弈与资本运作。",
         prerequisite="知道杠杆收购（LBO）和垃圾债券的基本含义。"),
    # 第五组
    dict(title="大分流：欧洲、中国及现代世界经济的发展", author="彭慕兰", stage=4,
         mode="精读", days=18, pages=450,
         background="挑战欧洲中心论，论证18世纪前中国与欧洲在经济发展上并无本质差距，工业革命的分流源于煤炭、殖民地与全球贸易等条件。",
         prerequisite="全球经济史基础；对“加州学派”与欧洲中心论的争论有了解更好。"),
    dict(title="大国的兴衰：1500—2000年的经济变迁与军事冲突", author="保罗·肯尼迪", stage=4,
         mode="选读", days=16, pages=600,
         background="从长时段比较大国兴衰，论证经济实力与军事扩张之间的张力：过度战略扩张会拖垮经济基础。",
         prerequisite="世界近现代史基本框架；成书于1987年，需补充冷战后的变化。"),
    dict(title="现代世界体系（第一卷）", author="伊曼纽尔·沃勒斯坦", stage=4,
         mode="选读", days=20, pages=500,
         background="用“核心—半边缘—边缘”的世界体系框架解释16世纪以来资本主义的全球扩张与分工。",
         prerequisite="对欧洲近代史、马克思主义政治经济学有基本了解；四卷本，建议先读第一卷。"),
    dict(title="国家建构：21世纪的国家治理与世界秩序", author="弗朗西斯·福山", stage=4,
         mode="选读", days=8, pages=200,
         background="区分“国家范围”与“国家能力”，讨论国家建构、公共行政能力与国际秩序；篇幅短，是《政治秩序的起源》的补充。",
         prerequisite="最好先读《政治秩序的起源》，或了解福山的基本框架。"),
    # 第六组
    dict(title="沧浪之水", author="阎真", stage=5,
         mode="通读", days=10, pages=450,
         background="以卫生系统公务员的成长与妥协为主线，呈现官僚组织中的权力、利益与个人选择，是理解体制内生存逻辑的文学样本。",
         prerequisite="无；可对照《中国国家治理的制度逻辑》读。"),
    dict(title="大江东去", author="阿耐", stage=5,
         mode="通读", days=20, pages=900,
         background="以改革开放中的国企、民企与个体户为群像，呈现企业成长、市场竞争与制度变迁。",
         prerequisite="无；对1990年代国企改革、下海潮有粗略了解更好。"),
    dict(title="繁花", author="金宇澄", stage=5,
         mode="选读", days=12, pages=450,
         background="以上海市民与商业社会为背景，用密集的对话与细节呈现改革开放以来的城市生活、人情与阶层变化。",
         prerequisite="无；方言与叙事风格需要适应，可先读几页试节奏。"),
    dict(title="白鹿原", author="陈忠实", stage=5,
         mode="通读", days=14, pages=500,
         background="以一个村庄与两大家族的命运，呈现宗族、权力、革命与乡土社会在20世纪的变迁。",
         prerequisite="无；对中国近现代农村史有基本了解更好。"),
]


def build():
    books = []
    cursor = 0
    for i, b in enumerate(BOOKS, 1):
        stage_name, slot, part = STAGES[b["stage"]]
        item = {
            "index": i,
            "title": b["title"],
            "author": b["author"],
            "part": part,
            "stage": stage_name,
            "slot": slot,
            "mode": b["mode"],
            "days": b["days"],
            "pages": b["pages"],
            "background": b["background"],
            "prerequisite": b["prerequisite"],
        }
        books.append(item)
        cursor += b["days"]

    plan = {
        "planId": "political-economy-28-2026",
        "planName": "国家、资本与产业 · 28 本阅读计划",
        "startDate": "2026-11-19",
        "totalDays": cursor,
        "books": books,
    }
    out = Path(__file__).resolve().parents[1] / "dist" / "plan-28-2026.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(plan, ensure_ascii=False, indent=2) + "\n"
    out.write_text(text, encoding="utf-8")
    sha = hashlib.sha256(text.encode("utf-8")).hexdigest()
    max_daily = max(b["pages"] / b["days"] for b in BOOKS)
    print(f"wrote {out}")
    print(f"books={len(books)} totalDays={cursor} pages={sum(b['pages'] for b in BOOKS)} maxDaily={max_daily:.1f}")
    print(f"sha256={sha}")


if __name__ == "__main__":
    build()
