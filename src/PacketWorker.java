
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.deuce.*;

public interface PacketWorker extends Runnable {
  public void run();
}

class SerialPacketWorker implements PacketWorker{
    PaddedPrimitiveNonVolatile<Boolean> done;
    final PacketGenerator pktGen;
    final Fingerprint residue = new Fingerprint();
    long fingerprint = 0;
    long totalPackets = 0;
    HashMap<Integer, Boolean> PNG;
    HashMap<Integer, IntervalSkipList> R;
    final AtomicInteger[] histogram;
    
    public SerialPacketWorker(PacketGenerator pktGen, 
            HashMap<Integer, Boolean> PNG,
            HashMap<Integer, IntervalSkipList> R,
            PaddedPrimitiveNonVolatile<Boolean> done,
            AtomicInteger[] histogram) {

        this.done = done;
        this.pktGen = pktGen;
        this.PNG = PNG;
        this.R = R;
        this.histogram = histogram;
    }
        
      
    public void run() {
        Packet pkt;
        while( !done.value ) {
            
            pkt = pktGen.getPacket();
            
            if(pkt.type.toString().equals("ConfigPacket")){
                
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
            else {                
                totalPackets++;
                
                int dest = pkt.header.dest;
                int source = pkt.header.source;
                
                if(!PNG.containsKey(source)){
                    continue;
                } 
                else if(!PNG.get(source) && R.containsKey(dest) && R.get(dest).contains(new SkipInterval(source, source))){
                    long tmpVal = residue.getFingerprint(pkt.body.iterations, pkt.body.seed);
                    fingerprint += tmpVal;
                    histogram[(int)tmpVal].getAndIncrement(); 
                } 
                else {
                    continue; //Drop packet
                }
            }
        }
    } 
}


/*
 * Need both data and config packet workers to handle
 * dequeuing packets from respective queues.
 */
class SerialQueueDataPacketWorker implements PacketWorker{
    PaddedPrimitiveNonVolatile<Boolean> done;
    final Fingerprint residue = new Fingerprint();
    long fingerprint = 0;
    long totalPackets = 0;
    HashMap<Integer, Boolean> PNG;
    HashMap<Integer, IntervalSkipList> R;
    final AtomicInteger[] histogram;
    //Also his lamport queue
    final LamportQueue<Packet> queue;
    AtomicInteger numPacketsInFlight;
    public SerialQueueDataPacketWorker(HashMap<Integer, Boolean> PNG,
            HashMap<Integer, IntervalSkipList> R,
            PaddedPrimitiveNonVolatile<Boolean> done,
            AtomicInteger[] histogram,
            LamportQueue<Packet> queue, 
            AtomicInteger numPacketsInFlight) {

        this.done = done;
        this.PNG = PNG;
        this.R = R;
        this.histogram = histogram;
        this.queue = queue;
        this.numPacketsInFlight = numPacketsInFlight;
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
    
    @Atomic
    public void processDataPacket(Packet pkt){
        int dest = pkt.header.dest;
        int source = pkt.header.source;
        
        if(!PNG.containsKey(source)){
            return;
        } 
        else if(!PNG.get(source) && R.containsKey(dest) && R.get(dest).contains(new SkipInterval(source,source))){
            long tmpVal = residue.getFingerprint(pkt.body.iterations, pkt.body.seed);
            fingerprint += tmpVal;
            histogram[(int)tmpVal].getAndIncrement(); 
        } 
        else {
            return; //Drop packet
        }

    }
}


class SerialQueueConfigPacketWorker implements PacketWorker{
    PaddedPrimitiveNonVolatile<Boolean> done;
    HashMap<Integer, Boolean> PNG;
    HashMap<Integer, IntervalSkipList> R;
    //Also his lamport queue
    final LamportQueue<Packet> queue;
    AtomicInteger numPacketsInFlight;
    public SerialQueueConfigPacketWorker(HashMap<Integer, Boolean> PNG,
            HashMap<Integer, IntervalSkipList> R,
            PaddedPrimitiveNonVolatile<Boolean> done,
            LamportQueue<Packet> queue,
            AtomicInteger numPacketsInFlight) {
        this.done = done;
        this.PNG = PNG;
        this.R = R;
        this.queue = queue;
        this.numPacketsInFlight = numPacketsInFlight;
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
    
    @Atomic
    public void processConfigPacket(Packet pkt){
        PNG.put(pkt.config.address, pkt.config.personaNonGrata);

        int addBegin = pkt.config.addressBegin;
        int addEnd = pkt.config.addressEnd;
        boolean acceptRange = pkt.config.acceptingRange;
        SkipInterval interval = new SkipInterval(addBegin, addEnd-1);
        IntervalSkipList configAddr;;
        

        if (!R.containsKey(pkt.config.address) && !acceptRange) {
            return;

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


