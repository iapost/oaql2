package oaql2;
import java.util.ArrayList;

public class TreeNode {
	public String alias;
	public String table;
	public String fullpath;
	public ArrayList<TreeNode> children;
	public TreeNode parent;
	
	public TreeNode(String alias, String table) {
		this.alias=alias==null?table:alias;
		this.table=table;
		this.parent=null;
		this.children=new ArrayList<TreeNode>();
	}
	
	public void resolveFullPath() {
		if(parent==null) {
			fullpath=table;
		}else {
			fullpath=parent.fullpath+"."+table;
		}
		for(TreeNode t : children) {
			t.resolveFullPath();
		}
	}
}
