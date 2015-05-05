/**
 *  This class implements the document score list data structure
 *  and provides methods for accessing and manipulating them.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.*;

public class ScoreList {

  //  A little utilty class to create a <docid, score> object.
  //public int ctf;

  public class ScoreListEntry implements Comparable<ScoreListEntry>{
    public int docid;
    private double score;
    public String externId;

    private ScoreListEntry(int docid, double score) throws IOException {
      this.docid = docid;
      this.score = score;
      //this.externId = QryEval.ExterID.get(docid);
    }
    
    public int compareTo(ScoreListEntry o){
    	int i = new Double(o.score).compareTo(this.score);
    	int j = 0;
    	if (i == 0){
    			j = this.externId.compareTo(o.externId);
    			return j;
    	}
    	return i;
    }
  }

  List<ScoreListEntry> scores = new ArrayList<ScoreListEntry>();
  
  /**
   *  Append a document score to a score list.
   *  @param docid An internal document id.
   *  @param score The document's score.
   *  @return void
 * @throws IOException 
   */
  public void add(int docid, double score) throws IOException {
    scores.add(new ScoreListEntry(docid, score));
  }

  /**
   *  Get the n'th document id.
   *  @param n The index of the requested document.
   *  @return The internal document id.
   */
  public int getDocid(int n) {
    return this.scores.get(n).docid;
  }

  /**
   *  Get the score of the n'th document.
   *  @param n The index of the requested document score.
   *  @return The document's score.
   */
  public double getDocidScore(int n) {
    return this.scores.get(n).score;
  }

  

}

