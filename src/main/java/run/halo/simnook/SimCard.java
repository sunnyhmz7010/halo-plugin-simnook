package run.halo.simnook;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "simnook.halo.run", version = "v1alpha1", kind = "SimCard", plural = "simcards", singular = "simcard")
public class SimCard extends AbstractExtension {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private CardSpec spec;

    @Data
    @Schema(name = "CardSpec")
    public static class CardSpec {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 100)
        private String name;

        private String carrier;
        private String number;
        private String region;
        private String countryName;

        @Schema(
                allowableValues = {"physical", "adapter", "embedded", "virtual"},
                defaultValue = "physical")
        private String typeKind;

        @Schema(
                allowableValues = {
                    "default", "gray", "brown", "orange", "yellow", "green", "blue", "purple", "pink", "red"
                },
                defaultValue = "default")
        private String typeColor;

        private String typeLabel;
        private String logo;
        private List<String> keepAlive;
        private Integer cycleDays;
        private String cycleLabel;

        @Schema(minimum = "0")
        private BigDecimal price;

        @Schema(pattern = "^[A-Z]{3}$")
        private String currency;

        @Schema(format = "date")
        private String expiresAt;

        @Schema(format = "date")
        private String firstPurchaseAt;

        @Schema(format = "date")
        private String nextRenewalAt;

        @Schema(format = "date")
        private String nextRenewalEnd;

        @Schema(format = "date")
        private String cancelledAt;

        @Schema(defaultValue = "false")
        private Boolean cancelled;

        private Integer order;
        private BigDecimal balance;

        @Schema(format = "date")
        private String balanceUpdatedAt;

        private String device;
        private String account;
        private List<String> services;
        private String dataCheck;

        @io.swagger.v3.oas.annotations.media.ArraySchema(maxItems = 1000)
        private List<ExpenseRecord> records;
    }

    @Data
    @Schema(name = "ExpenseRecord")
    public static class ExpenseRecord {
        private String id;

        private String type;

        @Schema(format = "date")
        private String date;

        @Schema(minimum = "0")
        private BigDecimal amount;

        @Schema(pattern = "^[A-Z]{3}$")
        private String currency;
        // 固定录入时汇率：1 单位原币对应的人民币金额。
        @Schema(minimum = "0.00000001")
        private BigDecimal rate;

        @Schema(format = "date")
        private String extendsTo;

        private String title;
    }
}
