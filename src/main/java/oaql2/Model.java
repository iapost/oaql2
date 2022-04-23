package oaql2;

import static java.util.Map.entry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import org.bson.Document;
import org.apache.commons.lang3.StringUtils;

public class Model {
	public static final char STR_TYPE=1;
	public static final char NUM_TYPE=2;
	public static final char BOOL_TYPE=4;
	public static final char ARRAY_TYPE=8;
	public static final char ANY_TYPE=(char)(STR_TYPE|NUM_TYPE|BOOL_TYPE);
	
	public int unwindsNum;
	public ArrayList<String> paths;
	public Object pathsForConcat;
	public Document contains;

	//Fields of each table and their data types
	public static HashMap<String,Character> tableFields=new HashMap<String,Character>(Map.ofEntries(
			entry("Service.title",STR_TYPE),
			entry("Service.id",STR_TYPE),
			entry("Service.description",STR_TYPE),
			entry("Service.openapiVersion",STR_TYPE),
			entry("Service.version",STR_TYPE),
			entry("Service.jsonSchemaDialect",STR_TYPE),
			entry("Service.termsOfService",STR_TYPE),
			entry("Service.contactName",STR_TYPE),
			entry("Service.contactUrl",STR_TYPE),
			entry("Service.contactEmail",STR_TYPE),
			entry("Service.licenseName",STR_TYPE),
			entry("Service.licenseUrl",STR_TYPE),
			entry("Service.extDocsDescription",STR_TYPE),
			entry("Service.extDocsUrl",STR_TYPE),
			entry("Service.summary",STR_TYPE),
			entry("Tag.name",STR_TYPE),
			entry("Tag.description",STR_TYPE),
			entry("Tag.extDocsDescription",STR_TYPE),
			entry("Tag.extDocsUrl",STR_TYPE),
			entry("Request.path",STR_TYPE),
			entry("Request.method",STR_TYPE),
			entry("Request.contentType",STR_TYPE),
			entry("Request.bodyRequired",BOOL_TYPE),
			entry("Request.summary",STR_TYPE),
			entry("Request.description",STR_TYPE),
			entry("Request.bodyDescription",STR_TYPE),
			entry("Request.operationId",STR_TYPE),
			entry("Request.deprecated",BOOL_TYPE),
			entry("Request.extDocsDescription",STR_TYPE),
			entry("Request.extDocsUrl",STR_TYPE),
			entry("Request.tags",(char)(STR_TYPE|ARRAY_TYPE)),
			entry("Request.x-operationType",STR_TYPE),
			entry("Server.url",STR_TYPE),
			entry("Server.description",STR_TYPE),
			entry("ServerVariable.name",STR_TYPE),
			entry("ServerVariable.enum",(char)(ANY_TYPE|ARRAY_TYPE)),
			entry("ServerVariable.default",ANY_TYPE),
			entry("ServerVariable.description",STR_TYPE),
			entry("Example.name",STR_TYPE),
			entry("Example.summary",STR_TYPE),
			entry("Example.description",STR_TYPE),
			entry("Example.value",ANY_TYPE),
			entry("Example.externalValue",ANY_TYPE),
			entry("Parameter.name",STR_TYPE),
			entry("Parameter.in",STR_TYPE),
			entry("Parameter.description",STR_TYPE),
			entry("Parameter.required",BOOL_TYPE),
			entry("Parameter.deprecated",BOOL_TYPE),
			entry("Parameter.allowEmptyValue",BOOL_TYPE),
			entry("Parameter.allowReserved",BOOL_TYPE),
			entry("Parameter.contentType",STR_TYPE),
			entry("Parameter.style",STR_TYPE),
			entry("Parameter.explode",BOOL_TYPE),
			entry("Security.name",STR_TYPE),
			entry("Security.type",STR_TYPE),
			entry("Security.description",STR_TYPE),
			entry("Security.apiKeyName",STR_TYPE),
			entry("Security.apiKeyIn",STR_TYPE),
			entry("Security.httpScheme",STR_TYPE),
			entry("Security.httpBearerFormat",STR_TYPE),
			entry("Security.openIdConnectUrl",STR_TYPE),
			entry("Security.oauth2ImplAuthUrl",STR_TYPE),
			entry("Security.oauth2ImplRefreshUrl",STR_TYPE),
			entry("Security.oauth2PassTokenUrl",STR_TYPE),
			entry("Security.oauth2PassRefreshUrl",STR_TYPE),
			entry("Security.oauth2ClientCredTokenUrl",STR_TYPE),
			entry("Security.oauth2ClientCredRefreshUrl",STR_TYPE),
			entry("Security.oauth2CodeAuthUrl",STR_TYPE),
			entry("Security.oauth2CodeTokenUrl",STR_TYPE),
			entry("Security.oauth2CodeRefreshUrl",STR_TYPE),
			entry("SecurityScope.name",STR_TYPE),
			entry("SecurityScope.description",STR_TYPE),
			entry("Response.statusCode",(char)(NUM_TYPE|STR_TYPE)),
			entry("Response.contentType",STR_TYPE),
			entry("Response.description",STR_TYPE),
			entry("Link.name",STR_TYPE),	
			entry("Link.operationRef",STR_TYPE),	
			entry("Link.operationId",STR_TYPE),	
			entry("Link.requestBody",STR_TYPE),	
			entry("Link.description",STR_TYPE),	
			entry("Link.url",STR_TYPE),	
			entry("Link.serverDescription",STR_TYPE),	
			entry("LinkParameter.name",STR_TYPE),
			entry("LinkParameter.value",ANY_TYPE),
			entry("Header.name",STR_TYPE),
			entry("Header.description",STR_TYPE),
			entry("Header.required",BOOL_TYPE),
			entry("Header.deprecated",BOOL_TYPE),
			entry("Header.allowEmptyValue",BOOL_TYPE),
			entry("Header.contentType",STR_TYPE),
			entry("Header.style",STR_TYPE),
			entry("Header.explode",BOOL_TYPE),
			entry("Schema.title",STR_TYPE),
			entry("Schema.description",STR_TYPE),
			entry("Schema.required",STR_TYPE),
			entry("Schema.multipleOf",NUM_TYPE),
			entry("Schema.maximum",NUM_TYPE),
			entry("Schema.exclusiveMaximum",NUM_TYPE),
			entry("Schema.minimum",NUM_TYPE),
			entry("Schema.exclusiveMinimum",NUM_TYPE),
			entry("Schema.maxLength",NUM_TYPE),
			entry("Schema.minLength",NUM_TYPE),
			entry("Schema.pattern",STR_TYPE),
			entry("Schema.maxItems",NUM_TYPE),
			entry("Schema.minItems",NUM_TYPE),
			entry("Schema.uniqueItems",BOOL_TYPE),
			entry("Schema.maxProperties",NUM_TYPE),
			entry("Schema.minProperties",NUM_TYPE),
			entry("Schema.type",STR_TYPE),
			entry("Schema.format",STR_TYPE),
			entry("Schema.default",ANY_TYPE),
			entry("Schema.const",ANY_TYPE),
			entry("Schema.enum",(char)(ANY_TYPE|ARRAY_TYPE)),
			entry("Schema.examples",ANY_TYPE),
			entry("Schema.readOnly",BOOL_TYPE),
			entry("Schema.writeOnly",BOOL_TYPE),
			entry("Schema.deprecated",BOOL_TYPE),
			entry("Schema.contentMediaType",STR_TYPE),
			entry("Schema.contentEncoding",STR_TYPE),
			entry("Schema.x-refersTo",STR_TYPE),
			entry("Schema.x-kindOf",STR_TYPE),
			entry("Schema.x-collectionOn",STR_TYPE),
			entry("Schema.extDocsDescription",STR_TYPE),
			entry("Schema.extDocsUrl",STR_TYPE),
			entry("Item.title",STR_TYPE),
			entry("Item.description",STR_TYPE),
			entry("Item.required",STR_TYPE),
			entry("Item.multipleOf",NUM_TYPE),
			entry("Item.maximum",NUM_TYPE),
			entry("Item.exclusiveMaximum",NUM_TYPE),
			entry("Item.minimum",NUM_TYPE),
			entry("Item.exclusiveMinimum",NUM_TYPE),
			entry("Item.maxLength",NUM_TYPE),
			entry("Item.minLength",NUM_TYPE),
			entry("Item.pattern",STR_TYPE),
			entry("Item.maxItems",NUM_TYPE),
			entry("Item.minItems",NUM_TYPE),
			entry("Item.uniqueItems",BOOL_TYPE),
			entry("Item.maxProperties",NUM_TYPE),
			entry("Item.minProperties",NUM_TYPE),
			entry("Item.type",STR_TYPE),
			entry("Item.format",STR_TYPE),
			entry("Item.default",ANY_TYPE),
			entry("Item.const",ANY_TYPE),
			entry("Item.enum",(char)(ANY_TYPE|ARRAY_TYPE)),
			entry("Item.examples",ANY_TYPE),
			entry("Item.readOnly",BOOL_TYPE),
			entry("Item.writeOnly",BOOL_TYPE),
			entry("Item.deprecated",BOOL_TYPE),
			entry("Item.contentMediaType",STR_TYPE),
			entry("Item.contentEncoding",STR_TYPE),
			entry("Item.x-refersTo",STR_TYPE),
			entry("Item.x-kindOf",STR_TYPE),
			entry("Item.x-collectionOn",STR_TYPE),
			entry("Property.name",STR_TYPE),
			entry("Property.title",STR_TYPE),
			entry("Property.description",STR_TYPE),
			entry("Property.required",STR_TYPE),
			entry("Property.multipleOf",NUM_TYPE),
			entry("Property.maximum",NUM_TYPE),
			entry("Property.exclusiveMaximum",NUM_TYPE),
			entry("Property.minimum",NUM_TYPE),
			entry("Property.exclusiveMinimum",NUM_TYPE),
			entry("Property.maxLength",NUM_TYPE),
			entry("Property.minLength",NUM_TYPE),
			entry("Property.pattern",STR_TYPE),
			entry("Property.maxItems",NUM_TYPE),
			entry("Property.minItems",NUM_TYPE),
			entry("Property.uniqueItems",BOOL_TYPE),
			entry("Property.maxProperties",NUM_TYPE),
			entry("Property.minProperties",NUM_TYPE),
			entry("Property.type",STR_TYPE),
			entry("Property.format",STR_TYPE),
			entry("Property.default",ANY_TYPE),
			entry("Property.const",ANY_TYPE),
			entry("Property.enum",(char)(ANY_TYPE|ARRAY_TYPE)),
			entry("Property.contentType",STR_TYPE),
			entry("Property.allowReserved",BOOL_TYPE),
			entry("Property.style",STR_TYPE),
			entry("Property.explode",BOOL_TYPE),
			entry("Property.examples",ANY_TYPE),
			entry("Property.readOnly",BOOL_TYPE),
			entry("Property.writeOnly",BOOL_TYPE),
			entry("Property.deprecated",BOOL_TYPE),
			entry("Property.contentMediaType",STR_TYPE),
			entry("Property.contentEncoding",STR_TYPE),
			entry("Property.x-refersTo",STR_TYPE),
			entry("Property.x-kindOf",STR_TYPE),
			entry("Property.x-collectionOn",STR_TYPE),
			entry("Property.xmlName",STR_TYPE),
			entry("Property.xmlNamespace",STR_TYPE),
			entry("Property.xmlPrefix",STR_TYPE),
			entry("Property.xmlAttribute",BOOL_TYPE),
			entry("Property.xmlWrapped",BOOL_TYPE),
			entry("Callback.name",STR_TYPE),
			entry("Callback.path",STR_TYPE),
			entry("Callback.method",STR_TYPE),
			entry("Callback.contentType",STR_TYPE),
			entry("Callback.bodyRequired",BOOL_TYPE),
			entry("Callback.summary",STR_TYPE),
			entry("Callback.description",STR_TYPE),
			entry("Callback.bodyDescription",STR_TYPE),
			entry("Callback.operationId",STR_TYPE),
			entry("Callback.deprecated",BOOL_TYPE),
			entry("Callback.extDocsDescription",STR_TYPE),
			entry("Callback.extDocsUrl",STR_TYPE),
			entry("Callback.tags",(char)(STR_TYPE|ARRAY_TYPE)),
			entry("Callback.x-operationType",STR_TYPE),
			entry("Webhook.name",STR_TYPE),
			entry("Webhook.method",STR_TYPE),
			entry("Webhook.contentType",STR_TYPE),
			entry("Webhook.bodyRequired",BOOL_TYPE),
			entry("Webhook.summary",STR_TYPE),
			entry("Webhook.description",STR_TYPE),
			entry("Webhook.bodyDescription",STR_TYPE),
			entry("Webhook.operationId",STR_TYPE),
			entry("Webhook.deprecated",BOOL_TYPE),
			entry("Webhook.extDocsDescription",STR_TYPE),
			entry("Webhook.extDocsUrl",STR_TYPE),
			entry("Webhook.x-operationType",STR_TYPE),
			entry("Webhook.tags",(char)(STR_TYPE|ARRAY_TYPE))
	));	
	
