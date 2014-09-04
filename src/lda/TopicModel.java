package lda;

import cc.mallet.types.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.topics.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** LDA with Mallet. 
 *  @author wellecks **/
public class TopicModel 
{
	static final String[] ADDL_STOP_WORDS = {"http", "rt", "https", "bit", "ly"};
	static final String DATA_DIR = "data";
	static final int NUM_WORDS = 20;
	static final int NUM_ITERS = 2000;
	
	/** Create the data loading pipeline **/
	private static ArrayList<Pipe> makePipeList()
	{
		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
		// Lowercase everything
		pipeList.add(new CharSequenceLowercase());
		// Unicode letters, underscore, and hashtag
		Pattern pat = Pattern.compile("[\\p{L}_#]+");
		pipeList.add(new CharSequence2TokenSequence(pat));
		// Remove stop words
		TokenSequenceRemoveStopwords tsrs = new TokenSequenceRemoveStopwords();
		tsrs.addStopWords(ADDL_STOP_WORDS);
		pipeList.add(tsrs);
		// Convert the token sequence to a feature sequence.
		pipeList.add(new TokenSequence2FeatureSequence());
		return pipeList;
	}
	
	/** Load a file, with one instance per line, 
	 *  and return as an InstanceList. **/
	public static InstanceList fileToInstanceList(String filename)
	{
		InstanceList instances = new InstanceList(new SerialPipes(makePipeList()));
		instances.addThruPipe(new SimpleFileLineIterator(filename));
		return instances;
	}
	
