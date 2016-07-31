import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;


public class SerialFirewall {


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
                
        StopWatch timer = new StopWatch();
        
        PacketGenerator src = new PacketGenerator(
                numAddressesLog, numTrainsLog, meanTrainSize, meanTrainsPerComm, meanWindow,
                meanCommsPerAddress, meanWork, configFraction, pngFraction, acceptingFraction);
    
        HashMap<Integer, Boolean> PNG = new HashMap<Integer, Boolean>();
        
        HashMap<Integer, IntervalSkipList> R = new HashMap<Integer, IntervalSkipList>();
        
        for (int i = 0; i < Math.pow(1 << numAddressesLog, 1.5); i++) {
            
            Packet pkt = src.getConfigPacket();
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
        
        PaddedPrimitiveNonVolatile<Boolean> done = new PaddedPrimitiveNonVolatile<Boolean>(false);
        PaddedPrimitive<Boolean> memFence = new PaddedPrimitive<Boolean>(false);
                 
        final int HIST_SIZE= (1<< 16);
        System.out.println(HIST_SIZE);
        AtomicInteger[] histogram = new AtomicInteger[HIST_SIZE];
        for(int i = 0 ; i < histogram.length; i++){
            histogram[i] = new AtomicInteger(0);
        }

        SerialPacketWorker workerData = new SerialPacketWorker(src, PNG, R,done, histogram);
        Thread workerThread = new Thread(workerData);
        
        workerThread.start();
        timer.startTimer();
        
        
        try {
          Thread.sleep(numMilliseconds);
        } catch (InterruptedException ignore) {;}
        done.value = true;
        memFence.value = true;  // memFence is a 'volatile' forcing a memory fence
        try {                   // which means that done.value is visible to the workers
          workerThread.join();
        } catch (InterruptedException ignore) {;}      
        timer.stopTimer();
        
        final long totalCount = workerData.totalPackets;
        System.out.println("count: " + totalCount);
        System.out.println("time: " + timer.getElapsedTime());
        System.out.println(totalCount/timer.getElapsedTime() + " pkts / ms");
    } 
}




/*
 * Initialize dispatcher thread - which places the packet in the appropriate worker's lamport queue,
 * based on packet type. 
 */

/*
 * Initialize worker thread for data workers - which dequeues from its lamport queue if it is not empty and
 * then checks permissions in PNG and R. If the permissions are good then calculate the figerprint and add to residue.
 * If the permissions are not determined, then only process the packet if there are no permissions for the source,
 * but there are permissions for the destination. Otherwise drop the packet. These process is done atomically.
 */

/*
 * Initialize worker thread for configuration packet workers - which dequeues the packet form its own lamport queue,
 * and then configures PNG and R atomically.  
 */

class SerialQueueFirewall {


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
                
        StopWatch timer = new StopWatch();
        
        PacketGenerator src = new PacketGenerator(
                numAddressesLog, numTrainsLog, meanTrainSize, meanTrainsPerComm, meanWindow,
                meanCommsPerAddress, meanWork, configFraction, pngFraction, acceptingFraction);

        PacketGenerator configSrc = new PacketGenerator(
                numAddressesLog, numTrainsLog, meanTrainSize, meanTrainsPerComm, meanWindow,
                meanCommsPerAddress, meanWork, 1, pngFraction, acceptingFraction);
        
        
    
        HashMap<Integer, Boolean> PNG = new HashMap<Integer, Boolean>();        
        HashMap<Integer, IntervalSkipList> R = new HashMap<Integer, IntervalSkipList>();
        
        
        for (int i = 0; i < Math.pow(1 << numAddressesLog, 1.5); i++) {
            
            Packet pkt = src.getConfigPacket();
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
        
        //System.out.println(PNG);
        //System.out.println(R);
        
        
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
        LamportQueue<Packet>[] configQueues = new LamportQueue[(int)Math.max(1, numSources*configFraction)];
        LamportQueue<Packet>[] dataQueues = new LamportQueue[(int)Math.max(1, numSources-configQueues.length)];
        
        SerialQueueDataPacketWorker[] dWorkerArray = new SerialQueueDataPacketWorker[(int)Math.max(1, numSources-configQueues.length)];
        SerialQueueConfigPacketWorker[] cWorkerArray = new SerialQueueConfigPacketWorker[(int)Math.max(1, numSources*configFraction)];
        
        Thread[] dataThreads = new Thread[(int)Math.max(1, numSources-configQueues.length)];
        Thread[] configThreads = new Thread[(int)Math.max(1, numSources*configFraction)];
        
        for(int i = 0; i < configQueues.length; i++){
            configQueues[i] = new LamportQueue<Packet>(8); 
            cWorkerArray[i] = new SerialQueueConfigPacketWorker(PNG, R, done, configQueues[i], numPacketsInFlight);
            configThreads[i] = new Thread(cWorkerArray[i]);

        }
        for(int i = 0; i < dataQueues.length; i++){
            dataQueues[i] = new LamportQueue<Packet>(8);  
            dWorkerArray[i] = new SerialQueueDataPacketWorker(PNG, R, done, histogram, dataQueues[i], numPacketsInFlight);
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
