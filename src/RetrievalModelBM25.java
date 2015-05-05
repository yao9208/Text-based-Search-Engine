
public class RetrievalModelBM25 extends RetrievalModel{
	public static double k_1;
	public static double b;
	public static double k_3;
	public boolean setParameter (String parameterName, double value){
		if(parameterName.equals("k_1")){
			k_1 = value;
			return true;
		}
		else if(parameterName.equals("b")){
			b = value;
			return true;
		}
		else if(parameterName.equals("k_3")){
			k_3 = value;
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
