
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class ParallelPacketWorkersHashTables {

}
/*
 * Need both data and config packet workers to handle
 * dequeuing packets from respective queues.
 */
class ParallelDataPacketWorkerHT implements PacketWorker{
    PaddedPrimitiveNonVolatile<Boolean> done;
    final Fingerprint residue = new Fingerprint();
    long fingerprint = 0;
    long totalPackets = 0;
    ConcurrentHashMap<Integer, Boolean> PNG;
    ConcurrentHashMap<Integer, IntervalSkipList> R;
    ConcurrentHashMap<Integer, HashMap<Integer, Boolean>> cache;
    final int[] histogram;
    //Also his lamport queue
    final LamportQueue<Packet> queue;
    AtomicInteger numPacketsInFlight;
    public ParallelDataPacketWorkerHT(ConcurrentHashMap<Integer, Boolean> PNG,
            ConcurrentHashMap<Integer, IntervalSkipList> R,
            PaddedPrimitiveNonVolatile<Boolean> done,
            int[] histogram,
            LamportQueue<Packet> queue, 
            AtomicInteger numPacketsInFlight,
            ConcurrentHashMap<Integer, HashMap<Integer, Boolean>> cache) {

        this.done = done;
        this.PNG = PNG;
        this.R = R;
        this.histogram = histogram;
        this.queue = queue;
        this.numPacketsInFlight = numPacketsInFlight;
        this.cache = cache;
    }

    @Override
    public void run() {
        while( !done.value || queue.tail - queue.head > 0) {
            try{
                Packet pkt = queue.dequeue();
                totalPackets++;                
                processDataPacket(pkt);
                numPacketsInFlight.getAndDecrement();
            } catch(EmptyException e){;}
        }
    }
    
    public void processDataPacket(Packet pkt){
        int dest = pkt.header.dest;
        int source = pkt.header.source;
        //System.out.println(cache.size());
        /**TODO
        HashMap<Integer, Boolean> cacheDest = cache.get(source);
        if(cacheDest != null){
            Boolean permission = cacheDest.get(dest);
            if(permission != null){
                if(permission.booleanValue()){
                    long tmpVal = residue.getFingerprint(pkt.body.iterations, pkt.body.seed);
                    fingerprint += tmpVal;
                    histogram[(int)tmpVal]+= 1;            
                }
                else{
                    return;
                }
            }
        }*/
        
        
        if(!PNG.containsKey(source)){
            return;
        } 
        else if(!PNG.get(source) && R.containsKey(dest) && R.get(dest).contains(new SkipInterval(source,source))){
            long tmpVal = residue.getFingerprint(pkt.body.iterations, pkt.body.seed);
            fingerprint += tmpVal;
            histogram[(int)tmpVal]+= 1; 
            //Put true cache value
            /** TODO
            if(cacheDest != null){
                cacheDest.put(dest, true);
            } else{
                HashMap<Integer, Boolean> toAdd = new HashMap<Integer, Boolean>();
                toAdd.put(dest, true);
                cache.put(source, toAdd);
            }*/
        } 
        else {
            //Put false cache value
            /**TODO
            if(cacheDest != null){
                cacheDest.put(dest, false);
            } else{
                HashMap<Integer, Boolean> toAdd = new HashMap<Integer, Boolean>();
                toAdd.put(dest, false);
                cache.put(source, toAdd);
            }*/
            return; //Drop packet
        }

    }
}


class ParallelConfigPacketWorkerHT implements PacketWorker{
    PaddedPrimitiveNonVolatile<Boolean> done;
    ConcurrentHashMap<Integer, Boolean> PNG;
    ConcurrentHashMap<Integer, IntervalSkipList> R;
    //Also his lamport queue
    final LamportQueue<Packet> queue;
    AtomicInteger numPacketsInFlight;
    ConcurrentHashMap<Integer, HashMap<Integer, Boolean>> cache;
    public ParallelConfigPacketWorkerHT(ConcurrentHashMap<Integer, Boolean> PNG,
            ConcurrentHashMap<Integer, IntervalSkipList> R,
            PaddedPrimitiveNonVolatile<Boolean> done,
            LamportQueue<Packet> queue,
            AtomicInteger numPacketsInFlight,
            ConcurrentHashMap<Integer, HashMap<Integer, Boolean>> cache) {
        this.done = done;
        this.PNG = PNG;
        this.R = R;
        this.queue = queue;
        this.numPacketsInFlight = numPacketsInFlight;
        this.cache = cache;
    }

    public void run() {
        while( !done.value || queue.tail - queue.head > 0) {
            try{
                Packet pkt = queue.dequeue();
                processConfigPacket(pkt);
                numPacketsInFlight.getAndDecrement();
            } catch(EmptyException e){;}
        }
    }
    
    public void processConfigPacket(Packet pkt){
        //basic strategy - delete cache completely each time a new pair comes in 
        //cache = new ConcurrentHashMap<SourceDestinationPair, Boolean>();
        //Currently the caching strategy doesn't even work - it's not linearizable!
        //Optimistic strategy:
        //It's working now but not very well
        //System.out.println(cache.size());
        //System.out.println(cache.size());
       
        /**TODO
        cache.remove(pkt.config.address);
        */
        PNG.put(pkt.config.address, pkt.config.personaNonGrata);

        int addBegin = pkt.config.addressBegin;
        int addEnd = pkt.config.addressEnd;
        boolean acceptRange = pkt.config.acceptingRange;
        SkipInterval interval = new SkipInterval(addBegin, addEnd-1);
        IntervalSkipList configAddr;

        if (!R.containsKey(pkt.config.address) && !acceptRange) {
 
        } 
        else if (!R.containsKey(pkt.config.address)) {
            configAddr = new IntervalSkipList();
            configAddr.add(interval);            
            R.put(pkt.config.address, configAddr);                
        } 
        else {
            configAddr = R.get(pkt.config.address);
            if(acceptRange){
                configAddr.add(interval);
            }
            else{
                configAddr.remove(interval);
            }
        }
    }    
}

