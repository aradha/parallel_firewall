import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Version of a parallel firewall using skip list hash table to
 * detect permissions.
 * @author aradha
 *
 */
public class ParallelFirewallSingleConfig {

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
        final int queueDepth = 50;
                
        StopWatch timer = new StopWatch();
        
        PacketGenerator src = new PacketGenerator(
                numAddressesLog, numTrainsLog, meanTrainSize, meanTrainsPerComm, meanWindow,
                meanCommsPerAddress, meanWork, configFraction, pngFraction, acceptingFraction);
        
    
        ConcurrentHashMap<Integer, Boolean> PNG = new ConcurrentHashMap<Integer, Boolean>();        
        ConcurrentHashMap<Integer, IntervalSkipListSingleConfig> R = new ConcurrentHashMap<Integer, IntervalSkipListSingleConfig>();
        
        /*
         * TODO: New structure for cache:
         * hashmap<source, hashmap<dest, boolean>> 
         */
        
        ConcurrentHashMap<Integer,HashMap<Integer, Boolean>> cache = new ConcurrentHashMap<Integer, HashMap<Integer, Boolean>>();
        //ConcurrentHashMap<SourceDestinationPair, Boolean> cache = new ConcurrentHashMap<SourceDestinationPair, Boolean>();
        
        for (int i = 0; i < Math.pow(1 << numAddressesLog, 1.5); i++) {
            
            Packet pkt = src.getConfigPacket();
            PNG.put(pkt.config.address, pkt.config.personaNonGrata);

            int addBegin = pkt.config.addressBegin;
            int addEnd = pkt.config.addressEnd;
            boolean acceptRange = pkt.config.acceptingRange;
            SkipInterval interval = new SkipInterval(addBegin, addEnd-1);
            IntervalSkipListSingleConfig configAddr;;
            

            if (!R.containsKey(pkt.config.address) && !acceptRange) {
                continue;

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
        LamportQueue<Packet>[] configQueues = new LamportQueue[1];
        LamportQueue<Packet>[] dataQueues = new LamportQueue[(int)Math.max(1, numSources-configQueues.length)];
        
        ParallelConfigPacketWorkerSC[] cWorkerArray = new ParallelConfigPacketWorkerSC[1];
        ParallelDataPacketWorkerSC[] dWorkerArray = new ParallelDataPacketWorkerSC[(int)Math.max(1, numSources-configQueues.length)];
        
        Thread[] dataThreads = new Thread[(int)Math.max(1, numSources-configQueues.length)];
        Thread[] configThreads = new Thread[1];
        
        boolean sendToSelf = false;
        for(int i = 0; i < configQueues.length; i++){
            configQueues[i] = new LamportQueue<Packet>(257); 
            cWorkerArray[i] = new ParallelConfigPacketWorkerSC(PNG, R, done, configQueues[i], numPacketsInFlight, cache, sendToSelf);
            configThreads[i] = new Thread(cWorkerArray[i]);
        }
        for(int i = 0; i < dataQueues.length; i++){
            dataQueues[i] = new LamportQueue<Packet>(queueDepth);  
            dWorkerArray[i] = new ParallelDataPacketWorkerSC(PNG, R, done, histogram, dataQueues[i], numPacketsInFlight, cache, sendToSelf);
            dataThreads[i] = new Thread(dWorkerArray[i]);
        }

        Dispatcher dispatcher = new Dispatcher(done, src, configQueues, dataQueues, numPacketsInFlight);
        
        Thread dispatcherThread = new Thread(dispatcher);
        
        dispatcherThread.start();
        
//        //Wait until in flight is 128
//        while(dispatcher.numPacketsInFlight.get() <= 128){
//            //System.out.println(dispatcher.numPacketsInFlight.get());
//        }
        
        
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
