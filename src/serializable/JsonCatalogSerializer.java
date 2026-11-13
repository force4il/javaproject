
package serializable;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import model.*;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

public final class JsonCatalogSerializer
        implements Serializer<List<CatalogItem>> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String format() {
        return "json";
    }

    private static String sha256(byte[] bytes)
            throws IOException {
        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(bytes);

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 недоступен", ex);
        }
    }

    private static JsonNode required(JsonNode node, String key)
            throws IOException {

        JsonNode value = node.get(key);

        if (value == null || value.isNull()) {
            throw new IOException("Отсутствует поле: " + key);
        }

        return value;
    }

    private static String string(JsonNode node, String key)
            throws IOException {

        JsonNode value = required(node, key);

        if (!value.isTextual()) {
            throw new IOException("Ожидается строка: " + key);
        }

        return value.textValue();
    }

    private static int integer(JsonNode node, String key)
            throws IOException {

        JsonNode value = required(node, key);

        if (!value.isIntegralNumber()
                || !value.canConvertToInt()) {
            throw new IOException("Ожидается int: " + key);
        }

        return value.intValue();
    }

    @Override
    public void save(
            List<CatalogItem> data,
            Path file,
            int version
    ) throws IOException {

        CatalogCodec.requireValidVersion(version, "JSON");

        List<CatalogItem> items = CatalogCodec.checked(data);
        CatalogCodec.checkVersionItems(items, version);

        ObjectNode root = mapper.createObjectNode();
        root.put("schema_version", version);

        ArrayNode array = root.putArray("items");

        for (CatalogItem item : items) {
            CatalogCodec.checkCancelled();

            ObjectNode node = array.addObject();

            // В v1 тип объекта отсутствует.
            if (version >= 2) {
                node.put("type", item.getType().getCsvName());
            }

            node.put("itemNumber", item.getItemNumber());
            node.put("productName", item.getProductName());
            node.put("category", item.getCategory());
            node.put("price", item.getPrice());
            node.put("remainder", item.getRemainder());

            // Дополнительные поля товара с гарантией.
            if (version >= 2
                    && item instanceof ProductWithWarranty warranty) {

                node.put(
                        "startOfWarranty",
                        warranty.getStartOfWarranty().toString()
                );

                node.put(
                        "warrantyMonths",
                        warranty.getWarrantyMonths()
                );
            }
        }

        // В v3 добавляем метаданные и контрольную сумму.
        if (version == 3) {
            ObjectNode metadata = root.putObject("metadata");

            metadata.put(
                    "exported_at",
                    Instant.now().toString()
            );

            metadata.put("item_count", items.size());

            metadata.put(
                    "sha256",
                    sha256(mapper.writeValueAsBytes(array))
            );
        }

        // Сначала записываем во временный файл.
        Path target = file.toAbsolutePath();

        Path temp = Files.createTempFile(
                target.getParent(),
                ".catalog-",
                ".tmp"
        );

        try {
            try (OutputStream out = new FilterOutputStream(
                    Files.newOutputStream(temp)) {

                @Override
                public void write(int b) throws IOException {
                    CatalogCodec.checkCancelled();
                    this.out.write(b);
                }

                @Override
                public void write(
                        byte[] bytes,
                        int offset,
                        int length
                ) throws IOException {

                    CatalogCodec.checkCancelled();
                    this.out.write(bytes, offset, length);
                }
            }) {
                mapper.writerWithDefaultPrettyPrinter()
                        .writeValue(out, root);
            }

            CatalogCodec.checkCancelled();

            // Заменяем целевой файл только после успешной записи.
            Files.move(
                    temp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (AtomicMoveNotSupportedException ex) {
            throw new IOException(
                    "Атомарное сохранение не поддерживается",
                    ex
            );
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public Loaded<List<CatalogItem>> load(Path file)
            throws IOException {

        // Ограничение размера входного файла.
        if (Files.size(file) > CatalogCodec.MAX_FILE_BYTES) {
            throw new IOException(
                    "JSON превышает лимит 256 МиБ"
            );
        }

        JsonNode root;

        try (InputStream in = new InterruptibleInputStream(
                new BoundedInputStream(
                        Files.newInputStream(file),
                        CatalogCodec.MAX_FILE_BYTES
                ))) {

            root = mapper.readTree(in);
        }

        CatalogCodec.checkCancelled();

        if (root == null || !root.isObject()) {
            throw new IOException("Некорректный корень JSON");
        }

        // Определяем версию файла.
        int version = integer(root, "schema_version");
        CatalogCodec.requireValidVersion(version, "JSON");

        JsonNode array = required(root, "items");

        if (!array.isArray()
                || array.size() > CatalogCodec.MAX_ITEMS) {
            throw new IOException("Некорректный массив items");
        }

        // Проверка метаданных версии 3.
        if (version == 3) {
            JsonNode metadata = required(root, "metadata");

            if (!metadata.isObject()) {
                throw new IOException("Некорректный metadata");
            }

            if (integer(metadata, "item_count") != array.size()) {
                throw new IOException("Не совпадает item_count");
            }

            try {
                Instant.parse(string(metadata, "exported_at"));
            } catch (RuntimeException ex) {
                throw new IOException(
                        "Некорректный exported_at",
                        ex
                );
            }

            String expected = string(metadata, "sha256");
            String actual = sha256(
                    mapper.writeValueAsBytes(array)
            );

            if (!actual.equals(expected)) {
                throw new IOException(
                        "Нарушена целостность JSON (SHA-256)"
                );
            }
        }

        List<CatalogItem> result =
                new ArrayList<>(array.size());

        // Восстанавливаем объекты каталога.
        for (JsonNode node : array) {
            CatalogCodec.checkCancelled();

            if (!node.isObject()) {
                throw new IOException(
                        "Запись должна быть объектом JSON"
                );
            }

            String type = version == 1
                    ? "Product"
                    : string(node, "type");

            if (version == 1 && node.has("type")) {
                throw new IOException(
                        "Поле type не предусмотрено в v1"
                );
            }

            String start = "Warranty".equals(type)
                    ? string(node, "startOfWarranty")
                    : null;

            Integer months = "Warranty".equals(type)
                    ? integer(node, "warrantyMonths")
                    : null;

            CatalogItem item = CatalogCodec.create(
                    type,
                    integer(node, "itemNumber"),
                    string(node, "productName"),
                    string(node, "category"),
                    integer(node, "price"),
                    integer(node, "remainder"),
                    start,
                    months
            );

            result.add(item);
        }

        List<CatalogItem> checked =
                CatalogCodec.checked(result);

        CatalogCodec.checkVersionItems(checked, version);

        return new Loaded<>(checked, version);
    }
}
