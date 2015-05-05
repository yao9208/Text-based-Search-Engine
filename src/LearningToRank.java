import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.util.BytesRef;


public class LearningToRank {
	String trainingQueryFile;
	String trainingQrelsFile;
	String trainingFeatureVectorsFile;
	String pageRankFile;
	
	String svmRankLearnPath;
	String svmRankClassifyPath;
	double svmRankParamC;
	String svmRankModelFile;
	String testingFeatureVectorsFile;
	String testingdocumentScores;
	String queryFilePath;
	
	String trecEvalOutputPath;
	RetrievalModel model;
	List<Integer> disableList;
	
	public double k_1;
	public double b;
	public double k_3;
	public double mu;
	public double lambda;
	
	DocLengthStore dls=QryEval.dls;
	
	int numDocs = QryEval.READER.numDocs();
	
	Map<String, Double> pageRankMap = new HashMap<String, Double>();
	
	public LearningToRank(double k_1, double b, double k_3, double mu, double lambda){
		this.k_1 = k_1;
		this.b = b;
		this.k_3 = k_3;
		this.mu = mu;
		this.lambda = lambda;
	}
	
	
	public void setPageRankFile(String pageRankFile){
		this.pageRankFile = pageRankFile;
	}
	public void setTrainingQueryFile(String trainingQueryFile) {
		this.trainingQueryFile = trainingQueryFile;
	}

	public void setTrainingQrelsFile(String trainingQrelsFile) {
		this.trainingQrelsFile = trainingQrelsFile;
	}

	public void setTrainingFeatureVectorsFile(String trainingFeatureVectorsFile) {
		this.trainingFeatureVectorsFile = trainingFeatureVectorsFile;
	}

	public void setSvmRankLearnPath(String svmRankLearnPath) {
		this.svmRankLearnPath = svmRankLearnPath;
	}

	public void setSvmRankClassifyPath(String svmRankClassifyPath) {
		this.svmRankClassifyPath = svmRankClassifyPath;
	}

	public void setSvmRankParamC(double svmRankParamC) {
		this.svmRankParamC = svmRankParamC;
	}

	public void setTestingFeatureVectorsFile(String testingFeatureVectorsFile) {
		this.testingFeatureVectorsFile = testingFeatureVectorsFile;
	}

	public void setTestingdocumentScores(String testingdocumentScores) {
		this.testingdocumentScores = testingdocumentScores;
	}


	public void setSvmRankModelFile(String svmRankModelFile) {
		this.svmRankModelFile = svmRankModelFile;
	}


	public void setTrecEvalOutputPath(String trecEvalOutputPath) {
		this.trecEvalOutputPath = trecEvalOutputPath;
	}


	public void setModel(RetrievalModel model) {
		this.model = model;
	}
	

	public void setQueryFilePath(String queryFilePath) {
		this.queryFilePath = queryFilePath;
	}

	public void setDisableList(List<Integer> list){
		this.disableList = list;
	}

	public double getSpamScore (Document d){
		double spamscore = Integer.parseInt(d.get("score"));
		return spamscore;
	}
	
	
	public double getUrlDepth (Document d){
		String rawUrl = d.get("rawUrl");
		double sum=0.0;
		for (int i=0; i<rawUrl.length(); i++){
			if (rawUrl.charAt(i)=='/')
				sum+=1;
		}
		return sum;
	}
	
	public double getWikipediaScore (Document d){
		String rawUrl = d.get("rawUrl");
		return rawUrl.contains("wikipedia.org")? 1:0;
	}
	
	public Double getPageRank (String docName){
		if (pageRankMap.containsKey(docName))
			return pageRankMap.get(docName);
		return null;
	}
	
	public Double getPageRank (int docid) throws IOException{
		String docName = QryEval.getExternalDocid(docid);
		if (pageRankMap.containsKey(docName))
			return pageRankMap.get(docName);
		return null;
	}
	
	public void ReadPageRank (String pageRankFile) throws FileNotFoundException{
		Scanner pageRankFileScan = new Scanner(new File(pageRankFile));
		while (pageRankFileScan.hasNext()){
			String line = pageRankFileScan.nextLine();
			String[] docAndScore = line.split("\t");
			pageRankMap.put(docAndScore[0], Double.parseDouble(docAndScore[1]));
		}
	}
	
