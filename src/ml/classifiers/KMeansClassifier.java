package ml.classifiers;
import java.lang.reflect.Array;
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
	private int numIterations; // number of iterations
	private ArrayList<Example> centroids;
	public HashMap<Integer, ArrayList<Example>> clusters; // hashmap from centroid index (in centroids array) to arraylist of examples representing the cluster
	private ArrayList<Example> examples; // array list of examples
	private Set<Integer> data_featureset; //consider using ArrayList for this
	private HashMap<Integer, Double> idf_hash; //hash from a feature number to its idf value
	
	private boolean clustering_changed; // boolean representing whether or not the clustering has changed during each iteration
	
	// Hyperparameters
	public void setK(int k) { this.k = k; }
	public void setNumIterations(int n) { this.numIterations = n; }
	
	public void train(DataSet data) {
		examples = data.getData();
		data_featureset = data.getAllFeatureIndices();
		centroids = new ArrayList<Example>();
		clusters = new HashMap<Integer, ArrayList<Example>>();
		idf_hash = new HashMap<Integer, Double>();
		
		for (int i = 0; i < k; i++) {
			clusters.put(i, new ArrayList<Example>());
		}
		
		populateIDF(examples); // populate the idf hash
		
		// Change the feature values of all examples to tf/idf values instead of  word counts
		initialize_tfidf_values(examples);
//		System.out.println("Initialized IDF hash");
		
		initializeMeans();
		
		// Initially associate each example with the nearest centroid (cluster them)
		for ( Example e : examples ) {
			int centroid_index = getClosestCentroid(e);
			clusters.get(centroid_index).add(e);
		}
		
		clustering_changed = true;
		
		// Debugging - print the points in each cluster, and the centroids
//		System.out.println("centroids: "+centroids);
//		System.out.println("clusters: "+clusters);
//		for (int cluster_index: clusters.keySet() ) {
//			System.out.println("cluster index: "+cluster_index);
//			ArrayList<Example> cluster = clusters.get(cluster_index);
//			for (Example example : cluster) {
//				System.out.println(example);
//			}
//			System.out.println("---------");
//		}
//		System.out.println(clustering_changed);
		
		int iterations = 0;
		while (clustering_changed) {
			if (iterations >= numIterations) { break; }
			clustering_changed = false;
			updateMeans();
			reassignClusters();
			iterations++;
		}
		
//		System.out.println("Done iterating! Iterations: "+iterations);
		
		// Debugging - print the points in each cluster, and the centroids
//		System.out.println("centroids: "+centroids);
//		System.out.println("clusters: "+clusters);
//		for (int cluster_index: clusters.keySet() ) {
//			System.out.println("cluster index: "+cluster_index);
//			ArrayList<Example> cluster = clusters.get(cluster_index);
//			for (Example example : cluster) {
//				System.out.println(example);
//			}
//			System.out.println("---------");
//		}
//		System.out.println(clustering_changed);
	
	}
	
	/**
	 * Reassigns points to their closest centroids. If an example's centroid (cluster) changes, clustering_changed is set to true
	 */
	private void reassignClusters() {
		HashMap<Integer, ArrayList<Example>> new_clusters = new HashMap<Integer, ArrayList<Example>>();
		// Initialize new_clusters hashmap
		for (int index : clusters.keySet()) {
			new_clusters.put(index, new ArrayList<Example>());
		}
		for (int centroid_index : clusters.keySet()) {
			ArrayList<Example> cluster_examples = clusters.get(centroid_index);
			for (Example e : cluster_examples) {
				int closest_centroid = getClosestCentroid(e);
				new_clusters.get(closest_centroid).add(e);
				if (closest_centroid != centroid_index) { // if this example's closest centroid has changed
					clustering_changed = true;
				} 
			}
		}
		if (clustering_changed) {
			clusters = new_clusters;
		}
	}
	
	/**
	 * Updates the means by recalculating them for each cluster in the clusters hashmap
	 */
	private void updateMeans() {
		for (int k = 0; k < centroids.size(); k++) {
			// Get the new mean for the cluster associated with this centroid
			Example new_centroid = recalculateMean(clusters.get(k));
			// Update the centroid in the centroids array
			centroids.set(k, new_centroid);
		}
	}
	
	/**
	 * Recalculate the mean for a given arraylist of examples
	 * @param examples
	 * @return
	 */
    private Example recalculateMean(ArrayList<Example> examples) {
        Example center = new Example();
        HashMap<Integer, Double> feat_values = new HashMap<Integer, Double>();
        
        for ( Example e : examples ) {
            for ( int f : data_featureset ) {
                if (!feat_values.containsKey(f)) {
                    feat_values.put(f, e.getFeature(f));
                }
                else {
                    double count = feat_values.get(f);
                    count += e.getFeature(f);
                    feat_values.put(f, count);
                }
            }
        }
        
        for ( int f : data_featureset ) {
            double avg = feat_values.get(f)/examples.size();
            center.setFeature(f, avg);
        }
        return center;
    }
	
	/***
	 * Initialize the means by, over each iteration, finding the example with the maximum (min distance to a previous centroid)
	 * @param examples
	 * @return array of means
	 */
	public void initializeMeans(){
		centroids.add(examples.get(0)); //just pick the first example arbitrarily
		for (int i = 1; i < k; i++) {
			double max_distance = Double.MAX_VALUE;
			Example farthest_example = examples.get(0);
			for ( Example e : examples ) {				
				// if a point is further away (smaller cosine similarity) from any centroid
				//	than the farthest seen point so far, make it the farthest example
				if ( getMaxCos(e, centroids) < max_distance ) { 
					max_distance = getMaxCos(e, centroids);
					farthest_example = e;
				}
			}
			centroids.add(farthest_example); // make the farthest example a centroid
		}
	}

	/**
	 * Given an example and the existing centroids, returns the maximum cosine similarity of the example to a centroid 
	 * (meaning cosine similarity of closest centroid)
	 * @param e
	 * @param centroids
	 * @return
	 */
	private double getMaxCos(Example e, ArrayList<Example> centroids) {
		double max_cos = Double.MIN_VALUE;
		for ( Example c : centroids ) {
			if (cos_sim(e, c) > max_cos) {
				max_cos = cos_sim(e, c);
			}
		}
		return max_cos;
	}
	
	/**
	 * Given an example e, returns the index of the closest centroid (in the centroids instance variable array list)
	 * @param e
	 * @return
	 */
	private int getClosestCentroid(Example e) {
		double max_cos = -1;
		int index_of_closest_centroid = -5;
		for ( int index = 0; index< centroids.size(); index++ ) {
			Example c = centroids.get(index);
			if (cos_sim(e, c) > max_cos)  {
				max_cos = cos_sim(e, c);
				index_of_closest_centroid = index;
			}
		}
		return index_of_closest_centroid;
	}
	
	
	/**
	 * Change the examples to have tf/idf values instead of word counts for feature values
	 * @param examples
	 */
	public void initialize_tfidf_values(ArrayList<Example> examples) {
        //weight each example's features using tf_idf
        for( Example e: examples ){
            for ( int f : e.getFeatureSet() ) {
                double tf_idf = getTFIDF(e, f);
                e.setFeature(f, tf_idf);
            }
        }
    }
	
	/**
	 * Gets the TFIDF value of a feature for a given example, by dividing term frequency by idf value (from idf_hash)
	 * @param example
	 * @param featurenum
	 * @return
	 */
    private double getTFIDF(Example example, int featurenum) {
        double tf = example.getFeature(featurenum); //just simple use of tf, not augmented frequency
        double idf = idf_hash.get(featurenum);
        return tf/idf;
    }
		
	
	public double classify(Example example) {
		int c = getClosestCentroid(example);
		double majority_label = getMajorityLabelCount(c)[0];
		return majority_label;
	}

	
	public double confidence(Example example) {
		return 0;
	}

	/**
	 * Given a term's feature number, iterates through each example in the data set and gets that word's number of occurances
	 * Returns the log (total number of examples divided by this count)
	 * @param term_index
	 * @param examples
	 */
	private void populateIDF(ArrayList<Example> examples) {
        for ( int feature_num : data_featureset ) {
            double count = 0;
            for ( Example e : examples ) {
                if (e.getFeature(feature_num) != 0) { //technically don't even need this comparison since it would be just adding 0
                    count += e.getFeature(feature_num); //increment count by how many times this word is in this example
                }
            }
            
            double idf = Math.log(examples.size() / count);
            idf_hash.put(feature_num, idf);
        }
        
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
		
		double dot_product = 0;
		double mag_1 = 0;
		double mag_2 = 0;
//		System.out.println("comparing " + exampleA + " to " + exampleB);
		for ( int f : data_featureset ) {
			dot_product += ( exampleA.getFeature(f) * exampleB.getFeature(f) );
			mag_1 += Math.pow(exampleA.getFeature(f), 2);
			mag_2 += Math.pow(exampleB.getFeature(f), 2);
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
    public double getClusterPurity(int cluster_id) {
    	ArrayList<Example> examples = clusters.get(cluster_id);
    	double[] majorityLabelCount = getMajorityLabelCount(cluster_id);
    	double max_label = majorityLabelCount[0];
    	double max_count = majorityLabelCount[1];
        System.out.println("cluster " + cluster_id + " max_label: " + max_label);
        System.out.println(max_count+"/"+examples.size());
        double purity = (double) max_count/ (double) examples.size();
        return purity;
    }
    
    public double getOveralPurity() {
    	int running_sum = 0;
    	int total_examples = examples.size();
    	for (int centroid_index : clusters.keySet()) {
			running_sum	+= getMajorityLabelCount(centroid_index)[1];
		}
    	return (double) running_sum/total_examples;
    }
    
    /**
     * Returns an array of two doubles. First is majority label, second is the count of that majority label (within the given cluster)
     * @param cluster_id
     * @return
     */
    public double[] getMajorityLabelCount(int cluster_id) {
	    HashMap<Double, Integer> counts = new HashMap<Double, Integer>(); //Hashmap for instances of each label in cluster
	    ArrayList<Example> examples = clusters.get(cluster_id);
	    for ( Example e : examples ) {
	        double label = e.getLabel();
	        if (!counts.containsKey(label)) {
	            counts.put(label, 1);
	        }
	        else {
	            counts.put(label, counts.get(label)+1); //else just increment the count
	        }
	    }
	    
	    //find max label
	    double max_label = -1;
	    int max_count = -1;
	    for ( Double l : counts.keySet() ) {
	        if ( counts.get(l) > max_count ) {
	            max_label = l;
	            max_count = counts.get(l);
	        }
	    }
	    double[] ret_array = {max_label, (double)max_count};
	    return ret_array;
    }
}
