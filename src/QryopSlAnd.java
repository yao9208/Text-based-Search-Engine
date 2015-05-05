/**
 *  This class implements the AND operator for all retrieval models.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

public class QryopSlAnd extends QryopSl {

  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
   *  @param q A query argument (a query operator).
   */
  public QryopSlAnd(Qryop... q) {
    for (int i = 0; i < q.length; i++)
      this.args.add(q[i]);
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

    if (r instanceof RetrievalModelUnrankedBoolean || r instanceof RetrievalModelRankedBoolean)
      return (evaluateBoolean (r));
    
    if (r instanceof RetrievalModelIndri){
    	return(evaluateIndri(r));
    }

    return null;
  }

  /**
   *  Evaluates the query operator for boolean retrieval models,
   *  including any child operators and returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluateBoolean (RetrievalModel r) throws IOException {

    //  Initialization

    allocDaaTPtrs (r);
    QryResult result = new QryResult ();

    //  Sort the arguments so that the shortest lists are first.  This
    //  improves the efficiency of exact-match AND without changing
    //  the result.

    for (int i=0; i<(this.daatPtrs.size()-1); i++) {
      for (int j=i+1; j<this.daatPtrs.size(); j++) {
	if (this.daatPtrs.get(i).scoreList.scores.size() >
	    this.daatPtrs.get(j).scoreList.scores.size()) {
	    ScoreList tmpScoreList = this.daatPtrs.get(i).scoreList;
	    this.daatPtrs.get(i).scoreList = this.daatPtrs.get(j).scoreList;
	    this.daatPtrs.get(j).scoreList = tmpScoreList;
	}
      }
    }

    //  Exact-match AND requires that ALL scoreLists contain a
    //  document id.  Use the first (shortest) list to control the
    //  search for matches.

    //  Named loops are a little ugly.  However, they make it easy
    //  to terminate an outer loop from within an inner loop.
    //  Otherwise it is necessary to use flags, which is also ugly.
    
    if (r instanceof RetrievalModelUnrankedBoolean){

    DaaTPtr ptr0 = this.daatPtrs.get(0);

    EVALUATEDOCUMENTS:
    for ( ; ptr0.nextDoc < ptr0.scoreList.scores.size(); ptr0.nextDoc ++) {

      int ptr0Docid = ptr0.scoreList.getDocid (ptr0.nextDoc);
      double docScore = 1.0;

      //  Do the other query arguments have the ptr0Docid?

      for (int j=1; j<this.daatPtrs.size(); j++) {

	DaaTPtr ptrj = this.daatPtrs.get(j);

	while (true) {
	  if (ptrj.nextDoc >= ptrj.scoreList.scores.size())
	    break EVALUATEDOCUMENTS;		// No more docs can match
	  else
	    if (ptrj.scoreList.getDocid (ptrj.nextDoc) > ptr0Docid)
	      continue EVALUATEDOCUMENTS;	// The ptr0docid can't match.
	  else
	    if (ptrj.scoreList.getDocid (ptrj.nextDoc) < ptr0Docid)
	      ptrj.nextDoc ++;			// Not yet at the right doc.
	  else
	      break;				// ptrj matches ptr0Docid
	}
      }

      //  The ptr0Docid matched all query arguments, so save it.

      result.docScores.add (ptr0Docid, docScore);
    }
    }
    
    else{//RankedBoolean
    	DaaTPtr ptr0 = this.daatPtrs.get(0);

        EVALUATEDOCUMENTS:
        for ( ; ptr0.nextDoc < ptr0.scoreList.scores.size(); ptr0.nextDoc ++) {

          int ptr0Docid = ptr0.scoreList.getDocid (ptr0.nextDoc);
          double docScore = ptr0.scoreList.getDocidScore(ptr0.nextDoc);

          //  Do the other query arguments have the ptr0Docid?

       for (int j=1; j<this.daatPtrs.size(); j++) {

    	DaaTPtr ptrj = this.daatPtrs.get(j);

    	while (true) {
    	  if (ptrj.nextDoc >= ptrj.scoreList.scores.size())
    	    break EVALUATEDOCUMENTS;		// No more docs can match
    	  else
    	    if (ptrj.scoreList.getDocid (ptrj.nextDoc) > ptr0Docid)
    	      continue EVALUATEDOCUMENTS;	// The ptr0docid can't match.
    	  else
    	    if (ptrj.scoreList.getDocid (ptrj.nextDoc) < ptr0Docid){
    	      ptrj.nextDoc ++;			// Not yet at the right doc.
    	    }
    	    else{
    	      docScore = Math.min(docScore, ptrj.scoreList.getDocidScore(ptrj.nextDoc));
    	      break;				// ptrj matches ptr0Docid
    	    }
    		}
         }

          //  The ptr0Docid matched all query arguments, so save it.

          result.docScores.add (ptr0Docid, docScore);
        }
    }

    freeDaaTPtrs ();

    return result;
  }
  
  
  public QryResult evaluateIndri (RetrievalModel r) throws IOException {
	  
	  allocDaaTPtrs (r);
	  QryResult result = new QryResult ();
	  
	  double queryLength = this.daatPtrs.size();
	  int minDoc, temp;
	  double AndScore = 1.0;
	  double exponent = 1.0/queryLength;

	  while(true){
	    	minDoc = Integer.MAX_VALUE;
	    	AndScore = 1.0;
	    	for (int i=0; i<queryLength; i++){
	    		if (daatPtrs.get(i).nextDoc>=daatPtrs.get(i).scoreList.scores.size())  {    //if can't find doc in this list, search for the next
	    			continue;
	    		}
	    		if ((temp=daatPtrs.get(i).scoreList.getDocid(daatPtrs.get(i).nextDoc))<minDoc)
	    			minDoc = temp;
	    	}
	    	
	    	if (minDoc == Integer.MAX_VALUE)
	    		break;
	    	
	    	for (int i=0; i<queryLength; i++){   //look for entries which nextDoc == minDoc
	    		if (daatPtrs.get(i).nextDoc>=daatPtrs.get(i).scoreList.scores.size()||!(daatPtrs.get(i).scoreList.getDocid(daatPtrs.get(i).nextDoc)==minDoc)){
	    			QryopSl q = (QryopSl)this.args.get(i);

	    			AndScore *= Math.pow(q.getDefaultScore(r, minDoc),exponent);
	    		}
	    		else{
	    			AndScore *= Math.pow(daatPtrs.get(i).scoreList.getDocidScore(daatPtrs.get(i).nextDoc), exponent);
	    			daatPtrs.get(i).nextDoc++;
	    		}
	    	}
	    	result.docScores.add(minDoc, AndScore);
	  }
	  freeDaaTPtrs ();

	  return result;
  }

  /*
   *  Calculate the default score for the specified document if it
   *  does not match the query operator.  This score is 0 for many
   *  retrieval models, but not all retrieval models.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @param docid The internal id of the document that needs a default score.
   *  @return The default score.
   */
  public double getDefaultScore (RetrievalModel r, long docid) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean)
      return (0.0);
    if (r instanceof RetrievalModelIndri){
    	double result = 1.0;
    	for (int i=0; i<args.size(); i++){
    		QryopSl q = (QryopSl)this.args.get(i);
    		result *= Math.pow(q.getDefaultScore(r, docid), (1.0/args.size())) ;    		
    		}
    	return result;
    }

    return 0.0;
  }

  /*
   *  Return a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString(){
    
    String result = new String ();

    for (int i=0; i<this.args.size(); i++)
      result += this.args.get(i).toString() + " ";

    return ("#AND( " + result + ")");
  }
}
