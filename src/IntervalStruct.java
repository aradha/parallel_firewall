import java.util.ArrayList;

public class IntervalStruct {
    ArrayList<Interval> list = new ArrayList<Interval>();
    
    public IntervalStruct(){        
    }
    
    
    /**
     * 
     * @param interval - is an interval with the same start and same end addresses
     * @return
     */
    public boolean contains(Interval interval){        
        //System.out.println(list.size());
        for(int i = 0; i < list.size(); i++){

            //System.out.println(list.size());
            try{
            if(interval.compareTo(list.get(i)) == 0){               
                return true;
            }
            }catch(NullPointerException e){
                System.out.println(i + " " + list.size() + " " + list + " " );
            }
        }
        return false;
    }
    
    public void add(Interval interval){
        //System.out.println("ADD");
        boolean added = false;
        int index = 0;
        //System.out.println(list.size());
        if(list.size() == 0){
            list.add(interval);
            return;
        }
        
        if(list.size() == 1){
            Interval succ = list.get(0);
            int tmpIndex = 0; 
            if(succ.endAddr < interval.startAddr){
                list.add(interval);
                tmpIndex = 1;
            }
            else if (interval.endAddr < succ.startAddr){
                list.add(0, interval);
                tmpIndex = 0;
            }
            else{
                Interval first = list.get(0);
                list.remove(0);
                list.add(new Interval((Math.min(first.startAddr, interval.startAddr)),
                        Math.max(first.endAddr, interval.endAddr)));
            }
            return;
        }
       
        //int lowerBound = list.get(0).startAddr;
        //int upperBound = list.get(list.size()-1).endAddr;
        
        /*
         * Optimizations to be added: if the new interval is outside the 
         * upper and lower bounds, just add to the end.
         */
        
        
        for(int i = 0; i < list.size(); i++){
            Interval succ = list.get(i);
            if(succ.endAddr < interval.startAddr ){
                index = i+1;
            }
        }
        
        //System.out.println("INDEX: " + index + " " + list.size() + " " + interval);
        Interval tmp = null;
        if(index < list.size()){
            tmp = list.get(index);
        }
        else{
            //Edge case that new interval is added at end
            list.add(interval);
            //list.remove(index-1);
            added = true;
        }

        
        
        //System.out.println(index + " " + interval);
        if(!added){
            list.add(index, interval);
        }
        
        boolean marked = false;
        //System.out.println("MARKING");
        while(!marked && index < list.size()-1){
            Interval next = list.get(index+1);
            //System.out.println(next + " " + index + " " + list.size() + " " + (!marked && index < list.size()-1));
            if(next.startAddr-1 > interval.endAddr){
                marked = true;
            }
            else{
                interval = merge(next, index+1, interval, index);
                //System.out.println(next + " " + index + " " + list.size() + " " + interval);
            }            
        }
        if(index != 0){
            Interval prev = list.get(index-1);
            if(interval.startAddr-1 == prev.endAddr){
                merge(interval, index, prev, index-1);
            }
        }
    }
    
    public Interval merge(Interval next, int nextIndex, Interval interval, int index){
        list.remove(nextIndex);
        list.remove(index);
        list.add(index, new Interval(Math.min(next.startAddr, interval.startAddr),
                Math.max(next.endAddr, interval.endAddr)));
        return list.get(index);
    }
    
    //Works for one interval list, empty interval list
    public void remove(Interval interval){
        //System.out.println("REMOVE");
        int index = 0;
        //System.out.println(list.size());
        if(list.size() == 0){
            return;
        }

        int lowerBound = list.get(0).startAddr;
        int upperBound = list.get(list.size()-1).endAddr;
        
        if(interval.startAddr > upperBound){
            return;
        }
        if(interval.endAddr < lowerBound){
            return;
        }
        
        if(list.size() == 1){
            Interval succ = list.get(0);

            //Case 0: [1, 5] remove [0,3]
            //Case 1: [1, 5] remove [2,4]
            //Case 2: [1, 5] remove [3, 6]
            //Case 3: [1, 5] remove [0, 6]
            //Case 1:
            if(interval.endAddr < succ.endAddr && succ.startAddr < interval.startAddr){
                list.remove(0);
                list.add(new Interval(succ.startAddr, interval.startAddr-1));
                list.add(new Interval(interval.endAddr+1, succ.endAddr));
            }
            //Case 0:
            else if (interval.endAddr < succ.endAddr && interval.startAddr <= succ.startAddr && interval.endAddr >= succ.startAddr){
                list.remove(0);
                list.add(new Interval(interval.endAddr+1, succ.endAddr));
            }
            //Case 2:
            else if (interval.startAddr > succ.startAddr && interval.endAddr >= succ.endAddr){
                list.remove(0);
                list.add(new Interval(succ.startAddr, interval.startAddr-1));
            }
            else if(interval.startAddr <= succ.startAddr && interval.endAddr >= succ.endAddr){
                list.remove(0);
            }
            else{
            }
            
            return;
        }
       

        /*
         * Optimizations to be added: if the new interval is outside the 
         * upper and lower bounds, just add to the end.
         */
        
        //Find the index of an overlapping interval
        for(int i = 0; i < list.size(); i++){
            Interval succ = list.get(i);
            if(succ.endAddr < interval.startAddr ){
                index = i+1;
            }
        }
        
        //System.out.println("INDEX: " + index + " " + list.size() + " " + interval);
        
        //Remove exactly 1 interval
        Interval succ = list.get(index);

        //Case 0: [1, 5] remove [0,3]
        //Case 1: [1, 5] remove [2,4]
        //Case 2: [1, 5] remove [3, 6]
        //Case 3: [1, 5] remove [0, 6]
        //Case 1:
        if(interval.endAddr <= succ.endAddr){    
            if(interval.endAddr < succ.endAddr && succ.startAddr < interval.startAddr){
                list.remove(index);
                list.add(index, new Interval(succ.startAddr, interval.startAddr-1));
                list.add(index +1, new Interval(interval.endAddr+1, succ.endAddr));
            }
            //Case 0:
            else if (interval.endAddr < succ.endAddr && interval.startAddr <= succ.startAddr && interval.endAddr >= succ.startAddr){
                list.remove(index);
                list.add(index, new Interval(interval.endAddr+1, succ.endAddr));
            }
            //Case 2:
            else if (interval.startAddr > succ.startAddr && interval.endAddr >= succ.endAddr){
                list.remove(index);
                list.add(index, new Interval(succ.startAddr, interval.startAddr-1));
            }
            else if(interval.endAddr == succ.endAddr && interval.startAddr == succ.startAddr){
                list.remove(index);
            }
            else{
            }            
            return;
        }
        
        boolean marked = false;
        Interval next = null;
        Interval start = list.get(index);
        int startIndex = index++;
        //System.out.println("START : " + start);
        //If interval overlaps more than 1 interval
        //index++;
        while(!marked && index < list.size()-1){
            next = list.get(index);
            //System.out.println(index);
            //System.out.println("REMOVING" + " " + next + " " + interval);
            if(next.endAddr <= interval.endAddr){
                //System.out.println("REMOVED");
                list.remove(index);
            }
            else{
                marked = true;
            }
        }
        if(index < list.size()){
            Interval end = list.get(index);
            int endIndex = index;
            //System.out.println(list);
            //System.out.println("END: " + end);
            split(start, startIndex, end, endIndex, interval);
        }
        else{
            if (interval.endAddr < start.endAddr && interval.startAddr <= start.startAddr){
                list.remove(startIndex);
                list.add(startIndex, new Interval(interval.endAddr+1, start.endAddr));
            }
            else if (interval.startAddr > start.startAddr && interval.endAddr >= start.endAddr){
                list.remove(startIndex);
                list.add(startIndex, new Interval(start.startAddr, interval.startAddr-1));
            }
        }
               
    }
    
