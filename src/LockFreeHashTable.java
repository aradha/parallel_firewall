import java.util.concurrent.locks.ReentrantLock;

class LockFreeClosedHashTable<T>{

    final ReentrantLock[] locks;
    
    private SerialList<T, Integer>[] table;
    private int logSize;
    private int mask;
    private final int maxBucketSize;


    @SuppressWarnings("unchecked")
    public LockFreeClosedHashTable(int logSize, int maxBucketSize){
        this.logSize = logSize;
        this.mask = (1 << logSize) -1;
        this.maxBucketSize = maxBucketSize;
        this.table = new SerialList[1 << logSize];
        locks = new ReentrantLock[1 << logSize];
        for(int i = 0; i <= mask; i++){
            locks[i] = new ReentrantLock();
        }
    }
    
    public void resizeIfNecessary(int key){
        while( table[key & mask] != null 
                && table[key & mask].getSize() >= maxBucketSize )
            resize();
    }
    
    public void resize(){
//        System.out.println("RESIZING:  " + table.length);
        int oldCapacity = table.length;
        for(ReentrantLock lock: locks){
            lock.lock();
        }
        try{
            if(oldCapacity != table.length){
                return; //We lost
            }
            SerialList<T,Integer>[] newTable = new SerialList[2*table.length];
            for( int i = 0; i < table.length; i++ ) {
              if( table[i] == null )
                continue;
              SerialList<T,Integer>.Iterator<T,Integer> iterator = table[i].getHead();
              while( iterator != null ) {
                if( newTable[iterator.key & ((2*mask)+1)] == null )
                  newTable[iterator.key & ((2*mask)+1)] = new SerialList<T,Integer>(iterator.key, iterator.getItem());
                else
                  newTable[iterator.key & ((2*mask)+1)].addNoCheck(iterator.key, iterator.getItem());
                iterator = iterator.getNext();
              }
            }
            table = newTable;
            logSize++;
            mask = (1 << logSize) - 1;

        }finally{
            for(ReentrantLock lock: locks){
                lock.unlock();
            }
        }
    }
    
    
    public void add(int key, T x){
        
        resizeIfNecessary(key);
        acquire(key);
        try{
            int index = key & mask;
            if( table[index] == null )
              table[index] = new SerialList<T, Integer>(key,x);
            else
              table[index].add(key,x);
        }
        finally{
            release(key);
        }
    }
    

    public boolean contains(int key, T x){
        if(table[key & mask]!= null)
            return table[key & mask].contains(key);
        else
            return false;
  
    }
    
    public boolean remove(int key, T x){
        acquire(key);
        try{
            int myBucket = key & mask;
            if(table[myBucket] != null){
                table[myBucket].remove(key);
                return true;
            }
            else 
                return false;
        }
        finally{
            release(key);
        }
    }
    
    public T get(int key) {
        int myBucket = key & mask;
        if ( table[myBucket] != null ) {
            acquire(key);
          if (table[myBucket].getItem(key) == null) {
            release(key);
            return null;
          }
          T item = table[myBucket].getItem(key).getItem();
          release(key);
          return item;
        }
        else {
          return null;
        }
      }
    
    public void acquire(int key){
        locks[(key & mask) % locks.length].lock();
    }
    
    public void release(int key){
        locks[(key & mask) %locks.length].unlock();
    }
        
}
