package run.halo.simnook;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/** 私有订阅与提交状态，不向号卡管理角色开放通用 CRUD。 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(
        group = "simnook.halo.run",
        version = "v1alpha1",
        kind = "RenewalReminder",
        plural = "renewalreminders",
        singular = "renewalreminder")
public class RenewalReminder extends AbstractExtension {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "RenewalReminderSpec")
    public static class Spec {
        private String username;
        private boolean enabled;
        private String lastSubmittedDate;
        private String lastSubmittedAt;
        private String lastError;
    }
}
