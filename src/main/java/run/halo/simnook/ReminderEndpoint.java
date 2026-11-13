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
                        "reminderchannels",
                        request -> username(request)
                                .then(reminders.view())
                                .flatMap(value -> ServerResponse.ok()
                                        .cacheControl(CacheControl.noStore())
                                        .bodyValue(value)),
                        builder -> builder.operationId("listReminderChannels")
                                .tag("ReminderChannels")
                                .response(responseBuilder().implementation(ReminderService.ChannelsView.class)))
                .PUT(
                        "reminderchannels",
                        request -> username(request)
                                .then(request.bodyToMono(ReminderService.ChannelInput.class))
                                .flatMap(reminders::save)
                                .flatMap(value -> ServerResponse.ok()
                                        .cacheControl(CacheControl.noStore())
                                        .bodyValue(value)),
                        builder -> builder.operationId("saveReminderChannel")
                                .tag("ReminderChannels")
                                .requestBody(requestBodyBuilder().implementation(ReminderService.ChannelInput.class))
                                .response(responseBuilder().implementation(ReminderService.ChannelView.class)))
                .DELETE(
                        "reminderchannels/{name}",
                        request -> username(request)
                                .then(Mono.defer(
                                        () -> reminders.delete(request.pathVariable("name"), version(request))))
                                .then(ServerResponse.noContent().build()),
                        builder -> builder.operationId("deleteReminderChannel")
                                .tag("ReminderChannels")
                                .parameter(org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder()
                                        .name("name")
                                        .in(io.swagger.v3.oas.annotations.enums.ParameterIn.PATH)
                                        .required(true)
                                        .implementation(String.class))
                                .parameter(org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder()
                                        .name("version")
                                        .in(io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
                                        .required(true)
                                        .implementation(Long.class)))
                .POST(
                        "reminderchannels/{name}/test",
                        request -> username(request)
                                .then(reminders.test(request.pathVariable("name")))
                                .flatMap(value -> ServerResponse.ok()
                                        .cacheControl(CacheControl.noStore())
                                        .bodyValue(value)),
                        builder -> builder.operationId("testReminderChannel")
                                .tag("ReminderChannels")
                                .parameter(org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder()
                                        .name("name")
                                        .in(io.swagger.v3.oas.annotations.enums.ParameterIn.PATH)
                                        .required(true)
                                        .implementation(String.class))
                                .response(responseBuilder().implementation(ReminderService.ChannelView.class)))
                .build();
    }

    private static Long version(ServerRequest request) {
        try {
            return Long.valueOf(request.queryParam("version").orElse(""));
        } catch (NumberFormatException error) {
            throw BackupService.bad("缺少有效渠道版本");
        }
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
