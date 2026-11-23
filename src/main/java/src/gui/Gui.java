package src.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import src.server.CatalogApplication;
import src.server.CatalogService;

public class Gui extends Application {

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        springContext = new SpringApplicationBuilder(CatalogApplication.class)
                .run();
    }

    @Override
    public void start(Stage primaryStage) {
        CatalogService service = springContext.getBean(CatalogService.class);
        ProductController controller = new ProductController(primaryStage, service);
        Scene scene = new Scene(controller.getView(), 1600, 800);
        primaryStage.setTitle("Управление товарами — B-дерево 4-го порядка");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        springContext.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}