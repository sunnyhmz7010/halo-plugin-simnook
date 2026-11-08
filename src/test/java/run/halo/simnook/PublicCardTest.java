package run.halo.simnook;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.app.extension.Metadata;

class PublicCardTest {
    @Test
    void codesKeepAliveActionsWithoutChangingOriginalValues() {
        var c = card();
        var actions = List.of("充值余额", " Auto-charge ", "自定义保号动作");
        c.getSpec().setKeepAlive(actions);
        var dto = PublicCard.from(c, LocalDate.of(2026, 11, 7));
        assertThat(dto.keepAlive()).isEqualTo(actions);
        assertThat(dto.keepAliveCodes()).containsExactly("topup", "autoCharge", (String) null);
        assertThat(c.getSpec().getKeepAlive()).isEqualTo(actions);
    }

    @Test
    void translatesGeneratedChecksAndPreservesPrivateFreeText() {
        var c = card();
        c.getSpec().setNumber("");
        c.getSpec().setDataCheck("我的手写备注");
        var expense = record("10", "7.2", "2027-01-01");
        expense.setTitle("手填标题保持原样");
        c.getSpec().setRecords(List.of(expense));
        var today = LocalDate.of(2026, 11, 7);
        var en = ManagementEndpoint.managedCard(c, today, 0, Locale.ENGLISH);
        var zh = ManagementEndpoint.managedCard(c, today, 0);
        assertThat(en.path("statusLabel").asString()).isEqualTo("Active");
        assertThat(en.path("type").asString()).isEqualTo("Physical SIM");
        assertThat(en.path("dataCheck").asString()).contains("Phone number is missing", "我的手写备注");
        assertThat(zh.path("dataCheck").asString()).contains("缺号码", "我的手写备注");
        assertThat(en.path("history").get(0).path("title").asString()).isEqualTo("手填标题保持原样");
        assertThat(PublicCard.from(c, today).toString()).doesNotContain("我的手写备注", "手填标题保持原样", "private-");
        c.getSpec().setTypeLabel("我的自定义卡型");
        assertThat(ManagementEndpoint.managedCard(c, today, 0, Locale.ENGLISH)
                        .path("type")
                        .asString())
                .isEqualTo("我的自定义卡型");
    }

    private SimCard card() {
        var card = new SimCard();
        var meta = new Metadata();
        meta.setName("test-card");
        card.setMetadata(meta);
        var spec = new SimCard.CardSpec();
        spec.setName("测试卡");
        spec.setNumber("+86 13800123456");
        spec.setAccount("private-account");
        spec.setDevice("private-device");
        spec.setBalance(new BigDecimal("99"));
        card.setSpec(spec);
        return card;
    }

    private SimCard.ExpenseRecord record(String amount, String rate, String end) {
        var r = new SimCard.ExpenseRecord();
        r.setAmount(new BigDecimal(amount));
        r.setRate(new BigDecimal(rate));
        r.setCurrency("USD");
        r.setType("续费");
        r.setDate("2026-10-01");
        r.setExtendsTo(end);
        r.setTitle("private-title");
        return r;
    }

    @Test
    void derivesDatesAndUsesRecordedRates() {
        var c = card();
        c.getSpec().setRecords(List.of(record("10", "7.2", "2027-01-01"), record("20", "0.9", "2026-12-01")));
        var dto = PublicCard.from(c, LocalDate.of(2026, 11, 6));
        assertThat(dto.expiresAt()).isEqualTo("2027-01-01");
        assertThat(dto.nextRenewalAt()).isEqualTo("2027-01-01");
        assertThat(dto.totalSpentCNY()).isEqualByComparingTo("90");
        assertThat(dto.last4()).isEqualTo("3456");
        assertThat(dto.toString()).doesNotContain("13800123456", "private-");
        c.getSpec().setExpiresAt("2028-01-01");
        c.getSpec().setNextRenewalAt("2027-12-01");
        dto = PublicCard.from(c, LocalDate.of(2026, 11, 6));
        assertThat(dto.expiresAt()).isEqualTo("2028-01-01");
        assertThat(dto.nextRenewalAt()).isEqualTo("2027-12-01");
        c.getSpec().setCancelled(true);
        dto = PublicCard.from(c, LocalDate.of(2026, 11, 6));
        assertThat(dto.nextRenewalAt()).isNull();
        assertThat(dto.status()).isEqualTo("cancelled");
    }

    @Test
    void handlesCardWithoutTransactions() {
        var dto = PublicCard.from(card(), LocalDate.of(2026, 11, 6));
        assertThat(dto.history()).isEmpty();
        assertThat(dto.expiresAt()).isNull();
        assertThat(dto.totalSpentCNY()).isEqualByComparingTo("0");
    }

