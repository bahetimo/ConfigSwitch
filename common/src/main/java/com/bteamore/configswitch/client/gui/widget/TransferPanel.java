package com.bteamore.configswitch.client.gui.widget;

import com.bteamore.configswitch.core.PackReport;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class TransferPanel extends AbstractWidget {
    public enum Kind {IMPORT, EXPORT}

    public enum Reason {OK, EMPTY, NOT_A_FILE, WRONG_EXTENSION, MULTIPLE}

    // 拖入结果：OK 时 packagePath 非空，否则只有 reason
    public record Drop(Path packagePath, Reason reason) {}

    // 遮罩与框
    private static final int SCRIM_COLOR = 0xC0101010;
    private static final int BOX_COLOR = 0xFF808080;
    private static final int ERROR_COLOR = 0xFFFF5555;
    private static final int HINT_COLOR = 0xFFFFFF;
    private static final int ERROR_TEXT_COLOR = 0xFF5555;
    private static final int BOX_MARGIN_X = 20;
    private static final int BOX_TOP = 40;
    private static final int BOX_BOTTOM_MARGIN = 40;
    // 提示行比框中心略高，给底部按钮行留位置
    private static final int HINT_ABOVE_CENTER = 10;

    // 按钮行：与设置屏同一套尺寸口径
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;
    private static final int BUTTON_INSET = 12;

    private static final String PACKAGE_SUFFIX = ".zip";
    private static final String DISMISS_IMPORT_KEY = "configswitch.button.cancel_import";
    private static final String DISMISS_RESULT_KEY = "configswitch.button.close";
    private static final String OPEN_FOLDER_KEY = "configswitch.button.open_folder";
    private static final String HINT_DROP_KEY = "configswitch.transfer.hint_drop";
    private static final String READING_KEY = "configswitch.transfer.reading";
    private static final String NOTHING_DROPPED_KEY = "configswitch.transfer.nothing_dropped";
    private static final String NOT_A_FILE_KEY = "configswitch.transfer.not_a_file";
    private static final String WRONG_EXTENSION_KEY = "configswitch.transfer.wrong_extension";
    private static final String MULTIPLE_KEY = "configswitch.transfer.multiple_files";
    private static final String IMPORT_DONE_KEY = "configswitch.transfer.import_done";
    private static final String EXPORT_DONE_KEY = "configswitch.transfer.export_done";
    private static final String NOT_A_PACKAGE_KEY = "configswitch.transfer.not_a_package";
    private static final String CORRUPTED_KEY = "configswitch.transfer.corrupted";
    private static final String IO_FAILED_KEY = "configswitch.transfer.io_failed";

    private final Font font;
    private final Runnable onDismiss;
    private final Button dismissButton;
    private final Button openButton;

    private Component hint;
    private boolean hintIsError;
    // 非空才显示「打开文件夹」：结果成功且目标确实存在
    private Path openTarget;
    private boolean waitingDrop;

    public TransferPanel(int screenWidth, int screenHeight, Runnable onDismiss) {
        super(0, 0, screenWidth, screenHeight, Component.empty());
        this.font = Minecraft.getInstance().font;
        this.onDismiss = onDismiss;
        this.dismissButton = Button.builder(Component.translatable(DISMISS_IMPORT_KEY), button -> this.dismiss())
                .bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.openButton = Button.builder(Component.translatable(OPEN_FOLDER_KEY), button -> this.openFolder())
                .bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.openButton.visible = false;
        // 先藏着：屏在 init() 里重建面板（窗口缩放会重跑），默认亮着会凭空冒出遮罩
        this.visible = false;
    }

    // 拖入的候选里挑出唯一可用的包；多选不猜顺序，直接拒绝
    static Drop pick(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return new Drop(null, Reason.EMPTY);
        }
        if (paths.size() > 1) {
            return new Drop(null, Reason.MULTIPLE);
        }
        Path first = paths.getFirst();
        if (first == null || !Files.isRegularFile(first)) {
            return new Drop(null, Reason.NOT_A_FILE);
        }
        // 只看后缀：包真假由 manifest 校验把关，这里不重复判断
        if (!first.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(PACKAGE_SUFFIX)) {
            return new Drop(null, Reason.WRONG_EXTENSION);
        }
        return new Drop(first, Reason.OK);
    }

    static String hintKey(Reason reason) {
        return switch (reason) {
            case EMPTY -> NOTHING_DROPPED_KEY;
            case NOT_A_FILE -> NOT_A_FILE_KEY;
            case WRONG_EXTENSION -> WRONG_EXTENSION_KEY;
            case MULTIPLE -> MULTIPLE_KEY;
            case OK -> HINT_DROP_KEY;
        };
    }

    static String resultKey(Kind kind, PackReport.outcome outcome) {
        return switch (outcome) {
            case SUCCESS -> kind == Kind.IMPORT ? IMPORT_DONE_KEY : EXPORT_DONE_KEY;
            case NOT_A_PACKAGE -> NOT_A_PACKAGE_KEY;
            case CORRUPTED -> CORRUPTED_KEY;
            case IO_FAILED -> IO_FAILED_KEY;
        };
    }

    // 失败不给「打开文件夹」：按钮点了也不会有反应（原版 open 失败只写日志），不如不亮
    static boolean canOpenFolder(PackReport report, Path openTarget) {
        return report != null && report.outcome() == PackReport.outcome.SUCCESS
                && openTarget != null && Files.isDirectory(openTarget);
    }

    // 进入等待拖入：亮出来，并清掉上一次的结果与按钮
    public final void beginDrop() {
        this.visible = true;
        this.waitingDrop = true;
        this.openTarget = null;
        this.dismissButton.setMessage(Component.translatable(DISMISS_IMPORT_KEY));
        this.setHint(Component.translatable(HINT_DROP_KEY), false);
        this.layoutButtons();
    }

    // 屏在 onFilesDrop 里调用：返回可导入的包；挑不出时已把原因写在框里
    // 成功时先把「正在读取」放上去：同步跑的状态机一般来不及渲染这一帧，异步化后才有意义
    public Path acceptDrop(List<Path> paths) {
        Drop drop = pick(paths);
        if (drop.reason() != Reason.OK) {
            this.setHint(Component.translatable(hintKey(drop.reason())), true);
            return null;
        }
        this.setHint(Component.translatable(READING_KEY, drop.packagePath().getFileName().toString()), false);
        return drop.packagePath();
    }

    // 结果：openTarget 由调用方给（导出给包所在目录，导入给仓库根这类去处）
    public void showResult(Kind kind, PackReport report, Path openTarget) {
        this.visible = true;
        this.waitingDrop = false;
        this.openTarget = canOpenFolder(report, openTarget) ? openTarget : null;
        this.dismissButton.setMessage(Component.translatable(DISMISS_RESULT_KEY));
        this.setHint(Component.translatable(resultKey(kind, report.outcome())), report.outcome() != PackReport.outcome.SUCCESS);
        this.layoutButtons();
    }

    public void dismiss() {
        this.visible = false;
        this.waitingDrop = false;
        if (this.onDismiss != null) {
            this.onDismiss.run();
        }
    }

    // 只有等待拖入时才该接管 onFilesDrop，其余时候拖文件进来要当没发生
    public boolean isWaitingDrop() {
        return this.visible && this.waitingDrop;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }
        int boxLeft = BOX_MARGIN_X;
        int boxRight = this.getWidth() - BOX_MARGIN_X;
        int boxTop = BOX_TOP;
        int boxBottom = this.getHeight() - BOX_BOTTOM_MARGIN;

        context.fill(0, 0, this.getWidth(), this.getHeight(), SCRIM_COLOR);
        // 4 次 fill 描边：比 drawBorder 麻烦一点，但边框粗细与颜色都在这里说清
        int boxColor = this.hintIsError ? ERROR_COLOR : BOX_COLOR;
        context.fill(boxLeft, boxTop, boxRight, boxTop + 1, boxColor);
        context.fill(boxLeft, boxBottom - 1, boxRight, boxBottom, boxColor);
        context.fill(boxLeft, boxTop + 1, boxLeft + 1, boxBottom - 1, boxColor);
        context.fill(boxRight - 1, boxTop + 1, boxRight, boxBottom - 1, boxColor);

        if (this.hint != null) {
            int hintY = (boxTop + boxBottom) / 2 - HINT_ABOVE_CENTER;
            context.drawCenteredString(this.font, this.hint, this.getWidth() / 2, hintY,
                    this.hintIsError ? ERROR_TEXT_COLOR : HINT_COLOR);
        }
        // 按钮最后画：遮罩和框都不能盖住它们
        this.dismissButton.render(context, mouseX, mouseY, delta);
        if (this.openButton.visible) {
            this.openButton.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible) {
            return false;
        }
        if (this.dismissButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.openButton.visible) {
            this.openButton.mouseClicked(mouseX, mouseY, button);
        }
        // 遮罩上的其余点击一律吃掉：下面的表单控件虽然被盖住，命中的仍是它们
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!this.visible) {
            return false;
        }
        // 按下再拖到按钮外放开要交回按钮自己判定，否则会留下按下态
        this.dismissButton.mouseReleased(mouseX, mouseY, button);
        if (this.openButton.visible) {
            this.openButton.mouseReleased(mouseX, mouseY, button);
        }
        return true;
    }

    // 面板自己只是一层遮罩与提示，要朗读的语义在里面的按钮上，这里不重复播报
    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }

    private void setHint(Component hint, boolean isError) {
        this.hint = hint;
        this.hintIsError = isError;
    }

    // 按钮行居中排：只有取消时它自己居中，结果成功时两个并排
    private void layoutButtons() {
        int buttonY = this.getHeight() - BOX_BOTTOM_MARGIN - BUTTON_HEIGHT - BUTTON_INSET;
        int count = this.openTarget != null ? 2 : 1;
        int rowWidth = BUTTON_WIDTH * count + BUTTON_GAP * (count - 1);
        int startX = (this.getWidth() - rowWidth) / 2;

        this.dismissButton.setPosition(startX, buttonY);
        this.openButton.setPosition(startX + BUTTON_WIDTH + BUTTON_GAP, buttonY);
        this.openButton.visible = this.openTarget != null;
    }

    private void openFolder() {
        if (this.openTarget == null) {
            return;
        }
        Util.getPlatform().openFile(this.openTarget.toFile());
    }
}
