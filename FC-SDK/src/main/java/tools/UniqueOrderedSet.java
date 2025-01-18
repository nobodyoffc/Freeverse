package tools;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Iterator;

public class UniqueOrderedSet<E> {
    private final ConcurrentLinkedDeque<E> deque;

    public UniqueOrderedSet() {
        this.deque = new ConcurrentLinkedDeque<>();
    }

    public boolean add(E element) {
        synchronized (deque) {
            // If the element is already in the set, remove it
            deque.remove(element);
            // Add the element to the top (beginning of the deque)
            deque.addFirst(element);
            return true;
        }
    }

    public boolean remove(E element) {
        return deque.remove(element);
    }

    public boolean contains(E element) {
        return deque.contains(element);
    }

    public int size() {
        return deque.size();
    }

    public void clear() {
        deque.clear();
    }

    public Iterator<E> iterator() {
        return deque.iterator();
    }

    // Additional method to get the newest element without removing it
    public E peek() {
        return deque.peekFirst();
    }

    // Additional method to get and remove the newest element
    public E poll() {
        return deque.pollFirst();
    }
}