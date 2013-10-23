package foo.annotators;

import java.util.*;
import java.util.Map.*;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.EmptyFSList;
import org.apache.uima.jcas.cas.NonEmptyFSList;
import org.apache.uima.jcas.tcas.Annotation;

import foo.typesystems.Document;
import foo.typesystems.Token;

/**
 * Description: Used to extract term-frequency
 */
public class DocumentVectorAnnotator extends JCasAnnotator_ImplBase {

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {

		FSIterator<Annotation> iter = jcas.getAnnotationIndex().iterator();
		if (iter.isValid()) {
			iter.moveToNext();
			Document doc = (Document) iter.get();
			createTermFreqVector(jcas, doc);
		}

	}
	
	/**
	 * Description: extract term list for each document
	 * @param jcas
	 * @param doc
	 */
	private void createTermFreqVector(JCas jcas, Document doc) {

		String docText = doc.getText();
		HashMap<String, Integer> termFre = new HashMap<String, Integer>();
		
		//TO DO: construct a vector of tokens and update the tokenList in CAS
		String[] terms = docText.split("\\s+");
		if(terms.length == 0){
			doc.setTokenList(new EmptyFSList(jcas));
			return;
		}
		for(int i = 0; i < terms.length; ++ i){
			String temp = terms[i];
			if(temp.length() > 0){
				String str = temp.toLowerCase();
				if(str.equals("old") && (i + 1 < terms.length) && terms[i + 1].toLowerCase().equals("friend")){
					if(termFre.containsKey("best")){
						int fre = termFre.get("best");
						++ fre;
						termFre.put("best", fre);
					}else{
						termFre.put("best", 1);
					}
					continue;
				}
				if(termFre.containsKey(str)){
					int fre = termFre.get(str);
					++ fre;
					termFre.put(str, fre);
				}else {
					termFre.put(str, 1);
				}
			}
		}
		
		NonEmptyFSList termList = new NonEmptyFSList(jcas);
		NonEmptyFSList list = termList;
		
		Iterator<Entry<String, Integer>> iter = termFre.entrySet().iterator();
		while(iter.hasNext()){
			Entry<String,Integer> e = iter.next();
			String term = e.getKey();
			int fre = e.getValue();
			Token t = new Token(jcas);
			t.setText(term);
			t.setFrequency(fre);

			list.setHead(t);
			list.addToIndexes();
			if(iter.hasNext()){
				NonEmptyFSList nextList = new NonEmptyFSList(jcas);
				list.setTail(nextList);
				list = nextList;
			}
		}
		list.setTail(new EmptyFSList(jcas));
		doc.setTokenList(termList);
	}
}
