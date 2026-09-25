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
import net.minecraft.text.MutableText;
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
    private static final int SETTINGS_BUTTON_WIDTH = 60;
    private static final int SETTINGS_BUTTON_Y = 10;
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
    private ClickableWidget settingsButton;

    private List<ModGroup> allGroups;
    private ModGroup residueGroup;
    private final Set<String> selectedModIds = new HashSet<>();
    private String searchText;
    private Text message;
    private double scrollAmount;

    private final Path gameDir = MinecraftClient.getInstance().runDirectory.toPath();

    private final StateManager stateManager = StateManager.getInstance();
    private final ConfigDiscovery discovery = new ConfigDiscovery(gameDir);
    private final ConfigPathResolver resolver = new ConfigPathResolver(gameDir);
    private SyncReport lastReport;

    public ConfigScreen() {
        super(Text.translatable("configswitch.screen.config"));
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
        this.listWidget = new ModGroupsWidget(MinecraftClient.getInstance(), listWidth, listHeight, LIST_TOP, ITEM_HEIGHT);
        this.listWidget.setX(MARGIN_X);

        this.refreshList();

        // 搜索行：左侧搜索框，右侧快捷按钮
        int searchY = this.height - BOTTOM_MARGIN - BUTTON_HEIGHT - SEARCH_GAP - SEARCH_HEIGHT;
        int quickButtonX = this.width - MARGIN_X - (QUICK_BUTTON_WIDTH * 3 + QUICK_BUTTON_GAP * 2);
        // 窗口过窄时收缩搜索框，避免与快捷按钮重叠
        int searchWidth = Math.min(SEARCH_WIDTH, quickButtonX - MARGIN_X - SEARCH_GAP);
        // 搜索框
        this.searchField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, MARGIN_X, searchY, searchWidth, SEARCH_HEIGHT, Text.translatable("configswitch.search.placeholder"));
        this.searchField.setPlaceholder(Text.translatable("configswitch.search.placeholder"));
        this.searchField.setText((this.searchText != null) ? this.searchText : "");
        this.searchField.setChangedListener(searchText -> {
            boolean wasNotSearching = (this.searchText == null || this.searchText.isEmpty());
            if (wasNotSearching && !searchText.isEmpty()){
                this.scrollAmount = this.listWidget.getScrollAmount();
            }
            this.searchText = searchText;
            this.refreshList();
        });

        // 快捷按钮
        ButtonWidget allSelected = ButtonWidget.builder(Text.translatable("configswitch.button.select_all"), btn -> setAllSelected(true))
                .dimensions(quickButtonX, searchY, QUICK_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        quickButtonX += QUICK_BUTTON_WIDTH + QUICK_BUTTON_GAP;
        ButtonWidget noneSelected = ButtonWidget.builder(Text.translatable("configswitch.button.select_none"), btn -> setAllSelected(false))
                .dimensions(quickButtonX, searchY, QUICK_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        quickButtonX += QUICK_BUTTON_WIDTH + QUICK_BUTTON_GAP;
        ButtonWidget invertSelected = ButtonWidget.builder(Text.translatable("configswitch.button.invert"), btn -> invertSelection())
                .dimensions(quickButtonX, searchY, QUICK_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();


        // 底部：Fetch / Push 居中并排，备份左下、完成右下
        int buttonY = this.height - BUTTON_HEIGHT - BOTTOM_MARGIN;
        int rowWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int startX = (this.width - rowWidth) / 2;

        // Fetch / Push 按钮
        this.fetchButton = ButtonWidget.builder(Text.translatable("configswitch.button.fetch"), btn -> {
                    boolean load = onPress("fetch");
                    if (load) {
                        MinecraftClient.getInstance().options.load();
                    }
                })
                .dimensions(startX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.pushButton = ButtonWidget.builder(Text.translatable("configswitch.button.push"), btn -> onPress("push"))
                .dimensions(startX + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        // 备份管理
        this.backupButton = ButtonWidget.builder(Text.translatable("configswitch.button.backup"),
                        btn -> MinecraftClient.getInstance().setScreen(new BackupScreen(Text.translatable("configswitch.screen.backup"), this.gameDir, this)))
                .dimensions(MARGIN_X, buttonY, BACKUP_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        // 完成按钮
        this.doneButton = ButtonWidget.builder(Text.translatable("configswitch.button.done"), btn -> this.close())
                .dimensions(this.width - MARGIN_X - DONE_BUTTON_WIDTH, buttonY, DONE_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        // 右上角：设置入口
        this.settingsButton = ButtonWidget.builder(Text.translatable("configswitch.button.settings"),
                        btn -> MinecraftClient.getInstance().setScreen(new SettingsScreen(Text.translatable("configswitch.screen.settings"), this)))
                .dimensions(this.width - MARGIN_X - SETTINGS_BUTTON_WIDTH, SETTINGS_BUTTON_Y, SETTINGS_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        this.addDrawableChild(listWidget);
        this.addDrawableChild(searchField);
        this.addDrawableChild(fetchButton);
        this.addDrawableChild(pushButton);
        this.addDrawableChild(doneButton);
        this.addDrawableChild(backupButton);
        this.addDrawableChild(settingsButton);
        this.addDrawableChild(allSelected);
        this.addDrawableChild(noneSelected);
        this.addDrawableChild(invertSelected);
    }

    private boolean onPress(String name) {
        List<ModGroup> selectedGroups = this.allGroups.stream()
                .filter(group -> this.selectedModIds.contains(group.getModId()))
                .toList();

        if (selectedGroups.isEmpty()) {
            this.message = Text.translatable("configswitch.message.no_selection");
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

    private Text buildMessage(String name, SyncReport report) {
        int success = report.count(SyncOutcome.SUCCESS);
        int failed = report.count(SyncOutcome.FAILED);
        int skipped = report.count(SyncOutcome.SKIPPED);

        String headKey = "configswitch.message." + name + ".head";

        if (report.results().isEmpty()) {
            return Text.translatable("configswitch.message.nothing");
        }
        if (success == 0 && failed == 0) {
            // 全部跳过
            return Text.translatable("configswitch.message.nothing")
                    .append(Text.translatable("configswitch.message.suffix.skipped", skipped));
        }

        MutableText msg = Text.translatable(headKey, success);
        if (failed > 0) {
            String join = String.join(Text.translatable("configswitch.label.list_sep").getString(), report.failedModIds());
            msg.append(Text.translatable("configswitch.message.suffix.failed", failed, join));
        }
        if (skipped > 0) {
            msg.append(Text.translatable("configswitch.message.suffix.skipped", skipped));
        }
        if ("fetch".equals(name) && involvesModConfig(report)) {
            msg.append(Text.translatable("configswitch.message.suffix.restart"));
        }
        return msg;
    }

    private boolean involvesModConfig(SyncReport report) {
        return report.results().keySet().stream()
                .anyMatch(id -> !ModGroup.VANILLA_ID.equals(id));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
        // 没有消息时退化为显示状态机当前状态（开发期可见）
        Text msg = (this.message == null)
                ? Text.translatable("configswitch.message.state", stateManager.getCurrentState().name())
                : this.message;
        context.drawCenteredTextWithShadow(this.textRenderer, msg, this.width / 2, TITLE_Y + 12, 0xAAAAAA);
        if (this.visibleGroups().isEmpty()){
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("configswitch.search.no_match"), this.width / 2, this.listWidget.getY() + this.listWidget.getHeight() / 2, 0xA0A0A0);
        }
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
            this.listWidget.addGroup(group.getModId(), group.getFiles().stream().map(Path::getFileName).map(Path::toString).toList(), this.selectedModIds.contains(group.getModId()), count, this::onToggle);
        }

        if (this.searchText != null){
            if (this.searchText.isEmpty()) {
                this.listWidget.setScrollAmount(this.scrollAmount);
            } else {
                this.listWidget.setScrollAmount(0.0);
            }
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