	public Double getDocLength(int docid, String field) throws IOException{
		return (double)dls.getDocLength(field, docid);
	}
	public Double getBM25(List<String> termList, int docid, String field) throws IOException{
		double score = 0.0;
		double avgLeng = QryEval.READER.getSumTotalTermFreq(field)/ (double) QryEval.READER.getDocCount(field);
		TermVector vector;
		Terms terms1 = QryEval.READER.getTermVector(docid, field);
		if (terms1 == null) {
		  // field doesn't exist!
		  return null;
		}
		else{
			vector = new TermVector(docid, field);
		}
		String[] terms = vector.stems;
		List<String> DocTerms = Arrays.asList(terms);
		for(int i=0; i<termList.size(); i++){
			if(DocTerms.contains(termList.get(i))){
				score += Math.log((numDocs - vector.stemDf(DocTerms.indexOf(termList.get(i))) +0.5) / (vector.stemDf(DocTerms.indexOf(termList.get(i))) + 0.5)) * vector.stemFreq(DocTerms.indexOf(termList.get(i))) / (vector.stemFreq(DocTerms.indexOf(termList.get(i))) + k_1 *(1-b+b*dls.getDocLength(field, docid) / avgLeng));		
			}
	
		}
		return score;
	}
	
	public Double getIndri(List<String> termList, int docid, String field) throws IOException{
		double score = 1.0;
		TermVector vector;
		long totalFreq = QryEval.READER
				.getSumTotalTermFreq(field);
		Terms terms1 = QryEval.READER.getTermVector(docid, field);
		if (terms1 == null) {
		  // field doesn't exist!
		  return null;
		}
		else{
			vector = new TermVector(docid, field);
		}
		String[] terms = vector.stems;
		List<String> DocTerms = Arrays.asList(terms);
		boolean flag = true;
		for(int i=0; i<termList.size(); i++){
			double ctf = QryEval.READER.totalTermFreq(new Term(field, new BytesRef(termList.get(i))));
			double MLE = ctf/totalFreq;
			if(DocTerms.contains(termList.get(i))){
				flag = false;
				score *=Math.pow(( lambda * (vector.stemFreq(DocTerms.indexOf(termList.get(i))) + mu * MLE)
						/ (dls.getDocLength(field, docid) + mu)
						+ (1 - lambda) * MLE),  (1.0/(double)termList.size()));
			}
			else{
				score *=Math.pow(( lambda * (mu * MLE)
						/ (dls.getDocLength(field, docid) + mu)
						+ (1 - lambda) * MLE) ,  (1.0/(double)termList.size()));
			}
		}
		if (flag)
			return 0.0;
		return score;
	}
	
	public Double getTermOverlap (List<String> termList, int docid, String field) throws IOException{
		double sum = 0.0;
		TermVector vector;
		Terms terms1 = QryEval.READER.getTermVector(docid, field);
		if (terms1 == null) {
		  // field doesn't exist!
		  return null;
		}
		else{
			vector = new TermVector(docid, field);
		}
		String[] terms = vector.stems;
		List<String> DocTerms = Arrays.asList(terms);
		
		for(int i=0; i<termList.size(); i++){
			if(DocTerms.contains(termList.get(i))){
				sum ++;
			}
		}
		return sum/(double)termList.size();
	}
	
	public Double getDocumentOverlap(List<String> termList, int docid, String field) throws IOException{
		double sum = 0.0;
		TermVector vector;
		Terms terms1 = QryEval.READER.getTermVector(docid, field);
		if (terms1==null){
			return null;
		}else{
			vector = new TermVector(docid, field);
		}
		String[] terms = vector.stems;
		for (int i=0; i<terms.length; i++){
			if (termList.contains(terms[i])){
				sum+=vector.stemsFreq[i];
			}
		}
		return sum/dls.getDocLength(field, docid);
	}
	
	public Double getUnrankedBooleanAnd (List<String> termList, int docid, String field) throws IOException{
		double score = 1.0;
		TermVector vector;
		Terms terms1 = QryEval.READER.getTermVector(docid, field);
		if (terms1==null){
			return null;
		}else{
			vector = new TermVector(docid, field);
		}
		String[] terms = vector.stems;
		List<String> DocTerms = Arrays.asList(terms);
		for(int i=0; i<termList.size(); i++){
			if(!DocTerms.contains(termList.get(i))){
				return 0.0;
			}
		}
		return score;
	}
	
