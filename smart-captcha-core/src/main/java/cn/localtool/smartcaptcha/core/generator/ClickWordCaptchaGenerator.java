package cn.localtool.smartcaptcha.core.generator;

import cn.localtool.smartcaptcha.core.CaptchaAnswer;
import cn.localtool.smartcaptcha.core.CaptchaChallenge;
import cn.localtool.smartcaptcha.core.CaptchaConfig;
import cn.localtool.smartcaptcha.core.CaptchaType;
import cn.localtool.smartcaptcha.core.GenerationResult;
import cn.localtool.smartcaptcha.core.support.ImageSupport;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 文字点选验证码：在背景上随机绘制若干字符，用户按提示顺序点击对应文字。
 *
 * <p>字符集由 {@code click.charset} 控制（{@code zh} 中文、默认；{@code en} 英文），
 * 也可在单次生成请求中通过 {@code clickCharset} 覆盖。</p>
 *
 * <p>展示数与点击数分离：{@code click.word-count} 是需要点击的字数，
 * {@code click.display-count}（可选，>= word-count）是画面上实际绘制的字数，
 * 多出来的字是干扰项，不参与提示与校验；不设置时两者相同。</p>
 *
 * <p>字体可自定义：代码里调 {@code config.getClick().setFont(Font)} 传入字体对象，
 * 或配置 {@code smart-captcha.click.font-path} 指定字体文件（本地路径 /
 * {@code classpath:} 资源）、{@code smart-captcha.click.font-family} 指定字体族名。
 * 不做任何字体校验、不做字体推荐：设置了外部字体就直接使用，
 * 未设置时用 SansSerif 逻辑字体（JVM 自动映射平台字体）。</p>
 */
public class ClickWordCaptchaGenerator implements CaptchaGenerator {

    private static final Logger log = Logger.getLogger(ClickWordCaptchaGenerator.class.getName());

    public static final String CHARSET_ZH = "zh";
    public static final String CHARSET_EN = "en";

