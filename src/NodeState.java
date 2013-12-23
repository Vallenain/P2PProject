
public class NodeState {

	String type ;
	long time ;
	
	public NodeState(String t){
		type = t ;
		time = (new java.util.Date()).getTime();
	}

	public void setType(String type) {
		this.type = type;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public String getType() {
		return type;
	}

	public long getTime() {
		return time;
	}
}
