package pl.j.reinmar.mapwaw.wtp;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ResourceLoadingTest {

    public static void main(String[] args) throws IOException {
        checkConfigProperties();
        checkImage("icons/bus.png");
        checkImage("icons/tram.png");
        checkImage("icons/metro.png");
        System.out.println("Wszystkie zasoby zostały poprawnie załadowane.");
    }

    private static void checkConfigProperties() throws IOException {
        try (InputStream input = getResource("config.properties")) {
            Properties properties = new Properties();
            properties.load(input);
            if (properties.isEmpty()) {
                throw new IllegalStateException("Plik config.properties nie powinien być pusty.");
            }
        }
    }

    private static void checkImage(String resourcePath) throws IOException {
        try (InputStream input = getResource(resourcePath)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new IllegalStateException("Nie można poprawnie odczytać obrazu: " + resourcePath);
            }
        }
    }

    private static InputStream getResource(String resourcePath) {
        InputStream input = ResourceLoadingTest.class.getClassLoader().getResourceAsStream(resourcePath);
        if (input == null) {
            throw new IllegalStateException("Brak zasobu na classpath: " + resourcePath);
        }
        return input;
    }
}
