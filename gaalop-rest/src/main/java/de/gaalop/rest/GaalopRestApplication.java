package de.gaalop.rest;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.system.ApplicationHome;

@SpringBootApplication
public class GaalopRestApplication {

    public static void main(String[] args) {
        setApplicationHome();
        SpringApplication.run(GaalopRestApplication.class, args);
    }

    private static void setApplicationHome() {
        if (System.getProperty("gaalop.app.home") != null) {
            return;
        }

        try {
            Path appHome = new ApplicationHome(GaalopRestApplication.class).getDir().toPath();
            System.setProperty("gaalop.app.home", appHome.toAbsolutePath().normalize().toString());
        } catch (SecurityException ex) {
            System.setProperty("gaalop.app.home", Paths.get("").toAbsolutePath().normalize().toString());
        }
    }
}
