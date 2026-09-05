package org.ringclouds.gtnecore.planner.gtceu;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeJEICategory;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import org.ringclouds.gtnecore.planner.api.PlannerRecipe;

import java.util.Optional;

/**
 * JEI 桥接：把 JEI 的配方显示**投影**到规划器自己的配方区域
 * （createRecipeLayoutDrawable → RecipeProjection），不打开 JEI 界面。
 * 官方 IModPlugin 方式获取 IJeiRuntime（JEI 本身是客户端模组，服务端不加载本类）。
 * 无 JEI 或非 GTCEu 配方时 createProjection 返回 null，调用方优雅降级。
 */
@JeiPlugin
public class PlannerJeiPlugin implements IModPlugin {

    private static IJeiRuntime RUNTIME;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation("gtnecore", "planner_jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        RUNTIME = jeiRuntime;
    }

    /** 创建 JEI 配方显示投影；失败（无 JEI/非 GTCEu 配方/异常）返回 null */
    public static RecipeProjection createProjection(PlannerRecipe r) {
        if (RUNTIME == null || !(r instanceof GtcePlannerRecipe g)) return null;
        GTRecipe recipe = g.gtRecipe();
        try {
            RecipeType<GTRecipe> type = GTRecipeJEICategory.TYPES.apply(recipe.recipeCategory);
            if (type == null) return null;
            IRecipeCategory<GTRecipe> category = RUNTIME.getRecipeManager().getRecipeCategory(type);
            if (category == null) return null;
            IFocusGroup focus = RUNTIME.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
            Optional<IRecipeLayoutDrawable<GTRecipe>> opt = RUNTIME.getRecipeManager()
                    .createRecipeLayoutDrawable(category, recipe, focus);
            return opt.map(RecipeProjection::new).orElse(null);
        } catch (Exception ex) {
            return null; // JEI 状态异常时降级
        }
    }
}
