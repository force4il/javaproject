package src.server;

import org.springframework.stereotype.Service;
import src.exceptions.ErrorCode;
import src.exceptions.TreeOperationException;
import src.model.CatalogItem;
import src.service.TreeService;
import src.tree.BTree;

import java.util.List;

@Service
public class CatalogService {

    private final TreeService treeService;

    public CatalogService(TreeService treeService) {
        this.treeService = treeService;
    }

    // ---------- GUI-friendly (мягкие) ----------

    public List<CatalogItem> findAll()               { return treeService.all(); }
    public int size()                                { return treeService.size(); }
    public boolean isEmpty()                         { return treeService.isEmpty(); }
    public void clear()                              { treeService.clear(); }
    public BTree<CatalogItem> raw()                  { return treeService.raw(); }
    public BTree.FillStats stats()                   { return treeService.stats(); }

    public CatalogItem findOrNull(int id) {
        try { return treeService.search(id); }
        catch (TreeOperationException e) { return null; }
    }

    public boolean createIfAbsent(CatalogItem item) throws TreeOperationException {
        return treeService.insertIfAbsent(item);
    }

    public boolean deleteIfExists(int id) throws TreeOperationException {
        return treeService.deleteIfExists(id);
    }

    public void replaceAll(List<CatalogItem> items) throws TreeOperationException {
        treeService.replaceAll(items);
    }

    public CatalogItem replace(int oldId, CatalogItem updated) throws TreeOperationException {
        treeService.replace(oldId, updated);
        return updated;
    }

    // ---------- REST-friendly (строгие, кидают исключения) ----------

    public CatalogItem findById(int id) throws TreeOperationException {
        CatalogItem item = treeService.search(id);
        if (item == null)
            throw new TreeOperationException(ErrorCode.TREE_KEY_NOT_FOUND, "id=" + id);
        return item;
    }

    public CatalogItem create(CatalogItem item) throws TreeOperationException {
        if (!treeService.insertIfAbsent(item))
            throw new TreeOperationException(ErrorCode.TREE_DUPLICATE_KEY,
                    "id=" + item.getItemNumber() + " уже существует");
        return item;
    }

    public void delete(int id) throws TreeOperationException {
        if (!treeService.deleteIfExists(id))
            throw new TreeOperationException(ErrorCode.TREE_KEY_NOT_FOUND, "id=" + id);
    }

    public List<CatalogItem> range(int from, int to) throws TreeOperationException {
        return treeService.range(from, to);
    }
}