	public List<List<Double>> Normalize (List<List<Double>> features){
		int length=18;
		List<Double> maxValue = new ArrayList<Double>();
		List<Double> minValue = new ArrayList<Double>();
		for (int i=0;i<18;i++){
			maxValue.add(-Double.MAX_VALUE);
			minValue.add(Double.MAX_VALUE);
		}
		
		for (int i=0; i<features.size(); i++){
			for (int j=0; j<features.get(i).size(); j++){
				if (features.get(i).get(j)!=null&&features.get(i).get(j)>maxValue.get(j)){
					maxValue.set(j,features.get(i).get(j) );
				}
				if (features.get(i).get(j)!=null&&features.get(i).get(j)<minValue.get(j)){
					minValue.set(j,features.get(i).get(j) );
				}
			}
		}
		

		for (int i=0; i<features.size(); i++){
			List<Double> tmp = new ArrayList<Double>();
			for (int k=0;k<length;k++){
				tmp.add(0.0);
			}
			for (int j=0; j<features.get(i).size(); j++){
				if (disableList!=null&&disableList.contains(j+1))
					continue;
				if (features.get(i).get(j)==null){
					continue;
				}
				if (maxValue.get(j)==minValue.get(j))
					continue;
				tmp.set(j, (features.get(i).get(j)-minValue.get(j))/(maxValue.get(j)-minValue.get(j)));
			}
			features.set(i, tmp);
		}
		return features;
	}
	
	public void WriteFeaturesToFile(List<List<Double>> features, List<List<String>> metainfos, String filename) throws IOException{
		BufferedWriter writer;
		writer = new BufferedWriter(new FileWriter(new File(filename)));
		StringBuilder builder=null;
		for (int i=0; i<features.size(); i++){
			builder = new StringBuilder();
			builder.append(metainfos.get(i).get(0)+" qid:"+metainfos.get(i).get(1)+" ");
			for (int j=0; j<features.get(i).size(); j++){
				
				builder.append(j+1+":"+features.get(i).get(j)+" ");
			}
			builder.append("# "+metainfos.get(i).get(2)+"\n");
			writer.write(builder.toString());
		}
		writer.close();
	}
	
	public void WriteFinalToFile(List<List<String>> metainfos,  String filename) throws IOException{
		BufferedWriter writer;
		writer = new BufferedWriter(new FileWriter(new File(filename)));
		StringBuilder builder=null;
		int currentQuery;
		int prev = Integer.parseInt(metainfos.get(0).get(1));
		int j=1;
		for (int i=0; i<metainfos.size(); i++){
			builder = new StringBuilder();
			currentQuery = Integer.parseInt(metainfos.get(i).get(1));
			if(currentQuery!=prev){
				j=1;
				prev = currentQuery;
			}
			builder.append(metainfos.get(i).get(1)+"\tQ0\t"+metainfos.get(i).get(2)+"\t"+j+"\t"+metainfos.get(i).get(3)+"\trun-1\n");
			j++;
			writer.write(builder.toString());
		}
		writer.close();
	}
	
	public void RunSVM() throws Exception{
		// runs svm_rank_learn from within Java to train the model
	    // execPath is the location of the svm_rank_learn utility, 
	    // which is specified by letor:svmRankLearnPath in the parameter file.
	    // FEAT_GEN.c is the value of the letor:c parameter.
	    Process cmdProc = Runtime.getRuntime().exec(
	        new String[] { svmRankLearnPath, "-c", String.valueOf(svmRankParamC), trainingFeatureVectorsFile, svmRankModelFile });

	    // The stdout/stderr consuming code MUST be included.
	    // It prevents the OS from running out of output buffer space and stalling.

	    // consume stdout and print it out for debugging purposes
	    BufferedReader stdoutReader = new BufferedReader(
	        new InputStreamReader(cmdProc.getInputStream()));
	    String line;
	    while ((line = stdoutReader.readLine()) != null) {
	      System.out.println(line);
	    }
	    // consume stderr and print it for debugging purposes
	    BufferedReader stderrReader = new BufferedReader(
	        new InputStreamReader(cmdProc.getErrorStream()));
	    while ((line = stderrReader.readLine()) != null) {
	      System.out.println(line);
	    }

	    // get the return value from the executable. 0 means success, non-zero 
	    // indicates a problem
	    int retValue = cmdProc.waitFor();
	    if (retValue != 0) {
	      throw new Exception("SVM Rank crashed.");
	    }
	}
	
