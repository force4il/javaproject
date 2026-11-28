package src.api;

import org.springframework.stereotype.Service;
import src.exceptions.ErrorCode;
import src.exceptions.api.ReadOnlyException;
import src.exceptions.api.ValidationException;
import src.exceptions.tree.TreeOperationException;
import src.model.CatalogItem;
import src.model.Editable;
import src.service.TreeService;
import src.tree.BTree;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class CatalogService {

    private final TreeService treeService;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    public CatalogService(TreeService treeService) {
        this.treeService = treeService;
    }

    public void onChange(Runnable listener) {
        listeners.add(listener);
    }

    private void changed() {
        for (Runnable listener : listeners)
            listener.run();
    }

    public Object lock() {
        return treeService;
    }

    private void validate(CatalogItem item) {
        if (!(item instanceof Editable editable))
            throw new ReadOnlyException("Товар не редактируется");

        var errors = editable.validate();
        if (!errors.isEmpty())
            throw new ValidationException(String.join("; ", errors));
    }

    public List<CatalogItem> findAll() {
        return treeService.all();
    }

    public int size() {
        return treeService.size();
    }

    public boolean isEmpty() {
        return treeService.isEmpty();
    }

    public void clear() {
        treeService.clear();
        changed();
    }

    public BTree<CatalogItem> raw() {
        return treeService.raw();
    }

    public BTree.FillStats stats() {
        return treeService.stats();
    }

    public CatalogItem findOrNull(int id) {
        try {
            return treeService.search(id);
        } catch (TreeOperationException e) {
            return null;
        }
    }

    public boolean createIfAbsent(CatalogItem item) throws TreeOperationException {
        validate(item);
        boolean ok = treeService.insertIfAbsent(item);
        if (ok)
            changed();

        return ok;
    }

    public boolean deleteIfExists(int id) throws TreeOperationException {
        boolean ok = treeService.deleteIfExists(id);
        if (ok)
            changed();

        return ok;
    }

    public void replaceAll(List<CatalogItem> items) throws TreeOperationException {
        treeService.replaceAll(items);
        changed();
    }

    public CatalogItem replace(int oldId, CatalogItem updated) throws TreeOperationException {
        synchronized (treeService) {
            if (!(findById(oldId) instanceof Editable))
                throw new ReadOnlyException("Товар не редактируется");

            validate(updated);
            treeService.replace(oldId, updated);
        }

        changed();
        return updated;
    }

    public CatalogItem findById(int id) throws TreeOperationException {
        CatalogItem item = treeService.search(id);
        if (item == null)
            throw new TreeOperationException(ErrorCode.TREE_KEY_NOT_FOUND, "id=" + id);

        return item;
    }

    public CatalogItem create(CatalogItem item) throws TreeOperationException {
        if (!createIfAbsent(item))
            throw new TreeOperationException(ErrorCode.TREE_DUPLICATE_KEY,
                    "id=" + item.getItemNumber() + " уже существует"
            );

        return item;
    }

    public void delete(int id) throws TreeOperationException {
        if (!deleteIfExists(id))
            throw new TreeOperationException(ErrorCode.TREE_KEY_NOT_FOUND, "id=" + id);
    }

    public List<CatalogItem> range(int from, int to) throws TreeOperationException {
        return treeService.range(from, to);
    }
}