	//Allowed joins between two tables
	public static HashSet<String> tablePairs = new HashSet<String>(Arrays.asList(
			"Service.Request",
			"Service.Tag",
			"Service.Webhook",
			"Request.Server",
			"Server.ServerVariable",
			"Request.Callback",
			"Request.Schema",
			"Schema.Property",
			"Schema.Item",
			"Property.Property",
			"Property.Item",
			"Item.Property",
			"Item.Item",
			"Property.Header",
			"Request.Example",
			"Request.Parameter",
			"Parameter.Schema",
			"Parameter.Example",
			"Request.Security",
			"Security.SecurityScope",
			"Request.Response",
			"Response.Link",
			"Link.ServerVariable",
			"Link.LinkParameter",
			"Response.Header",
			"Header.Schema",
			"Header.Example",
			"Response.Schema",
			"Response.Example",
			"Callback.Server",
			"Callback.Schema",
			"Callback.Example",
			"Callback.Parameter",
			"Callback.Security",
			"Callback.Response",
			"Webhook.Server",
			"Webhook.Schema",
			"Webhook.Example",
			"Webhook.Parameter",
			"Webhook.Security",
			"Webhook.Response",
			"Tag.Schema"
	));

	//Information about each table calculated once at startup and stored for better performance
	public static HashMap<String,Model> tables=new HashMap<String,Model>(Map.ofEntries(
		entry("Service",new Model("Service")),
		entry("Tag",new Model("Tag")),
		entry("Request",new Model("Request")),
		entry("Server",new Model("Server")),
		entry("ServerVariable",new Model("ServerVariable")),
		entry("Example",new Model("Example")),
		entry("Parameter",new Model("Parameter")),
		entry("Security",new Model("Security")),
		entry("SecurityScope",new Model("SecurityScope")),
		entry("Response",new Model("Response")),
		entry("Link",new Model("Link")),
		entry("LinkParameter",new Model("LinkParameter")),
		entry("Header",new Model("Header")),
		entry("Schema",new Model("Schema")),
		entry("Item",new Model("Item")),
		entry("Property",new Model("Property")),
		entry("Callback",new Model("Callback")),
		entry("Webhook",new Model("Webhook"))
	));
	
