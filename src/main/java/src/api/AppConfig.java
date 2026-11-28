package src.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import src.service.TreeService;

@Configuration
public class AppConfig {

    @Bean
    public TreeService treeService() {
        return new TreeService();
    }
}