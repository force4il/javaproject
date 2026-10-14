package tree;

import exceptions.ErrorCode;
import exceptions.TreeOperationException;
import exceptions.TreeStateException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * B-дерево 4-го порядка (m = 4).
 *  - до 3 ключей в узле;
 *  - до 4 детей;
 *  - все листья на одном уровне;
 *  - рост "вверх" через расщепление полного корня.
 *
 * Пользовательские ошибки — TreeOperationException (checked).
 * Нарушения инвариантов — TreeStateException (unchecked, значит баг).
 */
public final class BTree<T> {

    public static final int ORDER    = 4;
    public static final int MAX_KEYS = ORDER - 1;             // 3
    public static final int MIN_KEYS = (ORDER + 1) / 2 - 1;   // 1

    /** Флаг: включать проверку инвариантов на каждой вставке/удалении (медленно). */
    public static boolean DEBUG_INVARIANTS = false;

    public static final class Node {
        final int[] keys = new int[MAX_KEYS];
        final Object[] values = new Object[MAX_KEYS];
        final Node[] children = new Node[ORDER];
        int numKeys = 0;
        boolean leaf = true;

        // геометрия визуализации
        double layoutX, layoutY;
        int    depth;
        final double[] keyX = new double[MAX_KEYS];

        public int     getNumKeys()      { return numKeys; }
        public int     getKey(int i)     { return keys[i]; }
        public Object  getRawValue(int i){ return values[i]; }
        public boolean isLeaf()          { return leaf; }
        public Node    getChild(int i)   { return children[i]; }
        public double  getLayoutX()      { return layoutX; }
        public double  getLayoutY()      { return layoutY; }
        public double  getKeyX(int i)    { return keyX[i]; }
        public int     getDepth()        { return depth; }
    }

    public record FillStats(int nodes, int leaves, int totalKeys, int height,
                            double avgFill, int minKeys, int maxKeys) {}

    private Node root = new Node();
    private int  size = 0;
    private final List<Node> lastVisited = new ArrayList<>();

    public Node getRoot()              { return root; }
    public int  size()                 { return size; }
    public boolean isEmpty()           { return size == 0; }
    public List<Node> getLastVisited() { return lastVisited; }

    @SuppressWarnings("unchecked")
    public T value(Node n, int i) { return (T) n.values[i]; }

    // =====================================================================
    //  Поиск
    // =====================================================================
    public T search(int key) {
        if (key <= 0)
            throw new TreeStateException(ErrorCode.TREE_INVALID_KEY,
                    "ключ должен быть положительным: " + key);
        lastVisited.clear();
        return searchRec(root, key);
    }

    @SuppressWarnings("unchecked")
    private T searchRec(Node n, int key) {
        lastVisited.add(n);
        int i = 0;
        while (i < n.numKeys && key > n.keys[i]) i++;
        if (i < n.numKeys && n.keys[i] == key) return (T) n.values[i];
        if (n.leaf) return null;
        return searchRec(n.children[i], key);
    }

    /** То же, что search, но бросает исключение, если ключа нет. */
    public T require(int key) throws TreeOperationException {
        T v = search(key);
        if (v == null)
            throw new TreeOperationException(ErrorCode.TREE_KEY_NOT_FOUND,
                    "ключ " + key + " отсутствует");
        return v;
    }

    // =====================================================================
    //  Вставка
    // =====================================================================
    public void insert(int key, T value) throws TreeOperationException {
        if (key <= 0)
            throw new TreeOperationException(ErrorCode.TREE_INVALID_KEY,
                    "ключ должен быть положительным: " + key);
        if (value == null)
            throw new TreeOperationException(ErrorCode.TREE_INVALID_KEY,
                    "значение не может быть null");

        if (searchRec(root, key) != null) {
            lastVisited.clear();
            throw new TreeOperationException(ErrorCode.TREE_DUPLICATE_KEY,
                    "ключ " + key + " уже существует");
        }

        lastVisited.clear();
        if (root.numKeys == MAX_KEYS) {
            Node newRoot = new Node();
            newRoot.leaf = false;
            newRoot.children[0] = root;
            split(newRoot, 0);
            root = newRoot;
        }

        insertNonFull(root, key, value);
        size++;

        lastVisited.clear();
        if (DEBUG_INVARIANTS)
            checkInvariants();
    }

