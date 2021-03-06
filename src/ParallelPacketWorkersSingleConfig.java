
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interface for packet workers in a parallel firewall, which uses only
 * one configuration packet worker and multiple data packet workers. The
 * configuration packet worker processes packets atomically (see linearizability
 * arguments below).
 * @author aradha
 *
 */
public interface ParallelPacketWorkersSingleConfig extends Runnable {    
    public void run();
} 

/*
 * Need both data and config packet workers to handle
 * dequeuing packets from respective queues.
 */

/**
 * Class for data packet workers, which need not process data packets
 * atomically.  
 * @author aradha
 *
 */
class ParallelDataPacketWorkerSC implements ParallelPacketWorkersSingleConfig{
    PaddedPrimitiveNonVolatile<Boolean> done;
    final Fingerprint residue = new Fingerprint();
    long fingerprint = 0;
    long totalPackets = 0;
    ConcurrentHashMap<Integer, Boolean> PNG;
    ConcurrentHashMap<Integer, IntervalSkipListSingleConfig> R;
    ConcurrentHashMap<Integer, HashMap<Integer, Boolean>> cache;
    final AtomicInteger[] histogram;
    volatile boolean sendToSelf;
    //Also his lamport queue
    final LamportQueue<Packet> queue;
    final int meanTrainSize;
    AtomicInteger numPacketsInFlight;
    int cacheHelped;
    
    /**
     * Constructor for the data worker
     * @param PNG - source permission hash table
     * @param R - destination permission hash table
     * @param done - flag to stop the worker 
     * @param histogram - a histogram to analyze filtering
     * @param queue - the queue of packets this worker can access
     * @param numPacketsInFlight - the total number of packets in flight (< 256)
     * @param cache - the optional cache for the workers (used to test cache performance)
     * @param sendToSelf - a flag used to maintain linearizability of concurrent config packet calls.
     */
    public ParallelDataPacketWorkerSC(ConcurrentHashMap<Integer, Boolean> PNG,
            ConcurrentHashMap<Integer, IntervalSkipListSingleConfig> R,
            PaddedPrimitiveNonVolatile<Boolean> done,
            AtomicInteger[] histogram,
            LamportQueue<Packet> queue, 
            AtomicInteger numPacketsInFlight,
            ConcurrentHashMap<Integer, HashMap<Integer, Boolean>> cache,
            boolean sendToSelf) {

        this.done = done;
        this.PNG = PNG;
        this.R = R;
        this.histogram = histogram;
        this.queue = queue;
        this.numPacketsInFlight = numPacketsInFlight;
        this.cache = cache;
        this.meanTrainSize = 5;
        this.sendToSelf = sendToSelf;
    }

    @Override
    public void run() {
        while( !done.value || queue.tail - queue.head > 0) {
            try{
                Packet pkt = queue.dequeue(); //Dequeue your packet 
                totalPackets++;  //Update the packet count               
                processDataPacket(pkt); //Process the packet
                numPacketsInFlight.getAndDecrement(); //This packet is no longer in flight
            } catch(EmptyException e){;}
        }
    }
    
    /**
     * 
     * @param pkt - A packet produced from the provided random generator 
     *              (Passed in by the Dispatcher)
     */
    private void processDataPacket(Packet pkt){
        int dest = pkt.header.dest;
        int source = pkt.header.source;
        
        if(pkt.header.source == pkt.header.dest){
            while(sendToSelf){}; 
            /*
             * Linerization point for a successful process data packet call occurs here
             * for the case that sources are sending data packets to themselves and 
             * a concurrent process configuration packet call is made.
             */
        }
        /**Optional code to implement cache checking 
         
        HashMap<Integer, Boolean> cacheDest = cache.get(source);
        if(cacheDest != null){
            Boolean permission = cacheDest.get(dest);
            if(permission != null){
                if(permission.booleanValue()){
                    long tmpVal = residue.getFingerprint(pkt.body.iterations, pkt.body.seed);
                    fingerprint += tmpVal;
                    histogram[(int)tmpVal].getAndIncrement();
                    
                    if(pkt.header.sequenceNumber == pkt.header.trainSize-1){
                        cache.remove(source);
                    }
                }
                else{
                }
                cacheHelped++;
               return;
            }
        }*/ 
        
        if(!PNG.containsKey(source)){        
            return;
        } 
        else if(!PNG.get(source) && R.containsKey(dest) && R.get(dest).contains(new SkipInterval(source,source))){
            long tmpVal = residue.getFingerprint(pkt.body.iterations, pkt.body.seed);
            fingerprint += tmpVal;
            histogram[(int)tmpVal].getAndIncrement(); 
            //Put true cache value
            /**TODO
            if(cacheDest != null){
                cacheDest.put(dest, true);
            } else{

                if(pkt.header.trainSize > 2*meanTrainSize){
                    HashMap<Integer, Boolean> toAdd = new HashMap<Integer, Boolean>();
                    toAdd.put(dest, true);
                    cache.put(source, toAdd);
                }
            } */
        } 
        else {
            return; //Drop packet
        }

    }
}


class ParallelConfigPacketWorkerSC implements ParallelPacketWorkersSingleConfig{
    PaddedPrimitiveNonVolatile<Boolean> done;
    ConcurrentHashMap<Integer, Boolean> PNG;
    ConcurrentHashMap<Integer, IntervalSkipListSingleConfig> R;
    //Also his lamport queue
    final LamportQueue<Packet> queue;
    AtomicInteger numPacketsInFlight;
    volatile boolean sendToSelf;
    ConcurrentHashMap<Integer, HashMap<Integer, Boolean>> cache;
    public ParallelConfigPacketWorkerSC(ConcurrentHashMap<Integer, Boolean> PNG,
            ConcurrentHashMap<Integer, IntervalSkipListSingleConfig> R,
            PaddedPrimitiveNonVolatile<Boolean> done,
            LamportQueue<Packet> queue,
            AtomicInteger numPacketsInFlight,
            ConcurrentHashMap<Integer, HashMap<Integer, Boolean>> cache,
            boolean sendToSelf) {
        this.done = done;
        this.PNG = PNG;
        this.R = R;
        this.queue = queue;
        this.numPacketsInFlight = numPacketsInFlight;
        this.cache = cache;
        this.sendToSelf = sendToSelf;
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
    
    private void processConfigPacket(Packet pkt){
        //basic strategy - delete cache completely each time a new pair comes in 
        /**TODO
        cache = new ConcurrentHashMap<Integer, HashMap<Integer, Boolean>>();
        //Currently the caching strategy doesn't even work - it's not linearizable!
         */
        //System.out.println(cache.size());
        //System.out.println(cache.size());
       
        sendToSelf = true;
        
        PNG.put(pkt.config.address, pkt.config.personaNonGrata);

        int addBegin = pkt.config.addressBegin;
        int addEnd = pkt.config.addressEnd;
        boolean acceptRange = pkt.config.acceptingRange;
        SkipInterval interval = new SkipInterval(addBegin, addEnd-1);
        IntervalSkipListSingleConfig configAddr;

        if (!R.containsKey(pkt.config.address) && !acceptRange) {
            
        } 
        else if (!R.containsKey(pkt.config.address)) {
            configAddr = new IntervalSkipListSingleConfig();
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
        sendToSelf = false;
    }    
}

