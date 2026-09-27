package org.ringclouds.gtnecore.voltages;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * GTNEcore 的 22 档电压体系（0=ULV .. 21=MAX），全面重排：
 * UMV/SWV/GCV 插入 12-14 档（原 UXV/OpV/MAX 顺延到 15/18/21），电压 V[i] = 8 × 4^i。
 *
 * 安装方式：GTValuesMixin 在 {@link GTValues} 的 &lt;clinit&gt; 结束后、GTMachineUtilsMixin
 * 在 {@link GTMachineUtils} 的 &lt;clinit&gt; 结束后，用 Unsafe 覆写其 static final 字段。
 * 任何读取这些字段的类都必然先触发所属类的类初始化，因此覆写后所有读取方拿到的都是新表。
 *
 * 原生 GTCEu 内容（机器、部件）随 GTMachineUtils 的档位数组覆写自动延伸到 21 档；
 * GTNEcore 自己的方块（能量立方）使用 {@link #VN}/{@link #VNF} 阶梯表注册与显示。
 *
 * 注意：本类的静态初始化禁止读取 GTValues/GTMachineUtils（mixin 注入点在它们的
 * &lt;clinit&gt; 内，重入会导致类初始化死锁），所有表都用字面量硬编码。
 */
public final class GtneVoltages {

    /** 档位数量：0..21 共 22 档。 */
    public static final int TIER_COUNT = 22;

    /** 最高档索引（重排后 = 21）。安装给 GTValues.MAX（原版=14）。 */
    public static final int MAX_TIER = 21;

    /**
     * 电压值：V[i] = 8 × 4^i，i = 0..21（与 GTCEu VEX[0..21] 一致）。
     * 安装给 GTValues.V，GTM 原生与 GTNEcore 内容共享。
     */
    public static final long[] V = {
            8L, 32L, 128L, 512L, 2048L, 8192L, 32768L, 131072L, 524288L, 2097152L,
            8388608L, 33554432L, 134217728L, 536870912L, 2147483648L, 8589934592L,
            34359738368L, 137438953472L, 549755813888L, 2199023255552L,
            8796093022208L, 35184372088832L
    };

    // ---------------- 用户 22 档阶梯表（GTNEcore 方块注册与显示用） ----------------

    /** 档名（GTNEcore 方块注册名、方块标题的来源）。 */
    public static final String[] VN = {
            "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV",
            "UEV", "UIV", "UMV", "SWV", "GCV", "UXV", "QGV", "CYV", "OpV", "VEV", "SGV", "MAX"
    };

    /** 格式化档名（GTNEcore GUI 工具提示、档位选择器：颜色码 + 档名）。 */
    public static final String[] VNF = {
            "§8ULV", "§7LV", "§bMV", "§6HV", "§5EV", "§9IV", "§dLuV", "§cZPM", "§3UV", "§4UHV",
            "§aUEV", "§2UIV", "§eUMV", "§9§lSWV", "§c§lGCV", "§eUXV", "§d§lQGV", "§b§lCYV",
            "§9§lOpV", "§2§lVEV", "§6§lSGV", "§c§lMAX"
    };

    // ---------------- GTValues 安装表（22 项，全面重排） ----------------

    /** 安装给 GTValues.VN：12-14 改名为 UMV/SWV/GCV，15-21 = UXV/QGV/CYV/OpV/VEV/SGV/MAX。 */
    public static final String[] VN_INSTALL = {
            "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV",
            "UEV", "UIV", "UMV", "SWV", "GCV", "UXV", "QGV", "CYV", "OpV", "VEV", "SGV", "MAX"
    };

    /** 安装给 GTValues.VNF。 */
    public static final String[] VNF_INSTALL = {
            "§8ULV", "§7LV", "§bMV", "§6HV", "§5EV", "§9IV", "§dLuV", "§cZPM", "§3UV", "§4UHV",
            "§aUEV", "§2UIV", "§eUMV", "§9§lSWV", "§c§lGCV", "§eUXV", "§d§lQGV", "§b§lCYV",
            "§9§lOpV", "§2§lVEV", "§6§lSGV", "§c§lMAX"
    };

    /** 安装给 GTValues.VC（物品染色）。 */
    public static final int[] VC_INSTALL = {
            0xC80000, 0xDCDCDC, 0xFF6400, 0xFFFF1E, 0x808080, 0xF0F0F5, 0xE99797, 0x7EC3C4,
            0x7EB07E, 0xBF74C0, 0x0B5CFE, 0x914E91, 0x488748, 0x8C0000, 0x2828F5,
            0x488748, 0x00A8A8, 0xFF69B4, 0x8C0000, 0x9ACD32, 0xFF8C00, 0x2828F5
    };

    /** 安装给 GTValues.VCM（方块描边）。 */
    public static final int[] VCM_INSTALL = {
            0xC80000, 0xDCDCDC, 0xFF6400, 0xFFFF1E, 0x808080, 0xF0F0F5, 0xE99797, 0x7EC3C4,
            0x7EB07E, 0xBF74C0, 0x0B5CFE, 0x914E91, 0x488748, 0x8C0000, 0x2828F5,
            0x488748, 0x00A8A8, 0xFF69B4, 0x8C0000, 0x9ACD32, 0xFF8C00, 0x2828F5
    };

    /** 安装给 GTValues.ALL_TIERS：0..21（tiersBetween 与注册循环的基础）。 */
    public static final int[] ALL_TIERS_INSTALL = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21
    };

    /** 安装给 GTValues.VLVH（机器显示名前缀；0-14 原版，15-21 延续风格）。 */
    public static final String[] VLVH_INSTALL = {
            "Primitive", "Basic",
            "§bAdvanced", "§6Advanced", "§5Advanced",
            "§9Elite", "§dElite", "§cElite",
            "§3Ultimate", "§4Epic", "§aEpic", "§2Epic", "§eEpic",
            "§9§lLegendary", "§c§lMAX",
            "§3Ultimate", "§aEpic", "§2Epic", "§9§lLegendary", "§2§lLegendary", "§6§lEpic", "§c§lMAX"
    };

    /** 安装给 GTValues.VLVT（机器显示名后缀；0-14 原版，15-21 延续）。 */
    public static final String[] VLVT_INSTALL = {
            "§r", "§r", "§r", "II§r", "III§r", "§r", "II§r", "III§r",
            "§r", "§r", "II§r", "III§r", "IV§r", "§r", "§r",
            "§r", "II§r", "III§r", "IV§r", "§r", "II§r", "§r"
    };

    /** 安装给 GTValues.VCF（色码字符串）。 */
    public static final String[] VCF_INSTALL = {
            "§8", "§7", "§b", "§6", "§5", "§9", "§d", "§c", "§3", "§4", "§a", "§2", "§e",
            "§9§l", "§c§l", "§e", "§d§l", "§b§l", "§9§l", "§2§l", "§6§l", "§c§l"
    };

    /** 安装给 GTValues.VOLTAGE_NAMES（长名；0-14 原版，15-21 按档名释义，风格沿袭 GTCEu 的"形容词 + Voltage"）。 */
    public static final String[] VOLTAGE_NAMES_INSTALL = {
            "Ultra Low Voltage", "Low Voltage", "Medium Voltage", "High Voltage", "Extreme Voltage",
            "Insane Voltage", "Ludicrous Voltage", "ZPM Voltage", "Ultimate Voltage", "Ultra High Voltage",
            "Ultra Excessive Voltage", "Ultra Immense Voltage", "Ultimate Matter Voltage", "Stellar Warp Voltage",
            "Galactic Core Voltage", "Ultra eXtreme Voltage", "Quantum Gravity Voltage", "Calabi-Yau Voltage",
            "Overpowered Voltage", "Vacuum Expectation Voltage", "SuperGravity Voltage", "Maximum Voltage"
    };

    /** 安装给 GTValues.LVT（罗马数字；量子仓/箱等使用）。 */
    public static final String[] LVT_INSTALL = {
            "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV",
            "XV", "XVI", "XVII", "XVIII", "XIX", "XX", "XXI"
    };

    /**
     * 安装给 GTValues.VH（V/2）。0-14 原值；15-21 档真实值超 int 上限，
     * 用 Integer.MAX_VALUE 钳制——配方生成器在 15+ 档索引时不再越界，
     * 生成 EUt=2³¹ 的配方（15+ 档机器可正常处理，正确的档位配方由 GTNEcore 后续补充）。
     */
    public static final int[] VH_INSTALL = {
            4, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 1048576, 4194304, 16777216,
            67108864, 268435456, 1073741824,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE
    };

    /** 安装给 GTValues.VA（线损调整电压 V×15/16）。同上，15-21 档钳制。 */
    public static final int[] VA_INSTALL = {
            7, 30, 120, 480, 1920, 7680, 30720, 122880, 491520, 1966080, 7864320,
            31457280, 125829120, 503316480, 2013265920,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE
    };

    /** 安装给 GTValues.VHA（VA/2）。同上，15-21 档钳制。 */
    public static final int[] VHA_INSTALL = {
            3, 15, 60, 240, 960, 3840, 15360, 61440, 245760, 983040, 3932160, 15728640,
            62914560, 251658240, 1006632960,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE
    };

    private static final Unsafe UNSAFE = unsafe();
    private static final Logger LOGGER = LogUtils.getLogger();

    private GtneVoltages() {
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("GTNEcore: cannot access sun.misc.Unsafe", e);
        }
    }

    /** 覆写 GTValues 的 static final 字段。由 GTValuesMixin 在 clinit 结束后调用。 */
    public static void install() {
        putObject(GTValues.class, "V", V);
        putObject(GTValues.class, "VN", VN_INSTALL);
        putObject(GTValues.class, "VNF", VNF_INSTALL);
        putObject(GTValues.class, "VC", VC_INSTALL);
        putObject(GTValues.class, "VCM", VCM_INSTALL);
        putInt(GTValues.class, "TIER_COUNT", TIER_COUNT);
        putObject(GTValues.class, "ALL_TIERS", ALL_TIERS_INSTALL);
        putObject(GTValues.class, "VLVH", VLVH_INSTALL);
        putObject(GTValues.class, "VLVT", VLVT_INSTALL);
        putObject(GTValues.class, "VCF", VCF_INSTALL);
        putObject(GTValues.class, "VOLTAGE_NAMES", VOLTAGE_NAMES_INSTALL);
        putObject(GTValues.class, "LVT", LVT_INSTALL);
        // MAX：原版=14，22 档体系下最高档是 21。漏掉它会导致 GTRecipeWidget.setTier
        // 的 Mth.clamp(tier, minTier, MAX) 把超压配方档位压回 14（显示 GCV）。
        putInt(GTValues.class, "MAX", MAX_TIER);
        // 注意：VH/VA/VHA 已由 GTCEu patch 层升级为 long[]（22 档真实值），
        // 这里不再覆写（否则会把 long[] 换回 int[] 引发类型冲突）。
        LOGGER.info("GTNEcore: installed 22-tier voltage table (TIER_COUNT={})", TIER_COUNT);
    }

    /** 覆写 GTMachineUtils 的 5 个缓存档位数组。由 GTMachineUtilsMixin 在 clinit 结束后调用。 */
    public static void installMachineUtils() {
        putObject(GTMachineUtils.class, "ALL_TIERS", ALL_TIERS_INSTALL);
        putObject(GTMachineUtils.class, "ELECTRIC_TIERS", electricTiers());
        putObject(GTMachineUtils.class, "HIGH_TIERS", highTiers());
        putObject(GTMachineUtils.class, "MULTI_HATCH_TIERS", multiHatchTiers());
        putObject(GTMachineUtils.class, "DUAL_HATCH_TIERS", dualHatchTiers());
        LOGGER.info("GTNEcore: extended GTMachineUtils tier arrays to 21");
    }

    /** ELECTRIC_TIERS：LV(1)..MAX(21)，原 1..13。 */
    private static int[] electricTiers() {
        return range(1, 21);
    }

    /** HIGH_TIERS：IV(4)..21，原 4..13。 */
    private static int[] highTiers() {
        return range(4, 21);
    }

    /** MULTI_HATCH_TIERS：EV(4)..21，原 4..14。 */
    private static int[] multiHatchTiers() {
        return range(4, 21);
    }

    /** DUAL_HATCH_TIERS：LuV(6)..21，原 6..14。 */
    private static int[] dualHatchTiers() {
        return range(6, 21);
    }

    private static int[] range(int from, int to) {
        int[] tiers = new int[to - from + 1];
        for (int i = 0; i < tiers.length; i++) {
            tiers[i] = from + i;
        }
        return tiers;
    }

    private static void putObject(Class<?> owner, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            UNSAFE.putObject(owner, UNSAFE.staticFieldOffset(field), value);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("GTNEcore: missing field " + owner.getName() + "." + name, e);
        }
    }

    private static void putInt(Class<?> owner, String name, int value) {
        try {
            Field field = owner.getDeclaredField(name);
            UNSAFE.putInt(owner, UNSAFE.staticFieldOffset(field), value);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("GTNEcore: missing field " + owner.getName() + "." + name, e);
        }
    }
}
