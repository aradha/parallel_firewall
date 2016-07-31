package customParallelFirewall;
public class LamportQueue<T>{
    volatile int head, tail;
    T[] items;
    public LamportQueue(int size){
        items = (T[]) new Object[size];
        head = 0;
        tail = 0;
    }
    public void enqueue(T next) throws FullException{
        if(tail - head == items.length)
            throw new FullException();
        items[tail% items.length] = next;
        tail++;
    }
    public T dequeue() throws EmptyException{
        if(tail - head == 0)
            throw new EmptyException();
        T out = items[head % items.length];
        head++;
        return out;
    }    
}
