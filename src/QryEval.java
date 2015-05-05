/**
 *  QryEval illustrates the architecture for the portion of a search
 *  engine that evaluates queries.  It is a template for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class QryEval {
	
  

  static String usage = "Usage:  java " + System.getProperty("sun.java.command")
      + " paramFile\n\n";

  //  The index file reader is accessible via a global variable. This
  //  isn't great programming style, but the alternative is for every
  //  query operator to store or pass this value, which creates its
  //  own headaches.

  public static IndexReader READER;
  public static DocLengthStore dls; 
  public static Map<Integer, String> ExterID = new HashMap<Integer, String>();

  //  Create and configure an English analyzer that will be used for
  //  query parsing.

  public static EnglishAnalyzerConfigurable analyzer =
      new EnglishAnalyzerConfigurable (Version.LUCENE_43);
  static {
    analyzer.setLowercase(true);
    analyzer.setStopwordRemoval(true);
    analyzer.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
  }

  /**
   *  @param args The only argument is the path to the parameter file.
   *  @throws Exception
   */
  public static void main(String[] args) throws Exception {
	  
	//long startTime=System.currentTimeMillis();
    
    // must supply parameter file
    if (args.length < 1) {
      System.err.println(usage);
      System.exit(1);
    }

    // read in the parameter file; one parameter per line in format of key=value
    Map<String, String> params = new HashMap<String, String>();
    Scanner scan = new Scanner(new File(args[0]));
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split("=");
      params.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());
    scan.close();
    
    // parameters required for this example to run
    if (!params.containsKey("indexPath")) {
      fatalError("Error: Parameter indexPath were missing.");
    }   
    if (!params.containsKey("queryFilePath")){
    	fatalError("Error: parameter queryFilePath was missing.");
    }    
    if (!params.containsKey("retrievalAlgorithm")){
    	fatalError("Error: parameter retrieval retrievalAlgorithm was missing.");
    }    
    if (!params.containsKey("trecEvalOutputPath")){
    	fatalError("Error: parameter retrieval trecEvalOutputPath was missing.");
    }
    
    boolean fb = false;
    int fbDocs = 0;
    int fbTerms = 0;
    double fbMu = 0.0;
    double fbOrigWeight = 0.0;
    boolean hasRankingFile = false;
    String fbInitialRankingFile = null;
    String fbExpansionQueryFile = null;
    if (params.containsKey("fb")){
    	if (params.get("fb").equals("true"))
    		fb = true;
    }
    if (fb){
    	if (!params.containsKey("fbDocs")){
    		fatalError("Error: parameter fbDocs was missing.");
    	}
    	fbDocs = Integer.parseInt(params.get("fbDocs"));
    	
    	if (!params.containsKey("fbTerms")){
    		fatalError("Error: parameter fbTerms was missing.");
    	}
    	fbTerms = Integer.parseInt(params.get("fbTerms"));
    	
    	if (!params.containsKey("fbMu")){
    		fatalError("Error: parameter fbMu was missing.");
    	}
    	fbMu = Double.parseDouble(params.get("fbMu"));
    	
    	if (!params.containsKey("fbOrigWeight")){
    		fatalError("Error: parameter fbOrigWeight was missing.");
    	}
    	fbOrigWeight = Double.parseDouble(params.get("fbOrigWeight"));
    	
    	if (params.containsKey("fbInitialRankingFile")){
    		hasRankingFile = true;
    		fbInitialRankingFile = params.get("fbInitialRankingFile");
    	}
    	
    	if (!params.containsKey("fbExpansionQueryFile")){
    		fatalError("Error: parameter fbExpansionQueryFile was missing.");
    	}
    	fbExpansionQueryFile = params.get("fbExpansionQueryFile");
    	
    }
    
    
    //open the query file
    Scanner queryFileScan = new Scanner(new File(params.get("queryFilePath")));
    //Scanner rankingFileScan = new Scanner(new File(fbInitialRankingFile));

    // open the index
    READER = DirectoryReader.open(FSDirectory.open(new File(params.get("indexPath"))));
    
    if (READER == null) {
      System.err.println(usage);
      System.exit(1);
    }
    dls = new DocLengthStore(READER);
    //ExterID = CalculateExterID();
    long startTime=System.currentTimeMillis();

    
    boolean learningToRank = false;
    RetrievalModel model = null;
	if (params.get("retrievalAlgorithm").equals("UnrankedBoolean")) {
		model = new RetrievalModelUnrankedBoolean();
	}
	else if(params.get("retrievalAlgorithm").equals("RankedBoolean")){
		model = new RetrievalModelRankedBoolean();
	}
	else if(params.get("retrievalAlgorithm").equals("BM25")||params.get("retrievalAlgorithm").equals("letor")){
		if (!params.containsKey("BM25:k_1")){
	    	fatalError("Error: parameter BM25:k_1 was missing.");
	    }
		if (!params.containsKey("BM25:b")){
	    	fatalError("Error: parameter BM25:b was missing.");
	    }
		if (!params.containsKey("BM25:k_3")){
	    	fatalError("Error: parameter BM25:k_3 was missing.");
	    }
		model = new RetrievalModelBM25();
		model.setParameter("k_1", Double.parseDouble(params.get("BM25:k_1")));
		model.setParameter("b", Double.parseDouble(params.get("BM25:b")));
		model.setParameter("k_3", Double.parseDouble(params.get("BM25:k_3")));
		
		if (params.get("retrievalAlgorithm").equals("letor")){
			learningToRank=true;
		}
	}
	else if(params.get("retrievalAlgorithm").equals("Indri")){
		if (!params.containsKey("Indri:mu")){
	    	fatalError("Error: parameter Indri:mu was missing.");
	    }
		if (!params.containsKey("Indri:lambda")){
	    	fatalError("Error: parameter Indri:lambda was missing.");
	    }
		model = new RetrievalModelIndri();
		model.setParameter("mu", Double.parseDouble(params.get("Indri:mu")));
		model.setParameter("lambda", Double.parseDouble(params.get("Indri:lambda")));
	}
	else{
		fatalError("Error: illegal retrieval algorithm");
	}
	
	
	if (learningToRank){
		if (!params.containsKey("letor:trainingQueryFile")){
	    	fatalError("Error: parameter letor:trainingQueryFile was missing.");
	    }
		
		double k1= Double.parseDouble(params.get("BM25:k_1"));
		double b = Double.parseDouble(params.get("BM25:b"));
		double k3 = Double.parseDouble(params.get("BM25:k_3"));
		
		double mu = Double.parseDouble(params.get("Indri:mu"));
		double lambda = Double.parseDouble(params.get("Indri:lambda"));
				
		
		
		LearningToRank rank = new LearningToRank(k1, b, k3, mu, lambda);
		rank.setSvmRankLearnPath(params.get("letor:svmRankLearnPath"));
		rank.setSvmRankClassifyPath(params.get("letor:svmRankClassifyPath"));
		rank.setSvmRankParamC(Double.parseDouble(params.get("letor:svmRankParamC")));
		rank.setSvmRankModelFile(params.get("letor:svmRankModelFile"));
		rank.setTestingdocumentScores(params.get("letor:testingDocumentScores"));
		rank.setTestingFeatureVectorsFile(params.get("letor:testingFeatureVectorsFile"));
		rank.setTrainingFeatureVectorsFile(params.get("letor:trainingFeatureVectorsFile"));
		rank.setTrainingQrelsFile(params.get("letor:trainingQrelsFile"));
		rank.setTrainingQueryFile(params.get("letor:trainingQueryFile"));
		rank.setPageRankFile(params.get("letor:pageRankFile"));
		rank.setModel(model);
		rank.setTrecEvalOutputPath(params.get("trecEvalOutputPath"));
		rank.setQueryFilePath(params.get("queryFilePath"));
		if(params.containsKey("letor:featureDisable")){
			List<Integer> disableList = new ArrayList<Integer>();
			String[] disable = params.get("letor:featureDisable").split(",");
			for (int i=0; i<disable.length; i++){
				if (!disable[i].equals(""))
					disableList.add(Integer.parseInt(disable[i]));
			}
			rank.setDisableList(disableList);
		}
		
		rank.Training();
		rank.testing();
		return;
	}
	

    /*
     *  The code below is an unorganized set of examples that show
     *  you different ways of accessing the index.  Some of these
     *  are only useful in HW2 or HW3.
     */


    
    /**
     *  The index is open. Start evaluating queries. The examples
     *  below show query trees for two simple queries.  These are
     *  meant to illustrate how query nodes are created and connected.
     *  However your software will not create queries like this.  Your
     *  software will use a query parser.  See parseQuery.
     *
     *  The general pattern is to tokenize the  query term (so that it
     *  gets converted to lowercase, stopped, stemmed, etc), create a
     *  Term node to fetch the inverted list, create a Score node to
     *  convert an inverted list to a score list, evaluate the query,
     *  and print results.
     * 
     *  Modify the software so that you read a query from a file,
     *  parse it, and form the query tree automatically.
     */


    //  Using the example query parser.  Notice that this does no
    //  lexical processing of query terms.  Add that to the query
    //  parser.
    Qryop qTree;
    
    BufferedWriter writer = null;
    writer = new BufferedWriter(new FileWriter(new File(params.get("trecEvalOutputPath"))));
    
    if(fb){
   	
    	
    	String query;
    	//String queryID = query.substring(0, query.indexOf(':'));
    	List<String> originQueries;
    	originQueries = new ArrayList<String>();
    	do {
			query = queryFileScan.nextLine();
			query = query.trim();
			originQueries.add(query);
		} while (queryFileScan.hasNext());
		QueryExpansion expand = new QueryExpansion(originQueries, fbDocs, fbTerms, fbMu, fbOrigWeight, hasRankingFile, fbInitialRankingFile, fbExpansionQueryFile, model);
    	
    	List<String> newQueries = new ArrayList<String>();
    	newQueries = expand.expansion();
    	
    	for (int m=0; m<newQueries.size(); m++){
    		query = newQueries.get(m).trim();
        	
    		String queryID = query.substring(0, query.indexOf(':'));
    		qTree = parseQuery (query, model);
    		printResults (query, qTree.evaluate (model), writer, queryID);
    	}
    }
    else{
    	do{
    		String query = queryFileScan.nextLine();
    		query = query.trim();
    	
    		String queryID = query.substring(0, query.indexOf(':'));
    		qTree = parseQuery (query, model);
    		printResults (query, qTree.evaluate (model), writer, queryID);
    	}while (queryFileScan.hasNext());
    }
    queryFileScan.close();
    writer.close();
    
    long endTime=System.currentTimeMillis();
    System.out.println("Finish normally");
    System.out.println("Run time£º "+(endTime-startTime)+"ms");

    /*
     *  Create the trec_eval output.  Your code should write to the
     *  file specified in the parameter file, and it should write the
     *  results that you retrieved above.  This code just allows the
     *  testing infrastructure to work on QryEval.
     */
    

    // Later HW assignments will use more RAM, so you want to be aware
    // of how much memory your program uses.

    printMemoryUsage(false);

  }

  /**
   *  Write an error message and exit.  This can be done in other
   *  ways, but I wanted something that takes just one statement so
   *  that it is easy to insert checks without cluttering the code.
   *  @param message The error message to write before exiting.
   *  @return void
   */
  static void fatalError (String message) {
    System.err.println (message);
    System.exit(1);
  }

  /**
   *  Get the external document id for a document specified by an
   *  internal document id. If the internal id doesn't exists, returns null.
   *  
   * @param iid The internal document id of the document.
   * @throws IOException 
   */
  static String getExternalDocid (int iid) throws IOException {
    Document d = QryEval.READER.document (iid);
    String eid = d.get ("externalId");
    return eid;
  }

  /**
   *  Finds the internal document id for a document specified by its
   *  external id, e.g. clueweb09-enwp00-88-09710.  If no such
   *  document exists, it throws an exception. 
   * 
   * @param externalId The external document id of a document.s
   * @return An internal doc id suitable for finding document vectors etc.
   * @throws Exception
   */
  static int getInternalDocid (String externalId) throws Exception {
    Query q = new TermQuery(new Term("externalId", externalId));
    
    IndexSearcher searcher = new IndexSearcher(QryEval.READER);
    TopScoreDocCollector collector = TopScoreDocCollector.create(1,false);
    searcher.search(q, collector);
    ScoreDoc[] hits = collector.topDocs().scoreDocs;
    
    if (hits.length < 1) {
      throw new Exception("External id not found.");
    } else {
      return hits[0].doc;
    }
  }

  /**
   * parseQuery converts a query string into a query tree.
   * 
   * @param qString
   *          A string containing a query.
   * @param qTree
   *          A query tree
   * @throws IOException
   */
  static Qryop parseQuery(String qString, RetrievalModel model) throws IOException {

    Qryop currentOp = null;
    Stack<Qryop> stack = new Stack<Qryop>();

    // Add a default query operator to an unstructured query. This
    // is a tiny bit easier if unnecessary whitespace is removed.

    //qString = qString.trim();
    System.out.println(qString);
    qString = qString.replaceFirst("[0-9]*:", "");

    if(model instanceof RetrievalModelRankedBoolean|| model instanceof RetrievalModelUnrankedBoolean){
    	//if(!qString.toLowerCase().startsWith("#or"))
    			qString = "#or(" + qString + ")";
    }
    if (model instanceof RetrievalModelBM25){
    	//if(!qString.toLowerCase().startsWith("#sum")){
			qString = "#sum(" + qString + ")";
		//}
    }
    if (model instanceof RetrievalModelIndri){
    	//if(!qString.toLowerCase().startsWith("#and")){
    		qString = "#and(" +qString + ")";
    	//}
    }

    // Tokenize the query.

    StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
    String token = null;

    // Each pass of the loop processes one token. To improve
    // efficiency and clarity, the query operator on the top of the
    // stack is also stored in currentOp.
    boolean isWeight=false;
    while (tokens.hasMoreTokens()) {
    	
      token = tokens.nextToken();

      if (token.matches("[ (,\t\n\r]")) {
        // Ignore most delimiters.
      } 
      else if (token.equalsIgnoreCase("#and")) {
          
          currentOp = new QryopSlAnd();
          stack.push(currentOp);
        }
      else if (token.equalsIgnoreCase("#and")) {
      
        currentOp = new QryopSlAnd();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#syn")) {
        currentOp = new QryopIlSyn();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#or")){
    	  currentOp = new QryopSlOr();
    	  stack.push(currentOp);

      }else if (token.startsWith(")")) { // Finish current query operator.
        // If the current query operator is not an argument to
        // another query operator (i.e., the stack is empty when it
        // is removed), we're done (assuming correct syntax - see
        // below). Otherwise, add the current operator as an
        // argument to the higher-level operator, and shift
        // processing back to the higher-level operator.

    	  
        stack.pop();


        if (stack.empty())
          break;

        Qryop arg = currentOp;
        currentOp = stack.peek();
        currentOp.add(arg);
      } else if (token.toLowerCase().contains("#wand")){
    	  
    	  	  List<Double> weights = new ArrayList<Double>();
    		  currentOp = new QryopSlWand(weights);
    		  stack.push(currentOp);


      }else if (token.toLowerCase().contains("#wsum")){
    	  
    	  	  List<Double> weights = new ArrayList<Double>();
    		  currentOp = new QryopSlWsum(weights);
    		  stack.push(currentOp);

      }

      else if (token.toLowerCase().contains("#near/")){
    	  int nearPos = Integer.parseInt(token.substring(6));
    	  currentOp = new QryopIlNear(nearPos);
    	  stack.push(currentOp);

      }else if (token.toLowerCase().contains("#window/")){
    	  int nearPos = Integer.parseInt(token.substring(8));
    	  currentOp = new QryopIlWindow(nearPos);
    	  stack.push(currentOp);

      }else if(token.equalsIgnoreCase("#sum")){
    	  currentOp = new QryopSlSum();
    	  stack.push(currentOp);

      }
      else {

        // NOTE: You should do lexical processing of the token before
        // creating the query term, and you should check to see whether
        // the token specifies a particular field (e.g., apple.title).
    	 
    	try {
    		double weight = Double.parseDouble(token);
    		if (currentOp instanceof QryopSlWand){
    			QryopSlWand p = (QryopSlWand)currentOp;
    			if (p.argsLength()==p.weightLength()){
    				p.addweight(weight);
    			}
    			else{
        			Double.parseDouble("a");
        		}
    		}
    		else if (currentOp instanceof QryopSlWsum){
    			QryopSlWsum p = (QryopSlWsum)currentOp;
    			if (p.argsLength()==p.weightLength()){
    				p.addweight(weight);
    			}
    			else{
        			Double.parseDouble("a");
        		}
    		}
    		else{
    			Double.parseDouble("a");
    		}
			
		} catch (Exception e) {
			if (tokenizeQuery(token).length!=0){

				if (token.contains(".")){
					String token1, token2;
					token1 = token.substring(0,token.indexOf("."));
					token2 = token.substring(token.indexOf(".")+1);
					if (tokenizeQuery(token1).length!=0){
					token = tokenizeQuery(token1)[0];
					currentOp.add(new QryopIlTerm(token, token2));
					}
					else{
						if (currentOp instanceof QryopSlWand){
			    			QryopSlWand p = (QryopSlWand)currentOp;
			    			p.deteleLastWeight();
			    		}
			    		else if (currentOp instanceof QryopSlWsum){
			    			QryopSlWsum p = (QryopSlWsum)currentOp;
			    			p.deteleLastWeight();
			    		}
					}
				}
				else{
					token = tokenizeQuery(token)[0];
				   currentOp.add(new QryopIlTerm(token));
				}

			}
			else{
				if (currentOp instanceof QryopSlWand){
	    			QryopSlWand p = (QryopSlWand)currentOp;
	    			p.deteleLastWeight();
	    		}
	    		else if (currentOp instanceof QryopSlWsum){
	    			QryopSlWsum p = (QryopSlWsum)currentOp;
	    			p.deteleLastWeight();
	    		}
			}
		}
      }
    }

    // A broken structured query can leave unprocessed tokens on the
    // stack, so check for that.

    if (tokens.hasMoreTokens()) {
      System.err.println("Error:  Query syntax is incorrect.  " + qString);
      return null;
    }

    return currentOp;
  }

  /**
   *  Print a message indicating the amount of memory used.  The
   *  caller can indicate whether garbage collection should be
   *  performed, which slows the program but reduces memory usage.
   *  @param gc If true, run the garbage collector before reporting.
   *  @return void
   */
  public static void printMemoryUsage (boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc) {
      runtime.gc();
    }

    System.out.println ("Memory used:  " +
			((runtime.totalMemory() - runtime.freeMemory()) /
			 (1024L * 1024L)) + " MB");
  }
  
  /**
   * Print the query results. 
   * 
   * THIS IS NOT THE CORRECT OUTPUT FORMAT.  YOU MUST CHANGE THIS
   * METHOD SO THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK
   * PAGE, WHICH IS:
   * 
   * QueryID Q0 DocID Rank Score RunID
   * 
   * @param queryName Original query.
   * @param result Result object generated by {@link Qryop#evaluate()}.
   * @throws IOException 
   */

