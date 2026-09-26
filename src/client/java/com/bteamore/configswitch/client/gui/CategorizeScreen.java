package com.bteamore.configswitch.client.gui;

import com.bteamore.configswitch.client.gui.widget.CandidateListWidget;
import com.bteamore.configswitch.client.gui.widget.UnclassifiedListWidget;
import com.bteamore.configswitch.discovery.CandidateRanker;
import com.bteamore.configswitch.discovery.MappingConfig;
import com.bteamore.configswitch.discovery.MappingTable;
import com.bteamore.configswitch.discovery.ModCandidate;
import com.bteamore.configswitch.util.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.*;

// 整理未分类配置：左栏选文件，右栏点目标 mod（或忽略），只记录状态；点完成后一次性提交。
// 数据来源与状态记录（wire）见文件末尾各 onXxx 方法。
public class CategorizeScreen extends Screen {
    private static final int TITLE_Y = 15;
    private static final int MARGIN_X = 20;
    private static final int LABEL_Y = 40;
    private static final int LIST_TOP = 52;
    private static final int SEARCH_HEIGHT = 18;
    private static final int SEARCH_LIST_GAP = 4;
    private static final int LIST_BOTTOM_GAP = 40;
    private static final int ITEM_HEIGHT = 16;
    private static final int COLUMN_GAP = 8;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 12;
    private static final int DIVIDER_COLOR = 0x40FFFFFF;
    private static final int HEADER_COLOR = 0xA0A0A0;
    private static final int EMPTY_HINT_COLOR = 0xA0A0A0;

    private final Screen parentScreen;
    private final Runnable completeCallback;

    private final List<String> fileNames = new ArrayList<>();
    private String selectedFileName;
    private final List<ModCandidate> candidates = new ArrayList<>();
    private String selectedModId;
    private final Map<String, String> selectedMappings = new HashMap<>();

    private UnclassifiedListWidget fileList;
    private TextFieldWidget searchField;
    private CandidateListWidget candidateList;
    private ButtonWidget backButton;
    private ButtonWidget ignoreButton;
    private ButtonWidget doneButton;

    private Text message;
    private String searchText;
    private double scrollAmount;

    public CategorizeScreen(Text title, Screen parentScreen, List<String> fileNames, Runnable completeCallback) {
        super(title);
        this.parentScreen = parentScreen;
        this.completeCallback = completeCallback;

        this.fileNames.addAll(fileNames);
    }

    @Override
    protected void init() {
        if (this.candidates.isEmpty()) {
            this.loadCandidates();
        }

        int listBottom = this.height - LIST_BOTTOM_GAP;

        // 左栏：未分类文件列表
        this.fileList = new UnclassifiedListWidget(MinecraftClient.getInstance(), this.leftColumnWidth(), listBottom - LIST_TOP, LIST_TOP, ITEM_HEIGHT);
        this.fileList.setX(MARGIN_X);
        this.fileList.setOnSelect(this::onFileSelected);

        this.refreshFiles();

        // 右栏：搜索框 + 候选列表
        this.searchField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, this.rightColumnX(), LIST_TOP, this.rightColumnWidth(), SEARCH_HEIGHT, Text.translatable("configswitch.search.placeholder"));
        this.searchField.setPlaceholder(Text.translatable("configswitch.search.placeholder"));
        this.searchField.setText(this.searchText != null ? this.searchText : "");
        this.searchField.setChangedListener(this::onSearchChanged);

        int candidateTop = LIST_TOP + SEARCH_HEIGHT + SEARCH_LIST_GAP;
        this.candidateList = new CandidateListWidget(MinecraftClient.getInstance(), this.rightColumnWidth(), listBottom - candidateTop, candidateTop, ITEM_HEIGHT);
        this.candidateList.setX(this.rightColumnX());
        this.candidateList.setOnPick(this::onCandidatePicked);

        this.refreshCandidates();

