package cn.localtool.smartcaptcha.core;

/**
 * 前端上报的行为证据（人机验证用）。
 *
 * <p>这些都是**启发式信号**，不是密码学证据：客户端能伪造任意数字，所以它只用来
 * 拦住「连鼠标事件都没有的裸脚本」，不能当作真实性证明。真正有成本门槛的是
 * {@link cn.localtool.smartcaptcha.core.support.ProofOfWork}。</p>
 *
 * @param moves    指针移动采样次数
 * @param clicks   指针按下次数（触摸点击也算）
 * @param keys     键盘事件次数（键盘用户 / 无障碍场景）
 * @param distance 指针累计移动距离（像素）
 * @param straight 首尾位移 / 累计距离，0~1，越接近 1 越像机器直线
 * @param pointer  最后一次交互的指针类型：mouse / touch / pen
 */
public record HumanTrace(int moves, int clicks, int keys, double distance, double straight, String pointer) {

    /** 空证据：什么都没发生（典型脚本请求就是这个样子）。 */
    public static final HumanTrace EMPTY = new HumanTrace(0, 0, 0, 0, 0, "");

    public static HumanTrace of(int moves, int clicks, int keys, double distance, double straight, String pointer) {
        return new HumanTrace(
                Math.max(0, moves),
                Math.max(0, clicks),
                Math.max(0, keys),
                Double.isFinite(distance) ? Math.max(0, distance) : 0,
                Double.isFinite(straight) ? Math.min(1, Math.max(0, straight)) : 0,
                pointer == null ? "" : pointer.trim().toLowerCase());
    }

    /** 是否为触摸交互（触摸点击天然没有移动轨迹，判定要放宽）。 */
    public boolean touch() {
        return "touch".equals(pointer) || "pen".equals(pointer);
    }
}
