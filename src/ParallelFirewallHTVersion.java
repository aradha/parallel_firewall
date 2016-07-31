import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * An implementation of a parallel firewall with permissions being a 
 * concurrent hash table of sorted hash tables.
 * The contains method (instead of using skip list contains() )
 * uses hash table contains instead.
 * @author aradha
 *
 */
public class ParallelFirewallHTVersion {

    public static void main(String[] args) {

        final int numMilliseconds = Integer.parseInt(args[0]);
        final int numAddressesLog = Integer.parseInt(args[1]);
        final int numTrainsLog = Integer.parseInt(args[2]);
        final double meanTrainSize = Double.parseDouble(args[3]);
        final double meanTrainsPerComm = Double.parseDouble(args[4]);
        final int meanWindow = Integer.parseInt(args[5]);
        final int meanCommsPerAddress = Integer.parseInt(args[6]);
        final int meanWork = Integer.parseInt(args[7]);
        final double configFraction =  Double.parseDouble(args[8]);
        final double pngFraction =  Double.parseDouble(args[9]);
        final double acceptingFraction =  Double.parseDouble(args[10]);
        final int numSources = Integer.parseInt(args[11]);
        final int queueDepth = 8;
        final int maxBucketSize = 8;
                
        StopWatch timer = new StopWatch();
        
        PacketGenerator src = new PacketGenerator(
                numAddressesLog, numTrainsLog, meanTrainSize, meanTrainsPerComm, meanWindow,
                meanCommsPerAddress, meanWork, configFraction, pngFraction, acceptingFraction);

        PacketGenerator configSrc = new PacketGenerator(
                numAddressesLog, numTrainsLog, meanTrainSize, meanTrainsPerComm, meanWindow,
                meanCommsPerAddress, meanWork, 1, pngFraction, acceptingFraction);
        
        
    
        ConcurrentHashMap<Integer, Boolean> PNG = new ConcurrentHashMap<Integer, Boolean>();        
        ConcurrentHashMap<Integer, IntervalSkipList> R = new ConcurrentHashMap<Integer, IntervalSkipList>();
        
        /*
         * TODO: New structure for cache:
         * hashmap<source, hashmap<dest, boolean>> 
         */
        
        ConcurrentHashMap<Integer,HashMap<Integer, Boolean>> cache = new ConcurrentHashMap<Integer, HashMap<Integer, Boolean>>();
        //ConcurrentHashMap<SourceDestinationPair, Boolean> cache = new ConcurrentHashMap<SourceDestinationPair, Boolean>();
        
        for (int i = 0; i < Math.pow(1 << numAddressesLog, 1.5); i++) {
            
            Packet pkt = configSrc.getPacket();
            PNG.put(pkt.config.address, pkt.config.personaNonGrata);

            int addBegin = pkt.config.addressBegin;
            int addEnd = pkt.config.addressEnd;
            boolean acceptRange = pkt.config.acceptingRange;
            SkipInterval interval = new SkipInterval(addBegin, addEnd-1);
            IntervalSkipList configAddr;;
            

            if (!R.containsKey(pkt.config.address) && !acceptRange) {
                continue;

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
        
        //PNG.printTable();
        //R.printTable();
        //System.out.println(PNG);
        //System.out.println(R);
        System.out.println("CONFIGURED!");
        
        
        PaddedPrimitiveNonVolatile<Boolean> done = new PaddedPrimitiveNonVolatile<Boolean>(false);
        PaddedPrimitive<Boolean> memFence = new PaddedPrimitive<Boolean>(false);
                 
        final int HIST_SIZE= (1<< 16);
        AtomicInteger[] histogram = new AtomicInteger[HIST_SIZE];
        AtomicInteger numPacketsInFlight = new AtomicInteger(0);
        for(int i = 0 ; i < histogram.length; i++){
            histogram[i] = new AtomicInteger(0);
        }
        
        //
         //Initialize a single config queue and a single data queue of depth 8
         //
        final double SHIFT = 0;
        LamportQueue<Packet>[] configQueues = new LamportQueue[(int)Math.max(1, numSources*(configFraction+SHIFT))];
        LamportQueue<Packet>[] dataQueues = new LamportQueue[(int)Math.max(1, numSources-configQueues.length)];
        
        ParallelDataPacketWorker[] dWorkerArray = new ParallelDataPacketWorker[(int)Math.max(1, numSources-configQueues.length)];
        ParallelConfigPacketWorker[] cWorkerArray = new ParallelConfigPacketWorker[(int)Math.max(1, numSources*(configFraction+SHIFT))];
        
        Thread[] dataThreads = new Thread[(int)Math.max(1, numSources-configQueues.length)];
        Thread[] configThreads = new Thread[(int)Math.max(1, numSources*(configFraction+SHIFT))];
        
        for(int i = 0; i < configQueues.length; i++){
            configQueues[i] = new LamportQueue<Packet>(8); 
            cWorkerArray[i] = new ParallelConfigPacketWorker(PNG, R, done, configQueues[i], numPacketsInFlight, cache);
            configThreads[i] = new Thread(cWorkerArray[i]);

        }
        for(int i = 0; i < dataQueues.length; i++){
            dataQueues[i] = new LamportQueue<Packet>(8);  
            dWorkerArray[i] = new ParallelDataPacketWorker(PNG, R, done, histogram, dataQueues[i], numPacketsInFlight, cache);
            dataThreads[i] = new Thread(dWorkerArray[i]);
        }

        Dispatcher dispatcher = new Dispatcher(done, src, configQueues, dataQueues, numPacketsInFlight);
        
        Thread dispatcherThread = new Thread(dispatcher);
        
        dispatcherThread.start();
        
        for(int i = 0; i < configQueues.length; i++){
            configThreads[i].start();
        }
        for(int i = 0; i < dataQueues.length; i++){
            dataThreads[i].start();
        }
        
        
        timer.startTimer();
        
        try {
          Thread.sleep(numMilliseconds);
        } catch (InterruptedException ignore) {;}
        done.value = true;
        memFence.value = true;  // memFence is a 'volatile' forcing a memory fence
                                // which means that done.value is visible to the workers
        try {                   
          dispatcherThread.join();
          for(int i = 0; i < configQueues.length; i++){
              configThreads[i].join();
          }
          for(int i = 0; i < dataQueues.length; i++){
              dataThreads[i].join();
          }
        } catch (InterruptedException ignore) {;}      
        timer.stopTimer();
        
        final long totalCount = dispatcher.totalCount;
        long workerCount = 0;
        for(int i = 0; i < dataQueues.length; i++){
            workerCount += dWorkerArray[i].totalPackets;
        }
        
        System.out.println("count: " + totalCount + " " + workerCount);
        System.out.println("time: " + timer.getElapsedTime());
        System.out.println(workerCount/timer.getElapsedTime() + " pkts / ms");
      
    } 
}
