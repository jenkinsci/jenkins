
import java.util.*;

public class ExistanceofApSequence {
public static boolean ap(int []arr) {
	if(arr.length<=1) {
		return true;
	}
	int max=Integer.MIN_VALUE;
	int min=Integer.MAX_VALUE;
	Set<Integer>set=new HashSet<>();
	for(int val:arr) {
		max=Math.max(val,max);
		min=Math.min(val,min);
		set.add(val);
	}
	int cd=(max-min)/(arr.length-1);
	for(int i=0;i<arr.length;i++) {
		int ai=min+i*cd;
		if(set.contains(ai)==false) {
			return false;
		}
	}
	return true;
	
}
	public static void main(String[] args) {
	Scanner sc=new Scanner (System.in);
	int n=sc.nextInt();
	int []arr=new int[n];
	for(int i=0;i<arr.length;i++) {
		arr[i]=sc.nextInt();
	}
	boolean a=ap(arr);
	System.out.println(a);

	}

}
