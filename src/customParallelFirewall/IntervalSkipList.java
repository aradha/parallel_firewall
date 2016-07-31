package customParallelFirewall;

import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.locks.ReentrantLock;

public class IntervalSkipList {
    private AtomicInteger globalCounter = new AtomicInteger(0);
    private ConcurrentSkipListMap<SkipInterval, Integer> addList = new ConcurrentSkipListMap<SkipInterval, Integer>();
    private ConcurrentSkipListMap<SkipInterval, Integer> removeList = new ConcurrentSkipListMap<SkipInterval, Integer>();
    private AtomicStampedReference<SkipInterval> addMergerInterval;
    private AtomicStampedReference<SkipInterval> remMergerInterval;
    private ReentrantLock lock = new ReentrantLock();
    
    
    public IntervalSkipList(){
        
    }
    
    public void add(SkipInterval interval){
        //addList.put(interval, System.nanoTime());
        /*
         * If list is empty, just add new element and return.
         */
        if(addList.isEmpty()){
            addList.put(interval, globalCounter.getAndIncrement());
            return;
        }
        
        /*
         * If the interval is contained within some other interval,
         * just update the stamp and return.
         */
        if(addList.containsKey(interval)){
            //Adding accepting ranges is linearized here (in this case)   
            addList.replace(interval, globalCounter.getAndIncrement()); 
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
        /*
         * New idea - 
         * just go through the tail map and then find out the last interval that is overlapped
         *            and then 
         */
        for(SkipInterval key: subSet.keySet()){
            if(interval.startAddr -1 > key.endAddr){
                continue; //The previous one is disjoint - check the intervals in front
            }
            if(key.startAddr -1 > interval.endAddr){
                break;
            }
            else{
                /*
                 * Out should be the greatest key 
                 */
                out = merge(out, key, addList, addMergerInterval);
            }
        }
        addList.put(out, globalCounter.getAndIncrement());
        addMergerInterval = null;
    }
    
    
    /**
     * Used to merge two intervals in the skip list as in the following example:
     * merge( [20, 30] and [25, 36]) = [20, 36]
     */
    private SkipInterval merge(SkipInterval first, SkipInterval sec, 
            ConcurrentSkipListMap<SkipInterval, Integer> list, AtomicStampedReference<SkipInterval> mergerInterval){
        SkipInterval out =  new SkipInterval(Math.min(first.startAddr, sec.startAddr),
                                             Math.max(first.endAddr, sec.endAddr));
        mergerInterval = new AtomicStampedReference(new SkipInterval(out.startAddr, out.endAddr), globalCounter.get());
        list.remove(sec);
        return out;
    }
    
    public void remove(SkipInterval interval){
        /*
         * If list is empty, just add new element and return.
         */
        if(removeList.isEmpty()){
            removeList.put(interval, globalCounter.getAndIncrement());
            return;
        }
        
        /*
         * If the interval is contained within some other interval,
         * just update the stamp and return.
         */
        if(removeList.containsKey(interval)){
            //Adding accepting ranges is linearized here (in this case)
            removeList.replace(interval, globalCounter.getAndIncrement()); 
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
                    out = merge(out, key, removeList, remMergerInterval);
                }
            }
            removeList.put(out, globalCounter.getAndIncrement());
            remMergerInterval = null;
    }
    
    public boolean contains(SkipInterval interval){
        //Contains is fast since merging guarantees disjoint intervals!
        
        
        Integer addTime = addList.get(interval); //Check stamp of interval in added
        Integer remTime = removeList.get(interval); //Check stamp of interval in removed
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
