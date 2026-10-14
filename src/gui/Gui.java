package gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Gui extends Application {

    private static final double WIDTH  = 1200;
    private static final double HEIGHT = 720;

    @Override
    public void start(Stage primaryStage) {
        ProductController controller = new ProductController(primaryStage);
        Scene scene = new Scene(controller.getView(), WIDTH, HEIGHT);
        primaryStage.setTitle("Управление товарами — B-дерево 4-го порядка");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}