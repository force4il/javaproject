package src.api;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import src.exceptions.api.ValidationException;
import src.exceptions.tree.TreeOperationException;
import src.tree.BTree;

import java.util.*;

@RestController
@RequestMapping("/api/products")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductDto> getAllProductsWIthFilters(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) Integer minStock
    ) {
        if ((minPrice != null && minPrice < 0) || (maxPrice != null && maxPrice < 0))
            throw new ValidationException("minPrice and maxPrice cannot be negative");
        if (minPrice != null && maxPrice != null && minPrice > maxPrice)
            throw new ValidationException("minPrice cannot be greater than maxPrice");
        if (minStock != null && minStock < 0)
            throw new ValidationException("minStock cannot be negative");

        return service.findAll().stream()
                .filter(i -> name == null || i.getProductName()
                        .toLowerCase(Locale.ROOT)
                        .contains(name.toLowerCase(Locale.ROOT)))
                .filter(i -> category == null || i.getCategory().equalsIgnoreCase(category))
                .filter(i -> type == null || i.getType().getCsvName().equalsIgnoreCase(type))
                .filter(i -> minPrice == null || i.getPrice() >= minPrice)
                .filter(i -> maxPrice == null || i.getPrice() <= maxPrice)
                .filter(i -> minStock == null || i.getRemainder() >= minStock)

                .map(ProductDto::toDomainEntity)
                .toList();
    }

    @GetMapping("/{id}")
    public ProductDto getProductById(@PathVariable int id) throws TreeOperationException {
        return ProductDto.toDomainEntity(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ProductDto> createProduct(
            @RequestBody ProductDto dto
    ) throws TreeOperationException {
        var item = service.create(dto.toEntity());
        return ResponseEntity.created(ServletUriComponentsBuilder
                        .fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(item.getItemNumber())
                        .toUri()
        ).body(ProductDto.toDomainEntity(item));
    }

    @PutMapping("/{id}")
    public ProductDto updateProduct(
            @PathVariable int id,
            @RequestBody ProductDto dto
    ) throws TreeOperationException {
        return ProductDto.toDomainEntity(service.replace(id, dto.toEntity()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable int id) throws TreeOperationException {
        service.delete(id);
    }

    @GetMapping("/range")
    public List<ProductDto> rangeSearch(
            @RequestParam int from,
            @RequestParam int to
    ) throws TreeOperationException {
        return service.range(from, to)
                .stream()
                .map(ProductDto::toDomainEntity)
                .toList();
    }

    @GetMapping("/stats")
    public BTree.FillStats statsBTree() {
        return service.stats();
    }
}