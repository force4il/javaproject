package src.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;

import src.exceptions.api.FileSizeException;
import src.exceptions.api.UnsupportedFormatException;
import src.serializable.*;
import src.model.CatalogItem;
import src.exceptions.tree.TreeOperationException;

import java.nio.file.*;
import java.io.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class TransferController {

    private final CatalogService service;

    public TransferController(CatalogService service) {
        this.service = service;
    }

    private Serializer<List<CatalogItem>> serializer(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> new JsonCatalogSerializer();
            case "bin" -> new BinaryCatalogSerializer();
            default -> throw new UnsupportedFormatException("Формат должен быть json или bin");
        };
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam String format,
            @RequestParam(defaultValue = "3") int version
    ) throws IOException {
        var ser = serializer(format);
        Path tmp = Files.createTempFile("catalog-", "." + ser.format());
        try {
            ser.save(service.findAll(), tmp, version);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=catalog." + ser.format())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(Files.readAllBytes(tmp));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> load(
            @RequestParam String format,
            @RequestParam MultipartFile file
    ) throws IOException, TreeOperationException {
        var ser = serializer(format);
        if (file.isEmpty() || file.getSize() > 256L * 1024 * 1024)
            throw new FileSizeException("Недопустимый размер файла");

        Path tmp = Files.createTempFile("catalog-", "." + ser.format());
        try {
            file.transferTo(tmp);
            var result = ser.load(tmp);
            service.replaceAll(result.data());
            return Map.of("count", result.data().size(), "sourceVersion", result.sourceVersion(),
                    "targetVersion", result.targetVersion()
            );
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}