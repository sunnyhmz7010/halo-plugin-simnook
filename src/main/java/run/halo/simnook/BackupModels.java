package run.halo.simnook;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public final class BackupModels {
    private BackupModels() {}

    public record BackupDocument(
            String format,
            int version,
            String exportedAt,
            String pluginVersion,
            List<CardEntry> cards,
            List<AccountEntry> paymentAccounts) {}

    @Schema(name = "BackupCardEntry")
    public record CardEntry(String name, SimCard.CardSpec spec) {}

    @Schema(name = "BackupAccountEntry")
    public record AccountEntry(String name, PaymentAccount.Spec spec) {}
    // 用字符串传送原始 JSON，客户端不反序列化金额与汇率。
    @Schema(name = "BackupPreviewRequest")
    public record PreviewRequest(String content) {}

    @Schema(name = "BackupRestoreRequest")
    public record RestoreRequest(String content, String fileHash, List<RestoreDecision> decisions) {}

    @Schema(name = "BackupRestoreDecision")
    public record RestoreDecision(String kind, String name, String action, String expectedVersion) {}

    @Schema(name = "BackupFieldDifference")
    public record FieldDifference(String field, String before, String after) {}

    @Schema(name = "BackupPreviewItem")
    public record PreviewItem(
            String kind,
            String name,
            String title,
            String expectedVersion,
            List<String> actions,
            List<FieldDifference> differences,
            List<String> warnings,
            int appendCount) {}

    public record BackupPreview(String fileHash, List<PreviewItem> items) {}

    @Schema(name = "BackupRestoreItem")
    public record RestoreItem(String kind, String name, String action, String status, String message) {}

    @Schema(name = "BackupRestoreResult")
    public record RestoreResult(List<RestoreItem> items) {}
}