    /** 中文字符池：GB2312 一级字库前 2000 字（区位码 16 区起，拼音序常用简体字，每字一个元素）。 */
    private static final String[] CJK_POOL = {
            "啊", "阿", "埃", "挨", "哎", "唉", "哀", "皑", "癌", "蔼", "矮", "艾", "碍", "爱", "隘", "鞍", "氨", "安", "俺", "按",
            "暗", "岸", "胺", "案", "肮", "昂", "盎", "凹", "敖", "熬", "翱", "袄", "傲", "奥", "懊", "澳", "芭", "捌", "扒", "叭",
            "吧", "笆", "八", "疤", "巴", "拔", "跋", "靶", "把", "耙", "坝", "霸", "罢", "爸", "白", "柏", "百", "摆", "佰", "败",
            "拜", "稗", "斑", "班", "搬", "扳", "般", "颁", "板", "版", "扮", "拌", "伴", "瓣", "半", "办", "绊", "邦", "帮", "梆",
            "榜", "膀", "绑", "棒", "磅", "蚌", "镑", "傍", "谤", "苞", "胞", "包", "褒", "剥", "薄", "雹", "保", "堡", "饱", "宝",
            "抱", "报", "暴", "豹", "鲍", "爆", "杯", "碑", "悲", "卑", "北", "辈", "背", "贝", "钡", "倍", "狈", "备", "惫", "焙",
            "被", "奔", "苯", "本", "笨", "崩", "绷", "甭", "泵", "蹦", "迸", "逼", "鼻", "比", "鄙", "笔", "彼", "碧", "蓖", "蔽",
            "毕", "毙", "毖", "币", "庇", "痹", "闭", "敝", "弊", "必", "辟", "壁", "臂", "避", "陛", "鞭", "边", "编", "贬", "扁",
            "便", "变", "卞", "辨", "辩", "辫", "遍", "标", "彪", "膘", "表", "鳖", "憋", "别", "瘪", "彬", "斌", "濒", "滨", "宾",
            "摈", "兵", "冰", "柄", "丙", "秉", "饼", "炳", "病", "并", "玻", "菠", "播", "拨", "钵", "波", "博", "勃", "搏", "铂",
            "箔", "伯", "帛", "舶", "脖", "膊", "渤", "泊", "驳", "捕", "卜", "哺", "补", "埠", "不", "布", "步", "簿", "部", "怖",
            "擦", "猜", "裁", "材", "才", "财", "睬", "踩", "采", "彩", "菜", "蔡", "餐", "参", "蚕", "残", "惭", "惨", "灿", "苍",
            "舱", "仓", "沧", "藏", "操", "糙", "槽", "曹", "草", "厕", "策", "侧", "册", "测", "层", "蹭", "插", "叉", "茬", "茶",
            "查", "碴", "搽", "察", "岔", "差", "诧", "拆", "柴", "豺", "搀", "掺", "蝉", "馋", "谗", "缠", "铲", "产", "阐", "颤",
            "昌", "猖", "场", "尝", "常", "长", "偿", "肠", "厂", "敞", "畅", "唱", "倡", "超", "抄", "钞", "朝", "嘲", "潮", "巢",
            "吵", "炒", "车", "扯", "撤", "掣", "彻", "澈", "郴", "臣", "辰", "尘", "晨", "忱", "沉", "陈", "趁", "衬", "撑", "称",
            "城", "橙", "成", "呈", "乘", "程", "惩", "澄", "诚", "承", "逞", "骋", "秤", "吃", "痴", "持", "匙", "池", "迟", "弛",
            "驰", "耻", "齿", "侈", "尺", "赤", "翅", "斥", "炽", "充", "冲", "虫", "崇", "宠", "抽", "酬", "畴", "踌", "稠", "愁",
            "筹", "仇", "绸", "瞅", "丑", "臭", "初", "出", "橱", "厨", "躇", "锄", "雏", "滁", "除", "楚", "础", "储", "矗", "搐",
            "触", "处", "揣", "川", "穿", "椽", "传", "船", "喘", "串", "疮", "窗", "幢", "床", "闯", "创", "吹", "炊", "捶", "锤",
            "垂", "春", "椿", "醇", "唇", "淳", "纯", "蠢", "戳", "绰", "疵", "茨", "磁", "雌", "辞", "慈", "瓷", "词", "此", "刺",
            "赐", "次", "聪", "葱", "囱", "匆", "从", "丛", "凑", "粗", "醋", "簇", "促", "蹿", "篡", "窜", "摧", "崔", "催", "脆",
            "瘁", "粹", "淬", "翠", "村", "存", "寸", "磋", "撮", "搓", "措", "挫", "错", "搭", "达", "答", "瘩", "打", "大", "呆",
            "歹", "傣", "戴", "带", "殆", "代", "贷", "袋", "待", "逮", "怠", "耽", "担", "丹", "单", "郸", "掸", "胆", "旦", "氮",
            "但", "惮", "淡", "诞", "弹", "蛋", "当", "挡", "党", "荡", "档", "刀", "捣", "蹈", "倒", "岛", "祷", "导", "到", "稻",
            "悼", "道", "盗", "德", "得", "的", "蹬", "灯", "登", "等", "瞪", "凳", "邓", "堤", "低", "滴", "迪", "敌", "笛", "狄",
            "涤", "翟", "嫡", "抵", "底", "地", "蒂", "第", "帝", "弟", "递", "缔", "颠", "掂", "滇", "碘", "点", "典", "靛", "垫",
            "电", "佃", "甸", "店", "惦", "奠", "淀", "殿", "碉", "叼", "雕", "凋", "刁", "掉", "吊", "钓", "调", "跌", "爹", "碟",
            "蝶", "迭", "谍", "叠", "丁", "盯", "叮", "钉", "顶", "鼎", "锭", "定", "订", "丢", "东", "冬", "董", "懂", "动", "栋",
            "侗", "恫", "冻", "洞", "兜", "抖", "斗", "陡", "豆", "逗", "痘", "都", "督", "毒", "犊", "独", "读", "堵", "睹", "赌",
            "杜", "镀", "肚", "度", "渡", "妒", "端", "短", "锻", "段", "断", "缎", "堆", "兑", "队", "对", "墩", "吨", "蹲", "敦",
            "顿", "囤", "钝", "盾", "遁", "掇", "哆", "多", "夺", "垛", "躲", "朵", "跺", "舵", "剁", "惰", "堕", "蛾", "峨", "鹅",
            "俄", "额", "讹", "娥", "恶", "厄", "扼", "遏", "鄂", "饿", "恩", "而", "儿", "耳", "尔", "饵", "洱", "二", "贰", "发",
            "罚", "筏", "伐", "乏", "阀", "法", "珐", "藩", "帆", "番", "翻", "樊", "矾", "钒", "繁", "凡", "烦", "反", "返", "范",
            "贩", "犯", "饭", "泛", "坊", "芳", "方", "肪", "房", "防", "妨", "仿", "访", "纺", "放", "菲", "非", "啡", "飞", "肥",
            "匪", "诽", "吠", "肺", "废", "沸", "费", "芬", "酚", "吩", "氛", "分", "纷", "坟", "焚", "汾", "粉", "奋", "份", "忿",
            "愤", "粪", "丰", "封", "枫", "蜂", "峰", "锋", "风", "疯", "烽", "逢", "冯", "缝", "讽", "奉", "凤", "佛", "否", "夫",
            "敷", "肤", "孵", "扶", "拂", "辐", "幅", "氟", "符", "伏", "俘", "服", "浮", "涪", "福", "袱", "弗", "甫", "抚", "辅",
            "俯", "釜", "斧", "脯", "腑", "府", "腐", "赴", "副", "覆", "赋", "复", "傅", "付", "阜", "父", "腹", "负", "富", "讣",
            "附", "妇", "缚", "咐", "噶", "嘎", "该", "改", "概", "钙", "盖", "溉", "干", "甘", "杆", "柑", "竿", "肝", "赶", "感",
            "秆", "敢", "赣", "冈", "刚", "钢", "缸", "肛", "纲", "岗", "港", "杠", "篙", "皋", "高", "膏", "羔", "糕", "搞", "镐",
            "稿", "告", "哥", "歌", "搁", "戈", "鸽", "胳", "疙", "割", "革", "葛", "格", "蛤", "阁", "隔", "铬", "个", "各", "给",
            "根", "跟", "耕", "更", "庚", "羹", "埂", "耿", "梗", "工", "攻", "功", "恭", "龚", "供", "躬", "公", "宫", "弓", "巩",
            "汞", "拱", "贡", "共", "钩", "勾", "沟", "苟", "狗", "垢", "构", "购", "够", "辜", "菇", "咕", "箍", "估", "沽", "孤",
            "姑", "鼓", "古", "蛊", "骨", "谷", "股", "故", "顾", "固", "雇", "刮", "瓜", "剐", "寡", "挂", "褂", "乖", "拐", "怪",
            "棺", "关", "官", "冠", "观", "管", "馆", "罐", "惯", "灌", "贯", "光", "广", "逛", "瑰", "规", "圭", "硅", "归", "龟",
            "闺", "轨", "鬼", "诡", "癸", "桂", "柜", "跪", "贵", "刽", "辊", "滚", "棍", "锅", "郭", "国", "果", "裹", "过", "哈",
            "骸", "孩", "海", "氦", "亥", "害", "骇", "酣", "憨", "邯", "韩", "含", "涵", "寒", "函", "喊", "罕", "翰", "撼", "捍",
            "旱", "憾", "悍", "焊", "汗", "汉", "夯", "杭", "航", "壕", "嚎", "豪", "毫", "郝", "好", "耗", "号", "浩", "呵", "喝",
            "荷", "菏", "核", "禾", "和", "何", "合", "盒", "貉", "阂", "河", "涸", "赫", "褐", "鹤", "贺", "嘿", "黑", "痕", "很",
            "狠", "恨", "哼", "亨", "横", "衡", "恒", "轰", "哄", "烘", "虹", "鸿", "洪", "宏", "弘", "红", "喉", "侯", "猴", "吼",
            "厚", "候", "后", "呼", "乎", "忽", "瑚", "壶", "葫", "胡", "蝴", "狐", "糊", "湖", "弧", "虎", "唬", "护", "互", "沪",
            "户", "花", "哗", "华", "猾", "滑", "画", "划", "化", "话", "槐", "徊", "怀", "淮", "坏", "欢", "环", "桓", "还", "缓",
            "换", "患", "唤", "痪", "豢", "焕", "涣", "宦", "幻", "荒", "慌", "黄", "磺", "蝗", "簧", "皇", "凰", "惶", "煌", "晃",
            "幌", "恍", "谎", "灰", "挥", "辉", "徽", "恢", "蛔", "回", "毁", "悔", "慧", "卉", "惠", "晦", "贿", "秽", "会", "烩",
            "汇", "讳", "诲", "绘", "荤", "昏", "婚", "魂", "浑", "混", "豁", "活", "伙", "火", "获", "或", "惑", "霍", "货", "祸",
            "击", "圾", "基", "机", "畸", "稽", "积", "箕", "肌", "饥", "迹", "激", "讥", "鸡", "姬", "绩", "缉", "吉", "极", "棘",
            "辑", "籍", "集", "及", "急", "疾", "汲", "即", "嫉", "级", "挤", "几", "脊", "己", "蓟", "技", "冀", "季", "伎", "祭",
            "剂", "悸", "济", "寄", "寂", "计", "记", "既", "忌", "际", "妓", "继", "纪", "嘉", "枷", "夹", "佳", "家", "加", "荚",
            "颊", "贾", "甲", "钾", "假", "稼", "价", "架", "驾", "嫁", "歼", "监", "坚", "尖", "笺", "间", "煎", "兼", "肩", "艰",
            "奸", "缄", "茧", "检", "柬", "碱", "硷", "拣", "捡", "简", "俭", "剪", "减", "荐", "槛", "鉴", "践", "贱", "见", "键",
            "箭", "件", "健", "舰", "剑", "饯", "渐", "溅", "涧", "建", "僵", "姜", "将", "浆", "江", "疆", "蒋", "桨", "奖", "讲",
            "匠", "酱", "降", "蕉", "椒", "礁", "焦", "胶", "交", "郊", "浇", "骄", "娇", "嚼", "搅", "铰", "矫", "侥", "脚", "狡",
            "角", "饺", "缴", "绞", "剿", "教", "酵", "轿", "较", "叫", "窖", "揭", "接", "皆", "秸", "街", "阶", "截", "劫", "节",
            "桔", "杰", "捷", "睫", "竭", "洁", "结", "解", "姐", "戒", "藉", "芥", "界", "借", "介", "疥", "诫", "届", "巾", "筋",
            "斤", "金", "今", "津", "襟", "紧", "锦", "仅", "谨", "进", "靳", "晋", "禁", "近", "烬", "浸", "尽", "劲", "荆", "兢",
            "茎", "睛", "晶", "鲸", "京", "惊", "精", "粳", "经", "井", "警", "景", "颈", "静", "境", "敬", "镜", "径", "痉", "靖",
            "竟", "竞", "净", "炯", "窘", "揪", "究", "纠", "玖", "韭", "久", "灸", "九", "酒", "厩", "救", "旧", "臼", "舅", "咎",
            "就", "疚", "鞠", "拘", "狙", "疽", "居", "驹", "菊", "局", "咀", "矩", "举", "沮", "聚", "拒", "据", "巨", "具", "距",
            "踞", "锯", "俱", "句", "惧", "炬", "剧", "捐", "鹃", "娟", "倦", "眷", "卷", "绢", "撅", "攫", "抉", "掘", "倔", "爵",
            "觉", "决", "诀", "绝", "均", "菌", "钧", "军", "君", "峻", "俊", "竣", "浚", "郡", "骏", "喀", "咖", "卡", "咯", "开",
            "揩", "楷", "凯", "慨", "刊", "堪", "勘", "坎", "砍", "看", "康", "慷", "糠", "扛", "抗", "亢", "炕", "考", "拷", "烤",
            "靠", "坷", "苛", "柯", "棵", "磕", "颗", "科", "壳", "咳", "可", "渴", "克", "刻", "客", "课", "肯", "啃", "垦", "恳",
            "坑", "吭", "空", "恐", "孔", "控", "抠", "口", "扣", "寇", "枯", "哭", "窟", "苦", "酷", "库", "裤", "夸", "垮", "挎",
            "跨", "胯", "块", "筷", "侩", "快", "宽", "款", "匡", "筐", "狂", "框", "矿", "眶", "旷", "况", "亏", "盔", "岿", "窥",
            "葵", "奎", "魁", "傀", "馈", "愧", "溃", "坤", "昆", "捆", "困", "括", "扩", "廓", "阔", "垃", "拉", "喇", "蜡", "腊",
            "辣", "啦", "莱", "来", "赖", "蓝", "婪", "栏", "拦", "篮", "阑", "兰", "澜", "谰", "揽", "览", "懒", "缆", "烂", "滥",
            "琅", "榔", "狼", "廊", "郎", "朗", "浪", "捞", "劳", "牢", "老", "佬", "姥", "酪", "烙", "涝", "勒", "乐", "雷", "镭",
            "蕾", "磊", "累", "儡", "垒", "擂", "肋", "类", "泪", "棱", "楞", "冷", "厘", "梨", "犁", "黎", "篱", "狸", "离", "漓",
            "理", "李", "里", "鲤", "礼", "莉", "荔", "吏", "栗", "丽", "厉", "励", "砾", "历", "利", "傈", "例", "俐", "痢", "立",
            "粒", "沥", "隶", "力", "璃", "哩", "俩", "联", "莲", "连", "镰", "廉", "怜", "涟", "帘", "敛", "脸", "链", "恋", "炼",
            "练", "粮", "凉", "梁", "粱", "良", "两", "辆", "量", "晾", "亮", "谅", "撩", "聊", "僚", "疗", "燎", "寥", "辽", "潦",
            "了", "撂", "镣", "廖", "料", "列", "裂", "烈", "劣", "猎", "琳", "林", "磷", "霖", "临", "邻", "鳞", "淋", "凛", "赁",
            "吝", "拎", "玲", "菱", "零", "龄", "铃", "伶", "羚", "凌", "灵", "陵", "岭", "领", "另", "令", "溜", "琉", "榴", "硫",
            "馏", "留", "刘", "瘤", "流", "柳", "六", "龙", "聋", "咙", "笼", "窿", "隆", "垄", "拢", "陇", "楼", "娄", "搂", "篓",
            "漏", "陋", "芦", "卢", "颅", "庐", "炉", "掳", "卤", "虏", "鲁", "麓", "碌", "露", "路", "赂", "鹿", "潞", "禄", "录",
            "陆", "戮", "驴", "吕", "铝", "侣", "旅", "履", "屡", "缕", "虑", "氯", "律", "率", "滤", "绿", "峦", "挛", "孪", "滦",
            "卵", "乱", "掠", "略", "抡", "轮", "伦", "仑", "沦", "纶", "论", "萝", "螺", "罗", "逻", "锣", "箩", "骡", "裸", "落",
            "洛", "骆", "络", "妈", "麻", "玛", "码", "蚂", "马", "骂", "嘛", "吗", "埋", "买", "麦", "卖", "迈", "脉", "瞒", "馒",
            "蛮", "满", "蔓", "曼", "慢", "漫", "谩", "芒", "茫", "盲", "氓", "忙", "莽", "猫", "茅", "锚", "毛", "矛", "铆", "卯",
            "茂", "冒", "帽", "貌", "贸", "么", "玫", "枚", "梅", "酶", "霉", "煤", "没", "眉", "媒", "镁", "每", "美", "昧", "寐",
            "妹", "媚", "门", "闷", "们", "萌", "蒙", "檬", "盟", "锰", "猛", "梦", "孟", "眯", "醚", "靡", "糜", "迷", "谜", "弥",
            "米", "秘", "觅", "泌", "蜜", "密", "幂", "棉", "眠", "绵", "冕", "免", "勉", "娩", "缅", "面", "苗", "描", "瞄", "藐",
            "秒", "渺", "庙", "妙", "蔑", "灭", "民", "抿", "皿", "敏", "悯", "闽", "明", "螟", "鸣", "铭", "名", "命", "谬", "摸",
            "摹", "蘑", "模", "膜", "磨", "摩", "魔", "抹", "末", "莫", "墨", "默", "沫", "漠", "寞", "陌", "谋", "牟", "某", "拇",
            "牡", "亩", "姆", "母", "墓", "暮", "幕", "募", "慕", "木", "目", "睦", "牧", "穆", "拿", "哪", "呐", "钠", "那", "娜",
            "纳", "氖", "乃", "奶", "耐", "奈", "南", "男", "难", "囊", "挠", "脑", "恼", "闹", "淖", "呢", "馁", "内", "嫩", "能",
            "妮", "霓", "倪", "泥", "尼", "拟", "你", "匿", "腻", "逆", "溺", "蔫", "拈", "年", "碾", "撵", "捻", "念", "娘", "酿",
            "鸟", "尿", "捏", "聂", "孽", "啮", "镊", "镍", "涅", "您", "柠", "狞", "凝", "宁", "拧", "泞", "牛", "扭", "钮", "纽",
            "脓", "浓", "农", "弄", "奴", "努", "怒", "女", "暖", "虐", "疟", "挪", "懦", "糯", "诺", "哦", "欧", "鸥", "殴", "藕"
    };