        // 底部按钮行：返回（左栏左下）、忽略（右栏下方偏左）、完成（右下角）
        int buttonY = this.height - BUTTON_HEIGHT - BOTTOM_MARGIN;
        this.backButton = ButtonWidget.builder(Text.translatable("configswitch.button.back"), button -> this.onBack())
                .dimensions(MARGIN_X, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.ignoreButton = ButtonWidget.builder(Text.translatable("configswitch.button.ignore"), button -> this.onIgnore())
                .dimensions(this.rightColumnX(), buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.doneButton = ButtonWidget.builder(Text.translatable("configswitch.button.done"), button -> this.onDone())
                .dimensions(this.width - MARGIN_X - BUTTON_WIDTH, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        this.addDrawableChild(this.fileList);
        this.addDrawableChild(this.searchField);
        this.addDrawableChild(this.candidateList);
        this.addDrawableChild(this.backButton);
        this.addDrawableChild(this.ignoreButton);
        this.addDrawableChild(this.doneButton);

        this.refreshFiles();
        this.refreshCandidates();
    }

    // 两栏几何（随窗口宽度变化，实时计算）
    private int dividerX() {
        return this.width / 2;
    }

    private int leftColumnWidth() {
        return this.dividerX() - COLUMN_GAP - MARGIN_X;
    }

    private int rightColumnX() {
        return this.dividerX() + COLUMN_GAP;
    }

    private int rightColumnWidth() {
        return this.width - MARGIN_X - this.rightColumnX();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
        if (this.message != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, this.message, this.width / 2, TITLE_Y + 12, 0xAAAAAA);
        }
        // 两栏标题
        context.drawTextWithShadow(this.textRenderer, Text.translatable("configswitch.label.unclassified_count", this.fileNames.size()), MARGIN_X, LABEL_Y, HEADER_COLOR);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("configswitch.label.categorize_to"), this.rightColumnX(), LABEL_Y, HEADER_COLOR);
        // 分栏竖线（止于按钮行上方）
        context.fill(this.dividerX(), LABEL_Y, this.dividerX() + 1, this.height - BUTTON_HEIGHT - BOTTOM_MARGIN - 6, DIVIDER_COLOR);
        // 空态提示
        if (this.fileNames.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("configswitch.label.no_unclassified"),
                    MARGIN_X + this.leftColumnWidth() / 2, this.fileList.getY() + this.fileList.getHeight() / 2, EMPTY_HINT_COLOR);
        }
        if (this.candidates.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("configswitch.search.no_candidate"),
                    this.rightColumnX() + this.rightColumnWidth() / 2, this.candidateList.getY() + this.candidateList.getHeight() / 2, EMPTY_HINT_COLOR);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(this.parentScreen);
    }

    // 建左栏：选中标记与 " -> modid" 标注由 Supplier 每帧现问，状态变化不重建（重建会跳滚动位置）
    private void refreshFiles() {
        this.fileList.clearFiles();
        for (String fileName : this.fileNames) {
            this.fileList.addFile(fileName,
                    () -> {
                        String modId = this.selectedMappings.get(fileName);
                        return (modId == null) ? "" : " -> " + modId;
                    },
                    () -> fileName.equals(this.selectedFileName));
        }
    }

    // 重建右栏候选：仅在顺序/集合真的变化时调用（init / 换文件 / 搜索）；选中标记由 Supplier 现问
    private void refreshCandidates() {
        this.candidateList.clearCandidates();
        for (ModCandidate candidate : visibleCandidates()) {
            this.candidateList.addCandidate(candidate.modId(),
                    candidate.displayName(),
                    () -> candidate.modId() != null && candidate.modId().equals(this.selectedModId));
        }

        if (this.searchText != null) {
            if (this.searchText.isEmpty()) {
                this.candidateList.setScrollAmount(this.scrollAmount);
            } else {
                this.candidateList.setScrollAmount(0.0);
            }
        }
    }

    private List<ModCandidate> visibleCandidates() {
        if (this.searchText == null || this.searchText.isEmpty()){
            return this.candidates;
        }
        return this.candidates.stream()
                .filter(candidate -> candidate.displayName().toLowerCase().contains(this.searchText.toLowerCase()) || candidate.modId().toLowerCase().contains(this.searchText.toLowerCase()))
                .toList();
    }

    private void loadCandidates() {
        Set<String> filters = Set.of("java", "fabric-", "minecraft", "fabricloader", "configswitch");
        FabricLoader.getInstance().getAllMods()
                .stream()
                .filter(mod -> {
                    String modId = mod.getMetadata().getId();
                    return filters.stream().noneMatch(modId::startsWith);
                })
                .forEach(mod -> this.candidates.add(
                new ModCandidate(
                        mod.getMetadata().getId(),
                        mod.getMetadata().getName())));
    }

    private void onFileSelected(String fileName) {
        this.selectedFileName = fileName;
        // 左栏不重建：选中标记由 Supplier 现问，滚动位置保持
        // 按文件名重排候选
        List<ModCandidate> rank = CandidateRanker.rank(fileName, this.candidates);
        this.candidates.clear();
        this.candidates.addAll(rank);
        this.selectedModId = this.selectedMappings.getOrDefault(fileName, null);
        // 右栏顺序真的变了，必须重建
        this.refreshCandidates();
    }

    private void onCandidatePicked(String modId) {
        if (this.selectedFileName == null){
            return;
        }
        if (this.selectedModId == null || !this.selectedModId.equals(modId)) {
            this.selectedModId = modId;
            this.selectedMappings.put(this.selectedFileName, modId);
        } else {
            this.selectedModId = null;
            this.selectedMappings.remove(this.selectedFileName);
        }
        // 不重建列表：两栏的标记/标注由 Supplier 每帧现问
    }

    private void onSearchChanged(String text) {
        boolean wasNotSearching = (this.searchText == null || this.searchText.isEmpty());
        if (wasNotSearching && !text.isEmpty()) {
            this.scrollAmount = this.candidateList.getScrollAmount();
        }
        this.searchText = text;
        this.refreshCandidates();
    }

    private void onIgnore() {
        if (this.selectedFileName == null){
            return;
        }
        if (this.selectedModId == null || !this.selectedModId.equals(MappingTable.IGNORE)) {
            this.selectedModId = MappingTable.IGNORE;
            this.selectedMappings.put(this.selectedFileName, MappingTable.IGNORE);
        } else {
            this.selectedModId = null;
            this.selectedMappings.remove(this.selectedFileName);
        }
        // 不重建列表：两栏的标记/标注由 Supplier 每帧现问
    }

    private void onBack() {
        if (this.selectedMappings.isEmpty()){
            this.close();
        } else {
            this.client.setScreen(new ConfirmScreen(
                    (confirmed) -> {
                        if (confirmed) {
                            this.close();
                        } else {
                            this.client.setScreen(this);
                        }
                    },
                    Text.translatable("configswitch.confirm.back.title"),
                    Text.translatable("configswitch.confirm.back.text"),
                    Text.translatable("configswitch.confirm.back.yes"),
                    Text.translatable("configswitch.confirm.back.no")
            ));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (!this.selectedMappings.isEmpty()) {
            this.onBack();
            return false;
        }
        return true;
    }

    private void onDone() {
        if (this.selectedMappings.isEmpty()){
            this.close();
        } else {
            this.client.setScreen(new ConfirmScreen(
                    (confirmed) -> {
                        if (confirmed) {
                            boolean appended = MappingConfig.store().append(this.selectedMappings);
                            if (appended){
                                MappingConfig.reload();
                                this.completeCallback.run();
                                this.close();
                            } else {
                                this.message = Text.translatable("configswitch.message.failed");
                                this.client.setScreen(this);
                            }
                        } else {
                            this.client.setScreen(this);
                        }
                    },
                    Text.translatable("configswitch.confirm.classifier.title"),
                    Text.translatable("configswitch.confirm.classifier.text"),
                    Text.translatable("configswitch.confirm.classifier.yes"),
                    Text.translatable("configswitch.confirm.classifier.no")
            ));
        }
    }
}
