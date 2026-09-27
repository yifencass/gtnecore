package org.ringclouds.gtnecore.research;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.ringclouds.gtnecore.Gtnecore;

/**
 * 研究系统的调试命令。
 *
 * <p>加它的理由很实际：研究进度存在存档里，验证"锁定是否生效"如果每轮都要
 * 重启客户端 + 重新投料，一轮要几分钟。有了命令就能在游戏里即时切换状态。
 *
 * <pre>
 * /gtnecore research list              列出全部研究及其完成状态
 * /gtnecore research complete &lt;id&gt;     标记完成
 * /gtnecore research revoke &lt;id&gt;       撤销完成
 * /gtnecore research inject            手动触发一次配方注入（调试用）
 * </pre>
 *
 * <p>权限：{@code complete} / {@code revoke} / {@code inject} 需要 OP（level 2），
 * {@code list} 谁都能看。
 */
@Mod.EventBusSubscriber(modid = Gtnecore.MODID)
public final class ResearchCommands {

    private ResearchCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("gtnecore")
                .then(Commands.literal("research")
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("complete")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(ctx -> setCompleted(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"), true))))
                        .then(Commands.literal("revoke")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(ctx -> setCompleted(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"), false))))
                        .then(Commands.literal("inject")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> {
                                    ResearchRecipeInjector.inject();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("已触发研究配方注入，详见日志"), true);
                                    return 1;
                                }))));
    }

    private static int list(CommandSourceStack source) {
        var progress = ResearchProgress.get(source.getServer());
        var all = GtneResearch.all();
        source.sendSuccess(() -> Component.literal("== 研究清单（共 " + all.size() + " 项）=="), false);
        for (GtneResearch.ResearchCategory category : all) {
            boolean done = progress.isCompleted(category.id());
            String pre = category.prerequisites().isEmpty()
                    ? ""
                    : "  前置=" + category.prerequisites();
            source.sendSuccess(() -> Component.literal(
                    (done ? "  [已完成] " : "  [未完成] ")
                            + category.id() + "  —  " + category.displayName()
                            + "  管 " + category.membershipCount() + " 条配方" + pre), false);
        }
        return all.size();
    }

    private static int setCompleted(CommandSourceStack source, String rawId, boolean completed) {
        // 允许简写：不带命名空间时自动补 gtnecore:
        ResourceLocation id = rawId.contains(":")
                ? ResourceLocation.tryParse(rawId)
                : Gtnecore.id(rawId);
        if (id == null || !GtneResearch.exists(id)) {
            source.sendFailure(Component.literal("未知研究: " + rawId + "（用 /gtnecore research list 看清单）"));
            return 0;
        }
        var progress = ResearchProgress.get(source.getServer());
        if (completed) {
            progress.complete(id);
        } else {
            progress.revoke(id);
        }
        // 改完立刻重注入，让新加/移除的条件在本次会话内即时生效
        ResearchRecipeInjector.inject();
        source.sendSuccess(() -> Component.literal(
                (completed ? "已标记完成: " : "已撤销完成: ") + id), true);
        return 1;
    }
}