    /** 英文字符池（大写字母，去掉 I/O/Q 等易与数字混淆的字符）。 */
    private static final String[] LATIN_POOL = {
            "A", "B", "C", "D", "E", "F", "G", "H", "K", "M", "N", "P", "R", "S", "T", "U", "X", "Y", "Z"
    };

    private static final Color[] TEXT_COLORS = {
            new Color(0x0F172A), new Color(0x0F766E), new Color(0x7C3AED),
            new Color(0xB91C1C), new Color(0xA16207), new Color(0x1D4ED8)
    };

    @Override
    public CaptchaType type() {
        return CaptchaType.CLICK;
    }

    @Override
    public GenerationResult generate(BufferedImage background, CaptchaConfig config, Random random) {
        return generate(background, config, random, GenerateOptions.DEFAULT);
    }

    @Override
    public GenerationResult generate(BufferedImage background, CaptchaConfig config,
                                     Random random, GenerateOptions options) {
        int width = background.getWidth();
        int height = background.getHeight();
        int fontSize = Math.min(config.getClick().getFontSize(), Math.max(18, height / 5));
        int count = Math.max(2, Math.min(config.getClick().getWordCount(), 5));
        // 画面展示数：>= 需要点击数，多出的为干扰项；display-count 为 0 时与点击数相同
        int display = config.getClick().getDisplayCount() > 0
                ? Math.max(count, Math.min(config.getClick().getDisplayCount(), 8))
                : count;

        String charset = resolveCharset(options == null ? null : options.clickCharset(),
                config.getClick().getCharset());
        boolean cjk = CHARSET_ZH.equals(charset);
        String[] pool = cjk ? CJK_POOL : LATIN_POOL;
        List<String> picked = pickWords(pool, display, random);

        Font font = resolveFont(config.getClick(), fontSize, cjk);

        BufferedImage canvas = ImageSupport.copy(background);
        Graphics2D g = canvas.createGraphics();
        ImageSupport.applyQualityHints(g);
        FontRenderContext frc = g.getFontRenderContext();

        List<Placement> placements = new ArrayList<>();
        for (String word : picked) {
            Placement placement = placeWord(word, font, frc, width, height, placements, random);
            if (placement == null) {
                continue;
            }
            placements.add(placement);
            drawWord(g, font, placement, random);
        }

        // 打乱后取前 count 个作为“需要依次点击的字”，其余只是画面上的干扰项；
        // 若背景太挤导致实际放下的字少于 count，则以能放下的为准
        List<Placement> ordered = new ArrayList<>(placements);
        Collections.shuffle(ordered, random);
        int required = Math.min(count, ordered.size());

        List<String> words = new ArrayList<>();
        List<double[]> points = new ArrayList<>();
        for (Placement p : ordered.subList(0, required)) {
            words.add(p.word());
            points.add(new double[]{p.centerX(), p.centerY()});
        }
        g.dispose();

        String tip = cjk
                ? "请依次点击：" + String.join(" → ", words)
                : "Click in order: " + String.join(" → ", words);
        return GenerationResult.of(canvas, CaptchaAnswer.click(points),
                new CaptchaChallenge.ClickChallenge(words, charset), tip);
    }

