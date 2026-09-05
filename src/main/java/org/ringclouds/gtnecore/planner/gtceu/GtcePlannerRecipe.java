package org.ringclouds.gtnecore.planner.gtceu;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.GTItems;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.ringclouds.gtnecore.planner.api.PlannerRecipe;
import org.ringclouds.gtnecore.planner.api.PlannerStack;

import java.util.ArrayList;
import java.util.List;

/**
 * GTRecipe → PlannerRecipe 适配。
 * 注意：RecipeHelper.getOutputItems 对概率产物按满量返回；
 * 后续如需期望值计算，要改用 Content.chance 分支（TODO）。
 */
final class GtcePlannerRecipe implements PlannerRecipe {

    private final GTRecipe recipe;
    private final String typeId;
    private final List<PlannerStack> inputs;
    private final List<PlannerStack> outputs;

    GtcePlannerRecipe(GTRecipe recipe, String typeId) {
        this.recipe = recipe;
        this.typeId = typeId;
        this.inputs = flatten(RecipeHelper.getInputItems(recipe), RecipeHelper.getInputFluids(recipe));
        this.outputs = flatten(RecipeHelper.getOutputItems(recipe), RecipeHelper.getOutputFluids(recipe));
    }

    private static List<PlannerStack> flatten(List<ItemStack> items, List<FluidStack> fluids) {
        List<PlannerStack> list = new ArrayList<>();
        if (items != null) {
            for (ItemStack s : items) {
                if (s != null && !s.isEmpty()) {
                    // 编程电路不算输入（配置电路，非真实物料）
                    if (s.getItem() == GTItems.PROGRAMMED_CIRCUIT.get()) continue;
                    list.add(PlannerStack.of(s));
                }
            }
        }
        if (fluids != null) {
            for (FluidStack f : fluids) {
                if (f != null && !f.isEmpty()) list.add(PlannerStack.of(f));
            }
        }
        return list;
    }

    @Override
    public List<PlannerStack> inputs() {
        return inputs;
    }

    @Override
    public List<PlannerStack> outputs() {
        return outputs;
    }

    /** EU/t = 输入EUt − 输出EUt（正=耗电；EnergyStack.EMPTY 的 getTotalEU=0） */
    @Override
    public long eut() {
        return recipe.getInputEUt().getTotalEU() - recipe.getOutputEUt().getTotalEU();
    }

    /** 发电量：输出能量 RecipeResult（EU/t，恒正；燃气轮机等能量配方走这里） */
    @Override
    public long outputEut() {
        return Math.max(0, recipe.getOutputEUt().getTotalEU());
    }

    @Override
    public int duration() {
        return recipe.duration;
    }

    @Override
    public String recipeTypeId() {
        return typeId;
    }

    /** 底层 GTRecipe（JEI 桥接用，包内可见） */
    GTRecipe gtRecipe() {
        return recipe;
    }

    @Override
    public String displayName() {
        for (PlannerStack s : outputs) {
            if (!s.isEmpty()) return s.idString() + (s.isFluid() ? " ×" + s.amount() : "");
        }
        return "配方(" + typeId + ")";
    }
}