	//JS function for concatenating all Property arrays during querying in MongoDB
	public static String propFunc="function rec(obj){"
										+ "  var res=[];"
										+ "  if(obj.hasOwnProperty(\"Property\")){"
										+ "    for(var i=0; i<obj.Property.length; i++){"
										+ "      res.push(obj.Property[i]);"
										+ "      res=res.concat(rec(obj.Property[i]));"
										+ "    }"
										+ "  }"
										+ "  if(obj.hasOwnProperty(\"Item\")){"
										+ "    for(var j=0; j<obj.Item.length; j++){"
										+ "      res=res.concat(rec(obj.Item[j]));"
										+ "    }"
										+ "  }"
										+ "  return res;"
										+ "}";

	//JS function for concatenating all Item arrays during querying in MongoDB
	public static String itemFunc="function rec(obj){"
										+ "  var res=[];"
										+ "  if(obj.hasOwnProperty(\"Item\")){"
										+ "    for(var i=0; i<obj.Item.length; i++){"
										+ "      res.push(obj.Item[i]);"
										+ "      res=res.concat(rec(obj.Item[i]));"
										+ "    }"
										+ "  }"
										+ "  if(obj.hasOwnProperty(\"Property\")){"
										+ "    for(var j=0; j<obj.Property.length; j++){"
										+ "      res=res.concat(rec(obj.Property[j]));"
										+ "    }"
										+ "  }"
										+ "  return res;"
										+ "}";

