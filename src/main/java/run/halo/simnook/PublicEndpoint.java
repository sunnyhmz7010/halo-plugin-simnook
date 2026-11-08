package run.halo.simnook;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Component
public class PublicEndpoint implements CustomEndpoint {
    private final ReactiveExtensionClient client;
    private final ReactiveSettingFetcher settings;
    private final PluginContext pluginContext;

    public PublicEndpoint(
            ReactiveExtensionClient client, ReactiveSettingFetcher settings, PluginContext pluginContext) {
        this.client = client;
        this.settings = settings;
        this.pluginContext = pluginContext;
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.simnook.halo.run/v1alpha1");
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
                .GET("cards", request -> {
                    return settings.getSettingValues()
                            .map(RenewalSettings::from)
                            .flatMap(schedule -> client.list(
                                            SimCard.class,
                                            c -> c.getMetadata().getDeletionTimestamp() == null,
                                            PublicCard.SOURCE_ORDER)
                                    .index()
                                    .map(item -> PublicCard.from(
                                            item.getT2(),
                                            schedule.today(),
                                            item.getT1().intValue()))
                                    .collectList()
                                    .flatMap(cards -> ServerResponse.ok()
                                            .bodyValue(Map.of("cards", cards, "demo", false, "full", false))));
                })
                .GET("site", this::site)
                .build();
    }

    private Mono<ServerResponse> site(ServerRequest request) {
        var signedIn = request.principal()
                .ofType(Authentication.class)
                .map(auth -> auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
                .defaultIfEmpty(false);
        return settings.getSettingValues()
                .map(PublicDisplaySettings::from)
                .zipWith(signedIn)
                // 只返回登录标志来控制入口，避免共享缓存复用用户的登录状态。
                .flatMap(result -> ServerResponse.ok()
                        .cacheControl(CacheControl.noStore())
                        .varyBy(HttpHeaders.COOKIE, HttpHeaders.AUTHORIZATION)
                        .bodyValue(Map.of(
                                "sitename", result.getT1().title(),
                                "version", pluginContext.getVersion(),
                                "gitHash", "",
                                "demo", false,
                                "loggedIn", result.getT2(),
                                "setupRequired", false,
                                "theme_settings", result.getT1().theme())));
    }
}
