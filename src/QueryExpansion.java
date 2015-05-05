import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;


public class QueryExpansion {
	
	static int fbDocs = 0;
    int fbTerms = 0;
    double fbMu = 0.0;
    double fbOrigWeight = 0.0;
    boolean hasRankingFile = false;
    String fbInitialRankingFile = null;
    String fbExpansionQueryFile = null;
    Scanner rankingFileScan;
    Scanner queryFileScan;
    DocLengthStore dls=QryEval.dls;
    RetrievalModel model;
	
    public static Map<Integer, String> ExterID = QryEval.ExterID;
	BufferedWriter writer; 
    
    int internalID = 0;
    int currentQuery = -1;
    int prevQuery = 0;
    double fileScore = 0.0;
    List<String> originQueries;
    
    QueryExpansion(List<String> originQueries, int fbDocs, int fbTerms, double fbMu, double fbOrigWeight, boolean hasRankingFile, String fbInitialRankingFile, String fbExpansionQueryFile, RetrievalModel model) throws IOException{
    	this.originQueries = originQueries;
    	QueryExpansion.fbDocs = fbDocs;
        this.fbTerms = fbTerms;
        this.fbMu = fbMu;
        this.fbOrigWeight = fbOrigWeight;
        this.hasRankingFile = hasRankingFile;
        this.fbInitialRankingFile = fbInitialRankingFile;
        this.fbExpansionQueryFile = fbExpansionQueryFile;
        this.model = model;
        if(hasRankingFile)
        	rankingFileScan = new Scanner(new File(fbInitialRankingFile));
        writer = new BufferedWriter(new FileWriter(new File(fbExpansionQueryFile)));
    }
    
    
    