    private void insertNonFull(Node n, int key, T value) {
        lastVisited.add(n);
        int i = n.numKeys - 1;
        if (n.leaf) {
            while (i >= 0 && key < n.keys[i]) {
                n.keys[i + 1]   = n.keys[i];
                n.values[i + 1] = n.values[i];
                i--;
            }
            n.keys[i + 1]   = key;
            n.values[i + 1] = value;
            n.numKeys++;
        } else {
            while (i >= 0 && key < n.keys[i]) i--;
            i++;
            if (n.children[i].numKeys == MAX_KEYS) {
                split(n, i);
                if (key > n.keys[i]) i++;
            }
            insertNonFull(n.children[i], key, value);
        }
    }

    private void split(Node parent, int idx) {
        Node full  = parent.children[idx];
        Node right = new Node();
        right.leaf = full.leaf;

        int mid = MAX_KEYS / 2;                     // 1
        right.numKeys = MAX_KEYS - mid - 1;         // 1
        for (int j = 0; j < right.numKeys; j++) {
            right.keys[j]   = full.keys[mid + 1 + j];
            right.values[j] = full.values[mid + 1 + j];
        }
        if (!full.leaf)
            for (int j = 0; j <= right.numKeys; j++)
                right.children[j] = full.children[mid + 1 + j];
        full.numKeys = mid;

        for (int j = parent.numKeys; j > idx; j--)
            parent.children[j + 1] = parent.children[j];
        parent.children[idx + 1] = right;

        for (int j = parent.numKeys - 1; j >= idx; j--) {
            parent.keys[j + 1]   = parent.keys[j];
            parent.values[j + 1] = parent.values[j];
        }
        parent.keys[idx]   = full.keys[mid];
        parent.values[idx] = full.values[mid];
        parent.numKeys++;
    }

    // =====================================================================
    //  Удаление
    // =====================================================================
    public T delete(int key) throws TreeOperationException {
        if (key <= 0)
            throw new TreeOperationException(ErrorCode.TREE_INVALID_KEY,
                    "ключ должен быть положительным: " + key);

        T old = search(key);
        if (old == null)
            throw new TreeOperationException(ErrorCode.TREE_KEY_NOT_FOUND,
                    "ключ " + key + " отсутствует");

        lastVisited.clear();
        deleteRec(root, key);
        if (root.numKeys == 0 && !root.leaf)
            root = root.children[0];

        size--;

        lastVisited.clear();
        if (DEBUG_INVARIANTS)
            checkInvariants();

        return old;
    }

    private void deleteRec(Node n, int key) {
        lastVisited.add(n);
        int idx = 0;
        while (idx < n.numKeys && key > n.keys[idx]) idx++;

        if (idx < n.numKeys && n.keys[idx] == key) {
            if (n.leaf) removeFromLeaf(n, idx);
            else        removeFromInternal(n, idx);
        } else if (n.leaf) {
            return;
        } else {
            boolean last = (idx == n.numKeys);
            if (n.children[idx].numKeys == MIN_KEYS) fill(n, idx);
            if (last && idx > n.numKeys) deleteRec(n.children[idx - 1], key);
            else                         deleteRec(n.children[idx], key);
        }
    }

    private void removeFromLeaf(Node n, int idx) {
        for (int i = idx + 1; i < n.numKeys; i++) {
            n.keys[i - 1]   = n.keys[i];
            n.values[i - 1] = n.values[i];
        }
        n.numKeys--;
    }

    private void removeFromInternal(Node n, int idx) {
        int  key = n.keys[idx];
        Node l   = n.children[idx];
        Node r   = n.children[idx + 1];

        if (l.numKeys > MIN_KEYS) {
            int pred = maxKey(l);
            n.keys[idx]   = pred;
            n.values[idx] = valueOf(l, pred);
            deleteRec(l, pred);
        } else if (r.numKeys > MIN_KEYS) {
            int succ = minKey(r);
            n.keys[idx]   = succ;
            n.values[idx] = valueOf(r, succ);
            deleteRec(r, succ);
        } else {
            merge(n, idx);
            deleteRec(l, key);
        }
    }

    private int maxKey(Node n) { while (!n.leaf) n = n.children[n.numKeys]; return n.keys[n.numKeys - 1]; }
    private int minKey(Node n) { while (!n.leaf) n = n.children[0];         return n.keys[0]; }

    private Object valueOf(Node n, int key) {
        int i = 0;
        while (i < n.numKeys && key > n.keys[i]) i++;
        if (i < n.numKeys && n.keys[i] == key) return n.values[i];
        if (n.leaf) return null;
        return valueOf(n.children[i], key);
    }

