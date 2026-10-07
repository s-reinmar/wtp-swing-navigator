package pl.j.reinmar.mapwaw.wtp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogTest {
    private static final Logger logger = LoggerFactory.getLogger(LogTest.class);

    public static void main(String[] args) {
        logger.info("System logowania SLF4J + Logback został pomyślnie skonfigurowany.");
        logger.debug("To jest komunikat debugowania (widoczny przy poziomie DEBUG).");
        logger.warn("Przykładowe ostrzeżenie w aplikacji.");
    }
}
