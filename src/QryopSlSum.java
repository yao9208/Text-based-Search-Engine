import java.io.IOException;

public class QryopSlSum extends QryopSl{
	
	public QryopSlSum(Qryop... q) {
	    for (int i = 0; i < q.length; i++)
	      this.args.add(q[i]);
	}
	
	public void add (Qryop a) {
	    this.args.add(a);
	}
	
	public QryResult evaluate(RetrievalModel r) throws IOException {

	if (r instanceof RetrievalModelBM25)
	      return (evaluateBM25 (r));

	    return null;
	}
	
	public QryResult evaluateBM25 (RetrievalModel r) throws IOException {
		allocDaaTPtrs (r);
	    QryResult result = new QryResult ();
	    int numDocs = QryEval.READER.numDocs();
	    //DocLengthStore dls = new DocLengthStore(QryEval.READER);
	    double k_1=RetrievalModelBM25.k_1;
	    double b = RetrievalModelBM25.b;
	    double k_3 = RetrievalModelBM25.k_3;
	    
	    int minDoc, temp;
	    double score = 0.0;
	    
	    while(true){
	    	minDoc = Integer.MAX_VALUE;
	    	for (int i=0; i<this.daatPtrs.size(); i++){
	    		if(daatPtrs.get(i).nextDoc>=daatPtrs.get(i).scoreList.scores.size()){
	    			daatPtrs.remove(i);
	    			continue;
	    		}
	    		if((temp = daatPtrs.get(i).scoreList.getDocid(daatPtrs.get(i).nextDoc))<minDoc)
	    			minDoc = temp;

	    	}
	    	if(minDoc==Integer.MAX_VALUE)
	    		break;
	    	score = 0.0;
	    	for (int i=0; i<this.daatPtrs.size(); i++){   //look for entries which nextDoc == minDoc
	    		if (daatPtrs.get(i).nextDoc>=daatPtrs.get(i).scoreList.scores.size())
	    			continue;	    		
	    		if (daatPtrs.get(i).scoreList.getDocid(daatPtrs.get(i).nextDoc)==minDoc){
	    			 score += daatPtrs.get(i).scoreList.getDocidScore(daatPtrs.get(i).nextDoc);
	    			
	    			daatPtrs.get(i).nextDoc++;
	    		}
	    	}
	    	
	    	result.docScores.add(minDoc, score);
	    }
	    
	    return result;
	}
	
	public double getDefaultScore (RetrievalModel r, long docid) throws IOException {
		return 1.0;	   
	}
	
	public String toString(){
	    
	    String result = new String ();

	    for (int i=0; i<this.args.size(); i++)
	      result += this.args.get(i).toString() + " ";

	    return ("#Sum( " + result + ")");
	  }
}