	public void SVMTest() throws Exception{
		// runs svm_rank_learn from within Java to train the model
	    // execPath is the location of the svm_rank_learn utility, 
	    // which is specified by letor:svmRankLearnPath in the parameter file.
	    // FEAT_GEN.c is the value of the letor:c parameter.
	    Process cmdProc = Runtime.getRuntime().exec(
	        new String[] { svmRankClassifyPath,  testingFeatureVectorsFile, svmRankModelFile, testingdocumentScores });

	    // The stdout/stderr consuming code MUST be included.
	    // It prevents the OS from running out of output buffer space and stalling.

	    // consume stdout and print it out for debugging purposes
	    BufferedReader stdoutReader = new BufferedReader(
	        new InputStreamReader(cmdProc.getInputStream()));
	    String line;
	    while ((line = stdoutReader.readLine()) != null) {
	      System.out.println(line);
	    }
	    // consume stderr and print it for debugging purposes
	    BufferedReader stderrReader = new BufferedReader(
	        new InputStreamReader(cmdProc.getErrorStream()));
	    while ((line = stderrReader.readLine()) != null) {
	      System.out.println(line);
	    }

	    // get the return value from the executable. 0 means success, non-zero 
	    // indicates a problem
	    int retValue = cmdProc.waitFor();
	    if (retValue != 0) {
	      throw new Exception("SVM Rank test crashed.");
	    }
	}
	
	public void Training() throws Exception{
		Scanner trainingQueryScan = new Scanner(new File(trainingQueryFile));
		Scanner trainingQrelsScan = new Scanner(new File(trainingQrelsFile));
		int currentQuery;
		List<List<Double>> featuresAll = new ArrayList<List<Double>>();
		List<List<String>> metainfos = new ArrayList<List<String>>();
		ReadPageRank(pageRankFile);
		
		String Qrels = trainingQrelsScan.nextLine();
		int currentQrels = Integer.parseInt(Qrels.split(" ")[0]);
		while (trainingQueryScan.hasNext()){
			String query = trainingQueryScan.nextLine();			
			currentQuery = Integer.parseInt(query.substring(0,query.indexOf(":")));
			query= query.replaceFirst("[0-9]*:", "");

			StringTokenizer tokens = new StringTokenizer(query, "\t\n\r ,()", true);
		    String token = null;

		    // Each pass of the loop processes one token. To improve
		    // efficiency and clarity, the query operator on the top of the
		    // stack is also stored in currentOp.
		    List<String> Terms = new ArrayList<String>();
		    while (tokens.hasMoreTokens()) {
		    	token = tokens.nextToken();
		    	if (QryEval.tokenizeQuery(token).length!=0){
		    		Terms.add(QryEval.tokenizeQuery(token)[0]);
		    	}
		    }
					
			List<List<Double>> features = new ArrayList<List<Double>>();
			
			List<String> metainfo = new ArrayList<String>();
			do {
				String docName = Qrels.split(" ")[2];
				int docid = QryEval.getInternalDocid(docName);
				Document d = QryEval.READER.document(docid);
				
				String relevance = Qrels.split(" ")[3];
				
				metainfo.add(relevance);
				metainfo.add(String.valueOf(currentQuery));

				
				metainfo.add(docName);
				
				features.add(getFeature(d, docName, Terms, docid));
				metainfos.add(metainfo);

				metainfo = new ArrayList<String>();
				
				if (trainingQrelsScan.hasNext()){
					Qrels = trainingQrelsScan.nextLine();
					currentQrels = Integer.parseInt(Qrels.split(" ")[0]);
				}else{
					break;
				}
				
			} while (currentQrels == currentQuery);
			
			
			features = Normalize (features);
			featuresAll.addAll(features);
			
		}
		
		WriteFeaturesToFile(featuresAll, metainfos, trainingFeatureVectorsFile);
		RunSVM();
	}
	
