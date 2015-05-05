/**
 *  This class implements the SCORE operator for all retrieval models.
 *  The single argument to a score operator is a query operator that
 *  produces an inverted list.  The SCORE operator uses this
 *  information to produce a score list that contains document ids and
 *  scores.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;
import java.io.IOException;

public class QryopSlScore extends QryopSl {

	/**
	 * Construct a new SCORE operator. The SCORE operator accepts just one
	 * argument.
	 * 
	 * @param q
	 *            The query operator argument.
	 * @return @link{QryopSlScore}
	 */
	public String field;
	double mu = RetrievalModelIndri.mu;
	double lambda = RetrievalModelIndri.lambda;
	public double ctf;
	DocLengthStore dls=QryEval.dls;
	double MLE;
	int numDocs = QryEval.READER.numDocs();

	public QryopSlScore(Qryop q) {
		this.args.add(q);
	}

	/**
	 * Construct a new SCORE operator. Allow a SCORE operator to be created with
	 * no arguments. This simplifies the design of some query parsing
	 * architectures.
	 * 
	 * @return @link{QryopSlScore}
	 */
	public QryopSlScore() {
	}

	/**
	 * Appends an argument to the list of query operator arguments. This
	 * simplifies the design of some query parsing architectures.
	 * 
	 * @param q
	 *            The query argument to append.
	 */
	public void add(Qryop a) {
		this.args.add(a);
	}

	/**
	 * Evaluate the query operator.
	 * 
	 * @param r
	 *            A retrieval model that controls how the operator behaves.
	 * @return The result of evaluating the query.
	 * @throws IOException
	 */
	public QryResult evaluate(RetrievalModel r) throws IOException {

		if (r instanceof RetrievalModelUnrankedBoolean
				|| r instanceof RetrievalModelRankedBoolean)
			return (evaluateBoolean(r));

		if (r instanceof RetrievalModelIndri) {
			return (evaluateIndri(r));
		}
		if (r instanceof RetrievalModelBM25) {
			return (evaluateBM25(r));
		}

		return null;
	}

	/**
	 * Evaluate the query operator for boolean retrieval models.
	 * 
	 * @param r
	 *            A retrieval model that controls how the operator behaves.
	 * @return The result of evaluating the query.
	 * @throws IOException
	 */
	public QryResult evaluateBoolean(RetrievalModel r) throws IOException {

		QryResult result = args.get(0).evaluate(r);

		if (r instanceof RetrievalModelUnrankedBoolean) {
			for (int i = 0; i < result.invertedList.df; i++) {

				result.docScores.add(result.invertedList.postings.get(i).docid,
						(float) 1.0);
			}
		} else {
			// Ranked Boolean
			for (int i = 0; i < result.invertedList.df; i++) {
				result.docScores.add(result.invertedList.postings.get(i).docid,
						(float) result.invertedList.postings.get(i).tf);
			}
		}

		// The SCORE operator should not return a populated inverted list.
		// If there is one, replace it with an empty inverted list.

		if (result.invertedList.df > 0)
			result.invertedList = new InvList();

		return result;
	}

	public QryResult evaluateIndri(RetrievalModel r) throws IOException {
		QryResult result = args.get(0).evaluate(r);
		long totalFreq = QryEval.READER
				.getSumTotalTermFreq(result.invertedList.field);


		field = result.invertedList.field;
		ctf = result.invertedList.ctf;

		MLE = (double) result.invertedList.ctf / totalFreq;

		int numOfDoc = result.invertedList.df;
		InvList list = result.invertedList;
		int docid;
		for (int i = 0; i < numOfDoc; i++) {
			docid = list.getDocid(i);

			double score = lambda * (list.postings.get(i).tf + mu * MLE)
					/ (dls.getDocLength(field, docid) + mu)
					+ (1 - lambda) * MLE;

			result.docScores.add(docid,
					score);
		}

		return result;
	}

	public QryResult evaluateBM25(RetrievalModel r) throws IOException {
		QryResult result = args.get(0).evaluate(r);
		InvList list = result.invertedList;
		//dls = new DocLengthStore(QryEval.READER);
		
		double k_1 = RetrievalModelBM25.k_1;
		double b = RetrievalModelBM25.b;
		double k_3 = RetrievalModelBM25.k_3;
		double avgLeng = QryEval.READER.getSumTotalTermFreq(list.field)
				/ (double) QryEval.READER.getDocCount(list.field);
		double term1 = Math.log((numDocs - list.df + 0.5) / (list.df + 0.5));

		for (int i = 0; i < result.invertedList.df; i++) {
			int tf = list.postings.get(i).tf;

			double score = term1
					* tf
					/ (tf + k_1
							* (1 - b + b
									* dls.getDocLength(list.field,
											list.getDocid(i)) / avgLeng));

			result.docScores.add(result.invertedList.postings.get(i).docid,
					score);
		}
		if (result.invertedList.df > 0)
			result.invertedList = new InvList();
		return result;

	}

	/*
	 * Calculate the default score for a document that does not match the query
	 * argument. This score is 0 for many retrieval models, but not all
	 * retrieval models.
	 * 
	 * @param r A retrieval model that controls how the operator behaves.
	 * 
	 * @param docid The internal id of the document that needs a default score.
	 * 
	 * @return The default score.
	 */
	//double part1 = lambda * mu * MLE;
	//double part2= (1 - lambda) * MLE;
	public double getDefaultScore(RetrievalModel r, long docid)
			throws IOException {

		if (r instanceof RetrievalModelIndri) {

			return lambda * mu * MLE / (dls.getDocLength(field, (int) docid) + mu)+ (1 - lambda) * MLE;

		}

		if (r instanceof RetrievalModelUnrankedBoolean)
			return (0.0);

		return 0.0;
	}

	/**
	 * Return a string version of this query operator.
	 * 
	 * @return The string version of this query operator.
	 */
	public String toString() {
		String result = new String();
		for (Iterator<Qryop> i = this.args.iterator(); i.hasNext();)
			result += (i.next().toString() + " ");
		return ("#SCORE( " + result + ")");
	}
}
