package run.halo.simnook;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public class ReminderEndpoint implements CustomEndpoint {
    private final ReminderService reminders;

    public ReminderEndpoint(ReminderService reminders) {
        this.reminders = reminders;
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.simnook.halo.run/v1alpha1");
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET(
                        "reminders/subscription",
                        request -> username(request)
                                .flatMap(reminders::view)
                                .flatMap(value -> ServerResponse.ok()
                                        .cacheControl(CacheControl.noStore())
                                        .bodyValue(value)),
                        builder -> builder.operationId("getReminderSubscription")
                                .tag("SimnookManagementV1alpha1")
                                .response(responseBuilder().implementation(ReminderService.SubscriptionView.class)))
                .PUT(
                        "reminders/subscription",
                        request -> username(request)
                                .zipWith(request.bodyToMono(ReminderService.SubscriptionUpdate.class))
                                .flatMap(pair -> reminders.subscribe(
                                        pair.getT1(), pair.getT2().subscribed()))
                                .flatMap(value -> ServerResponse.ok()
                                        .cacheControl(CacheControl.noStore())
                                        .bodyValue(value)),
                        builder -> builder.operationId("updateReminderSubscription")
                                .tag("SimnookManagementV1alpha1")
                                .requestBody(
                                        requestBodyBuilder().implementation(ReminderService.SubscriptionUpdate.class))
                                .response(responseBuilder().implementation(ReminderService.SubscriptionView.class)))
                .build();
    }

    private Mono<String> username(ServerRequest request) {
        return request.principal()
                .ofType(Authentication.class)
                .filter(auth -> auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
                .map(Authentication::getName)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED)))
                .flatMap(username -> reminders
                        .canManage(username)
                        .flatMap(allowed -> allowed
                                ? Mono.just(username)
                                : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN))));
    }
}
