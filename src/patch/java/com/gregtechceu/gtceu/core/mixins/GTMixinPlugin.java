package com.gregtechceu.gtceu.core.mixins;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.LoadingModList;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 覆盖 GTCEu 原版 GTMixinPlugin，修一个启动期崩溃。
 *
 * <h2>问题</h2>
 * 原版 {@code isModLoaded} 只防了 {@code ModList.get() == null}：
 * <pre>
 *   if (ModList.get() == null)
 *       return LoadingModList.get().getModFileById(id) != null;   // ← 这里仍会炸
 *   return ModList.get().isLoaded(id);
 * </pre>
 * 而它的 {@code static {}} 块会调用 {@code addModCompatMixin(...)} → {@code isModLoaded(...)}。
 * 于是 Mixin 只要**实例化这个插件**，静态初始化就会去碰 FML 的三个类。
 *
 * <p>Forge 在 {@code FMLLoader.beforeStart} → {@code DisplayWindow.updateModuleReads} 阶段会执行
 * <pre>
 *   Class.forName(forgeModule, "net.minecraftforge.client.loading.ForgeLoadingOverlay")
 * </pre>
 * 这次类解析会触发 Mixin 变换 → 实例化本插件 → 静态初始化。**但此时 LoadingModList 还没有
 * 对 {@code gtceu.patched} 模块开放**，于是抛出：
 * <pre>
 *   java.lang.ClassNotFoundException: net.minecraftforge.fml.ModList
 *     at GTMixinPlugin.isModLoaded(GTMixinPlugin.java:90)
 *     ...
 *     at DisplayWindow.updateModuleReads(DisplayWindow.java:615)
 *     at FMLLoader.beforeStart(FMLLoader.java:216)
 * </pre>
 * 整个启动链因此中断（表现为 MixinInitialisationError 之类的连带报错，容易误判成"配置读不了"）。
 *
 * <h2>修法</h2>
 * 用 try/catch 包住两种查询，**任何** FML 尚未就绪的异常都安全降级为 {@code false}
 * （即"该可选前置未安装"）。插件实例会被 Mixin 缓存，稍后 FML 准备就绪、真正调用
 * {@code shouldApplyMixin} 时，{@code ModList} 已可用，兼容 mixin 照常生效。
 *
 * <p>注意：本类必须与 GTCEu 原版**同包同名**，作为 patch 类覆盖进 official jar。
 */
public class GTMixinPlugin implements IMixinConfigPlugin {

    private static final String MIXIN_PACKAGE = "com.gregtechceu.gtceu.core.mixins.";
    private static final Map<String, String> MOD_COMPAT_MIXINS = new HashMap<>();
    private static final String DEV_PACKAGE = "dev.";
    private static final String DATAGEN_PACKAGE = "datagen.";

    static {
        MOD_COMPAT_MIXINS.put("roughlyenoughitems", "rei.");
        addModCompatMixin("emi");
        addModCompatMixin("jei");
        addModCompatMixin("top");
        addModCompatMixin("ftbchunks");
        addModCompatMixin("xaerominimap");
        addModCompatMixin("xaeroworldmap");
    }

    public GTMixinPlugin() {
        // 原版无逻辑构造器
    }

    @Override
    public void onLoad(String mixinPackage) {
        // 原版为空实现
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(MIXIN_PACKAGE)) {
            return true;
        }
        String name = mixinClassName.substring(MIXIN_PACKAGE.length());

        // dev 包下的 mixin 只在开发环境生效；datagen 另需对应 launch handler
        if (name.startsWith(DEV_PACKAGE)) {
            if (net.minecraftforge.fml.loading.FMLLoader.isProduction()) {
                return false;
            }
            String sub = name.substring(DEV_PACKAGE.length());
            if (sub.startsWith(DATAGEN_PACKAGE)) {
                return net.minecraftforge.fml.loading.FMLLoader.getLaunchHandler().isData();
            }
            return true;
        }

        // 可选前置的兼容 mixin：只有对应 mod 装了才应用
        for (Map.Entry<String, String> entry : MOD_COMPAT_MIXINS.entrySet()) {
            if (name.startsWith(entry.getValue())) {
                return isModLoaded(entry.getKey());
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 原版为空实现
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 原版为空实现
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 原版为空实现
    }

    private static void addModCompatMixin(String modId) {
        MOD_COMPAT_MIXINS.put(modId, modId + ".");
    }

    /**
     * 与 GTCEu 原版语义一致，但**任何** FML 未就绪的异常都降级为 false。
     *
     * <p>原版的缺陷：只判了 {@code ModList.get() == null}，却在同一分支里直接使用
     * {@code LoadingModList.get()}。启动早期（{@code beforeStart} → 解析 ForgeLoadingOverlay 时）
     * 该类的模块可见性还没配好，会对 {@code gtceu.patched} 抛 ClassNotFoundException。
     */
    private static boolean isModLoaded(String modId) {
        try {
            ModList modList = ModList.get();
            if (modList != null) {
                return modList.isLoaded(modId);
            }
            LoadingModList loading = LoadingModList.get();
            return loading != null && loading.getModFileById(modId) != null;
        } catch (Throwable t) {
            // FML 尚未就绪（含 NoClassDefFoundError / ClassNotFoundException / NPE 等）
            // —— 视为该可选前置未安装，安全降级。稍后 shouldApplyMixin 被正式调用时
            //    ModList 已可用，兼容 mixin 照常判定。
            return false;
        }
    }
}
