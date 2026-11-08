package run.halo.simnook;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static run.halo.simnook.BackupModels.*;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.nio.charset.StandardCharsets;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public class BackupEndpoint implements CustomEndpoint {
    private final BackupService backups;

    public BackupEndpoint(BackupService backups) {
        this.backups = backups;
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.simnook.halo.run/v1alpha1");
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET(
                        "backups/export",
                        request -> {
                            var format = request.queryParam("format").orElse("json");
                            if ("json".equals(format))
                                return backups.export()
                                        .flatMap(value -> download("simnook-backup.json", MediaType.APPLICATION_JSON)
                                                .bodyValue(value));
                            return backups.csv(format)
                                    .flatMap(value -> download(
                                                    "simnook-" + format + ".csv",
                                                    new MediaType("text", "csv", StandardCharsets.UTF_8))
                                            .bodyValue(value));
                        },
                        builder -> builder.operationId("exportBackup")
                                .tag("SimnookManagementV1alpha1")
                                .parameter(parameterBuilder()
                                        .name("format")
                                        .in(ParameterIn.QUERY)
                                        .implementation(String.class))
                                .response(responseBuilder().implementation(BackupDocument.class)))
                .POST(
                        "backups/preview",
                        request -> body(request, PreviewRequest.class)
                                .flatMap(value -> backups.preview(value.content()))
                                .flatMap(value -> ServerResponse.ok()
                                        .cacheControl(CacheControl.noStore())
                                        .bodyValue(value)),
                        builder -> builder.operationId("previewBackup")
                                .tag("SimnookManagementV1alpha1")
                                .requestBody(requestBodyBuilder().implementation(PreviewRequest.class))
                                .response(responseBuilder().implementation(BackupPreview.class)))
                .POST(
                        "backups/restore",
                        request -> body(request, RestoreRequest.class)
                                .flatMap(backups::restore)
                                .flatMap(value -> ServerResponse.ok()
                                        .cacheControl(CacheControl.noStore())
                                        .bodyValue(value)),
                        builder -> builder.operationId("restoreBackup")
                                .tag("SimnookManagementV1alpha1")
                                .requestBody(requestBodyBuilder().implementation(RestoreRequest.class))
                                .response(responseBuilder().implementation(RestoreResult.class)))
                .build();
    }

    private static ServerResponse.BodyBuilder download(String filename, MediaType contentType) {
        return ServerResponse.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
    }

    private static <T> Mono<T> body(ServerRequest request, Class<T> type) {
        // JSON 字符串封装存在转义开销，原始备份仍由服务层严格限制为 32 MiB。
        return DataBufferUtils.join(request.bodyToFlux(DataBuffer.class), 96 * 1024 * 1024)
                .publishOn(Schedulers.boundedElastic())
                .map(buffer -> {
                    try {
                        var bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        try {
                            var value = BackupService.JSON.readValue(bytes, type);
                            if (value == null) throw BackupService.bad("请求不能为空");
                            return value;
                        } catch (RuntimeException invalid) {
                            throw BackupService.bad("请求 JSON 格式错误");
                        }
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .switchIfEmpty(Mono.error(BackupService.bad("请求不能为空")))
                .onErrorMap(
                        DataBufferLimitException.class,
                        error -> new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "备份文件不能超过 32 MiB"));
    }
}