    /**
     * 决定本次绘制使用的字体，优先级：
     * <ol>
     *   <li>{@code click.font} —— 代码里传入的 {@link Font} 对象（最高）；</li>
     *   <li>{@code click.fontPath} —— 字体文件（绝对/相对路径或 {@code classpath:} 资源，ttf/otf）；</li>
     *   <li>{@code click.fontFamily} —— 字体族名（可由 yaml 配置）；</li>
     *   <li>默认：SansSerif 逻辑字体（JVM 自动映射平台字体）。</li>
     * </ol>
     * 不做任何字体能力校验：设置了外部字体就直接使用（字号按参数派生，
     * Font 对象保留传入样式，字体文件加粗绘制）；仅当字体文件本身加载失败
     * （不存在 / 格式不对）时回退默认字体。
     */
    Font resolveFont(CaptchaConfig.Click click, int size, boolean cjk) {
        Font custom = click.getFont();
        if (custom != null) {
            return custom.deriveFont((float) size);
        }

        String path = click.getFontPath();
        if (path != null && !path.isBlank()) {
            Font base = ImageSupport.loadFont(path);
            if (base != null) {
                return base.deriveFont(Font.BOLD, (float) size);
            }
            log.warning("自定义字体文件「" + path + "」加载失败，点选验证码回退到默认字体");
        }

        String family = click.getFontFamily();
        if (family != null && !family.isBlank()) {
            // 不校验是否安装：未知族名由 JVM 自行映射（通常落到 Dialog）
            return new Font(family.trim(), Font.BOLD, size);
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, size);
    }