	public void testing() throws Exception{
		Scanner queryFileScan = new Scanner(new File(queryFilePath));
	    
	    int currentQuery;
		List<List<Double>> featuresAll = new ArrayList<List<Double>>();
		List<List<String>> metainfos = new ArrayList<List<String>>();
		Qryop qTree;
		do{
    		String query = queryFileScan.nextLine();
    		query = query.trim();
    	
    		String queryID = query.substring(0, query.indexOf(':'));
    		qTree = QryEval.parseQuery (query, model);
    		
    		List<ScoreEntry> result = new ArrayList<ScoreEntry>();
    		result = saveResults (query, qTree.evaluate (model), queryID);
    		
    					
			currentQuery = Integer.parseInt(queryID);
			query= query.replaceFirst("[0-9]*:", "");
			String[] queryTerms = QryEval.tokenizeQuery(query);
			List<String> Terms = Arrays.asList(queryTerms);
			
			List<List<Double>> features = new ArrayList<List<Double>>();
			
			List<String> metainfo = new ArrayList<String>();
			for (int i=0; i<result.size(); i++){
				String docName = "clueweb09-en"+result.get(i).docExternalID;
				int docid = result.get(i).docid;
				Document d = QryEval.READER.document(docid);
				
				
				metainfo.add("0");
				metainfo.add(String.valueOf(currentQuery));
				
				metainfo.add(docName);
				
				features.add(getFeature(d, docName, Terms, docid));
				metainfos.add(metainfo);


				metainfo = new ArrayList<String>();
				
			} 
			
			
			features = Normalize (features);
			featuresAll.addAll(features);
			
    		
    		
    	}while (queryFileScan.hasNext());
		
		queryFileScan.close();
		
		WriteFeaturesToFile(featuresAll, metainfos, testingFeatureVectorsFile);
		SVMTest();
		
		Scanner svmTestScan = new Scanner(new File(testingdocumentScores));
		int i=0;
		while(svmTestScan.hasNext()){
			String line = svmTestScan.nextLine();
			metainfos.get(i).add(line);
			i++;
		}
		
		Collections.sort(metainfos, new Comparator<List<String>>(){
			public int compare(List<String> o1, List<String> o2){
				int i = new Integer(Integer.parseInt(o1.get(1))).compareTo( new Integer(Integer.parseInt(o2.get(1))));
				if (i==0)
					return new Double(Double.parseDouble(o2.get(o1.size()-1))).compareTo( new Double(Double.parseDouble(o1.get(o1.size()-1))));
				return i;
			}
		});
		svmTestScan.close();
		
		WriteFinalToFile(metainfos, trecEvalOutputPath);
		//writer.close();
	
	}
	
	public List<Double> getFeature(Document d, String docName, List<String> Terms, int docid) throws IOException{
		List<Double> feature = new ArrayList<Double>();
		feature.add(getSpamScore(d));
		feature.add(getUrlDepth(d));
		feature.add(getWikipediaScore(d));
		feature.add(getPageRank(docName));
		feature.add(getBM25(Terms, docid, "body"));
		feature.add(getIndri(Terms, docid, "body"));
		feature.add(getTermOverlap(Terms, docid, "body"));
		feature.add(getBM25(Terms, docid, "title"));
		feature.add(getIndri(Terms, docid, "title"));
		feature.add(getTermOverlap(Terms, docid, "title"));
		feature.add(getBM25(Terms, docid, "url"));
		feature.add(getIndri(Terms, docid, "url"));
		feature.add(getTermOverlap(Terms, docid, "url"));
		feature.add(getBM25(Terms, docid, "inlink"));
		feature.add(getIndri(Terms, docid, "inlink"));
		feature.add(getTermOverlap(Terms, docid, "inlink"));
		
		feature.add(getDocLength(docid, "body"));
		//feature.add(getDocumentOverlap(Terms, docid, "body"));
		feature.add(getUnrankedBooleanAnd(Terms, docid, "body"));
		return feature;
	}
	
	static List<ScoreEntry> saveResults(String queryName, QryResult result, String queryID) throws IOException {
		Map<Integer, String> ExterID = QryEval.ExterID;
    	List<ScoreEntry> list = new ArrayList<ScoreEntry>();
        	for (int i=0; i<result.docScores.scores.size(); i++){        		
        		if(ExterID.get(result.docScores.scores.get(i).docid)!=null){
        			result.docScores.scores.get(i).externId = ExterID.get(result.docScores.scores.get(i).docid);
        		}
        		else{
        			result.docScores.scores.get(i).externId = QryEval.getExternalDocid(result.docScores.scores.get(i).docid).substring(12);
        			ExterID.put(result.docScores.scores.get(i).docid, result.docScores.scores.get(i).externId);
        		}
        	}
          Collections.sort(result.docScores.scores);
          for (int i = 0; i < Math.min(100, result.docScores.scores.size()); i++) {  
        	  try{
        		  int id = result.docScores.scores.get(i).docid;
        		  list.add(new ScoreEntry(id, result.docScores.getDocidScore(i),ExterID.get(id) ));
        	  }
        	  catch(Exception e){
        		  e.printStackTrace();
        	  }
          }
          return list;
     }
	
}

class ScoreEntry{
	int docid;
	double score;
	String docExternalID;
	public ScoreEntry(int docid, double score,  String docExternalID){
		this.docExternalID = docExternalID;
		this.docid = docid;
		this.score = score;
	}
}