import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

class Dispatcher implements Runnable {
    // MUST take in the packetSrc *src*
    PaddedPrimitiveNonVolatile<Boolean> done;
    final PacketGenerator src;
    int totalCount = 0;

    // Also all lamport queues.
    final LamportQueue<Packet>[] configQueues;
    final LamportQueue<Packet>[] dataQueues;
    int[] dist;
    AtomicInteger numPacketsInFlight;
    //final Queue<Packet> configDelayQueue =  new LinkedBlockingQueue<Packet>();
    public Dispatcher(PaddedPrimitiveNonVolatile<Boolean> done,
            PacketGenerator src, LamportQueue<Packet>[] configQueues,
            LamportQueue<Packet>[] dataQueues, AtomicInteger numPacketsInFlight) {
        this.done = done;
        this.src = src;
        this.configQueues = configQueues;
        this.dataQueues = dataQueues;
        this.dist = new int[dataQueues.length];
        this.numPacketsInFlight = numPacketsInFlight;
    }

    public void run() {
        int dQNum = 0;
        int cQNum = 0;
        while (!done.value) {
            /*
             * Dispatcher ideas:
             * (1) Need an idea for postponing config packets
             * (2) Need to keep track of trains and give them to the same data worker
             *      (i) Have an array of size numDataWorkers and use to keep track of trains
             * (3) Caches for each worker should be a hash map from the pseudo random number tag to the fingerprint
             * (4) Each data worker should keep a list of tags that it has processed.
             * (5) The configuration workers need to somehow update the data worker caches
             */
            
            while(numPacketsInFlight.get() > 256){
                //System.out.println(numPacketsInFlight.get());
                if(done.value){
                    //System.out.println(cQNum + " " + dQNum);
                    break;
                }
            }
            
            Packet pkt = src.getPacket();
            numPacketsInFlight.getAndIncrement();
           
            /*if(pkt.type.toString().equals("DataPacket") && pkt.header.source == 0 && pkt.header.dest == 0){
                System.out.println(pkt.header.source + " "+ pkt.header.dest +
                    " " + pkt.header.sequenceNumber + " " + pkt.header.trainSize + " " + pkt.header.tag);
            }*/
            if (pkt.type.toString().equals("ConfigPacket")) {
//                //Maybe to delay config packets so that we have burst traffic
//                // Helper method to enqueue onto config packet queue
//                configDelayQueue.add(pkt);
//                if(configDelayQueue.size() >= 30){
//                    while(!configDelayQueue.isEmpty()){   
//                        //System.out.println("GOT HERE");
//                        enqConfig(configDelayQueue.remove(), (cQNum++)%configQueues.length);
//                    }
//                }
                enqConfig(pkt, (cQNum++)%configQueues.length);
                
            } else {
                // Helper method to enqueue onto data packet queue
                //System.out.println("DATA PACKET");
                enqData(pkt, (dQNum++)%dataQueues.length);
//                if(dQNum - cQNum == 10 && !configDelayQueue.isEmpty()){ //Every 10 data packets process a config packt
//                    enqConfig(configDelayQueue.remove(), (cQNum++)%configQueues.length);
//                }
            }
        }

         for(int i = 0; i < dist.length; i++){
             System.out.print(dist[i] + " "); //To print distribution across workers
         }
         System.out.println();
    }

    public void enqData(Packet pkt, int index){
        for (int i = index; i < dataQueues.length; i++) {
            boolean enqueued = false;
            int j = i;
            while (!enqueued) {
                try {
                    dataQueues[j % dataQueues.length].enqueue(pkt);
                    dist[j%dataQueues.length]++;
                    enqueued = true;
                    totalCount++;
                    return;
                } catch (FullException e) {
                    j++;
                }
            }
        }
    }
    
    public void enqConfig(Packet pkt, int index) {
        for (int i = index; i < configQueues.length; i++) {
            boolean enqueued = false;
            int j = i;
            while (!enqueued) {
                try {
                    configQueues[j % configQueues.length].enqueue(pkt);
                    //dist[j%numSources]++;
                    enqueued = true;
                    //totalCount++;
                    return;
                } catch (FullException e) {
                    j++;
                }
            }
        }
    }
}
