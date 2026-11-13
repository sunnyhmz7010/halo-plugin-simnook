package run.halo.simnook;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.nio.file.Files;
import java.nio.file.Path;

/** 离线从后端模型生成渠道客户端契约，无需部署未验证的插件来获取 API 文档。 */
public class ReminderOpenApi {
    public static void main(String[] args) throws Exception {
        var components = new Components();
        for (var type : new Class<?>[] {
            ReminderService.ChannelInput.class, ReminderService.ChannelsView.class, ReminderService.ChannelView.class
        }) {
            ModelConverters.getInstance().readAll(type).forEach(components::addSchemas);
        }
        var base = "/apis/console.api.simnook.halo.run/v1alpha1/reminderchannels";
        var paths = new Paths();
        paths.addPathItem(
                base,
                new PathItem()
                        .get(operation("listReminderChannels", "ReminderChannelsView"))
                        .put(operation("saveReminderChannel", "ReminderChannelView")
                                .requestBody(
                                        new RequestBody().required(true).content(content("ReminderChannelInput")))));
        paths.addPathItem(
                base + "/{name}",
                new PathItem()
                        .delete(operation("deleteReminderChannel", null)
                                .addParametersItem(name())
                                .addParametersItem(new Parameter()
                                        .name("version")
                                        .in("query")
                                        .required(true)
                                        .schema(new Schema<>().type("integer").format("int64")))));
        paths.addPathItem(
                base + "/{name}/test",
                new PathItem()
                        .post(operation("testReminderChannel", "ReminderChannelView")
                                .addParametersItem(name())));
        var api = new OpenAPI()
                .openapi("3.0.3")
                .info(new Info().title("SIMnook reminders").version("1"))
                .components(components)
                .paths(paths);
        var output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        Files.writeString(output, Json.pretty(api));
    }

    private static Parameter name() {
        return new Parameter().name("name").in("path").required(true).schema(new Schema<>().type("string"));
    }

    private static Content content(String model) {
        return new Content()
                .addMediaType(
                        "application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + model)));
    }

    private static Operation operation(String id, String model) {
        var response = new ApiResponse().description("Success");
        if (model != null) response.content(content(model));
        return new Operation()
                .operationId(id)
                .addTagsItem("ReminderChannels")
                .responses(new ApiResponses().addApiResponse(model == null ? "204" : "200", response));
    }
}
