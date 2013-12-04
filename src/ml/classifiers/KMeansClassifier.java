package ml.classifiers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import ml.data.DataSet;
import ml.data.Example;

public class KMeansClassifier implements Classifier {

	private int k; // number of clusters
	private int i,j,p,q; //iteration variables
	private int numIterations;
	
	private Set<Integer> data_featureset; //consider using ArrayList for this
	private HashMap<Example, tfidf_vector> tfidf_vectors; //Hashmap of examples to their tf_idf vectors
	private HashMap<Integer, Double> idf_hash;
	
	HashMap<Example,Integer> finalClusterAssignments; //initialize centroids
	
	
	public void train(DataSet data) {
		ArrayList<Example> examples = data.getData();
		
//		-------------------------------------------------------------------------------------------------------------------
		data_featureset = data.getAllFeatureIndices();
		
//		populate idf hash map
		idf_hash = new HashMap<Integer, Double>();
		for ( int f : data_featureset ) {
			populateIDF(f, examples);
		}
		
//		get all the tf_idf vectors
		for ( Example e : examples ) {
			tfidf_vector vector = new tfidf_vector(e, data_featureset, idf_hash);
			tfidf_vectors.put(e, vector);
		}
		
		
//		-------------------------------------------------------------------------------------------------------------------
		
		
		weightExamples(examples);  //tf_idf
		
		
		ArrayList<HashMap<Integer,Double>> centroids = exampleToCentroid(initializeMeans(examples),examples.size()); //initialize centroids

		HashMap<Example,Integer> clusterAssignments = new HashMap<Example,Integer>();
			
		for(int iter=0; iter<numIterations;iter++){
		
			for(Example e: examples){
				ArrayList<Double> distances = new ArrayList<Double>();
				
				for(HashMap<Integer,Double> hash: centroids){
					double distance = 0.0;
					
					for (Map.Entry<Integer,Double> centroid : hash.entrySet()){
						
						double exampleFeature = e.getFeature(centroid.getKey());
						distance += Math.pow(exampleFeature-centroid.getValue(),2);
					}
					
					distances.add(Math.sqrt(distance));
				}
				
				double minDistance = Double.MAX_VALUE;
				int closestCentroid = -1; //initialize for error checking
				
				for(i=0;i<centroids.size();i++){
					if(distances.get(i) < minDistance){
						minDistance = distances.get(i);
						closestCentroid = i;
					}
				}
				
				clusterAssignments.put(e, closestCentroid);
			}
			
			for (Map.Entry<Example,Integer> mapping : clusterAssignments.entrySet()){
				System.out.println(mapping.getKey() + " = " + mapping.getValue());
			}
				
			this.finalClusterAssignments = clusterAssignments;
			//centroids.clear(); //update centroids : 2nd for loop 
		
			for(i=0;i<k;i++){
				
				HashMap<Integer,Double> currCentroid = new HashMap<Integer,Double>();
				HashMap<Integer,Double> denominator = new HashMap<Integer,Double>();
			
				for (Integer p=0;p<data.getAllFeatureIndices().size();p++){ currCentroid.put(p,0.0); denominator.put(p,0.0); }
				
				for (Map.Entry<Example,Integer> example : clusterAssignments.entrySet()){
					if(example.getValue() == i){
						for(Integer feature: example.getKey().getFeatureSet()){
							System.out.println(currCentroid.get(feature) + " | " + example.getKey().getFeature(feature));
							currCentroid.put(feature, currCentroid.get(feature) + example.getKey().getFeature(feature)); // 
							//denominator.put(feature, denominator.get(feature) + 1);
						}
					}
				}
				
				for(j=0;j<currCentroid.size();j++){ currCentroid.put(j, currCentroid.get(j)/denominator.get(j)); }
				centroids.set(i, currCentroid);
			}
		}
	} 
	

	
/* Problems with this implementation:
 * should use a vector with values for each feature in the whole data set, not just the feature set for this example
 */
	
	public void weightExamples(ArrayList<Example> examples) {
		//weight each example's features using tf_idf
		for(Example e: examples){
			ArrayList<Integer> exampleFeatures = new ArrayList<Integer>(e.getFeatureSet());
			double max = maxFeatValueInDoc(e);
			//System.out.println("example: " + e);
					
			//weight current example's features
			for(Integer feature: exampleFeatures){						 
				double tf_idf = tf_idf(feature, max, e, examples);
				//don't need to deep copy since we don't use the specific feature value after modifying it
				 e.setFeature(feature, tf_idf*e.getFeature(feature));
			}
		}
	}
	
