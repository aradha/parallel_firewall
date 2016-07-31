
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

public class IntervalHashTable {
    private AtomicInteger globalCounter = new AtomicInteger(0);
    private ConcurrentSkipListMap<SkipInterval, Integer> addList = new ConcurrentSkipListMap<SkipInterval, Integer>();
    private ConcurrentSkipListMap<SkipInterval, Integer> removeList = new ConcurrentSkipListMap<SkipInterval, Integer>();
    private AtomicStampedReference<SkipInterval> addMergerInterval;
    private AtomicStampedReference<SkipInterval> remMergerInterval;
    private ConcurrentHashMap<SkipInterval, Integer> addTable = new ConcurrentHashMap<SkipInterval, Integer>();
    private ConcurrentHashMap<SkipInterval, Integer> removeTable = new ConcurrentHashMap<SkipInterval, Integer>();

    
    public IntervalHashTable(){
        
    }
    
    public void add(SkipInterval interval){
        //addList.put(interval, System.nanoTime());
        /*
         * If list is empty, just add new element and return.
         */
        if(addList.isEmpty()){
            addList.put(interval, globalCounter.getAndIncrement());
            addTable.put(interval, globalCounter.getAndIncrement());
            return;
        }
        
        /*
         * If the interval is contained within some other interval,
         * just update the stamp and return.
         */
        if(addList.containsKey(interval)){
            //Adding accepting ranges is linearized here (in this case)   
            addList.replace(interval, globalCounter.getAndIncrement()); 
            addTable.replace(interval, globalCounter.getAndIncrement());
            return;
        }
        
        /*
         * Otherwise add the interval to the list
         * and loop onwards through overlapping intervals
         * while merging them.
         */
        //System.out.println("INTERVAL: " + interval);
        SkipInterval out = interval;
        SkipInterval low = addList.floorKey(interval);
        if(low == null){ //Case that interval will be the start of the skiplist
            low = interval;
        }
        ConcurrentNavigableMap<SkipInterval, Integer> subSet = addList.tailMap(low);
        //Merge from previous element on to last element if applicable
        for(SkipInterval key: subSet.keySet()){
            if(interval.startAddr -1 > key.endAddr){
                continue; //The previous one is disjoint - check the intervals in front
            }
            if(key.startAddr -1 > interval.endAddr){
                break;
            }
            else{
                out = merge(out, key, addList, addMergerInterval, "ADD");
            }
        }
        addList.put(out, globalCounter.getAndIncrement());
        addTable.put(out, globalCounter.getAndIncrement());
        addMergerInterval = null;
    }
    
    
    /**
     * Used to merge two intervals in the skip list as in the following example:
     * merge( [20, 30] and [25, 36]) = [20, 36]
     */
    public SkipInterval merge(SkipInterval first, SkipInterval sec, 
            ConcurrentSkipListMap<SkipInterval, Integer> list, AtomicStampedReference<SkipInterval> mergerInterval,
            String type){
        SkipInterval out =  new SkipInterval(Math.min(first.startAddr, sec.startAddr),
                                             Math.max(first.endAddr, sec.endAddr));
        mergerInterval = new AtomicStampedReference(new SkipInterval(out.startAddr, out.endAddr), globalCounter.get());
        list.remove(sec);
        if(type.equals("ADD"))
            addTable.remove(sec);
        else
            removeTable.remove(sec);
        return out;
    }
    
    public void remove(SkipInterval interval){
        /*
         * If list is empty, just add new element and return.
         */
        if(removeList.isEmpty()){
            removeList.put(interval, globalCounter.getAndIncrement());
            removeTable.put(interval, globalCounter.getAndIncrement());

            return;
        }
        
        /*
         * If the interval is contained within some other interval,
         * just update the stamp and return.
         */
        if(removeList.containsKey(interval)){
            //Adding accepting ranges is linearized here (in this case)
            removeList.replace(interval, globalCounter.getAndIncrement()); 
            removeTable.replace(interval, globalCounter.getAndIncrement());

            return;
        }
        
        /*
         * Otherwise add the interval to the list
         * and loop onwards through overlapping intervals
         * while merging them.
         */
        SkipInterval out = interval;
        SkipInterval low = removeList.floorKey(interval);
        if(low == null){
            low = interval;
        }
        ConcurrentNavigableMap<SkipInterval, Integer> subSet = removeList.tailMap(low);
        //Merge from previous element on to last element if applicable
        for(SkipInterval key: subSet.keySet()){
            if(interval.startAddr -1 > key.endAddr){
                continue; //The previous one is disjoint - check the intervals in front
            }
            if(key.startAddr -1 > interval.endAddr){
                break;
            }
            else{
                out = merge(out, key, removeList, remMergerInterval, "REMOVE");
            }
        }
        removeList.put(out, globalCounter.getAndIncrement());
        removeTable.put(out, globalCounter.getAndIncrement());
        remMergerInterval = null;
    }
    
