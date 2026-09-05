package org.ringclouds.gtnecore.planner.gtceu;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.gui.GuiGraphics;

/**
 * JEI 配方渲染投影：把 JEI 的完整配方显示（输入槽、概率产物、EUt、
 * 时长、悬停 tooltip）画到我们自己的 GUI 区域，而非打开 JEI 界面。
 * 本类内部持有 JEI 类型，调用方（planner.client）只依赖 width/height/draw。
 */
public final class RecipeProjection {

    private final IRecipeLayoutDrawable<GTRecipe> drawable;
    private final int width;
    private final int height;

    RecipeProjection(IRecipeLayoutDrawable<GTRecipe> drawable) {
        this.drawable = drawable;
        this.width = drawable.getRect().getWidth();
        this.height = drawable.getRect().getHeight();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** 在屏幕全局坐标 (x,y) 绘制配方显示；鼠标悬停时附 JEI tooltip 覆盖层 */
    public void draw(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        drawable.setPosition(x, y);
        drawable.drawRecipe(guiGraphics, mouseX, mouseY);
        if (drawable.isMouseOver(mouseX, mouseY)) {
            drawable.drawOverlays(guiGraphics, mouseX, mouseY);
        }
    }
}