	/***
	 * Find the means most likely to yield a global maximum
	 * @param examples
	 * @return array of means
	 */
	public ArrayList<Example> initializeMeans(ArrayList<Example> examples){
		ArrayList<Example> u = new ArrayList<Example>();
		
		//pick first mean randomly, then add it to u
//		Random randomGenerator = new Random();
//		int randomIndex = randomGenerator.nextInt(examples.size());
//		u.add(examples.get(randomIndex));
		u.add(examples.get(0)); //debugging
		
		HashMap<Example,Double> minDistances = new HashMap<Example,Double>();
		
		//fork=2 to K do
		for(i=1;i<k;i++){
			for(Example e: examples){
					minDistances.put(e, getMinDistance(e,u));
			}
			
			//normalize to probability distribution. Each example is assigned a weighted probability of being picked as an initial mean.
			double normalizer = 0.0;
			for (Double value : minDistances.values()) { normalizer += value; }
			for (Map.Entry<Example,Double> entry : minDistances.entrySet()){ entry.setValue(entry.getValue() / normalizer); }
			
			double randomProb = Math.random(); //random probability
			//used to find the example whose (weighted) probability range contains the just-generated random probability
			double findExample = 0.0;
			
			
//			double maxDist = -Double.MAX_VALUE; Example newMean = new Example(); //find argmax min(k' < k) debugging
			
			//Locate the example chosen to be the initial mean
			for (Map.Entry<Example,Double> entry : minDistances.entrySet()){
//				if( maxDist < entry.getValue()){ maxDist = entry.getValue(); newMean = entry.getKey(); } //debugging
				
				findExample += entry.getValue();
				if(randomProb < findExample) { u.add(entry.getKey()); break; }
			}
			minDistances.clear(); //u.add(newMean); //debugging 
		}
		
//		for(Example e: u){ System.out.println(e); } //debugging
		
		return u;
	}

	
	/***
	 * Find the distance from this example to the closest mean
	 * @param 
	 * @return the distance to the closest centroid
	 */
	private double getMinDistance(Example e, ArrayList<Example> u) {
		
		ArrayList<Integer> exampleFeatures = new ArrayList<Integer>(e.getFeatureSet());
		ArrayList<Integer> meanFeatures;
		ArrayList<Double> distances = new ArrayList<Double>();
		
		for(Example mean: u){
			double distance = 0.0;
			meanFeatures = new ArrayList<Integer>(mean.getFeatureSet());//get features for current mean
		
			//compute squared distance for all relevant features
			for(Integer feature: exampleFeatures){
				distance += Math.pow(e.getFeature(feature)-mean.getFeature(feature),2);
			}
			
			for(Integer feature: meanFeatures){
				if(!exampleFeatures.contains(feature)){
					distance += Math.pow(e.getFeature(feature)-mean.getFeature(feature),2);	
				}
			}
			distances.add(distance);
		}//end for
		
		double minDistance = Double.MAX_VALUE;
		
		for(Double distance: distances){
			if(distance < minDistance){
				minDistance = distance;
			}
		}
		return minDistance;
	}
	
	
	
	private double tf_idf(int featureNum, double docMax, Example document, ArrayList<Example> allDocuments){
		
		double allOccurrences = 0.0;
		for(Example e: allDocuments){
			if(e.getFeatureSet().contains(featureNum)) { allOccurrences += 1.0; }
		}
		
		double tf = 0.5 + 0.5*document.getFeature(featureNum)/docMax;
		double idf = Math.log(allDocuments.size()/allOccurrences);
		
//		System.out.println("tf: " + tf + " | " + "idf " + idf + " | " + "ao " + allOccurrences + " | " + "ad " +  
//		allDocuments.size() + " | " + " tf*idf " + tf*idf);
		
		return tf*idf;
		
	}
	
	
	private double maxFeatValueInDoc(Example document){
		
		ArrayList<Integer> exampleFeatures = new ArrayList<Integer>(document.getFeatureSet());
		
		double maxOccurrence = -Double.MAX_VALUE;
		for(Integer feature: exampleFeatures){
			double current = document.getFeature(feature);
			if(maxOccurrence < current){
				maxOccurrence = current;
			}
		}
		
		return maxOccurrence;
		
	}
	
	
	private ArrayList<HashMap<Integer,Double>> exampleToCentroid(ArrayList<Example> initialMeans, int numExamples){
		
		ArrayList<HashMap<Integer,Double>> centroids = new ArrayList<HashMap<Integer,Double>>();
		
		
		for(Example e: initialMeans){
			HashMap<Integer,Double> hash = new HashMap<Integer,Double>();
			for(i=0;i<numExamples;i++){
				hash.put(i, e.getFeature(i));
			}
			centroids.add(hash);
		}

		return centroids;
	}
	
	
	public double classify(Example example) {
		return 0;
	}

	
	public double confidence(Example example) {
		return 0;
	}
	
	
	public void setK(int k) { this.k = k; }
	public void setNumIterations(int numIterations) { this.numIterations = numIterations; }
	public HashMap<Example,Integer> getResult(){ return finalClusterAssignments; }
	
	
	
//	-----------------------------------------------------------------------------------------------------------------------------
	
//	tfidf_vector class
	private class tfidf_vector {
		//each example should have this vector
		private HashMap<Integer, Double> vector; //vector of td_idf values 
		
