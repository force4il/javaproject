package src.service;

import src.exceptions.ErrorCode;
import src.exceptions.tree.TreeOperationException;
import src.exceptions.tree.TreeStateException;
import src.model.CatalogItem;
import src.tree.BTree;

import java.util.List;

public final class TreeService {

    private BTree<CatalogItem> tree = new BTree<>();

    public synchronized BTree<CatalogItem> raw() {
        return tree;
    }

    public synchronized int size() {
        return tree.size();
    }

    public synchronized boolean isEmpty() {
        return tree.isEmpty();
    }

    public synchronized void clear() {
        tree.clear();
    }

    public synchronized List<CatalogItem> all() {
        return tree.inOrder();
    }

    public synchronized BTree.FillStats stats() {
        return tree.stats();
    }

    public synchronized CatalogItem search(int key) throws TreeOperationException {
        return tree.search(key);
    }

    public synchronized boolean insertIfAbsent(CatalogItem item) throws TreeOperationException {
        try {
            tree.insert(item.getItemNumber(), item);
            return true;
        } catch (TreeOperationException e) {
            if (e.getErrorCode() == ErrorCode.TREE_DUPLICATE_KEY)
                return false;

            throw e;
        }
    }

    public synchronized void replaceAll(List<CatalogItem> items) throws TreeOperationException {
        if (items == null)
            throw new IllegalArgumentException("Список товаров не может быть null");

        BTree<CatalogItem> replacement = new BTree<>();
        for (CatalogItem item : items) {
            if (item == null)
                throw new IllegalArgumentException("Список содержит null");

            replacement.insert(item.getItemNumber(), item);
        }

        try {
            replacement.validateInvariants();
        } catch (TreeStateException ex) {
            throw new TreeOperationException(
                    ErrorCode.TREE_STATE_CORRUPTED, "Не удалось построить дерево: " + ex.getMessage(), ex
            );
        }

        tree = replacement;
    }


    public synchronized boolean deleteIfExists(int key) throws TreeOperationException {
        try {
            tree.delete(key);
            return true;
        } catch (TreeOperationException e) {
            if (e.getErrorCode() == ErrorCode.TREE_KEY_NOT_FOUND)
                return false;

            throw e;
        }
    }

    public synchronized List<CatalogItem> range(int from, int to) throws TreeOperationException {
        return tree.rangeScan(from, to);
    }

    public synchronized void replace(int oldKey, CatalogItem updated) throws TreeOperationException {
        int newKey = updated.getItemNumber();
        if (oldKey != newKey && tree.search(newKey) != null)
            throw new TreeOperationException(ErrorCode.TREE_DUPLICATE_KEY, "ключ " + newKey + " уже существует");

        CatalogItem old = tree.search(oldKey);
        if (old == null)
            throw new TreeOperationException(ErrorCode.TREE_KEY_NOT_FOUND, "ключ " + oldKey + " отсутствует");

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