    private void fill(Node n, int idx) {
        if (idx > 0 && n.children[idx - 1].numKeys > MIN_KEYS)               borrowPrev(n, idx);
        else if (idx < n.numKeys && n.children[idx + 1].numKeys > MIN_KEYS)  borrowNext(n, idx);
        else {
            if (idx < n.numKeys) merge(n, idx);
            else                 merge(n, idx - 1);
        }
    }

    private void borrowPrev(Node parent, int idx) {
        Node child = parent.children[idx];
        Node sib   = parent.children[idx - 1];
        for (int i = child.numKeys - 1; i >= 0; i--) {
            child.keys[i + 1]   = child.keys[i];
            child.values[i + 1] = child.values[i];
        }
        if (!child.leaf)
            for (int i = child.numKeys; i >= 0; i--)
                child.children[i + 1] = child.children[i];
        child.keys[0]   = parent.keys[idx - 1];
        child.values[0] = parent.values[idx - 1];
        if (!child.leaf) child.children[0] = sib.children[sib.numKeys];
        parent.keys[idx - 1]   = sib.keys[sib.numKeys - 1];
        parent.values[idx - 1] = sib.values[sib.numKeys - 1];
        child.numKeys++;
        sib.numKeys--;
    }

    private void borrowNext(Node parent, int idx) {
        Node child = parent.children[idx];
        Node sib   = parent.children[idx + 1];
        child.keys[child.numKeys]   = parent.keys[idx];
        child.values[child.numKeys] = parent.values[idx];
        if (!child.leaf) child.children[child.numKeys + 1] = sib.children[0];
        parent.keys[idx]   = sib.keys[0];
        parent.values[idx] = sib.values[0];
        for (int i = 1; i < sib.numKeys; i++) {
            sib.keys[i - 1]   = sib.keys[i];
            sib.values[i - 1] = sib.values[i];
        }
        if (!sib.leaf)
            for (int i = 1; i <= sib.numKeys; i++)
                sib.children[i - 1] = sib.children[i];
        child.numKeys++;
        sib.numKeys--;
    }

    private void merge(Node parent, int idx) {
        Node l = parent.children[idx];
        Node r = parent.children[idx + 1];
        l.keys[l.numKeys]   = parent.keys[idx];
        l.values[l.numKeys] = parent.values[idx];
        for (int i = 0; i < r.numKeys; i++) {
            l.keys[l.numKeys + 1 + i]   = r.keys[i];
            l.values[l.numKeys + 1 + i] = r.values[i];
        }
        if (!l.leaf)
            for (int i = 0; i <= r.numKeys; i++)
                l.children[l.numKeys + 1 + i] = r.children[i];
        l.numKeys += 1 + r.numKeys;

        for (int i = idx; i < parent.numKeys - 1; i++) {
            parent.keys[i]   = parent.keys[i + 1];
            parent.values[i] = parent.values[i + 1];
        }
        for (int i = idx + 1; i < parent.numKeys; i++)
            parent.children[i] = parent.children[i + 1];
        parent.numKeys--;
    }

    // =====================================================================
    //  Диапазонный скан
    // =====================================================================
    public List<T> rangeScan(int from, int to) throws TreeOperationException {
        if (from > to)
            throw new TreeOperationException(ErrorCode.TREE_INVALID_RANGE,
                    "from=" + from + " > to=" + to);

        lastVisited.clear();
        List<T> out = new ArrayList<>();
        if (root.numKeys > 0)
            rangeScanRec(root, from, to, out);

        lastVisited.clear();
        return out;
    }

    @SuppressWarnings("unchecked")
    private void rangeScanRec(Node n, int from, int to, List<T> out) {
        lastVisited.add(n);
        for (int i = 0; i < n.numKeys; i++) {
            if (!n.leaf) {
                int childMin = (i == 0) ? Integer.MIN_VALUE : n.keys[i - 1] + 1;
                int childMax = n.keys[i] - 1;
                if (childMax >= from && childMin <= to)
                    rangeScanRec(n.children[i], from, to, out);
            }
            if (n.keys[i] >= from && n.keys[i] <= to)
                out.add((T) n.values[i]);
            if (n.keys[i] > to) return;
        }
        if (!n.leaf) {
            int childMin = (n.numKeys == 0) ? Integer.MIN_VALUE : n.keys[n.numKeys - 1] + 1;
            if (childMin <= to) rangeScanRec(n.children[n.numKeys], from, to, out);
        }
    }

