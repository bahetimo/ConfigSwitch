package com.bteamore.configswitch.client.gui.widget;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 主菜单上的图标按钮：不显示文字，按状态各画一张贴图；点击、tooltip、无障碍朗读都沿用原版按钮。
 * 位置由调用方给出：主菜单入口放在语言按钮正上方，行距取主菜单自己的行距（见 aboveY、rowStep）；
 * 认不出语言按钮时退到左下角（见 bottomLeftY）。
 */
public class ConfigIconButton extends ButtonWidget {
    // 原版主菜单默认行距：单人游戏/多人游戏/Realms 相邻两行差 24（按钮高 20 + 间隙 4）
    public static final int DEFAULT_ROW_STEP = 24;
    // 与语言按钮等长
    public static final int SIZE = 20;
    // 贴屏幕边缘的边距，跟原版控件之间的间隙同宽
    public static final int MARGIN = 4;

    private static final String LABEL_KEY = "configswitch.button.open";
    private static final String TEXTURE_DIR = "textures/gui/sprites/";
    private static final String TEXTURE_NAME = "cs_button";

    public ConfigIconButton(int x, int y, int size, Runnable onPress) {
        super(x, y, size, size, Text.translatable(LABEL_KEY), button -> onPress.run(), ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        // icon 没有文字，提示必须挂在 tooltip 上，文字走 lang
        this.setTooltip(Tooltip.of(Text.translatable(LABEL_KEY)));
    }

    // 锚点按钮正上方一行：y = 锚点 y − 行距
    public static int aboveY(int anchorY, int rowStep) {
        return anchorY - rowStep;
    }

    // 行距：相邻两行的 y 差；量不到（缺按钮或行重合）就落回原版默认值
    public static int rowStep(int upperRowY, int lowerRowY) {
        int step = lowerRowY - upperRowY;
        return step > 0 ? step : DEFAULT_ROW_STEP;
    }

    // 左下角的 y：贴屏幕底边，往上留出按钮高与边距
    public static int bottomLeftY(int screenHeight) {
        return screenHeight - SIZE - MARGIN;
    }

    // 状态对应的贴图路径：禁用优先，其次悬停，最后常态
    static String texturePath(boolean active, boolean hovered) {
        String state = !active ? "_disabled" : (hovered ? "_highlighted" : "");
        return TEXTURE_DIR + TEXTURE_NAME + state + ".png";
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        Identifier texture = Identifier.of("configswitch", texturePath(this.active, isMouseOver(mouseX, mouseY)));
        context.drawTexture(texture, getX(), getY(), 0, 0, getWidth(), getHeight(), getWidth(), getHeight());
    }
}
