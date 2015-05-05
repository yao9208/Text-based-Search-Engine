import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;



public class QryopIlWindow extends QryopIl{
	
	protected int WindowPos;

	public QryopIlWindow(int WindowPos, Qryop... q) {
	    for (int i = 0; i < q.length; i++)
	      this.args.add(q[i]);
	    this.WindowPos = WindowPos;
	  }


	  public void add (Qryop a) {
	    this.args.add(a);
	  }

	public QryResult evaluate(RetrievalModel r) throws IOException {
		allocDaaTPtrs (r);
	    syntaxCheckArgResults (this.daatPtrs);
	    QryResult result = new QryResult ();
	    result.invertedList.field = new String (this.daatPtrs.get(0).invList.field);
	    int matchTimes;
	    int numInvList = this.daatPtrs.size();
	    
	    int numFisrtTerm = this.daatPtrs.get(0).invList.postings.size();
	    int currentDocId;
	    int flag = 1, j=0;
	    ArrayList<Integer> docNo = new ArrayList<Integer>();//record common docids in invertedlists
	    int listPtr[] = new int[numInvList];
	    
	    for (int k = 0; k < numFisrtTerm; k++) {
	 	   flag=1;
	 	   matchTimes = 0;
	 	   currentDocId = this.daatPtrs.get(0).invList.getDocid(k);
	 	   docNo.add(k);
	 	   
	 	   
	 	   for (int i = 1; i < numInvList; i++) {
	 		   
	 		   InvList curPtr = this.daatPtrs.get(i).invList;
	 		   
	 		   for (j=listPtr[i]; j<curPtr.postings.size(); j++){//j<...
	 			   if (curPtr.getDocid(j) == currentDocId){
	 				   docNo.add(j);
	 				   listPtr[i]=j+1;
	 				   break;
	 			   }
	 			   if (curPtr.getDocid(j) > currentDocId){
	 				   listPtr[i]=j;
	 				   flag = 0;
	 				   break;
	 			   }
	 		   }
	 		   if (flag == 0||j==curPtr.postings.size()){
	 			   flag = 0;
	 			   break;
	 		   }
	 	   }
	 	   if (flag == 1){//all the inverted lists have the same doc id
	 		   
	 		  int minPtr;
	 		  int minPos, maxPos;
	 		 List<Integer> resultPosition = new ArrayList<Integer>();
	 		  List<Vector<Integer>> posVector = new ArrayList<Vector<Integer>>();
			  
			  for (int m=0; m<numInvList; m++){
				  posVector.add(this.daatPtrs.get(m).invList.postings.get(docNo.get(m)).positions);//j?
			  }
			  
			  //find the minPtr and maxPtr
			  int[] pos = new int[numInvList];
			  
			  LOOP:
			  while (true) {
				  minPos=Integer.MAX_VALUE;
				  maxPos=Integer.MIN_VALUE;
				  minPtr=Integer.MAX_VALUE;
				for (int m = 0; m < numInvList; m++) {
					if (posVector.get(m).get(pos[m]) < minPos) {
						minPos = posVector.get(m).get(pos[m]);
						minPtr = m;
					}
					if (posVector.get(m).get(pos[m]) > maxPos) {
						maxPos = posVector.get(m).get(pos[m]);
					}
				}
				if ((maxPos - minPos +1) <= this.WindowPos) {
					matchTimes++;
					resultPosition.add(pos[numInvList-1]);
					for (int m = 0; m < numInvList; m++) {
						pos[m]++;
					}
				} else {
					pos[minPtr]++;
				}
				for (int m = 0; m < numInvList; m++) {
					if (pos[m] >= posVector.get(m).size())
						break LOOP;
				}
			}
			if(matchTimes!=0){
				Collections.sort(resultPosition);
				result.invertedList.appendPosting(currentDocId, resultPosition);
			}
			resultPosition.clear();

	 	  }
	 	  docNo.clear();
	    }
	    
	    freeDaaTPtrs();

	    return result;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	public Boolean syntaxCheckArgResults (List<DaaTPtr> ptrs) {

	    for (int i=0; i<this.args.size(); i++) {

	      if (! (this.args.get(i) instanceof QryopIl)) 
	    	  QryEval.fatalError ("Error:  Invalid argument in " + this.toString());
	      else if ((i>0) && (! ptrs.get(i).invList.field.equals (ptrs.get(0).invList.field)))
	    	  QryEval.fatalError ("Error:  Arguments must be in the same field:  " +  this.toString());
	    }

	    return true;
	  }

}
