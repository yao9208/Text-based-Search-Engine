/**
 *  This class implements the NEAR operator for all retrieval models.
 *  The synonym operator creates a new inverted list that is the union
 *  of its constituents.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;


public class QryopIlNear extends QryopIl {

  protected int nearPos;
  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new QryopIlSyn (arg1, arg2, arg3, ...).
   */
  public QryopIlNear(int nearPos, Qryop... q) {
    for (int i = 0; i < q.length; i++)
      this.args.add(q[i]);
    this.nearPos = nearPos;
  }

  /**
   *  Appends an argument to the list of query operator arguments.  This
   *  simplifies the design of some query parsing architectures.
   *  @param {q} q The query argument (query operator) to append.
   *  @return void
   *  @throws IOException
   */
  public void add (Qryop a) {
    this.args.add(a);
  }

  /**
   *  Evaluates the query operator, including any child operators and
   *  returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {

    //  Initialization

    allocDaaTPtrs (r);
    syntaxCheckArgResults (this.daatPtrs);

    QryResult result = new QryResult ();
    result.invertedList.field = new String (this.daatPtrs.get(0).invList.field);
    int matchTimes;
    int numInvList = this.daatPtrs.size();


   int numFisrtTerm = this.daatPtrs.get(0).invList.postings.size();
   int currentDocId;
   int flag = 1, j=0;
   ArrayList<Integer> docNo = new ArrayList<Integer>();
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
	   if (flag == 1){
		   
		   List<Integer> resultPosition = new ArrayList<Integer>();
		   List<Vector<Integer>> posVector = new ArrayList<Vector<Integer>>();
		  
		   for (int m=0; m<numInvList; m++){
			   posVector.add(this.daatPtrs.get(m).invList.postings.get(docNo.get(m)).positions);//j?
		   }
		   int[] posPtr = new int[numInvList];
		   for(int i=0; i<posVector.get(0).size(); i++){
			   int[] pos = new int[numInvList];
			   
			   
			   for(int p=0; p<numInvList; p++){
				   pos[p]=-1;
			   }
			   pos[0] = posVector.get(0).get(i);
			   int n;
			   EVERYFILE://position list
			   
			   for (n=1; n<numInvList; n++){
				   int sizep = posVector.get(n).size();
				   for (int p=posPtr[n]; p<sizep; p++){
					   int curPos = posVector.get(n).get(p);

					   if ((curPos-pos[n-1]) <= nearPos && (curPos-pos[n-1])>0){
						   pos[n] = curPos;
						   posPtr[n] = p+1;
						   break;
					   }
					   if((curPos-pos[n-1])>nearPos){
						   posPtr[n] = p;
						   break EVERYFILE;
					   }
				   }
			   }			   
			   
			   if (pos[numInvList-1]!=-1){
				   matchTimes++;
				   resultPosition.add(pos[numInvList-1]);				   
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

  /**
   *  Return the smallest unexamined docid from the DaaTPtrs.
   *  @return The smallest internal document id.
   */
  public int getSmallestCurrentDocid () {

    int nextDocid = Integer.MAX_VALUE;

    for (int i=0; i<this.daatPtrs.size(); i++) {
      DaaTPtr ptri = this.daatPtrs.get(i);
      if (nextDocid > ptri.invList.getDocid (ptri.nextDoc))
    	  nextDocid = ptri.invList.getDocid (ptri.nextDoc);
    }

    return (nextDocid);
  }

  /**
   *  syntaxCheckArgResults does syntax checking that can only be done
   *  after query arguments are evaluated.
   *  @param ptrs A list of DaaTPtrs for this query operator.
   *  @return True if the syntax is valid, false otherwise.
   */
  public Boolean syntaxCheckArgResults (List<DaaTPtr> ptrs) {

    for (int i=0; i<this.args.size(); i++) {

      if (! (this.args.get(i) instanceof QryopIl)) 
    	  QryEval.fatalError ("Error:  Invalid argument in " + this.toString());
      else if ((i>0) && (! ptrs.get(i).invList.field.equals (ptrs.get(0).invList.field)))
    	  QryEval.fatalError ("Error:  Arguments must be in the same field:  " +  this.toString());
    }

    return true;
  }

  /*
   *  Return a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString(){
    
    String result = new String ();

    for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
      result += (i.next().toString() + " ");

    return ("#NEAR( " + result + ")");
  }
}