    /**
     * 决定本次使用的字符集：请求参数优先，其次全局配置，不做环境校验。
     */
    private String resolveCharset(String override, String configured) {
        String raw = override != null && !override.isBlank() ? override : configured;
        String normalized = normalizeCharset(raw);
        if (CHARSET_ZH.equals(normalized) || CHARSET_EN.equals(normalized)) {
            return normalized;
        }
        log.warning("未知的点选字符集配置「" + raw + "」，按默认值 " + CHARSET_ZH + " 处理");
        return CHARSET_ZH;
    }

    private String normalizeCharset(String raw) {
        if (raw == null) {
            return CHARSET_ZH;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("zh") || value.equals("cn") || value.equals("chinese")
                || value.equals("han") || value.equals("zh_cn")) {
            return CHARSET_ZH;
        }
        if (value.startsWith("en") || value.equals("latin") || value.equals("english")) {
            return CHARSET_EN;
        }
        return value;
    }

    private List<String> pickWords(String[] pool, int count, Random random) {
        List<String> all = new ArrayList<>(Arrays.asList(pool));
        Collections.shuffle(all, random);
        Set<String> picked = new LinkedHashSet<>(all.subList(0, Math.min(count, all.size())));
        return new ArrayList<>(picked);
    }

    private Placement placeWord(String word, Font font, FontRenderContext frc,
                                int width, int height, List<Placement> existing, Random random) {
        GlyphVector probe = font.createGlyphVector(frc, word);
        Rectangle2D bounds = probe.getLogicalBounds();
        double textWidth = Math.max(10, bounds.getWidth());
        double textHeight = Math.max(10, font.getSize() * 1.1);

        int pad = 10;
        for (int attempt = 0; attempt < 240; attempt++) {
            double cx = pad + textWidth / 2 + random.nextDouble() * Math.max(1, width - textWidth - pad * 2);
            double cy = pad + textHeight / 2 + random.nextDouble() * Math.max(1, height - textHeight - pad * 2);
            boolean overlap = false;
            for (Placement p : existing) {
                if (Math.hypot(p.centerX() - cx, p.centerY() - cy) < font.getSize() * 1.8) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) {
                continue;
            }
            double rotate = Math.toRadians(random.nextInt(41) - 20);
            return new Placement(word, cx, cy, rotate);
        }
        return null;
    }

    /**
     * 用指定字体绘制一个字（白色描边 + 随机彩色填充）。
     * 字体必须显式传入：Graphics2D 的默认字体不随配置变化，用 {@code g.getFont()}
     * 会导致外部字体（{@code font-path} 等）只影响测量、不影响绘制。
     */
    void drawWord(Graphics2D g, Font font, Placement placement, Random random) {
        AffineTransform origin = g.getTransform();
        g.rotate(placement.rotate(), placement.centerX(), placement.centerY());

        GlyphVector glyphVector = font.createGlyphVector(g.getFontRenderContext(), placement.word());
        Rectangle2D bounds = glyphVector.getLogicalBounds();
        double x = placement.centerX() - bounds.getWidth() / 2;
        double y = placement.centerY() + (bounds.getHeight() * 0.72) / 2;
        Shape outline = glyphVector.getOutline((float) x, (float) y);

        // 白色描边保证在任意背景上可读
        g.setColor(new Color(255, 255, 255, 232));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outline);

        g.setColor(TEXT_COLORS[random.nextInt(TEXT_COLORS.length)]);
        g.fill(outline);

        g.setTransform(origin);
    }

    record Placement(String word, double centerX, double centerY, double rotate) {
    }
}