static void printResults(String queryName, QryResult result, BufferedWriter writer, String queryID) throws IOException {

    if (result.docScores.scores.size() < 1) {
    	try{
      	  writer.write(queryID+ "\t" + "Q0\t"
  			   + "dummy\t1\t0\trun-1\n");
      	  }
      	  catch(Exception e){
      		  e.printStackTrace();
      	  }
    } else {
    	
    	for (int i=0; i<result.docScores.scores.size(); i++){
    		
    		if(ExterID.get(result.docScores.scores.get(i).docid)!=null){
    			result.docScores.scores.get(i).externId = ExterID.get(result.docScores.scores.get(i).docid);
    		}
    		else{
    			result.docScores.scores.get(i).externId = getExternalDocid(result.docScores.scores.get(i).docid).substring(12);
    			ExterID.put(result.docScores.scores.get(i).docid, result.docScores.scores.get(i).externId);
    		}
    	}
      Collections.sort(result.docScores.scores);
      for (int i = 0; i < Math.min(100, result.docScores.scores.size()); i++) {  
    	  try{
    	  writer.write(queryID+ "\t" + "Q0\tclueweb09-en"
    		   + result.docScores.scores.get(i).externId
			   + "\t"+ (i+1) +" "
			   + result.docScores.getDocidScore(i)+"\trun-1\n");
    	  }
    	  catch(Exception e){
    		  e.printStackTrace();
    	  }
      }
    }
  }


  /**
   *  Given a query string, returns the terms one at a time with stopwords
   *  removed and the terms stemmed using the Krovetz stemmer. 
   * 
   *  Use this method to process raw query terms. 
   * 
   *  @param query String containing query
   *  @return Array of query tokens
   *  @throws IOException
   */
  static String[] tokenizeQuery(String query) throws IOException {

    TokenStreamComponents comp = analyzer.createComponents("dummy", new StringReader(query));
    TokenStream tokenStream = comp.getTokenStream();

    CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();

    List<String> tokens = new ArrayList<String>();
    while (tokenStream.incrementToken()) {
      String term = charTermAttribute.toString();
      tokens.add(term);
    }
    return tokens.toArray(new String[tokens.size()]);
  }
  
  static HashMap<Integer, String> CalculateExterID() throws IOException{
	  HashMap<Integer, String> ExterID = new HashMap<Integer, String>();
	  for (int i=0; i<READER.numDocs(); i++){
		  ExterID.put(i, getExternalDocid(i).substring(12));
	  }
	  return ExterID;
  }
}