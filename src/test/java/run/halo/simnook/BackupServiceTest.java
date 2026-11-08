package run.halo.simnook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static run.halo.simnook.BackupModels.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;

class BackupServiceTest {
    @Test
    void cardCsvUsesDerivedExpiryAndRenewalDates() {
        var client = mock(ReactiveExtensionClient.class);
        var settings = mock(ReactiveSettingFetcher.class);
        var card = card("csv-card");
        var row = record("r", "1");
        row.setDate("2026-11-08");
        row.setExtendsTo("2026-12-08");
        card.getSpec().setRecords(List.of(row));
        when(client.list(eq(SimCard.class), isNull(), any())).thenReturn(Flux.just(card));
        when(settings.getSettingValues()).thenReturn(Mono.just(Map.of()));
        var csv = new BackupService(client, mock(PluginContext.class), settings)
                .csv("cards")
                .block(Duration.ofSeconds(3));
        assertThat(csv).contains("\"2026-12-08\",\"2026-12-08\"");
    }

    static SimCard card(String id) {
        var card = new SimCard();
        var metadata = new Metadata();
        metadata.setName(id);
        metadata.setVersion(3L);
        card.setMetadata(metadata);
        var spec = new SimCard.CardSpec();
        spec.setName("测试卡");
        spec.setNumber("+8613800123456");
        card.setSpec(spec);
        return card;
    }

    static SimCard.ExpenseRecord record(String id, String amount) {
        var row = new SimCard.ExpenseRecord();
        row.setId(id);
        row.setAmount(new BigDecimal(amount));
        row.setCurrency("CNY");
        return row;
    }

    @Test
    void preservesDecimalPrecisionAndRejectsMalformedBackupsBeforeWriting() {
        var card = card("card-a");
        card.getSpec().setRecords(List.of(record("r1", "12345678901234567890.123456789")));
        var document = new BackupDocument(
                "simnook-backup", 1, "2026-11-08", "test", List.of(new CardEntry("card-a", card.getSpec())));
        var text = BackupService.JSON.writeValueAsString(document);
        assertThat(text).doesNotContain("paymentAccounts", "paymentAccountIds");
        assertThat(BackupService.parse(text)
                        .cards()
                        .getFirst()
                        .spec()
                        .getRecords()
                        .getFirst()
                        .getAmount())
                .isEqualByComparingTo("12345678901234567890.123456789");
        assertThatThrownBy(() -> BackupService.parse(text.replace("\"version\":1", "\"version\":2")))
                .hasMessageContaining("版本");
        assertThatThrownBy(() -> BackupService.parse(text + "{}")).hasMessageContaining("格式");
        var duplicate = new BackupDocument(
                "simnook-backup",
                1,
                "",
                "",
                List.of(document.cards().getFirst(), document.cards().getFirst()));
        assertThatThrownBy(() -> BackupService.parse(BackupService.JSON.writeValueAsString(duplicate)))
                .hasMessageContaining("重复资源");
        assertThatThrownBy(() -> BackupService.parse(" ".repeat(BackupService.MAX_BYTES) + "{}"))
                .hasMessageContaining("32 MiB");
    }

    @Test
    void appendKeepsCurrentRecordsAndPreservesLegacyMultiplicityAcrossRetries() {
        var existing = List.of(record("same-id", "10"), record(null, "20"));
        var incoming =
                List.of(record("same-id", "999"), record("new-id", "30"), record(null, "20.00"), record(null, "20"));
        var merged = BackupService.appendRecords(existing, incoming);
        assertThat(merged).hasSize(4);
        assertThat(merged.getFirst().getAmount()).isEqualByComparingTo("10");
        assertThat(BackupService.appendRecords(merged, incoming)).hasSize(4);
        var mixed = List.of(record("explicit", "10"), record(null, "10"));
        assertThat(BackupService.appendRecords(List.of(record("explicit", "10")), mixed))
                .hasSize(2);
    }

    @Test
    void preflightChecksVersionsAndAppendsRecords() {
        var card = card("card-a");
        card.getSpec().setRecords(List.of(record("old", "1")));
        var imported = card("card-a");
        imported.getSpec().setName("备份名称");
        imported.getSpec().setRecords(List.of(record("new", "2")));
        var document =
                new BackupDocument("simnook-backup", 1, "", "", List.of(new CardEntry("card-a", imported.getSpec())));
        var snapshot = new BackupService.Snapshot(Map.of("card-a", card));
        var plan = BackupService.prepare(
                document, List.of(new RestoreDecision("SimCard", "card-a", "append", "3")), snapshot);
        var updated = (SimCard) plan.getFirst().resource();
        assertThat(updated.getSpec().getName()).isEqualTo("测试卡");
        assertThat(updated.getSpec().getRecords()).hasSize(2);
        assertThat(card.getSpec().getRecords()).hasSize(1);
        assertThatThrownBy(() -> BackupService.prepare(
                        document, List.of(new RestoreDecision("SimCard", "card-a", "overwrite", "2")), snapshot))
                .hasMessageContaining("重新预览");
    }

    @Test
    void stopsAfterFailedWriteAndReturnsPendingRows() {
        var client = mock(ReactiveExtensionClient.class);
        when(client.list(eq(SimCard.class), isNull(), any())).thenReturn(Flux.empty());
        when(client.create(any(SimCard.class))).thenAnswer(call -> {
            SimCard card = call.getArgument(0);
            return "b".equals(card.getMetadata().getName())
                    ? Mono.error(new IllegalStateException("write failed"))
                    : Mono.just(card);
        });
        var document = new BackupDocument(
                "simnook-backup",
                1,
                "",
                "",
                List.of(
                        new CardEntry("a", card("a").getSpec()),
                        new CardEntry("b", card("b").getSpec()),
                        new CardEntry("c", card("c").getSpec())));
        var text = BackupService.JSON.writeValueAsString(document);
        var request = new RestoreRequest(
                text,
                BackupService.hash(text),
                List.of(
                        new RestoreDecision("SimCard", "a", "create", null),
                        new RestoreDecision("SimCard", "b", "create", null),
                        new RestoreDecision("SimCard", "c", "create", null)));
        var result = new BackupService(client, mock(PluginContext.class), mock(ReactiveSettingFetcher.class))
                .restore(request)
                .block(Duration.ofSeconds(3));
        assertThat(result.items()).extracting(RestoreItem::status).containsExactly("success", "failed", "pending");
        verify(client, times(2)).create(any(SimCard.class));
    }

    @Test
    void csvEscapesSpreadsheetFormulasQuotesAndKeepsDecimals() {
        var result = new StringBuilder();
        BackupService.csvRow(
                result, "+8613800123456", "=1+1", "a,\"b\n", new BigDecimal("12345678901234567890.123456789"));
        assertThat(result.toString())
                .isEqualTo("\"'+8613800123456\",\"'=1+1\",\"a,\"\"b\n\",\"12345678901234567890.123456789\"\n");
    }
}
