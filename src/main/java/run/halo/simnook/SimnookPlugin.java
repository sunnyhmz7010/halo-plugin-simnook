package run.halo.simnook;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * Plugin main class to manage the lifecycle of the plugin.
 *
 * <p>This class must be public and have a public constructor.
 *
 * <p>Only one main class extending {@link BasePlugin} is allowed per plugin.
 *
 * @author Sunny
 * @since 1.0.0
 */
@Component
@EnableScheduling
public class SimnookPlugin extends BasePlugin {

    private final SchemeManager schemes;

    public SimnookPlugin(PluginContext pluginContext, SchemeManager schemes) {
        super(pluginContext);
        this.schemes = schemes;
    }

    @Override
    public void start() {
        schemes.register(RenewalReminder.class);
        // Halo 先标记删除，再异步回收。列表在分页前排除已标记的卡。
        schemes.register(
                SimCard.class,
                indexes -> indexes.add(IndexSpecs.<SimCard, Boolean>single("deleting", Boolean.class)
                        .indexFunc(card -> card.getMetadata().getDeletionTimestamp() != null)
                        .nullable(false)));
    }

    @Override
    public void stop() {
        schemes.unregister(Scheme.buildFromType(RenewalReminder.class));
        schemes.unregister(Scheme.buildFromType(SimCard.class));
    }
}
