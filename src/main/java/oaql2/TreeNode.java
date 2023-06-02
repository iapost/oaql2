/** @file TreeNode.java */

package oaql2;

import java.util.ArrayList;

/**
 * Helper class used while translating an OAQL2 query to a MongoDB query
 */
public class TreeNode {

	/** The alias specified for a table in the OAQL2 query */
	public String alias;

	/** The name of a table in the OAQL2 query */
	public String table;

	/** The full sequence of parent tables joined until this table */
	public String fullpath;

	/** The child tables joined with this table */
	public ArrayList<TreeNode> children;

	/** The immediate parent table */
	public TreeNode parent;
	
	/**
	 * Constructor to initialize a TreeNode
	 * 
	 * @param alias the alias specified or null if not specified
	 * @param table the table name specified
	 * 
	 */
	public TreeNode(String alias, String table) {
		this.alias = alias==null ? table : alias;
		this.table = table;
		this.parent = null;
		this.children = new ArrayList<TreeNode>();
	}
	
	/**
	 * Finds the full sequence of parent tables that were joined until this table
	 */
	public void resolveFullPath() {
		if(parent == null) {
			fullpath = table;
		}else {
			fullpath = parent.fullpath + "." + table;
		}
		for(TreeNode t : children) {
			t.resolveFullPath();
		}
	}
}
