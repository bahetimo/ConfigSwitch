package com.bteamore.configswitch.client.gui;

import com.bteamore.configswitch.client.ConfigDiscovery;
import com.bteamore.configswitch.client.gui.widget.ModGroupsWidget;
import com.bteamore.configswitch.core.SyncRequest;
import com.bteamore.configswitch.core.SyncOutcome;
import com.bteamore.configswitch.core.SyncReport;
import com.bteamore.configswitch.discovery.ModGroup;
import com.bteamore.configswitch.manager.StateManager;
import com.bteamore.configswitch.repo.ConfigPathResolver;
import com.bteamore.configswitch.repo.ConfigPaths;
import com.bteamore.configswitch.util.Time;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ConfigScreen extends Screen {
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;
    private static final int DONE_BUTTON_WIDTH = 60;
    private static final int BACKUP_BUTTON_WIDTH = 60;
    private static final int BOTTOM_MARGIN = 12;
    private static final int TITLE_Y = 15;
    private static final int MARGIN_X = 20;
    private static final int LIST_TOP = 40;
    private static final int LIST_BOTTOM_GAP = 68;
    private static final int ITEM_HEIGHT = 46;
    private static final int SEARCH_WIDTH = 200;
    private static final int SEARCH_HEIGHT = 20;
    private static final int SEARCH_GAP = 8;
    private static final int QUICK_BUTTON_WIDTH = 50;
    private static final int QUICK_BUTTON_GAP = 4;

    private ModGroupsWidget listWidget;
    private TextFieldWidget searchField;
    private ClickableWidget fetchButton;
    private ClickableWidget pushButton;
    private ClickableWidget doneButton;
    private ClickableWidget backupButton;

    private List<ModGroup> allGroups;
    private final Set<String> selectedModIds = new HashSet<>();
    private String searchText;
    private String message;

    private final Path gameDir = MinecraftClient.getInstance().runDirectory.toPath();

    private final StateManager stateManager = StateManager.getInstance();
    private final ConfigDiscovery discovery = new ConfigDiscovery(gameDir);
    private final ConfigPathResolver resolver = new ConfigPathResolver(gameDir);
    private SyncReport lastReport;

    public ConfigScreen() {
        super(Text.literal("Config Switch"));
    }

    @Override
    protected void init() {
        this.allGroups = (allGroups == null) ? this.discovery.discoverLocal().stream()
                .filter(group -> !ModGroup.UNCATEGORIZED_ID.equals(group.getModId()))
                .toList() : allGroups;
        // 中部列表区域：左右留边距，占满标题与底部搜索行之间的空间
        // mod配置列表
        int listWidth = this.width - MARGIN_X * 2;
        int listHeight = this.height - LIST_TOP - LIST_BOTTOM_GAP;
        this.listWidget = new ModGroupsWidget(MinecraftClient.getInstance(), listWidth, listHeight, LIST_TOP, ITEM_HEIGHT);
        this.listWidget.setX(MARGIN_X);

        this.refreshList();

        // 搜索行：左侧搜索框，右侧快捷按钮
        int searchY = this.height - BOTTOM_MARGIN - BUTTON_HEIGHT - SEARCH_GAP - SEARCH_HEIGHT;
        int quickButtonX = this.width - MARGIN_X - (QUICK_BUTTON_WIDTH * 3 + QUICK_BUTTON_GAP * 2);
        // 窗口过窄时收缩搜索框，避免与快捷按钮重叠
        int searchWidth = Math.min(SEARCH_WIDTH, quickButtonX - MARGIN_X - SEARCH_GAP);
        // 搜索框
        this.searchField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, MARGIN_X, searchY, searchWidth, SEARCH_HEIGHT, Text.literal("搜索"));
        this.searchField.setPlaceholder(Text.literal("搜索…"));
        this.searchField.setText((this.searchText != null) ? this.searchText : "");
        this.searchField.setChangedListener(searchText -> {
            this.searchText = searchText;
            this.refreshList();
        });

        // 快捷按钮
        ButtonWidget allSelected = ButtonWidget.builder(Text.literal("全选"), btn -> setAllSelected(true))
                .dimensions(quickButtonX, searchY, QUICK_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        quickButtonX += QUICK_BUTTON_WIDTH + QUICK_BUTTON_GAP;
        ButtonWidget noneSelected = ButtonWidget.builder(Text.literal("全不选"), btn -> setAllSelected(false))
                .dimensions(quickButtonX, searchY, QUICK_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        quickButtonX += QUICK_BUTTON_WIDTH + QUICK_BUTTON_GAP;
        ButtonWidget invertSelected = ButtonWidget.builder(Text.literal("反选"), btn -> invertSelection())
                .dimensions(quickButtonX, searchY, QUICK_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();


        // 底部：Fetch / Push 居中并排，备份左下、完成右下
        int buttonY = this.height - BUTTON_HEIGHT - BOTTOM_MARGIN;
        int rowWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int startX = (this.width - rowWidth) / 2;

        // Fetch / Push 按钮
        this.fetchButton = ButtonWidget.builder(Text.literal("Fetch"), btn -> {
                    boolean load = onPress("fetch");
                    if (load) {
                        MinecraftClient.getInstance().options.load();
                    }
                })
                .dimensions(startX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.pushButton = ButtonWidget.builder(Text.literal("Push"), btn -> onPress("push"))
                .dimensions(startX + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        // 备份管理
        this.backupButton = ButtonWidget.builder(Text.literal("备份"),
                        btn -> MinecraftClient.getInstance().setScreen(new BackupScreen(Text.literal("备份管理"), this.gameDir, this)))
                .dimensions(MARGIN_X, buttonY, BACKUP_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        // 完成按钮
        this.doneButton = ButtonWidget.builder(Text.literal("完成"), btn -> this.close())
                .dimensions(this.width - MARGIN_X - DONE_BUTTON_WIDTH, buttonY, DONE_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        this.addDrawableChild(listWidget);
        this.addDrawableChild(searchField);
        this.addDrawableChild(fetchButton);
        this.addDrawableChild(pushButton);
        this.addDrawableChild(doneButton);
        this.addDrawableChild(backupButton);
        this.addDrawableChild(allSelected);
        this.addDrawableChild(noneSelected);
        this.addDrawableChild(invertSelected);
    }

    private boolean onPress(String name) {
        List<ModGroup> selectedGroups = this.allGroups.stream()
                .filter(group -> this.selectedModIds.contains(group.getModId()))
                .toList();

        if (selectedGroups.isEmpty()) {
            this.message = "没有选择任何模组!";
            return false;
        }
        this.message = null;

        String timeStamp = Time.timeString();

        List<ConfigPaths> configPaths = selectedGroups.stream()
                .flatMap(group -> group.getFiles().stream()
                        .map(path -> resolver.resolve(group.getModId(), path.getFileName().toString(), timeStamp, name)))
                .filter(Objects::nonNull)
                .toList();

        SyncRequest request = new SyncRequest(configPaths, (report) -> {
            this.lastReport = report;
            this.message = buildMessage(name, report);
        });
        stateManager.transition(name, request);
        return true;
    }

    private String buildMessage(String name, SyncReport report) {
        int success = report.count(SyncOutcome.SUCCESS);
        int failed = report.count(SyncOutcome.FAILED);
        int skipped = report.count(SyncOutcome.SKIPPED);

        String prefix = "push".equals(name) ? "Push" : "Fetch";

        if (report.results().isEmpty()) {
            return "没有可同步的内容";
        }
        if (success == 0 && failed == 0) {
            return "没有可同步的内容，" + skipped + " 个跳过";
        }

        String join = String.join("、", report.failedModIds());

        return String.format("%s 完成：%d 个模组", prefix, success) +
                (failed > 0 ? String.format("，%d 个失败（%s）", failed, join) : "") +
                (skipped > 0 ? String.format("，%d 个跳过", skipped) : "") +
                ((prefix.equals("Fetch") && involvesModConfig(report)) ? "；部分配置需重启游戏生效" : "");
    }

    private boolean involvesModConfig(SyncReport report) {
        return report.results().keySet().stream()
                .anyMatch(id -> !ModGroup.VANILLA_ID.equals(id));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
        // 暂时显示状态机的当前状态或 msg
        String msg = (this.message == null) ? ("当前状态：" + stateManager.getCurrentState().name()) : this.message;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(msg), this.width / 2, TITLE_Y + 12, 0xAAAAAA);
    }

    private List<ModGroup> visibleGroups() {
        if (this.searchText == null || this.searchText.isEmpty()) {
            return this.allGroups;
        }
        return this.allGroups.stream()
                .filter(group -> group.getModId().toLowerCase().contains(this.searchText.toLowerCase())) // 暂时只匹配modid
                .toList();
    }

    private void setAllSelected(boolean selected) {
        visibleGroups().forEach(group -> {
            if (selected) {
                this.selectedModIds.add(group.getModId());
            } else {
                this.selectedModIds.remove(group.getModId());
            }
        });
        this.refreshList();
    }

    private void invertSelection() {
        visibleGroups().forEach(group -> {
            if (!this.selectedModIds.remove(group.getModId())) {
                this.selectedModIds.add(group.getModId());
            }
        });
        this.refreshList();
    }

    private void refreshList() {
        this.listWidget.clearGroups();
        for (ModGroup group : visibleGroups()) {
            this.listWidget.addGroup(group.getModId(), group.getFiles().stream().map(Path::getFileName).map(Path::toString).toList(), this.selectedModIds.contains(group.getModId()), this::onToggle);
        }
    }

    private void onToggle(String modId, boolean checked) {
        if (checked) {
            this.selectedModIds.add(modId);
        } else {
            this.selectedModIds.remove(modId);
        }
    }
}