	//paths to index in metadata objects
	public static String[] indexPaths = { 
		"Service.Request.method",
		"Service.Request.path",
		"Service.Request.contentType",
		"Service.Request.x-operationType",
		"Service.Request.Parameter.name",
		"Service.Request.Response.statusCode",
		"Service.Request.Response.contentType",
		"Service.Request.Response.Header.name",
		"Service.Request.Security.type",
		"Service.Request.Schema.x-refersTo",
		"Service.Request.Parameter.Schema.x-refersTo",
		"Service.Request.Response.x-refersTo",
		"Service.Request.Response.Header.x-refersTo",
		"Service.Request.Schema.x-kindOf",
		"Service.Request.Parameter.Schema.x-kindOf",
		"Service.Request.Response.x-kindOf",
		"Service.Request.Response.Header.x-kindOf",
		"Service.Request.Schema.type",
		"Service.Request.Parameter.Schema.type",
		"Service.Request.Response.type",
		"Service.Request.Response.Header.type",
		"Service.Request.Schema.Property.name",
		"Service.Request.Parameter.Schema.Property.name",
		"Service.Request.Response.Property.name",
		"Service.Request.Response.Header.Property.name",
		"Service.Request.Schema.Property.x-refersTo",
		"Service.Request.Parameter.Schema.Property.x-refersTo",
		"Service.Request.Response.Property.x-refersTo",
		"Service.Request.Response.Header.Property.x-refersTo",
		"Service.Request.Schema.Property.x-kindOf",
		"Service.Request.Parameter.Schema.Property.x-kindOf",
		"Service.Request.Response.Property.x-kindOf",
		"Service.Request.Response.Header.Property.x-kindOf",
		"Service.Request.Schema.Property.type",
		"Service.Request.Parameter.Schema.Property.type",
		"Service.Request.Response.Property.type",
		"Service.Request.Response.Header.Property.type",
		"Service.Request.Schema.Item.x-refersTo",
		"Service.Request.Parameter.Schema.Item.x-refersTo",
		"Service.Request.Response.Item.x-refersTo",
		"Service.Request.Response.Header.Item.x-refersTo",
		"Service.Request.Schema.Item.x-kindOf",
		"Service.Request.Parameter.Schema.Item.x-kindOf",
		"Service.Request.Response.Item.x-kindOf",
		"Service.Request.Response.Header.Item.x-kindOf",
		"Service.Request.Schema.Item.type",
		"Service.Request.Parameter.Schema.Item.type",
		"Service.Request.Response.Item.type",
		"Service.Request.Response.Header.Item.type"
	};

