import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class QryopSlWsum extends QryopSl{
private List<Double> weights = new ArrayList<Double>();

double totalWeight = 0.0;
	
	public QryopSlWsum(List weight, Qryop... q) {
	    for (int i = 0; i < q.length; i++){
	      this.args.add(q[i]);
	    }
	    this.weights = weight;
	  }
	
	public void add (Qryop a) {
	    this.args.add(a);
	  }
	
	public void addweight(double weight){
		this.weights.add(weight);
	}
	public int weightLength(){
		return this.weights.size();
	}
	public int argsLength(){
		return this.args.size();
	}
	public void deteleLastWeight(){
		this.weights.remove(this.weightLength()-1);
	}
	
	public QryResult evaluate(RetrievalModel r) throws IOException {

	    
	    if (r instanceof RetrievalModelIndri){
	    	return(evaluateIndri(r));
	    }

	    return null;
	  }

	private QryResult evaluateIndri(RetrievalModel r) throws IOException {
		allocDaaTPtrs (r);
		QryResult result = new QryResult ();
		
		double queryLength = this.daatPtrs.size();
		  int minDoc, temp;
		  double SumScore = 0.0;
		  
		  for (int i=0; i<queryLength; i++){
			  totalWeight+=this.weights.get(i);
		  }
		  //double exponent = 1.0/queryLength;

		  
		  while(true){
		    	minDoc = Integer.MAX_VALUE;
		    	SumScore = 0.0;
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

		    			SumScore += q.getDefaultScore(r, minDoc) * this.weights.get(i)/totalWeight ;
		    		}
		    		else{
		    			SumScore += daatPtrs.get(i).scoreList.getDocidScore(daatPtrs.get(i).nextDoc) * this.weights.get(i)/totalWeight;
		    			daatPtrs.get(i).nextDoc++;
		    		}
		    	}
		    	result.docScores.add(minDoc, SumScore);
		  }
		  
		  freeDaaTPtrs ();

		  return result;

	}

	@Override
	public double getDefaultScore(RetrievalModel r, long docid)
			throws IOException {
		// TODO Auto-generated method stub
		if (r instanceof RetrievalModelIndri){
	    	double result = 0;
	    	if(args.size()==0){
	    		return 1.0;
	    	}
	    	for (int i=0; i<args.size(); i++){
	    		QryopSl q = (QryopSl)this.args.get(i);
	    		//result += Math.pow(q.getDefaultScore(r, docid), (1.0/args.size())) ;
	    		result += q.getDefaultScore(r, docid) * this.weights.get(i)/totalWeight ;
	    		}
	    	return result;
	    }
		return 1.0;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return null;
	}
}
