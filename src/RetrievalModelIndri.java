
public class RetrievalModelIndri extends RetrievalModel{
	public static double mu;
	public static double lambda;

	public boolean setParameter (String parameterName, double value){
		if(parameterName.equals("mu")){
			mu = value;
			return true;
		}
		else if(parameterName.equals("lambda")){
			lambda = value;
			return true;
		}
		else{
			System.err.println("The parameter name for BM25 is not exist");
			return false;
		}
		
	}
	
	public boolean setParameter (String parameterName, String value) {
	    System.err.println ("Error: Unknown parameter name for retrieval model " +
				"UnrankedBoolean: " +
				parameterName);
	    return false;
	  }
}
