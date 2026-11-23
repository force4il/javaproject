package src.server;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import src.exceptions.TreeOperationException;
import src.model.CatalogItem;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    // GET /api/products
    @GetMapping
    public List<CatalogItem> getAll() {
        return catalogService.findAll();
    }

    // GET /api/products/{id}
    @GetMapping("/{id}")
    public CatalogItem getById(@PathVariable int id) throws TreeOperationException {
        return catalogService.findById(id);
    }

    // POST /api/products → 201 + Location
    @PostMapping
    public ResponseEntity<CatalogItem> create(@RequestBody CatalogItem item)
            throws TreeOperationException {
        CatalogItem created = catalogService.create(item);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getItemNumber())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/products/{id} — только для Editable
    @PutMapping("/{id}")
    public CatalogItem update(@PathVariable int id,
                              @RequestBody CatalogItem item)
            throws TreeOperationException {
        return catalogService.replace(id, item);
    }

    // DELETE /api/products/{id}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) throws TreeOperationException {
        catalogService.delete(id);
    }

    // Дополнительная операция из лаб. 2: диапазонный скан
    @GetMapping("/range")
    public List<CatalogItem> range(@RequestParam int from, @RequestParam int to)
            throws TreeOperationException {
        return catalogService.range(from, to);
    }
}