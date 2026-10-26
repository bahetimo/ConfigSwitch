package com.bteamore.configswitch.client.gui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConfigIconButton 的四条纯规则：三态贴图路径、锚点按钮正上方的 y、行距、左下角兜底；
 * 外加一条资源检查：三张贴图要真的在 resources 里，路径与文件名对不上就在这里红。
 * 贴图路径与 assets/configswitch/textures/gui/sprites 下的文件名一一对应，改文件名的同时要改这里。
 */
public class ConfigIconButtonTest {

    // ---------- 三态贴图 ----------

    // 常态：可用且没悬停
    @Test
    void testNormalTextureWhenActiveAndNotHovered() {
        assertEquals("textures/gui/sprites/cs_button.png", ConfigIconButton.texturePath(true, false));
    }

    // 悬停：可用且鼠标在上面
    @Test
    void testHighlightedTextureWhenActiveAndHovered() {
        assertEquals("textures/gui/sprites/cs_button_highlighted.png", ConfigIconButton.texturePath(true, true));
    }

    // 禁用压过悬停：不可用时无论鼠标在哪都是禁用图
    @Test
    void testDisabledTextureWinsOverHovered() {
        assertEquals("textures/gui/sprites/cs_button_disabled.png", ConfigIconButton.texturePath(false, true));
        assertEquals("textures/gui/sprites/cs_button_disabled.png", ConfigIconButton.texturePath(false, false));
    }

    // ---------- 锚点按钮正上方一行 ----------

    // 用主菜单量到的行距：正上方一行的 y 就是锚点 y 减行距
    @Test
    void testAboveYIsOneRowAboveAnchor() {
        assertEquals(24, ConfigIconButton.aboveY(48, ConfigIconButton.DEFAULT_ROW_STEP));
        assertEquals(76, ConfigIconButton.aboveY(100, ConfigIconButton.DEFAULT_ROW_STEP));
    }

    // 量到多少就按多少上移，不写死 24
    @Test
    void testAboveYUsesMeasuredRowStep() {
        assertEquals(28, ConfigIconButton.aboveY(48, 20));
        assertEquals(13, ConfigIconButton.aboveY(40, 27));
    }

    // ---------- 行距 ----------

    // 正常量到的是两行的 y 差
    @Test
    void testRowStepIsDistanceBetweenRows() {
        assertEquals(24, ConfigIconButton.rowStep(48, 72));
        assertEquals(26, ConfigIconButton.rowStep(100, 126));
    }

    // 量不到（两行重合或顺序反了）落回默认值，不能把 0 或负数当行距用
    @Test
    void testRowStepFallsBackWhenRowsDoNotOrder() {
        assertEquals(ConfigIconButton.DEFAULT_ROW_STEP, ConfigIconButton.rowStep(48, 48));
        assertEquals(ConfigIconButton.DEFAULT_ROW_STEP, ConfigIconButton.rowStep(72, 48));
    }

    // 默认行距就是原版主菜单的行距：按钮高 20 加 4 的间隙
    @Test
    void testDefaultRowStepLeavesFourPixelGap() {
        assertEquals(24, ConfigIconButton.DEFAULT_ROW_STEP);
        assertEquals(4, ConfigIconButton.DEFAULT_ROW_STEP - ConfigIconButton.SIZE);
    }

    // ---------- 兜底：主菜单左下角 ----------

    // 认不出语言按钮时贴左下角：x 固定边距，y 从屏幕底边往上留出按钮高和边距
    @Test
    void testBottomLeftHugsScreenCorner() {
        assertEquals(4, ConfigIconButton.MARGIN);
        assertEquals(216, ConfigIconButton.bottomLeftY(240));
        assertEquals(314, ConfigIconButton.bottomLeftY(338));
    }

    // 屏幕变矮也跟着上移，按钮底边始终留在屏幕内
    @Test
    void testBottomLeftStaysInsideScreen() {
        int screenHeight = 120;
        assertTrue(ConfigIconButton.bottomLeftY(screenHeight) + ConfigIconButton.SIZE <= screenHeight);
    }

    // ---------- 贴图真的在 resources 里 ----------

    // 三种状态各自的贴图都要能在 classpath 上取到：路径改了、文件名对不上，这条就红
    @Test
    void testTexturesExistOnClasspath() {
        assertTextureExists(true, false);
        assertTextureExists(true, true);
        assertTextureExists(false, true);
    }

    // 取不到时把完整资源路径带进断言消息，省得去猜是哪张
    private static void assertTextureExists(boolean active, boolean hovered) {
        String path = "/assets/configswitch/" + ConfigIconButton.texturePath(active, hovered);
        assertNotNull(ConfigIconButton.class.getResource(path), path);
    }
}
