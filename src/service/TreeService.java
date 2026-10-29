package service;

import exceptions.ErrorCode;
import exceptions.TreeOperationException;
import exceptions.TreeStateException;
import model.CatalogItem;
import tree.BTree;

import java.util.List;

public final class TreeService {

    private BTree<CatalogItem> tree = new BTree<>();
    public BTree<CatalogItem> raw() { return tree; }
    public int  size() { return tree.size(); }
    public boolean isEmpty() { return tree.isEmpty(); }
    public void clear() { tree.clear(); }
    public List<CatalogItem> all() { return tree.inOrder(); }
    public BTree.FillStats stats() { return tree.stats(); }

    public CatalogItem search(int key) throws TreeOperationException {
        return tree.search(key);
    }

    public boolean insertIfAbsent(CatalogItem item) throws TreeOperationException {
        try {
            tree.insert(item.getItemNumber(), item);
            return true;
        } catch (TreeOperationException e) {
            if (e.getErrorCode() == ErrorCode.TREE_DUPLICATE_KEY)
                return false;

            throw e;
        }
    }

    public void replaceAll(List<CatalogItem> items) throws TreeOperationException {
        BTree<CatalogItem> replacement = new BTree<>();
        for (CatalogItem item : items)
            replacement.insert(item.getItemNumber(), item);

        try {
            replacement.validateInvariants();
        } catch (TreeStateException ex) {
            throw new TreeOperationException(ErrorCode.TREE_STATE_CORRUPTED,
                    "не удалось построить дерево: " + ex.getMessage(), ex);
        }
        tree = replacement;
    }

    public boolean deleteIfExists(int key) throws TreeOperationException {
        try {
            tree.delete(key);
            return true;
        } catch (TreeOperationException e) {
            if (e.getErrorCode() == ErrorCode.TREE_KEY_NOT_FOUND)
                return false;

            throw e;
        }
    }

    public List<CatalogItem> range(int from, int to) throws TreeOperationException {
        return tree.rangeScan(from, to);
    }

    public void replace(int oldKey, CatalogItem updated) throws TreeOperationException {
        int newKey = updated.getItemNumber();
        if (oldKey != newKey && tree.search(newKey) != null)
            throw new TreeOperationException(ErrorCode.TREE_DUPLICATE_KEY,
                    "ключ " + newKey + " уже существует");

        CatalogItem old = tree.search(oldKey);
        if (old == null)
            throw new TreeOperationException(ErrorCode.TREE_KEY_NOT_FOUND,
                    "ключ " + oldKey + " отсутствует");

        tree.delete(oldKey);
        try {
            tree.insert(newKey, updated);
        } catch (TreeOperationException e) {
            try {
                tree.insert(oldKey, old);
            } catch (TreeOperationException rollback) {
                e.addSuppressed(rollback);
            }

            throw e;
        }
    }
}