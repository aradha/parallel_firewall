package customParallelFirewall;
import java.util.concurrent.atomic.AtomicStampedReference;


public class SkipInterval implements Comparable<SkipInterval>{
    public final int startAddr;
    public final int endAddr;
    public boolean accepting = true;
    public SkipInterval(int startAddr, int endAddr){
        this.startAddr = startAddr;
        this.endAddr = endAddr;
    }
    
    @Override
    public String toString(){
        return "[ "+ startAddr + "-" + endAddr + " ]";
    }

    @Override
    public int compareTo(SkipInterval interval) {
        //System.out.println("COMPARING: " + this +  " " + interval);
        //Sort according to starting time
        if(this.startAddr >= interval.startAddr && this.endAddr <= interval.endAddr){
          return 0;
        }
        if(this.startAddr > interval.startAddr){
            return 1;
        } else if(this.startAddr < interval.startAddr){
            return -1;
        } else if(this.endAddr > interval.endAddr){
            return 1;
        } else if(this.endAddr < interval.endAddr){
            return -1;
        } else
            return 0;
        
//        
//        if(this.startAddr >= interval.startAddr && this.endAddr <= interval.endAddr){
//            return 0;
//        } else if(this.startAddr > interval.endAddr){
//            return 1;
//        } else{
//            return -1;
//        }
    }
    
    @Override
    public int hashCode(){
        return this.toString().hashCode();
    }
}
