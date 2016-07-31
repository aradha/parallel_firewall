import java.util.concurrent.ConcurrentHashMap;



public class SourceDestinationPair{
    public int source;
    public int dest;
    public SourceDestinationPair(int source, int dest){
        this.source = source;
        this.dest = dest;
    }
    

    
    @Override
    public int hashCode(){
        return (source + " " + dest).hashCode();
    }
    
    @Override
    public boolean equals(Object o){
        if(o instanceof SourceDestinationPair){
            SourceDestinationPair sd = (SourceDestinationPair) o;
            return sd.source == source && sd.dest == dest;
        }
        else 
            return false;
    }
}

class SDTest{
    public static void main(String[] args){
        ConcurrentHashMap<SourceDestinationPair, Boolean> cache = new ConcurrentHashMap<SourceDestinationPair, Boolean>();
        cache.put(new SourceDestinationPair(0, 0), false);
        System.out.println(cache.containsKey(new SourceDestinationPair(0,0)));
    }
}