    // =====================================================================
    //  Обход + статистика
    // =====================================================================
    public List<T> inOrder() {
        List<T> out = new ArrayList<>(size);
        if (root.numKeys > 0) inOrderRec(root, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private void inOrderRec(Node n, List<T> out) {
        for (int i = 0; i < n.numKeys; i++) {
            if (!n.leaf) inOrderRec(n.children[i], out);
            out.add((T) n.values[i]);
        }
        if (!n.leaf) inOrderRec(n.children[n.numKeys], out);
    }

    public FillStats stats() {
        int[] acc = new int[4];          // nodes, leaves, keys, height
        int[] mm  = {Integer.MAX_VALUE, Integer.MIN_VALUE};
        if (root.numKeys > 0 || !root.leaf) statsRec(root, 1, acc, mm);
        double avg = acc[0] == 0 ? 0 : (double) acc[2] / acc[0];
        return new FillStats(acc[0], acc[1], acc[2], acc[3], avg,
                mm[0] == Integer.MAX_VALUE ? 0 : mm[0],
                mm[1] == Integer.MIN_VALUE ? 0 : mm[1]);
    }

    private void statsRec(Node n, int depth, int[] acc, int[] mm) {
        acc[0]++;
        acc[2] += n.numKeys;
        acc[3] = Math.max(acc[3], depth);
        if (n.leaf) acc[1]++;
        else for (int i = 0; i <= n.numKeys; i++) statsRec(n.children[i], depth + 1, acc, mm);
        if (n != root) {
            mm[0] = Math.min(mm[0], n.numKeys);
            mm[1] = Math.max(mm[1], n.numKeys);
        }
    }

    public void clear() {
        root = new Node();
        size = 0;
        lastVisited.clear();
    }

    // =====================================================================
    //  Проверка инвариантов
    // =====================================================================
    private void checkInvariants() {
        if (!root.leaf && root.numKeys == 0)
            throw new TreeStateException(ErrorCode.TREE_STATE_CORRUPTED,
                    "пустой не-листовой корень");
        int counted = checkNode(root, true, Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (counted != size)
            throw new TreeStateException(ErrorCode.TREE_STATE_CORRUPTED,
                    "size=" + size + ", но ключей=" + counted);
    }

    private int checkNode(Node n, boolean isRoot, int lo, int hi) {
        if (n.numKeys > MAX_KEYS)
            throw new TreeStateException(ErrorCode.TREE_CAPACITY,
                    "numKeys=" + n.numKeys + " > " + MAX_KEYS);
        if (!isRoot && n.numKeys < MIN_KEYS)
            throw new TreeStateException(ErrorCode.TREE_STATE_CORRUPTED,
                    "узлов с " + n.numKeys + " ключами (< " + MIN_KEYS + ")");
        int total = 0;
        for (int i = 0; i < n.numKeys; i++) {
            if (i > 0 && n.keys[i - 1] >= n.keys[i])
                throw new TreeStateException(ErrorCode.TREE_STATE_CORRUPTED,
                        "ключи не отсортированы");
            if (n.keys[i] < lo || n.keys[i] > hi)
                throw new TreeStateException(ErrorCode.TREE_STATE_CORRUPTED,
                        "ключ " + n.keys[i] + " вне [" + lo + ", " + hi + "]");
            total++;
        }
        if (!n.leaf) {
            for (int i = 0; i <= n.numKeys; i++) {
                if (n.children[i] == null)
                    throw new TreeStateException(ErrorCode.TREE_STATE_CORRUPTED,
                            "null-потомок в " + i);
                int clo = (i == 0) ? lo : n.keys[i - 1] + 1;
                int chi = (i == n.numKeys) ? hi : n.keys[i] - 1;
                total += checkNode(n.children[i], false, clo, chi);
            }
        }
        return total;
    }

    // =====================================================================
    //  Layout для визуализации
    // =====================================================================
    public void computeLayout() {
        int[] counter = {0};
        assignPositions(root, 0, counter);
    }

    private void assignPositions(Node n, int depth, int[] counter) {
        n.depth   = depth;
        n.layoutY = depth;
        double sum = 0;
        for (int i = 0; i < n.numKeys; i++) {
            if (!n.leaf) assignPositions(n.children[i], depth + 1, counter);
            n.keyX[i] = counter[0]++;
            sum += n.keyX[i];
        }
        if (!n.leaf) assignPositions(n.children[n.numKeys], depth + 1, counter);
        n.layoutX = (n.numKeys == 0) ? counter[0] : sum / n.numKeys;
    }

    /** BFS-обход. */
    public List<Node> bfs() {
        List<Node> out = new ArrayList<>();
        Deque<Node> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            Node n = q.poll();
            out.add(n);
            if (!n.leaf)
                for (int i = 0; i <= n.numKeys; i++) q.add(n.children[i]);
        }
        return out;
    }
}