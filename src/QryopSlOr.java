/**
 *  This class implements the Or operator for all retrieval models.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

public class QryopSlOr extends QryopSl {

  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
   *  @param q A query argument (a query operator).
   */
  public QryopSlOr(Qryop... q) {
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

    if (r instanceof RetrievalModelUnrankedBoolean|| r instanceof RetrievalModelRankedBoolean)
      return (evaluateBoolean (r));

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
    //  improves the efficiency of exact-match Or without changing
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

    //  Exact-match OR requires that ANY scoreLists contain a
    //  document id.  Use the first (shortest) list to control the
    //  search for matches.

    //  Named loops are a little ugly.  However, they make it easy
    //  to terminate an outer loop from within an inner loop.
    //  Otherwise it is necessary to use flags, which is also ugly.

    int minDoc, temp;
    double OrScore = 1.0;

    while(true){
    	minDoc = Integer.MAX_VALUE;
    	OrScore = 1.0;
    	for (int i=0; i<this.daatPtrs.size(); i++){
    		if (daatPtrs.get(i).nextDoc>=daatPtrs.get(i).scoreList.scores.size()) {     //if can't find doc in this list, search for the next
    			daatPtrs.remove(i);
    			continue;
    		}
    		if ((temp=daatPtrs.get(i).scoreList.getDocid(daatPtrs.get(i).nextDoc))<minDoc)
    			minDoc = temp;
    	}
    	if (minDoc == Integer.MAX_VALUE)
    		break;
    	
    	for (int i=0; i<this.daatPtrs.size(); i++){   //look for entries which nextDoc == minDoc
    		if (daatPtrs.get(i).nextDoc>=daatPtrs.get(i).scoreList.scores.size())
    			continue;
    		if (daatPtrs.get(i).scoreList.getDocid(daatPtrs.get(i).nextDoc)==minDoc){
    			
    			//To Ranked Boolean, max()
    			if(r instanceof RetrievalModelRankedBoolean){
    				OrScore = Math.max(OrScore, daatPtrs.get(i).scoreList.getDocidScore(daatPtrs.get(i).nextDoc));
    			}
    			daatPtrs.get(i).nextDoc++;
    		}
    	}
    	result.docScores.add(minDoc, OrScore);
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

    return ("#Or( " + result + ")");
  }
}
