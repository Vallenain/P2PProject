
public class Ressource {
	String url ;
	int hit ;
	
	public Ressource(String s){
		url = s ;
		hit = 1 ;
	}
	
	public void addOneHit(){
		hit++ ;
	}

	public String getUrl() {
		return url;
	}

	public int getHit() {
		return hit;
	}
	
}