	/*
	* Calculates info for each table
	*/
	public Model(String tableName) {
		if(tableName.equals("Property")||tableName.equals("Item")){
			paths=new ArrayList<String>();
		}else {
			paths=getPaths(tableName,0);
		}
		
		unwindsNum=0;
		if(tableName.equals("Item")||tableName.equals("Property")) {
			for(String p : getPaths("Schema",0)) {
				int tmp=StringUtils.countMatches(p, '.')+1;
				if(tmp>unwindsNum) {
					unwindsNum=tmp;
				}
			}
		}else {
			for(String p : paths) {
				int tmp=StringUtils.countMatches(p, '.')+1;
				if(tmp>unwindsNum) {
					unwindsNum=tmp;
				}
			}
		}
		
		contains=new Document();
		for(String pair : tablePairs){
			if(pair.startsWith(tableName+".")){
				contains.append(pair.substring(pair.indexOf(".")+1),0);
			}
		}
		if(contains.size()==0){
			contains=null;
		}
		
		ArrayList<String> newlist=new ArrayList<String>();
		if(tableName.equals("Item")||tableName.equals("Property")){
			for(String s : getPaths("Schema",0)){
				newlist.add("$"+s+"Schema");
			}
		}else {
			for(String s : paths){
				newlist.add("$"+s+tableName);
			}
		}
		if(newlist.size()==1){
			pathsForConcat=newlist.get(0);
			return;
		}
		pathsForConcat=new Document("$concatArrays",newlist);
	}
	
	/*
	* Finds all locations of an array in a metadata object
	*/
	static ArrayList<String> getPaths(String table,int propNum){
		if(propNum==2) {
			return null;
		}
		ArrayList<String> result=new ArrayList<String>();
		for(String s : Model.tablePairs) {
			if(s.endsWith("."+table)) {
				String parent=s.substring(0,s.indexOf("."));
				if(parent.equals("Item")) {
					continue;
				}
				ArrayList<String> tmp;
				if(parent.equals("Property")) {
					tmp=getPaths(parent,propNum+1);
				}else {
					tmp=getPaths(parent,propNum);
				}
				if(tmp==null) {
					continue;
				}
				for(String t : tmp) {
					result.add(t+parent+".");
				}
			}
		}
		if(result.size()==0) {
			result.add("");
		}
		return result;
	}
}
