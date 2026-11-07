package run.halo.simnook;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class PageRouter {
    @Bean
    RouterFunction<ServerResponse> simnookPages() {
        return RouterFunctions.route()
                .GET("/simnook", r -> ServerResponse.ok().render("plugin:simnook:simnook"))
                .GET("/simnook/", r -> ServerResponse.ok().render("plugin:simnook:simnook"))
                .GET("/simnook/card/{id}", r -> ServerResponse.ok().render("plugin:simnook:simnook"))
                .build();
    }
}
