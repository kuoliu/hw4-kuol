package foo.casconsumers;

import java.io.IOException;
import java.util.*;
import java.util.Map.*;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.ProcessTrace;

import foo.typesystems.Document;
import foo.typesystems.Token;
import foo.utils.Utils;

/**
 * Description: Used for compute the cosine similarity/ dice coefficient/
 * jaccard coefficient. Compute the MRR.
 */
public class RetrievalEvaluator extends CasConsumer_ImplBase {

	/** query id number **/
	private ArrayList<Integer> qIdList;

	/** query and text relevant values **/
	private ArrayList<Integer> relList;

	/** termVector for query or text **/
	private LinkedList<HashMap<String, Integer>> termVectors;

	/** text String **/
	private HashMap<Integer, String> relSentence;

	/** termVector for queries **/
	private HashMap<Integer, HashMap<String, Integer>> queryTermVector;

	/**
	 * Description: Used to do some initialization jobs.
	 */
	public void initialize() throws ResourceInitializationException {

		qIdList = new ArrayList<Integer>();

		relList = new ArrayList<Integer>();

		relSentence = new HashMap<Integer, String>();

		termVectors = new LinkedList<HashMap<String, Integer>>();

		queryTermVector = new HashMap<Integer, HashMap<String, Integer>>();

	}

	/**
	 * Description: 1. construct the global word dictionary 2. keep the word
	 * frequency for each sentence
	 */
	@Override
	public void processCas(CAS aCas) throws ResourceProcessException {

		JCas jcas;
		try {
			jcas = aCas.getJCas();
		} catch (CASException e) {
			throw new ResourceProcessException(e);
		}

		FSIterator it = jcas.getAnnotationIndex(Document.type).iterator();

		if (it.hasNext()) {
			Document doc = (Document) it.next();

			FSList fsTokenList = doc.getTokenList();
			ArrayList<Token> tokenList = Utils.fromFSListToCollection(
					fsTokenList, Token.class);

			qIdList.add(doc.getQueryID());
			relList.add(doc.getRelevanceValue());

			// Do something useful here
			HashMap<String, Integer> map = new HashMap<String, Integer>();
			Iterator<Token> iter = tokenList.iterator();
			while (iter.hasNext()) {
				Token t = iter.next();
				if (!Utils.isStopWord(t.getText()))
					map.put(t.getText(), t.getFrequency());
			}
			termVectors.add(map);

			if (doc.getRelevanceValue() == 99) {
				queryTermVector.put(doc.getQueryID(), map);
			} else if (doc.getRelevanceValue() == 1) {
				relSentence.put(doc.getQueryID(), doc.getText());
			}
		}
	}

	/**
	 * Description: 1. Compute Cosine Similarity and rank the retrieved
	 * sentences 2. Compute the MRR metric
	 */
	@Override
	public void collectionProcessComplete(ProcessTrace arg0)
			throws ResourceProcessException, IOException {

		super.collectionProcessComplete(arg0);

		HashMap<Integer, Double> relScore = new HashMap<Integer, Double>();
		LinkedList<Double> scoreList = new LinkedList<Double>();
		HashMap<Integer, Integer> rank = new HashMap<Integer, Integer>();

		// compute the cosine similarity measure
		Iterator<Integer> qIditer = this.qIdList.iterator();
		Iterator<Integer> reliter = this.relList.iterator();
		Iterator<HashMap<String, Integer>> veciter = this.termVectors
				.iterator();
		while (qIditer.hasNext()) {
			int id = qIditer.next();
			int rel = reliter.next();
			HashMap<String, Integer> termVec = veciter.next();
			double sim = 0.0;
			if (rel != 99) {
				HashMap<String, Integer> query = this.queryTermVector.get(id);
				sim = computeCosineSimilarity(query, termVec);
				//sim += computeDiceCoefficient(query, termVec);
				sim += computeJaccardCoefficient(query, termVec);
				//System.out.println(sim + "\t" + rel);

				if (rel == 1) {
					relScore.put(id, sim);
					rank.put(id, 1);
				}
			}
			scoreList.add(sim);
		}

		// compute the rank of retrieved sentences
		qIditer = this.qIdList.iterator();
		reliter = this.relList.iterator();
		Iterator<Double> scoreiter = scoreList.iterator();
		while (scoreiter.hasNext()) {
			int id = qIditer.next();
			int rel = reliter.next();
			double sim = scoreiter.next();

			if (rel == 0) {
				double relscore = relScore.get(id);
				if (relscore < sim) {
					int r = rank.get(id);
					++r;
					rank.put(id, r);
				}
			}
		}

		for (int id : relScore.keySet()) {
			System.out.print("Score:\t" + relScore.get(id));
			System.out.print("\t");
			System.out.print("rank=" + rank.get(id));
			System.out.print("\t");
			System.out.print("rel=1\tqid=" + id + "\t"
					+ this.relSentence.get(id));
			System.out.println();
		}

		// compute the metric:: mean reciprocal rank
		double metric_mrr = compute_mrr(rank.values());
		System.out.println(" (MRR) Mean Reciprocal Rank ::" + metric_mrr);
	}