		public tfidf_vector(Example example, Set<Integer> features, HashMap<Integer, Double> idf) {
			vector = new HashMap<Integer, Double>();
			
			for ( Integer f : features ) {
				double td_idf = tf(example, f)/idf.get(f);
				vector.put(f, td_idf);
			}
		}
		
		public HashMap<Integer, Double> getVector() {
			return vector;
		}
		
		private double tf(Example example, int word_index) { //consider using augmented tf?
			return example.getFeature(word_index); // Example class stores a count for the feature value of each word
		}
		
	}
	
	/**
	 * Given a term's feature number, iterates through each example in the data set and gets that word's number of occurances
	 * Returns the log (total number of examples divided by this count)
	 * @param term_index
	 * @param examples
	 */
	private void populateIDF(int term_index, ArrayList<Example> examples) {
		double count = 0;
		for ( Example e : examples ) {
			if (e.getFeature(term_index) != 0) { //technically don't even need this comparison since it would be just adding 0
				count += e.getFeature(term_index); //increment count by how many times this word is in this example
			}
		}
		
		double idf = Math.log(examples.size() / count);
		idf_hash.put(term_index, idf);
		
	}
	
	/**
	 * Calculates the cosine similarity between two examples
	 * finds each example's tf_idf vector and calculates the cosine similarity between them
	 * Formula: dot(A, B) / mag(A) * mag(B)
	 * @param exampleA
	 * @param exampleB
	 * @return
	 */
	public double cos_sim(Example exampleA, Example exampleB) {
		HashMap<Integer, Double> vA = tfidf_vectors.get(exampleA).getVector();
		HashMap<Integer, Double> vB = tfidf_vectors.get(exampleB).getVector();
		
		double dot_product = 0;
		double mag_1 = 0;
		double mag_2 = 0;
		
		for ( int f : data_featureset ) {
			dot_product += ( vA.get(f) * vB.get(f) );
			mag_1 += Math.pow(vA.get(f), 2);
			mag_2 += Math.pow(vB.get(f), 2);
		}
		
		mag_1 = Math.pow(mag_1, .5);
		mag_2 = Math.pow(mag_2, .5);
		
		return dot_product/(mag_1*mag_2);
	}
	
	
	/**
	 * Given a cluster, calculate its purity
	 * Do this by finding the majority label and its count, then divide this count by total number of examples in the cluster
	 * @param cluster_id
	 * @return
	 */
	public double getPurity(int cluster_id) {
		HashMap<Double, Integer> counts = new HashMap<Double, Integer>(); //Hashmap for instances of each label in cluster
		
		HashMap<Example, Integer> clusters = getResult();
		int total_num = 0;
		for ( Example e : clusters.keySet() ) {
			//find all examples in this particular cluster
			if ( clusters.get(e) == cluster_id ) { 
				total_num++; //for counting how many examples in this cluster
				
				//look at each example. increment its label's count for this cluster
				double label = e.getLabel();
				if (!counts.containsKey(label)) { //check if this label is already in the hashmap
					counts.put(label, 1);
				}
				else {
					counts.put(label, counts.get(label)+1); //else just increment the count
				}
				
			}
		}
		
		//find max label
		double max_label = -1;
		int max_count = Integer.MIN_VALUE;
		for ( Double l : counts.keySet() ) {
			if ( counts.get(l) > max_count ) {
				max_label = l;
				max_count = counts.get(l);
			}
		}
		
		double purity = max_count/total_num;
		return purity;
	}
	
//	-----------------------------------------------------------------------------------------------------------------------------
	
	
}
