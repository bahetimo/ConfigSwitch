package com.bteamore.configswitch.client.gui;

import com.bteamore.configswitch.client.ConfigDiscovery;
import com.bteamore.configswitch.client.gui.widget.ModGroupsWidget;
import com.bteamore.configswitch.core.SyncOutcome;
import com.bteamore.configswitch.core.SyncReport;
import com.bteamore.configswitch.core.SyncRequest;
import com.bteamore.configswitch.discovery.ModGroup;
import com.bteamore.configswitch.manager.StateManager;
import com.bteamore.configswitch.platform.Platforms;
import com.bteamore.configswitch.repo.ConfigPathResolver;
import com.bteamore.configswitch.repo.ConfigPaths;
import com.bteamore.configswitch.util.Log;
import com.bteamore.configswitch.util.Time;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
    private static final int SETTINGS_BUTTON_WIDTH = 60;
    private static final int SETTINGS_BUTTON_Y = 10;
    private static final int HELP_BUTTON_SIZE = 10;
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
    private EditBox searchField;
    private AbstractWidget fetchButton;
    private AbstractWidget pushButton;
    private AbstractWidget doneButton;
    private AbstractWidget backupButton;
    private AbstractWidget settingsButton;
    private Screen parentscreen;

    private List<ModGroup> allGroups;
    private ModGroup residueGroup;
    private final Set<String> selectedModIds = new HashSet<>();
    private String searchText;
    private Component message;
    private double scrollAmount;

    private final Path gameDir = Platforms.get().gameDir();

    private final StateManager stateManager = StateManager.getInstance();
    private final ConfigDiscovery discovery = new ConfigDiscovery(gameDir);
    private final ConfigPathResolver resolver = new ConfigPathResolver(gameDir);
    private SyncReport lastReport;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("configswitch.screen.config"));
        this.parentscreen = parent;
    }

    @Override
    protected void init() {
        if (this.allGroups == null){
            List<ModGroup> all = this.discovery.discoverLocal();
            this.allGroups = all.stream()
                    .filter(group -> !group.getModId().equals(ModGroup.RESIDUE_ID))
                    .toList();
            this.residueGroup = all.stream()
                    .filter(group -> group.getModId().equals(ModGroup.RESIDUE_ID))
                    .findFirst()
                    .orElse(null);
        }
        // 中部列表区域：左右留边距，占满标题与底部搜索行之间的空间
        // mod配置列表
        int listWidth = this.width - MARGIN_X * 2;
        int listHeight = this.height - LIST_TOP - LIST_BOTTOM_GAP;
        this.listWidget = new ModGroupsWidget(Minecraft.getInstance(), listWidth, listHeight, LIST_TOP, ITEM_HEIGHT);
        this.listWidget.setX(MARGIN_X);

        this.refreshList();

        // 搜索行：左侧搜索框，右侧快捷按钮
        int searchY = this.height - BOTTOM_MARGIN - BUTTON_HEIGHT - SEARCH_GAP - SEARCH_HEIGHT;
        int quickButtonX = this.width - MARGIN_X - (QUICK_BUTTON_WIDTH * 3 + QUICK_BUTTON_GAP * 2);
        // 窗口过窄时收缩搜索框，避免与快捷按钮重叠
        int searchWidth = Math.min(SEARCH_WIDTH, quickButtonX - MARGIN_X - SEARCH_GAP);
        // 搜索框
        this.searchField = new EditBox(Minecraft.getInstance().font, MARGIN_X, searchY, searchWidth, SEARCH_HEIGHT, Component.translatable("configswitch.search.placeholder"));
        this.searchField.setHint(Component.translatable("configswitch.search.placeholder"));
        this.searchField.setValue((this.searchText != null) ? this.searchText : "");
        this.searchField.setResponder(searchText -> {
            boolean wasNotSearching = (this.searchText == null || this.searchText.isEmpty());
            if (wasNotSearching && !searchText.isEmpty()){
                this.scrollAmount = this.listWidget.getScrollAmount();
            }
            this.searchText = searchText;
            this.refreshList();
        });

        // 快捷按钮
        Button allSelected = Button.builder(Component.translatable("configswitch.button.select_all"), btn -> setAllSelected(true))
                .bounds(quickButtonX, searchY, QUICK_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        quickButtonX += QUICK_BUTTON_WIDTH + QUICK_BUTTON_GAP;
        Button noneSelected = Button.builder(Component.translatable("configswitch.button.select_none"), btn -> setAllSelected(false))
                .bounds(quickButtonX, searchY, QUICK_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        quickButtonX += QUICK_BUTTON_WIDTH + QUICK_BUTTON_GAP;
        Button invertSelected = Button.builder(Component.translatable("configswitch.button.invert"), btn -> invertSelection())
                .bounds(quickButtonX, searchY, QUICK_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();


        // 底部：Fetch / Push 居中并排，备份左下、完成右下
        int buttonY = this.height - BUTTON_HEIGHT - BOTTOM_MARGIN;
        int rowWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int startX = (this.width - rowWidth) / 2;

        // Fetch / Push 按钮
        this.fetchButton = Button.builder(Component.translatable("configswitch.button.fetch"), btn -> {
                    boolean load = onPress("fetch");
                    if (load) {
                        Minecraft.getInstance().options.load();
                    }
                })
                .bounds(startX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.pushButton = Button.builder(Component.translatable("configswitch.button.push"), btn -> onPress("push"))
                .bounds(startX + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        // 备份管理
        this.backupButton = Button.builder(Component.translatable("configswitch.button.backup"),
                        btn -> Minecraft.getInstance().setScreen(new BackupScreen(Component.translatable("configswitch.screen.backup"), this.gameDir, this)))
                .bounds(MARGIN_X, buttonY, BACKUP_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        // 完成按钮
        this.doneButton = Button.builder(Component.translatable("configswitch.button.done"), btn -> this.onClose())
                .bounds(this.width - MARGIN_X - DONE_BUTTON_WIDTH, buttonY, DONE_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        // 右上角：设置入口
        this.settingsButton = Button.builder(Component.translatable("configswitch.button.settings"),
                        btn -> Minecraft.getInstance().setScreen(new SettingsScreen(Component.translatable("configswitch.screen.settings"), this, this.stateManager, this::onClassifyCommitted)))
                .bounds(this.width - MARGIN_X - SETTINGS_BUTTON_WIDTH, SETTINGS_BUTTON_Y, SETTINGS_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        this.addRenderableWidget(listWidget);
        this.addRenderableWidget(searchField);
        this.addRenderableWidget(fetchButton);
        this.addRenderableWidget(pushButton);
        this.addRenderableWidget(doneButton);
        this.addRenderableWidget(backupButton);
        this.addRenderableWidget(settingsButton);
        this.addRenderableWidget(allSelected);
        this.addRenderableWidget(noneSelected);
        this.addRenderableWidget(invertSelected);
    }

    private boolean onPress(String name) {
        List<ModGroup> selectedGroups = this.allGroups.stream()
                .filter(group -> this.selectedModIds.contains(group.getModId()))
                .toList();

        if (selectedGroups.isEmpty()) {
            this.message = Component.translatable("configswitch.message.no_selection");
            return false;
        }
        this.message = null;

        String timeStamp = Time.timeString();

        List<ConfigPaths> configPaths = selectedGroups.stream()
                .flatMap(group -> group.getFiles().stream()
                        .map(path -> resolver.resolve(group.getModId(), relativeOf(path), timeStamp, name)))
                .filter(Objects::nonNull)
                .toList();

        SyncRequest request = new SyncRequest(configPaths, (report) -> {
            this.lastReport = report;
            this.message = buildMessage(name, report);
        });
        stateManager.transition(name, request);
        return true;
    }

    private Component buildMessage(String name, SyncReport report) {
        int success = report.count(SyncOutcome.SUCCESS);
        int failed = report.count(SyncOutcome.FAILED);
        int skipped = report.count(SyncOutcome.SKIPPED);

        // i18n-dynamic: configswitch.message.{push,fetch}.head
        String headKey = "configswitch.message." + name + ".head";

        if (report.results().isEmpty()) {
            return Component.translatable("configswitch.message.nothing");
        }
        if (success == 0 && failed == 0) {
            // 全部跳过
            if (name.equals("fetch")) {
                return Component.translatable("configswitch.message.repo_empty");
            }
            return Component.translatable("configswitch.message.nothing")
                    .append(Component.translatable("configswitch.message.suffix.skipped", skipped));
        }

        MutableComponent msg = Component.translatable(headKey, success);
        if (failed > 0) {
            String join = String.join(Component.translatable("configswitch.label.list_sep").getString(), report.failedModIds());
            msg.append(Component.translatable("configswitch.message.suffix.failed", failed, join));
        }
        if (skipped > 0) {
            msg.append(Component.translatable("configswitch.message.suffix.skipped", skipped));
        }
        if ("fetch".equals(name) && involvesModConfig(report)) {
            msg.append(Component.translatable("configswitch.message.suffix.restart"));
        }
        return msg;
    }

    private boolean involvesModConfig(SyncReport report) {
        return report.results().keySet().stream()
                .anyMatch(id -> !ModGroup.VANILLA_ID.equals(id));
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
        // 没有消息时退化为显示状态机当前状态（开发期可见）
        Component msg = (this.message == null && Log.isDebugEnabled())
                ? Component.translatable("configswitch.message.state", stateManager.getCurrentState().name())
                : this.message;
        context.drawCenteredString(this.font, msg==null ? Component.empty() : msg, this.width / 2, TITLE_Y + 12, 0xAAAAAA);
        if (this.visibleGroups().isEmpty()){
            context.drawCenteredString(this.font, Component.translatable("configswitch.search.no_match"), this.width / 2, this.listWidget.getY() + this.listWidget.getHeight() / 2, 0xA0A0A0);
        }
        this.renderHelpIcon(context, mouseX, mouseY);
    }

    // 左上角：帮助图标（自绘 tooltip 风格外观，不参与点击交互；悬停显示 tips）
    private void renderHelpIcon(GuiGraphics context, int mouseX, int mouseY) {
        int x = MARGIN_X;
        int y = SETTINGS_BUTTON_Y + 8;
        int s = HELP_BUTTON_SIZE;
        // 原版 tooltip 背景：深色填充 + 亮暗渐变紫边框
        context.fill(x, y, x + s, y + s, 0xF0100010);
        context.fill(x, y, x + s, y + 1, 0x505000FF);
        context.fill(x, y + s - 1, x + s, y + s, 0x5028007F);
        context.fill(x, y + 1, x + 1, y + s - 1, 0x505000FF);
        context.fill(x + s - 1, y + 1, x + s, y + s - 1, 0x5028007F);
        context.drawCenteredString(this.font, "?", x + s / 2, y + (s - 8) / 2, 0xFFFFFF);
        // 悬停时用原版 tooltip 渲染显示说明书
        if (mouseX >= x && mouseX < x + s && mouseY >= y && mouseY < y + s) {
            context.renderComponentTooltip(this.font, List.of(
                    Component.translatable("configswitch.help.line.push"),
                    Component.translatable("configswitch.help.line.fetch"),
                    Component.translatable("configswitch.help.line.newpack"),
                    Component.translatable("configswitch.help.line.order"),
                    Component.translatable("configswitch.help.line.backup")
                    ), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentscreen);
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
        int count = (residueGroup == null) ? 0 : residueGroup.getFiles().size();
        this.listWidget.clearGroups();
        for (ModGroup group : visibleGroups()) {
            this.listWidget.addGroup(group.getModId(), group.getFiles().stream().map(Path::getFileName).map(Path::toString).toList(), this.selectedModIds.contains(group.getModId()), count, this::onToggle,this::onClassify);
        }

        if (this.searchText != null){
            if (this.searchText.isEmpty()) {
                this.listWidget.setScrollAmount(this.scrollAmount);
            } else {
                this.listWidget.setScrollAmount(0.0);
            }
        }
    }

    private void onClassify() {
        Minecraft.getInstance().setScreen(new CategorizeScreen(Component.translatable("configswitch.screen.categorize"), this, this.uncategorized(), this::onClassifyCommitted));
    }

    private void onClassifyCommitted() {
        this.discovery.invalidate();
        this.allGroups = null;
        this.residueGroup = null;
    }

    private void onToggle(String modId, boolean checked) {
        if (checked) {
            this.selectedModIds.add(modId);
        } else {
            this.selectedModIds.remove(modId);
        }
    }

    private List<String> uncategorized() {
        return this.allGroups.stream()
                .filter(group -> group.getModId().equals(ModGroup.UNCATEGORIZED_ID))
                .flatMap(group -> group.getFiles().stream()
                        .map(path -> {
                            String[] split = relativeOf(path).split("/");
                            if (split.length > 1) {
                                return split[0]+"/";
                            }
                            return split[0];
                        }))
                .distinct()
                .toList();
    }

    private String relativeOf(Path file) {
        Path configDir = this.gameDir.resolve("config");
        if (file.startsWith(configDir)){
            return configDir.relativize(file).toString().replace("\\", "/");
        }
        return file.getFileName().toString();
    }
}