    public void split(Interval start, int startIndex, Interval end, int endIndex, Interval interval){
        int size = list.size();


        if (interval.endAddr < start.endAddr && interval.startAddr <= start.startAddr){
            list.remove(startIndex);
            list.add(startIndex, new Interval(interval.endAddr+1, start.endAddr));
        }
        else if (interval.startAddr > start.startAddr && interval.endAddr >= start.endAddr){
            list.remove(startIndex);
            list.add(startIndex, new Interval(start.startAddr, interval.startAddr-1));
        }
        else if(interval.startAddr <= start.startAddr){
            list.remove(startIndex);            
        }

        if(size == list.size()){
            
        }
        else{
            endIndex--;
        }
        
        if (interval.endAddr < end.endAddr && interval.startAddr <= end.startAddr && interval.endAddr >= end.startAddr){
            list.remove(endIndex);
            list.add(endIndex, new Interval(interval.endAddr+1, end.endAddr));
        }
        else if (interval.startAddr > end.startAddr && interval.endAddr >= end.endAddr){
            list.remove(endIndex);
            list.add(endIndex, new Interval(end.startAddr, interval.startAddr-1));
        }
        else if(interval.endAddr >= end.endAddr){
            list.remove(endIndex);            
        }


    }
    
    @Override
    public String toString(){
        return list.toString();
    }

}

class IntervalStructTest{
    public static void main(String[] args){
        IntervalStruct tmp = new IntervalStruct();
        tmp.add(new Interval(2, 8));
        tmp.add(new Interval(15, 19));
        tmp.add(new Interval(5, 9));
        tmp.add(new Interval(0, 5));
        tmp.add(new Interval(-2, 0));
        tmp.add(new Interval(17, 21));
        tmp.add(new Interval(0, 5));
        tmp.add(new Interval(5, 18));
        System.out.println(tmp.toString());
        tmp.add(new Interval(33, 100));
        System.out.println(tmp.toString());
        tmp.add(new Interval(50,60));
        tmp.add(new Interval(100,103));
        tmp.add(new Interval(22,30));
        System.out.println(tmp.toString());

        tmp.add(new Interval(105, 200));
        System.out.println(tmp.toString());
        tmp.add(new Interval(204, 210));
        tmp.add(new Interval(32,212));
        tmp.add(new Interval(213, 300));
        tmp.add(new Interval(-10, -4));
        System.out.println(tmp);
//        tmp.add(new Interval(-1, 300));
//
//        tmp.add(new Interval(29, 203));
//        tmp.add(new Interval(-100,-4));
//        //tmp.add(new Interval(-5, -1));
//        tmp.add(new Interval(33, 50));
//        tmp.add(new Interval(31, 32));



        //System.out.println(tmp.list.size());
        //tmp.add(new Interval(-200, 12));
        tmp.add(new Interval(22, 25));
        System.out.println(tmp.contains(new Interval(14,14)));

        //tmp.add(new Interval(-3, 1));
        System.out.println(tmp.toString());
        tmp.remove(new Interval(-2, 3500));
        tmp.remove(new Interval(-10, -4));
//        tmp.remove(new Interval(15, 100));
//        System.out.println(tmp.toString());
//        tmp.add(new Interval(30, 70));
//        System.out.println(tmp.toString());
//        tmp.remove(new Interval(-1, 200));
//        System.out.println(tmp.toString());
//        tmp.remove(new Interval(-5, -1));
        System.out.println(tmp.toString());

        
    }
}