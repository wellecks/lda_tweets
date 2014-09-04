package lda;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;

/** A wrapper for using the Twitter API. 
 *  @author wellecks **/
public class TwitterClient 
{
	private static final String DATA_DIR = "data";
	private Twitter twitter;
	
	public TwitterClient()
	{
		TwitterFactory tf = new TwitterFactory();
		twitter = tf.getInstance();
	}
	
	/** Downloads all of a user's tweets to two files:
	 * 
	 * 	  ./data/username/username_tweets.txt        (one tweet per line)
	 *    ./data/username/username_tweets_single.txt (all tweets on one line)
	 * 
	 *  Creates the ./data/username directory if necessary.
	 *  
	 *  Set numTweets <= 0 to download all of the user's tweets. **/
	public void downloadTweetsFromUser(String username, int numTweets) 
			throws IOException, TwitterException
	{
		if (numTweets <= 0) 
		{ 
			numTweets = Integer.MAX_VALUE; 
		}
		File userDir = new File(String.format("%s/%s", DATA_DIR, username));
		userDir.mkdir();
		String multiLineFile = 
			String.format("%s/%s/%s_tweets.txt", DATA_DIR, username, username);
		String singleLineFile = 
			String.format("%s/%s/%s_tweets_single.txt", DATA_DIR, username, username);
		username = "@" + username;
		downloadTweetsToFile(username, multiLineFile,  numTweets, true);
		downloadTweetsToFile(username, singleLineFile, numTweets, false);
	}
	
	/** Download n of a user's tweets to outFilename. **/
	public void downloadTweetsToFile(String username, String outFilename, 
			                         int n, boolean newLine) 
			                         throws IOException, TwitterException
	{
		PrintWriter out = new PrintWriter(outFilename);
		List<String> tweets = this.getUserTweetsText(username, n);
		for (String t: tweets)
		{
			if (newLine)
			{
				out.print(t.replace("\n", "") + "\n");
			}
			else
			{
				out.print(t.replace("\n", "") + " ");
			}
		}
		System.out.println(String.format(
				"Wrote %d tweets for user %s to file %s.", 
				tweets.size(), username, outFilename));
		out.close();
	}
	
	public List<String> getUserTweetsText(String username, int n) 
										  throws TwitterException
	{
		System.out.println("Getting tweets for user " + username + ".");
		List<String> tweets = new ArrayList<String>();
		if (n <= 0) { return tweets; }
		int iters = (int) Math.ceil(n / 200.0);
		for(int i = 0; i<iters; i++)
		{
			int currSize = tweets.size();
			Paging p = new Paging(i + 1, 200);
			ResponseList<Status> statuses = this.twitter.getUserTimeline(username, p);
			for (Status s: statuses)
			{
				tweets.add(s.getText());
			}
			if (tweets.size() == currSize) break;
			else currSize = tweets.size();
		}
		System.out.println(String.format(
				"Downloaded %d tweets for user %s.", 
				tweets.size(), username));
		return tweets;
	}
	
}
