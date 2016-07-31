
public class Interval implements Comparable{
    public final int startAddr;
    public final int endAddr;
    public Interval(int startAddr, int endAddr){
        this.startAddr = startAddr;
        this.endAddr = endAddr;
    }
    
    @Override
    public String toString(){
        return "[ "+ startAddr + "-" + endAddr + " ]";
    }

    @Override
    public int compareTo(Object o) {
        Interval interval = (Interval) o;
        if(this.startAddr >= interval.startAddr && this.endAddr <= interval.endAddr){
            return 0;
        }
        else if (this.startAddr > interval.endAddr){
            return 1;
        }
        return -1;
    }
}
