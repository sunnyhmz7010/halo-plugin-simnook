package run.halo.simnook;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(
        group = "simnook.halo.run",
        version = "v1alpha1",
        kind = "PaymentAccount",
        plural = "paymentaccounts",
        singular = "paymentaccount")
public class PaymentAccount extends AbstractExtension {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "PaymentAccountSpec")
    public static class Spec {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 100)
        private String name;

        @Schema(allowableValues = {"bank", "virtual", "alipay", "wechat", "paypal", "balance", "other"})
        private String type;

        @Schema(allowableValues = {"ok", "low", "frozen", "closed"})
        private String status;
    }
}