    @Test
    void classifiesExistingRecordsAndDisplaysRenewalWindow() {
        var c = card();
        var topup = record("2", "7.2", null);
        topup.setType("充值");
        var purchase = record("10", "7.2", "2027-01-01");
        purchase.setType("购卡");
        purchase.setDate("2026-10-02");
        var renewal = record("20", "0.9", "2026-12-01");
        renewal.setType("Renewal");
        renewal.setDate("2026-10-03");
        c.getSpec().setRecords(List.of(topup, purchase, renewal));
        c.getSpec().setNextRenewalAt("2026-11-05");
        c.getSpec().setNextRenewalEnd("2026-11-08");
        c.getSpec().setTypeColor("purple");
        var today = LocalDate.of(2026, 11, 6);
        var dto = PublicCard.from(c, today);
        assertThat(dto.history())
                .extracting(PublicCard.PublicRecord::kind)
                .containsExactly("renewal", "purchase", "topup");
        assertThat(dto.expiresAt()).isEqualTo("2026-12-01");
        assertThat(dto.periodStart()).isEqualTo("2026-10-03");
        assertThat(dto.firstPurchaseAt()).isEqualTo("2026-10-01");
        assertThat(dto.nextRenewalAt()).isEqualTo("2026-11-05");
        assertThat(dto.nextRenewalEnd()).isEqualTo("2026-11-08");
        assertThat(dto.urgency()).isEqualTo("⓪ 续费窗口开放中（剩 2 天）");
        assertThat(dto.typeColor()).isEqualTo("purple");

        c.getSpec().setFirstPurchaseAt("2026-09-01");
        c.getSpec().setNextRenewalAt("2027-02-08");
        dto = PublicCard.from(c, today);
        assertThat(dto.firstPurchaseAt()).isEqualTo("2026-09-01");
        assertThat(dto.nextRenewalEnd()).isNull();
        c.getSpec().setNextRenewalEnd("2027-02-10");
        c.getSpec().setCancelledAt("2026-10-20");
        dto = PublicCard.from(c, today);
        assertThat(dto.status()).isEqualTo("cancelled");
        assertThat(dto.urgency()).isEqualTo("⑨ 号码已失效");
        assertThat(dto.nextRenewalAt()).isNull();
        assertThat(dto.nextRenewalEnd()).isNull();
    }

    @Test
    void recordsChecksOnlyInManagedViewWithoutPaymentFields() {
        var c = card();
        c.getSpec().setTypeKind("embedded");
        c.getSpec().setCountryName("美国");
        var paid = record("6.25", "2", null);
        paid.setCurrency("CNY");
        paid.setType("额外充值");
        paid.setId("stable-record");
        var incomplete = new SimCard.ExpenseRecord();
        incomplete.setCurrency("JPY");
        incomplete.setAmount(BigDecimal.TEN);
        c.getSpec().setRecords(List.of(paid, incomplete));
        var today = LocalDate.of(2026, 11, 6);
        var publicCard = PublicCard.from(c, today);
        var managed = ManagementEndpoint.managedCard(c, today, 0);
        assertThat(publicCard.totalSpentCNY()).isEqualByComparingTo("6.25");
        assertThat(publicCard.history().get(0).id()).isEqualTo("test-card-stable-record");
        assertThat(publicCard.history().get(1).amountCNY()).isNull();
        assertThat(publicCard.toString()).doesNotContain("private-");
        assertThat(managed.has("paymentAccounts")).isFalse();
        assertThat(managed.path("history").get(0).has("accounts")).isFalse();
        assertThat(managed.path("history").get(1).path("checks").toString())
                .contains("缺消费日期", "外币缺汇率")
                .doesNotContain("扣款账户");
        assertThat(managed.path("checks").toString()).doesNotContain("扣款账户");
        assertThat(managed.path("dataCheck").asString()).contains("号码与号码国家不符（应以 +1 开头）");
    }

    @Test
    void urgencyUsesInclusiveTierBoundaries() {
        var today = LocalDate.of(2026, 11, 6);
        var limits = Map.of(3, "②", 7, "③", 15, "④", 30, "⑤", 60, "⑥", 61, "⑦");
        limits.forEach((days, prefix) -> assertThat(
                        CardFacts.urgency("active", today.plusDays(days).toString(), null, today))
                .startsWith(prefix));
        assertThat(CardFacts.urgency("active", null, null, today)).startsWith("⑧");
        assertThat(CardFacts.urgency(
                        "expired", today.toString(), today.plusDays(7).toString(), today))
                .startsWith("①");
        assertThat(CardFacts.urgency("active", today.minusDays(1).toString(), today.toString(), today))
                .isEqualTo("⓪ 续费窗口开放中（剩 0 天）");
    }

    @Test
    void reportsMalformedDatesWithoutBreakingTheView() {
        var c = card();
        c.getSpec().setExpiresAt("2026-02-31");
        c.getSpec().setCancelledAt("not-a-date");
        var valid = record("6", "7", "2026-12-01");
        var invalid = record("1", "7", "2026-13-01");
        invalid.setDate("2026-11-05");
        c.getSpec().setRecords(List.of(valid, invalid));
        var today = LocalDate.of(2026, 11, 6);
        var dto = PublicCard.from(c, today);
        assertThat(dto.expiresAt()).isEqualTo("2026-12-01");
        assertThat(dto.status()).isEqualTo("active");
        assertThat(dto.history().getFirst().extendsTo()).isNull();
        var managed = ManagementEndpoint.managedCard(c, today, 0);
        assertThat(managed.path("dataCheck").asString()).contains("旧版手填有效期不是有效日期", "停用日期不是有效日期");
        assertThat(managed.path("history").get(0).path("checks").toString()).contains("本次有效期延至不是有效日期");
    }
}