    public boolean contains(SkipInterval interval){
        //Contains is fast since merging guarantees disjoint intervals!
        Integer addTime = addTable.get(interval); //Check stamp of interval in added
        Integer remTime = removeTable.get(interval); //Check stamp of interval in removed
        if(addMergerInterval != null && interval.equals(addMergerInterval.getReference())){
            addTime = addMergerInterval.getStamp();
        }
        if(remMergerInterval != null && interval.equals(remMergerInterval.getReference())){
            remTime = remMergerInterval.getStamp();
        }

        if(addTime == null){
            return false;
        }
        else if(remTime != null && addTime < remTime){ 
            return false; //If the removed interval was after the added stamp
        }      
        return true;
    }
    
    @Override
    public String toString(){
        return addList.toString() + "\n" + removeList.toString();
    }

}

class HashListTest{
    
    public static void main(String[] args){
        
        //Used to test skip list implementation for correctness
        IntervalHashTable tmp = new IntervalHashTable();
        tmp.add(new SkipInterval(2,8));
        tmp.add(new SkipInterval(3,9));
        tmp.add(new SkipInterval(2, 8));
        tmp.add(new SkipInterval(15, 19));
        tmp.add(new SkipInterval(5, 9));
        tmp.add(new SkipInterval(0, 5));
        tmp.add(new SkipInterval(-2, 0));
        tmp.add(new SkipInterval(17, 21));
        tmp.add(new SkipInterval(0, 5));
        tmp.add(new SkipInterval(-2, 9));
        tmp.add(new SkipInterval(15, 21));
          System.out.println(tmp.toString());
        tmp.add(new SkipInterval(5, 18));
        System.out.println(tmp.toString());
        tmp.add(new SkipInterval(33, 100));
        System.out.println(tmp.toString());
        tmp.add(new SkipInterval(50,60));
        tmp.add(new SkipInterval(100,103));
        tmp.add(new SkipInterval(22,30));
        System.out.println(tmp.toString());

        tmp.add(new SkipInterval(105, 200));
        System.out.println(tmp.toString());
        tmp.add(new SkipInterval(204, 210));
        tmp.add(new SkipInterval(32,212));
        tmp.add(new SkipInterval(213, 300));
        tmp.add(new SkipInterval(-10, -4));
        System.out.println(tmp);
        tmp.add(new SkipInterval(-1, 300));

        tmp.add(new SkipInterval(29, 203));
        tmp.add(new SkipInterval(-100,-4));
        tmp.add(new SkipInterval(-5, -1));
        tmp.add(new SkipInterval(33, 50));
        tmp.add(new SkipInterval(31, 32));



        //System.out.println(tmp.list.size());
        //tmp.add(new Interval(-200, 12));
        tmp.add(new SkipInterval(22, 25));
        System.out.println(tmp.contains(new SkipInterval(14,14)));

        //tmp.add(new Interval(-3, 1));
        System.out.println(tmp.toString());
        tmp.remove(new SkipInterval(-2, 3500));
        tmp.remove(new SkipInterval(-10, -4));
        tmp.remove(new SkipInterval(15, 100));
        System.out.println(tmp.toString());
        tmp.add(new SkipInterval(30, 70));
        System.out.println(tmp.toString());
        tmp.remove(new SkipInterval(-1, 200));
        System.out.println(tmp.toString());
        tmp.remove(new SkipInterval(-5, -1));
        System.out.println(tmp.contains(new SkipInterval(14,14)));

        System.out.println(tmp.toString());
    }
    
}