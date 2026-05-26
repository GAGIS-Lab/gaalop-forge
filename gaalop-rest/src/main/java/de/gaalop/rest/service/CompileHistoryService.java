package de.gaalop.rest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gaalop.rest.dto.CompileRequest;
import de.gaalop.rest.dto.CompileResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CompileHistoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompileHistoryService.class);

    private final ObjectMapper objectMapper;
    private final Path historyDirectory;
    private final boolean enabled;

    public CompileHistoryService(
            ObjectMapper objectMapper,
            @Value("${gaalop.compile-history.enabled:true}") boolean enabled,
            @Value("${gaalop.compile-history.path:compile-history}") String historyPath
    ) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.historyDirectory = Paths.get(historyPath).toAbsolutePath().normalize();
    }

    public void recordSuccess(CompileRequest request, CompileResponse response) {
        if (!enabled) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        LocalDate date = now.toLocalDate();
        Path dayDirectory = historyDirectory.resolve(date.toString());
        Path jsonlFile = dayDirectory.resolve("gaalop-success.jsonl");

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("timestamp", now.toString());
        record.put("request", request);
        record.put("response", response);

        try {
            Files.createDirectories(dayDirectory);
            String line = objectMapper.writeValueAsString(record) + System.lineSeparator();
            synchronized (this) {
                Files.writeString(
                        jsonlFile,
                        line,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to write successful compile history to {}", jsonlFile, ex);
        }
    }
}