	/** numTopics = 20, alphaT = 1.0, betaW = 0.01 **/
	public static ParallelTopicModel trainModel(InstanceList instances, 
												int numTopics, int numIters,
												double alphaT, double betaW)
	{
		ParallelTopicModel model = new ParallelTopicModel(numTopics, alphaT, betaW);
		model.addInstances(instances);
		model.setNumThreads(2);
		model.setOptimizeInterval(20);
		model.setNumIterations(numIters);
		try
		{
			model.estimate();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		return model;
	}
	
	/** Given a trained model, infer the distribution for a single new instance.
	 *  (Only infers for the first element of the input instances). **/
	public static double[] inferTopicDistribution(ParallelTopicModel model,
			  InstanceList instances)
	{
		TopicInferencer inferencer = model.getInferencer();
		double[] distribution = inferencer.getSampledDistribution(instances.get(0), NUM_ITERS, 1, 5);
		for (int i=0; i< distribution.length; i++)
		{
			System.out.println(i + "\t" + distribution[i]);
		}
		return distribution;
	}
	
	public static String[][] getTopWords(ParallelTopicModel model, 
			 					   InstanceList instances,
			 					   int numTopics, int numWords)
	{
		String[][] topWords = new String[numTopics][];
		Alphabet dataAlphabet = instances.getDataAlphabet();
		// Get an array of sorted sets of word ID/count pairs
		ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
		// Show top words in topics with proportions
		for (int topic = 0; topic < numTopics; topic++) 
		{
			String[] words = new String[numWords];
			Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
			int rank = 0;
			while (iterator.hasNext() && rank < numWords) {
				IDSorter idCountPair = iterator.next();
				words[rank] = String.format("%s:%.0f", 
						dataAlphabet.lookupObject(idCountPair.getID()), 
						idCountPair.getWeight());
				rank++;
			}
		topWords[topic] = words;
		}
		return topWords;
	}

	
	public static void topWordsToFile(String outFile,
								   	  ParallelTopicModel model, 
									  InstanceList instances,
									  int numTopics, int numWords)
	{
		try
		{
			PrintWriter pw = new PrintWriter(outFile);
			String[][] topWords = TopicModel.getTopWords(model, instances, numTopics, numWords);
			for (int topic = 0; topic<topWords.length; topic++)
			{
				pw.println(String.format("Topic %d", topic));
				System.out.print(String.format("Topic %d \t", topic));
				for (String word: topWords[topic])
				{
					pw.println(String.format("%s", word));
					System.out.print(String.format("%s ", word));
				}
				pw.println();
				System.out.println();
			}
	        pw.close();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void distToFile(String outFile, double[] dist)
	{
		try
		{
			PrintWriter pw = new PrintWriter(outFile);
	        for (int i = 0; i < dist.length; i++) {
	            pw.println(i + "\t" + dist[i]);
	        }
	        pw.close();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static String[][] topTopicsToFile(String outFile,
									   double[] inferredDistribution, 
									   String[][] topWords, 
									   int numTopics)
	{
		String[][] topTopics = new String[numTopics][];
		// get ranked topic indices
		Integer[] idx = new Integer[inferredDistribution.length];
		final double[] data = inferredDistribution;
		for (int i=0; i<idx.length; i++) { idx[i] = i; }
		Arrays.sort(idx, new Comparator<Integer>() {
		    @Override public int compare(Integer o1, Integer o2) {
		        return Double.compare(data[o1], data[o2]);
		    }
		});
		try
		{
			PrintWriter pw = new PrintWriter(outFile);
			for (int i = 0; i<numTopics; i++)
			{
				String[] words = new String[topWords[0].length];
				int topicNum = idx[idx.length - 1 - i];
				pw.println("Topic " + topicNum + ": " + inferredDistribution[topicNum]);
				int j = 0;
				for (String word: topWords[topicNum])
				{
					pw.println(String.format("%s", word));
					words[j] = word;
					j++;
				}
				pw.println();
				topTopics[i] = words;
			}
	        pw.close();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		return topTopics;
	}
	
	/** Assumes folder structure generated by TwitterClient **/
	public static void analyzeTwitterUser(String username, int numTopics)
	{
		String multiLineFile = 
				String.format("%s/%s/%s_tweets.txt", DATA_DIR, username, username);
		String singleLineFile = 
			String.format("%s/%s/%s_tweets_single.txt", DATA_DIR, username, username);
		String topWordsFile = 
				String.format("%s/%s/%s_top_words.txt", DATA_DIR, username, username);
		String distFile = 
				String.format("%s/%s/%s_composition.txt", DATA_DIR, username, username);
		String top5File = 
				String.format("%s/%s/%s_ranked.txt", DATA_DIR, username, username);
		String jsonFile = 
				String.format("viz/d3bubble/json/%s.json", username);
		
		InstanceList instances = TopicModel.fileToInstanceList(multiLineFile);
		ParallelTopicModel model = TopicModel.trainModel(
				instances, numTopics, NUM_ITERS, 1.0, 0.01);
		
		topWordsToFile(topWordsFile, model, instances, numTopics, NUM_WORDS);
		
		System.out.println("Inferring overall topic distribution...");
		double[] dist = inferTopicDistribution(model, 
				TopicModel.fileToInstanceList(singleLineFile));
		distToFile(distFile, dist);
		String[][] topTopics = topTopicsToFile(
				top5File, 
				dist, 
				getTopWords(model, instances, numTopics, NUM_WORDS), 
				10);
		toJSON(jsonFile, topTopics);
	}

	public static void toJSON(String outFile, String[][] topWords)
	{
		JsonObject jo = new JsonObject();
		jo.addProperty("name", "");
		JsonArray ja = new JsonArray();
		for (int i = 0; i<topWords.length; i++)
		{
			String[] topic = topWords[i];
			JsonObject jTopic = new JsonObject();
			jTopic.addProperty("name", topic[0].split(":")[0]);
			JsonArray jWordsArray = new JsonArray();
			for (String word: topic)
			{
				JsonObject jWord = new JsonObject();
				String[] toks = word.split(":");
				jWord.addProperty("name", toks[0]);
				jWord.addProperty("size", Integer.parseInt(toks[1]));
				jWordsArray.add(jWord);
			}
			jTopic.add("children", jWordsArray);
			ja.add(jTopic);
		}
		jo.add("children", ja);
		try
		{
			PrintWriter pw = new PrintWriter(outFile);
			pw.println(jo.toString());
			pw.close();
		} catch (IOException e) { e.printStackTrace(); }
	}
	
	public static void main(String[] args) throws Exception 
	{
		String username = "BarackObama";
		int numTopics = 50;
		int numTweets = 4000;
		TwitterClient tc = new TwitterClient();
		tc.downloadTweetsFromUser(username, numTweets);
		analyzeTwitterUser(username, numTopics);
	}

}