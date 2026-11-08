package run.halo.simnook;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuickRenewalTest {
    @Test
    void appendsOncePreservesDecimalsAndSynchronizesLegacyDates() {
        var card = BackupServiceTest.card("a");
        var old = BackupServiceTest.record("old", "12345678901234567890.123456789");
        card.getSpec().setRecords(List.of(old));
        card.getSpec().setExpiresAt("2026-11-01");
        card.getSpec().setNextRenewalAt("2026-10-30");
        card.getSpec().setNextRenewalEnd("2026-11-01");
        var row = BackupServiceTest.record("new", "0");
        row.setType("续费/保号");
        row.setDate("2026-11-08");
        row.setExtendsTo("2026-12-08");
        var request = new ManagementModels.QuickRenewalRequest("3", row, null, null);
        var renewed = ManagementEndpoint.renew(card, request);
        assertThat(renewed.getSpec().getRecords()).hasSize(2);
        assertThat(renewed.getSpec().getRecords().getFirst().getAmount()).isEqualByComparingTo(old.getAmount());
        assertThat(renewed.getSpec().getExpiresAt()).isEqualTo("2026-12-08");
        assertThat(renewed.getSpec().getNextRenewalAt()).isNull();
        assertThat(renewed.getSpec().getNextRenewalEnd()).isNull();
        assertThat(card.getSpec().getRecords()).hasSize(1);
        assertThat(ManagementEndpoint.renew(renewed, request)).isSameAs(renewed);
        assertThatThrownBy(() ->
                        ManagementEndpoint.renew(card, new ManagementModels.QuickRenewalRequest("2", row, null, null)))
                .hasMessageContaining("409");
        assertThatThrownBy(() -> ManagementEndpoint.renew(
                        card, new ManagementModels.QuickRenewalRequest("3", row, "2026-12-09", "2026-12-08")))
                .hasMessageContaining("截止日");
        row.setAmount(new BigDecimal("10"));
        row.setCurrency("USD");
        assertThatThrownBy(() -> ManagementEndpoint.renew(card, request)).hasMessageContaining("汇率");
    }
}