	/**
	 * Description: Used to compute cosine similarity
	 * 
	 * @param queryVector
	 * @param docVector
	 * @return cosineSimilarity
	 */
	private double computeCosineSimilarity(Map<String, Integer> queryVector,
			Map<String, Integer> docVector) {
		double cosineSimilarity = 0.0;

		Iterator<Entry<String, Integer>> qiter = queryVector.entrySet()
				.iterator();

		int sum = 0;
		int qSqrt = 0;
		while (qiter.hasNext()) {
			Entry<String, Integer> e = qiter.next();
			String term = e.getKey();
			int fre = e.getValue();
			qSqrt += (fre * fre);
			if (docVector.containsKey(term))
				sum += (fre * docVector.get(term));
		}
		Iterator<Entry<String, Integer>> diter = docVector.entrySet()
				.iterator();
		int dSqrt = 0;
		while (diter.hasNext()) {
			Entry<String, Integer> e = diter.next();
			int fre = e.getValue();
			dSqrt += (fre * fre);
		}

		cosineSimilarity = (double) sum
				/ (Math.sqrt((double) qSqrt) * Math.sqrt((double) dSqrt));
		return cosineSimilarity;
	}

	/**
	 * Description: Used to compute dice Coefficient
	 * 
	 * @param queryVector
	 * @param docVector
	 * @return diceCoefficient
	 */
	private double computeDiceCoefficient(Map<String, Integer> queryVector,
			Map<String, Integer> docVector) {
		int intersect = 0;
		for (String term : queryVector.keySet()) {
			if (docVector.containsKey(term))
				++intersect;
		}
		double diceCoefficient = (double) 2 * intersect
				/ ((double) queryVector.size() + docVector.size());
		return diceCoefficient;
	}

	/**
	 * Description: Used to compute Jaccard Coefficient
	 * 
	 * @param queryVector
	 * @param docVector
	 * @return diceCoefficient
	 */
	private double computeJaccardCoefficient(Map<String, Integer> queryVector,
			Map<String, Integer> docVector) {
		int included = 0;
		int excluded = 0;
		for (String term : queryVector.keySet()) {
			if (docVector.containsKey(term))
				++included;
			else
				++excluded;
		}
		double jaccard = (double) included
				/ (double) (excluded + docVector.size());
		return jaccard;
	}

	/**
	 * Description: Used to compute MRR
	 * 
	 * @param list
	 * @return metricMrr
	 */
	private double compute_mrr(Collection<Integer> list) {
		double metricMrr = 0.0;

		Iterator<Integer> iter = list.iterator();
		while (iter.hasNext()) {
			metricMrr += (1.0 / (double) iter.next());
		}

		metricMrr /= (double) list.size();
		return metricMrr;
	}

}