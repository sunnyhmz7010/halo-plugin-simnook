package run.halo.simnook;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/** 为既有动态管理视图提供 OpenAPI 模型，实际响应仍沿用字段白名单。 */
public final class ManagementModels {
    private ManagementModels() {}

    @Schema(name = "ManagedCardViews")
    public record CardViews(List<ManagedCardView> cards, boolean demo, boolean full) {}

    public record QuickRenewalRequest(
            String expectedVersion, SimCard.ExpenseRecord record, String nextRenewalAt, String nextRenewalEnd) {}

    @Data
    public static class ManagedCardView {
        private String id;
        private String name;
        private String carrier;
        private String last4;
        private String numberMasked;
        private String region;
        private String countryName;
        private String typeKind;
        private String typeColor;
        private String logo;
        private List<String> keepAlive;
        private List<String> keepAliveCodes;
        private int cycleDays;
        private String cycleLabel;
        private BigDecimal price;
        private String currency;
        private String expiresAt;
        private String nextRenewalAt;
        private String nextRenewalEnd;
        private String periodStart;
        private String status;
        private String statusLabel;
        private String cancelledAt;
        private String firstPurchaseAt;
        private BigDecimal totalSpentCNY;
        private String urgency;
        private int order;
        private String number;
        private BigDecimal balance;
        private String balanceUpdatedAt;
        private String device;
        private String account;
        private String type;
        private List<String> services;
        private List<String> paymentAccounts;
        private List<String> checks;
        private String dataCheck;

        @Schema(
                allowableValues = {
                    "window",
                    "overdue",
                    "days3",
                    "days7",
                    "days15",
                    "days30",
                    "days60",
                    "beyond",
                    "noDate",
                    "cancelled"
                })
        private String urgencyCode;

        private String asOfDate;
        private List<ManagedRecordView> history;
    }

    @Data
    public static class ManagedRecordView {
        private String id;
        private String type;
        private String kind;
        private String date;
        private BigDecimal amount;
        private String currency;
        private BigDecimal rate;
        private BigDecimal amountCNY;
        private String extendsTo;
        private String title;
        private List<String> accounts;
        private List<String> checks;
    }
}
