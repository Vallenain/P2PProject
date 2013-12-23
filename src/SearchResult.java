
public class SearchResult {
	String words; // strings matched for this url
	String[] url;   // url matching search query 
	long frequency; //number of hits for page
	
	public SearchResult(String word, String[] urls, long f){
		words=word;
		url=urls;
		frequency=f ;
	}
	
}