    class tfEntry{
    	int[] tf;
    	double score;
    	long ctf;
    	tfEntry(){
    		tf = new int[fbDocs];
    	}
    	tfEntry(int[] entry, long ctf){
    		tf = entry;
    		this.ctf = ctf;
    	}
    }
    
    
    List<List<docScoreEntry>> docsID = new ArrayList<List<docScoreEntry>>();
    public List<String> expansion() throws Exception{
    	int i=-1;
    	int j=0;
    	if (hasRankingFile){
    		do{
    			j++;
    			String fileline = rankingFileScan.nextLine();
    	    	fileline = fileline.trim();
    	    	currentQuery = Integer.parseInt(fileline.split("[ \t]")[0]);
    			if(j>=QueryExpansion.fbDocs && currentQuery==prevQuery){
    				while (rankingFileScan.hasNext()&&currentQuery==prevQuery) {
						fileline = rankingFileScan.nextLine();
						fileline = fileline.trim();
						currentQuery = Integer
								.parseInt(fileline.split("[ \t]")[0]);
					}
    				if(rankingFileScan.hasNext()){
    					String fileID = fileline.split("[ \t]")[2];
    	    	    	fileScore = Double.parseDouble(fileline.split("[ \t]")[4]);
    		    		internalID = QryEval.getInternalDocid(fileID);
    					i++;
        	    		docScoreEntry entry = new docScoreEntry(internalID, fileScore);
        	    		docsID.add(new ArrayList<docScoreEntry>());
        	    		docsID.get(i).add(entry);
        	    		prevQuery = currentQuery;
        	    		j=0; 
    				}
        	    	
    			}else{
//    				if(!rankingFileScan.hasNext())
//    					break;
    	    	
    	    	String fileID = fileline.split("[ \t]")[2];
    	    	fileScore = Double.parseDouble(fileline.split("[ \t]")[4]);
	    		internalID = QryEval.getInternalDocid(fileID);
    	    	if (currentQuery == -1 || currentQuery!=prevQuery){    		  		
    	    		i++;
    	    		docScoreEntry entry = new docScoreEntry(internalID, fileScore);
    	    		docsID.add(new ArrayList<docScoreEntry>());
    	    		docsID.get(i).add(entry);
    	    		prevQuery = currentQuery;
    	    		j=0;    	    		
    	    	}
    	    	else{
    	    		docScoreEntry entry = new docScoreEntry(internalID, fileScore);
    	    		docsID.get(i).add(entry);
    	    	}
    			}

    	    }while (rankingFileScan.hasNext());
    		
    	}
    	
    	else{
    		Qryop qTree;
        		for (int k = 0; k < originQueries.size(); k++) {
        			List<docScoreEntry> list = new ArrayList<docScoreEntry>();
					String query = originQueries.get(k);
					query = query.trim();
					String queryID = query.substring(0, query.indexOf(':'));
					qTree = QryEval.parseQuery(query, model);
					list = saveResults(query, qTree.evaluate(model), queryID);
					docsID.add(list);
				}
    	}
    	
    	List<Map<String, tfEntry>> termMap = new ArrayList<Map<String, tfEntry>>();
    	//extract term vectors

    	for (int m=0; m<docsID.size(); m++){//every query
    		termMap.add(new HashMap<String, tfEntry>());
    		for(int n=0; n<docsID.get(m).size(); n++){//every doc
    			TermVector vector = new TermVector(docsID.get(m).get(n).docid, "body");
    			for(int p=0; p<vector.stemsLength(); p++){
    				if(vector.stemString(p)==null||vector.stemString(p).contains(".")||vector.stemString(p).contains(","))
    					continue;
    				if(termMap.get(m).containsKey(vector.stemString(p))){
    					tfEntry entry = termMap.get(m).get(vector.stemString(p));
    					int[] tmptf = entry.tf;
    					tmptf[n] = vector.stemsFreq[p];
    					termMap.get(m).put(vector.stemString(p), entry);
    				}
    				else{
    					int[] tmptf = new int[fbDocs];
    					tmptf[n] = vector.stemsFreq[p];
    					long ctf;
//    					if(vector.stemString(p)==null)
//    						ctf = 0;
//    					else
    					ctf = QryEval.READER.totalTermFreq(new Term("body", new BytesRef(vector.stemString(p))));
    					termMap.get(m).put(vector.stemString(p), new tfEntry(tmptf, ctf));
    				}
    			}
    		}
    	}
    	long totalFreq = QryEval.READER.getSumTotalTermFreq("body");
    	for (int m=0; m<termMap.size(); m++){//query
    		for (String key:termMap.get(m).keySet()){
    			if (key==null)
    				continue;
    			double tempScore = 0;
    			tfEntry entry = termMap.get(m).get(key);
    			for (int n=0; n<fbDocs; n++){
    				tempScore += (entry.tf[n]+fbMu*entry.ctf/totalFreq)/(dls.getDocLength("body", docsID.get(m).get(n).docid)+fbMu)*docsID.get(m).get(n).score*Math.log(totalFreq/entry.ctf);
    			}
    			entry.score = tempScore;
    			termMap.get(m).put(key, entry);
    		}
    	}
    	
    	List<List<Map.Entry<String, tfEntry>>> termList = new ArrayList<List<Map.Entry<String, tfEntry>>>();
    	for (int m=0; m<termMap.size(); m++){//query
    		List<Map.Entry<String, tfEntry>> list = new ArrayList<Map.Entry<String, tfEntry>>(termMap.get(m).entrySet());
    		Collections.sort(list, new Comparator<Map.Entry<String, tfEntry>>(){
    			public int compare(Map.Entry<String, tfEntry> o1, Map.Entry<String, tfEntry> o2){
    				return   new Double(o2.getValue().score).compareTo( new Double(o1.getValue().score));
    			}
    		});
    		termList.add(list);
    	}
    	List<String> newQueries = new ArrayList<String>();
    	String queryID;
    	String originQuery;
    	for (int m=0; m<termMap.size(); m++){
    		StringBuilder b = new StringBuilder();
    		queryID = originQueries.get(m).substring(0, originQueries.get(m).indexOf(':'));
    		originQuery = originQueries.get(m).substring(originQueries.get(m).indexOf(':')+1);
    		for(int n=0; n<fbTerms; n++){
    			b.append(termList.get(m).get(n).getValue().score+" "+termList.get(m).get(n).getKey()+" ");
    		}
    		newQueries.add(queryID+":#WAND ("+fbOrigWeight+" #AND("+originQuery+") "+(1-fbOrigWeight)+" #WAND("+b.toString()+"))");
    		writer.write(queryID+": #WAND("+b.toString()+")\n");
    	}
    	writer.close();
    	
    	return newQueries;
    }
    
    static List<docScoreEntry> saveResults(String queryName, QryResult result, String queryID) throws IOException {

    	List<docScoreEntry> list = new ArrayList<docScoreEntry>();
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
          for (int i = 0; i < Math.min(fbDocs, result.docScores.scores.size()); i++) {  
        	  try{
        		  list.add(new docScoreEntry(result.docScores.scores.get(i).docid, result.docScores.getDocidScore(i)));
        	  }
        	  catch(Exception e){
        		  e.printStackTrace();
        	  }
          }
          return list;
     }
    
}

class docScoreEntry{
	int docid;
	double score;
	
	docScoreEntry(int docid, double score){
		this.docid = docid;
		this.score = score;
	}
}
