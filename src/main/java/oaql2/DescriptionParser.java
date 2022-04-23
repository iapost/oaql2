package oaql2;

import org.json.JSONObject;
import org.json.JSONArray;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.sun.net.httpserver.HttpExchange;
import java.util.Scanner;
import org.bson.Document;

public class DescriptionParser{
	private JSONObject description;
	
	private static String[] httpMethods = {"get","put","post","delete","options","head","patch","trace"};
	
	//keys in Schema objects that need special parsing
	private static String[] schemaKeys= {"properties","patternProperties","additionalProperties",
			"unevaluatedProperties","prefixItems","items","contains","unevaluatedItems",
			"if","then","else","dependentSchemas","allOf","anyOf","oneOf","not","discriminator","x-mapsTo","externalDocs","xml"};
	
	/*
	* Parses the new description and inserts it into the database
	*/
	public void handleExchange(String mongoUrl, HttpExchange ex) throws Exception {
		//only accept POST requests
		if(!ex.getRequestMethod().equals("POST")){
			throw new Exception("Only supporting POST requests");
		}
		
		//get description as string from request body
		Scanner s=new Scanner(ex.getRequestBody());
		String desc=s.useDelimiter("\\Z").next();
		s.close();
		
		//parse description to get metadata object
		JSONObject obj = this.parse(desc);
		
		//insert description into "originalDescriptions" collection of Mongo, get generated id and put it in metadata object
		MongoClient cli =MongoClients.create(mongoUrl);
		MongoDatabase db=cli.getDatabase(Server.dbName);
		MongoCollection<Document> coll=db.getCollection(Server.originalDescriptionsCollectionName);
		obj.getJSONArray("Service").getJSONObject(0).put("id",coll.insertOne(Document.parse(desc)).getInsertedId().asObjectId().getValue().toString());
		
		//insert metadata object into "metadataCollection" collection of Mongo
		coll = db.getCollection(Server.collectionName);
		coll.insertOne(Document.parse(obj.toString()));
		cli.close();
		
		//return 204 code
		ex.sendResponseHeaders(204,-1);
		ex.close();
	}
	
	/*
	* Copies each field from src to dest under the same key if they exist in src
	*/
	static void copyField(JSONObject dest, JSONObject src, String... key) {
		for(String k : key) {
			copyField(dest,k,src,k);
		}
	}
	
	/*
	* If value under srcKey exists in src, it is copied into dest under destKey
	*/
	static void copyField(JSONObject dest, String destKey, JSONObject src, String srcKey) {
		if(src.has(srcKey)) {
			dest.put(destKey, src.get(srcKey));
		}
	}
	
	/*
	* Parses a description and returns a metadata object
	*/
	public JSONObject parse(String desc)throws Exception{
		description=new JSONObject(desc);
		JSONObject parsedDescription=new JSONObject();
		
		//copy fields from OpenAPI and Info objects
		JSONObject service=new JSONObject();
		service.put("openapiVersion", description.getString("openapi"));
		copyField(service,description,"jsonSchemaDialect");
		parseXProperties(service,description);
		JSONObject info = description.getJSONObject("info");
		copyField(service,info,"title","version","description","summary","termsOfService");
		parseXProperties(service,info);
		
		//copy fields from Contact object
		if(info.has("contact")) {
			JSONObject cont=info.getJSONObject("contact");
			copyField(service,"contactName",cont,"name");
			copyField(service,"contactUrl",cont,"url");
			copyField(service,"contactEmail",cont,"email");
			parseXProperties(service,cont);
		}
		
		//copy fields from License object
		if(info.has("license")) {
			JSONObject lic=info.getJSONObject("license");
			copyField(service,"licenseName",lic,"name");
			copyField(service,"licenseUrl",lic,"url");
			copyField(service,"licenseIdentifier",lic,"identifier");
			parseXProperties(service,lic);
		}
		
		//copy fields from External Documentation object
		parseExternalDocs(service,description.optJSONObject("externalDocs"));
		
		//get servers globally declared to use while parsing requests
		JSONArray servers=null;
		if(description.has("servers")) {
			servers=parseServers(description.getJSONArray("servers"));
		}
		
		//get security globally declared to use while parsing requests
		JSONArray sec=null;
		if(description.has("security")) {
			sec=parseSecurityRequirements(description.getJSONArray("security"));
		}
		
		//parse requests
		if(description.has("paths")) {
			service.put("Request",parseRequests(description.getJSONObject("paths"),sec,servers));
		}
		
		//parse tags
		if(description.has("tags")) {
			service.put("Tag", parseTags(description.getJSONArray("tags")));
		}
		
		//parse webhooks
		if(description.has("webhooks")) {
			service.put("Webhook", parseWebhooks(description.getJSONObject("webhooks")));
		}
		
		//put object in an array under Service and return
		parsedDescription.put("Service",new JSONArray().put(service));
		return parsedDescription;
	}
	
	/*
	* Copies all fields whose key begins with "x-" from source to dest
	*/
	private void parseXProperties(JSONObject dest, JSONObject source) {
		for(String k: source.keySet()) {
			if(k.startsWith("x-")) {
				//if key does not exist in dest, simply put the value
				if(!dest.has(k)) {
					dest.put(k, source.get(k));
					continue;
				}
				
				//if key already exists there are many cases
				Object sourceobj=source.get(k);
				Object destobj=dest.get(k);
				if(destobj.getClass()==JSONArray.class) {
					//if value in dest is an array
					JSONArray destarr=(JSONArray)destobj;
					if(sourceobj.getClass()==JSONArray.class) {
						//if value in source is also an array
						//append the items from the array in source to the array in dest
						destarr.putAll((JSONArray)sourceobj);
					}else {
						//append value from source to the array in dest
						destarr.put(sourceobj);
					}
				}else if(sourceobj.getClass()==JSONArray.class) {
					//if value in source is an array
					//append value from dest to the array in source and copy the array into dest
					dest.put(k, new JSONArray(((JSONArray)sourceobj).toString()).put(destobj));
				}else {
					//put in dest an array containing both values
					dest.put(k,new JSONArray().put(destobj).put(sourceobj));
				}
			}
		}
	}
	
	/*
	* Returns array corresponding to Tag table
	*/
	private JSONArray parseTags(JSONArray tags)throws Exception{
		JSONArray result=new JSONArray();
		for(int i=0; i<tags.length(); i++) {
			JSONObject tag=tags.getJSONObject(i);
			
			//put name and description in new object
			JSONObject newtag=new JSONObject().put("name",tag.getString("name"));
			copyField(newtag,tag,"description");
			
			//if x-onResource exists, find referenced schema and parse it
			//also remove x-onResource so it is not copied by parseXProperties later
			if(tag.has("x-onResource")){
				newtag.put("Schema", parseSchema(resolveStringRef((String)(tag.remove("x-onResource"))),null,null));
			}
			
			//copy other fields
			parseExternalDocs(newtag,tag.optJSONObject("externalDocs"));
			parseXProperties(newtag,tag);
			result.put(newtag);
		}
		return result;
	}
	
	/*
	* Returns array corresponding to Security table
	*/
	private JSONArray parseSecurityRequirements(JSONArray reqs)throws Exception{
		JSONArray result=new JSONArray();
		for(int i=0; i<reqs.length(); i++) {
			JSONObject obj=reqs.getJSONObject(i);
			
			//store empty security requirements
			if(obj.isEmpty()) {
				result.put(obj);
				continue;
			}
			
			//get name and scopes of Security Requirement object
			String name=obj.keys().next();
			JSONObject newobj=new JSONObject().put("name", name); 
			
			//store required scopes in an array
			JSONArray scopes=obj.getJSONArray(name);
			JSONArray secScope=new JSONArray();
			for(int j=0; j<scopes.length(); j++) {
				secScope.put(new JSONObject().put("name", scopes.getString(j)));
			}
			
			//find Security Scheme object containing information about this security requirement
			JSONObject secScheme=resolveRef(description.getJSONObject("components").getJSONObject("securitySchemes").getJSONObject(name));
			
			//copy the various fields from Security Scheme object
			copyField(newobj,secScheme,"type","description","openIdConnectUrl");
			copyField(newobj,"apiKeyName",secScheme,"name");
			copyField(newobj,"apiKeyIn",secScheme,"in");
			copyField(newobj,"httpScheme",secScheme,"scheme");
			copyField(newobj,"httpBearerFormat",secScheme,"bearerFormat");
			parseXProperties(newobj,secScheme);
			
			//copy fields from Oauth Flows object
			if(secScheme.has("flows")) {
				JSONObject allscopes=null;
				JSONObject flows=secScheme.getJSONObject("flows");
				parseXProperties(newobj,flows);
				
				//only one of the four objects will exist
				if(flows.has("implicit")) {
					JSONObject impl=flows.getJSONObject("implicit");
					newobj.put("oauth2ImplAuthUrl", impl.getString("authorizationUrl"));
					copyField(newobj,"oauth2ImplRefreshUrl",impl,"refreshUrl");
					parseXProperties(newobj,impl);
					allscopes=impl.getJSONObject("scopes");
				}
				if(flows.has("password")) {
					JSONObject pass=flows.getJSONObject("password");
					newobj.put("oauth2PassTokenUrl", pass.getString("tokenUrl"));
					copyField(newobj,"oauth2PassRefreshUrl",pass,"refreshUrl");
					parseXProperties(newobj,pass);
					allscopes=pass.getJSONObject("scopes");
				}
				if(flows.has("clientCredentials")) {
					JSONObject cred=flows.getJSONObject("clientCredentials");
					newobj.put("oauth2ClientCredTokenUrl", cred.getString("tokenUrl"));
					copyField(newobj,"oauth2ClientCredRefreshUrl",cred,"refreshUrl");
					parseXProperties(newobj,cred);
					allscopes=cred.getJSONObject("scopes");
				}
				if(flows.has("authorizationCode")) {
					JSONObject code=flows.getJSONObject("authorizationCode");
					newobj.put("oauth2CodeAuthUrl", code.getString("authorizationUrl"));
					newobj.put("oauth2CodeTokenUrl", code.getString("tokenUrl"));
					copyField(newobj,"oauth2CodeRefreshUrl",code,"refreshUrl");
					parseXProperties(newobj,code);
					allscopes=code.getJSONObject("scopes");
				}
				
				//copy description for each of the security scopes required
				if(allscopes!=null) {
					for(int j=0; j<secScope.length(); j++) {
						JSONObject tmp=secScope.getJSONObject(j);
						Object tmpval= allscopes.opt(tmp.getString("name"));
						if(tmpval!=null) {
							tmp.put("description",tmpval);
						}
					}
					newobj.put("SecurityScope", secScope);
				}
			}
			result.put(newobj);
		}
		return result;
	}
	
	/*
	* Returns the object that a string reference points to
	*/
	private JSONObject resolveStringRef(String path)throws Exception{
		String[] ref=path.split("/");
		
		//only resolve references to objects inside this description
		if(!ref[0].equals("#")) {
			throw new Exception("Reference outside this description: "+path);
		}
		
		//follow path inside description
		JSONObject counter=description;
		for(int i=1; i<ref.length; i++) {
			counter=counter.optJSONObject(ref[i]);
			if(counter==null) {
				throw new Exception("Reference path error: "+path);
			}
		}
		
		//return copy of referenced object
		return new JSONObject(counter.toString());
	}
	
	/*
	* Returns the object referenced by a Reference object
	*/
	private JSONObject resolveRef(JSONObject obj) throws Exception{
		//if obj is not a Reference object return itself
		if(obj==null || !obj.has("$ref")) {
			return obj;
		}
		
		//find referenced object
		JSONObject result=resolveStringRef(obj.getString("$ref"));
		
		//overwrite description and summary fields
		copyField(result,obj,"description","summary");
		
		//keep resolving references recursively 
		return resolveRef(result);
	}
	
	/*
	* Returns array corresponding to Header table
	*/
	private JSONArray parseHeaders(JSONObject headers) throws Exception{
		JSONArray result=new JSONArray();
		for(String headername:headers.keySet()) {
			//copy fields to new object
			JSONObject newheader=new JSONObject().put("name", headername);
			JSONObject header=resolveRef(headers.getJSONObject(headername));
			copyField(newheader,header,"description");
			newheader.put("required",header.optBoolean("required"));
			newheader.put("deprecated",header.optBoolean("deprecated"));
			newheader.put("allowEmptyValue",header.optBoolean("allowEmptyValue"));
			newheader.put("style", header.optString("style","simple"));
			newheader.put("explode",header.optBoolean("explode"));
			
			//parse Schema or Media Type object
			if(header.has("schema")) {
				newheader.put("Schema", parseSchema(header.getJSONObject("schema"),null,null));
			}else if(header.has("content")) {
				JSONObject tmp=parseContent(header.getJSONObject("content")).getJSONObject(0);
				copyField(newheader,tmp,"contentType","Schema","Example");
				parseXProperties(newheader,tmp);
			}
			
			//parse examples
			if(header.has("example") || header.has("examples")) {
				if(newheader.has("Example")) {
					newheader.getJSONArray("Example").putAll(parseExamples(header.opt("example"), header.optJSONObject("examples")));
				}else {
					newheader.put("Example", parseExamples(header.opt("example"), header.optJSONObject("examples")));
				}
			}
			
			parseXProperties(newheader,header);
			result.put(newheader);
		}
		return result;
	}
	
	/*
	* Returns array corresponding to Link table
	*/
	private JSONArray parseLinks(JSONObject links) throws Exception{
		JSONArray result=new JSONArray();
		for(String lname : links.keySet()) {
			JSONObject link=resolveRef(links.getJSONObject(lname));

			//copy fields
			JSONObject newlink=new JSONObject().put("name", lname);
			copyField(newlink,link,"operationRef","operationId","description");
			if(link.has("server")) {
				JSONObject serv=parseServer(link.getJSONObject("server"));
				copyField(newlink,serv,"url","ServerVariable");
				copyField(newlink,"serverDescription",serv,"description");
			}

			//create array corresponding to LinkParameter table
			if(link.has("parameters")) {
				JSONObject params=link.getJSONObject("parameters");
				for(String pname : params.keySet()) {
					newlink.append("LinkParameter", new JSONObject().put("name", pname).put("value", params.get(pname)));
				}
			}

			//copy rest fields
			copyField(newlink,link,"requestBody");
			parseXProperties(newlink,link);
			result.put(newlink);
		}
		return result;
	}
	
	/*
	* Returns array corresponding to Response table
	*/
	private JSONArray parseResponses(JSONObject responses)throws Exception{
		JSONArray resultarr=new JSONArray();
		for(String code: responses.keySet()) {
			JSONObject result=new JSONObject();

			//copy status code
			if(code.equals("default") || code.equals("1XX") || code.equals("2XX") || code.equals("3XX") || code.equals("4XX") || code.equals("5XX")) {
				//copy code as string if it is a range or "default"
				result.put("statusCode", code);
			}else if(code.startsWith("x-")){ //code may be an extension property
				result.put(code,responses.get(code));
				continue;
			}else {
				//copy code as int
				result.put("statusCode", Integer.parseInt(code));
			}

			JSONObject r=resolveRef(responses.getJSONObject(code));
			copyField(result,r,"description");

			//parse headers
			if(r.has("headers")) {
				result.put("Header", parseHeaders(r.getJSONObject("headers")));
			}

			//parse links
			if(r.has("links")) {
				result.put("Link", parseLinks(r.getJSONObject("links")));
			}

			parseXProperties(result,r);
			
			//parse Media Type object 
			if(r.has("content")) {
				JSONArray content= parseContent(r.getJSONObject("content"));
				String base=result.toString();

				//create new response for each contentType
				for(int i=1; i<content.length(); i++) {
					JSONObject newresponse=new JSONObject(base);
					JSONObject c=content.getJSONObject(i);
					copyField(newresponse,c,"contentType","Schema","Example");
					parseXProperties(newresponse,c);
					resultarr.put(newresponse);
				}
				JSONObject c=content.getJSONObject(0);
				copyField(result,c,"contentType","Schema","Example");
				parseXProperties(result,c);
			}
			resultarr.put(result);
		}
		return resultarr;
	}
	
	/*
	* Returns array corresponding to Example table
	*/
	private JSONArray parseExamples(Object example,JSONObject examples)throws Exception{
		JSONArray result=new JSONArray();

		//parse "example" field
		if(example!=null) {
			result.put(new JSONObject().put("value",example));
		}

		//parse Example object
		if(examples!=null) {
			for(String k : examples.keySet()) {
				result.put(new JSONObject(resolveRef(examples.getJSONObject(k)).toString()).put("name", k));
			}
		}

		return result;
	}
	
	/*
	* Returns array corresponding to Schema, Property or Item table
	*/
	private JSONArray parseSchema(JSONObject schema,JSONObject encoding, String name)throws Exception{
		//encoding and name might be null
		schema=resolveRef(schema);
		JSONArray resultArray=new JSONArray();

		//copy all fields to new object except those needing additional parsing (found in "schemaKeys")
		JSONObject baseSchema=new JSONObject(schema.toString());
		for(String s : schemaKeys) {
			baseSchema.remove(s);
		}

		//add property name if not null
		if(name!=null) {
			baseSchema.put("name", name);
		}

		//parse XML object
		if(schema.has("xml")){
			JSONObject xml=schema.getJSONObject("xml");
			copyField(baseSchema,"xmlName",xml,"name");
			copyField(baseSchema,"xmlNamespace",xml,"namespace");
			copyField(baseSchema,"xmlPrefix",xml,"prefix");
			copyField(baseSchema,"xmlWrapped",xml,"wrapped");
			baseSchema.put("xmlAttribute",xml.optBoolean("namespace"));
		}

		//parse External Documentation object
		if(schema.has("externalDocs")){
			parseExternalDocs(baseSchema,schema.getJSONObject("externalDocs"));
		}

		//copy x-refersTo and x-kindOf from referenced schema to this one
		if(schema.has("x-mapsTo")) {
			copyField(baseSchema,resolveStringRef(schema.getString("x-mapsTo")),"x-refersTo","x-kindOf");
		}

		resultArray.put(baseSchema);
		
		//parse properties under "properties" keyword
		if(schema.has("properties")) {
			JSONObject properties=schema.getJSONObject("properties");
			for(String key : properties.keySet()) {
				JSONArray newproperty=parseSchema(properties.getJSONObject(key),null,key);
				resultArray=merge(resultArray,newproperty,"Property");
			}
		}

		//parse properties under "patternProperties" keyword
		if(schema.has("patternProperties")) {
			JSONObject properties=schema.getJSONObject("patternProperties");
			for(String key : properties.keySet()) {
				JSONArray newproperty=parseSchema(properties.getJSONObject(key),null,key);
				resultArray=merge(resultArray,newproperty,"Property");
			}
		}

		//parse property under "additionalProperties" keyword
		JSONObject addProperties=schema.optJSONObject("additionalProperties");
		if(addProperties!=null) {
			//additionalProperties contains a schema object
			resultArray=merge(resultArray,parseSchema(addProperties,null,null),"Property");
		}else if(schema.has("additionalProperties")) {
			//additionalProperties is a boolean instead of an object
			for(int i=0; i<resultArray.length(); i++) {
				resultArray.getJSONObject(i).put("additionalProperties", schema.getBoolean("additionalProperties"));
			}
		}

		//parse properties under "unevaluatedProperties" keyword
		if(schema.has("unevaluatedProperties")) {
			resultArray=merge(resultArray,parseSchema(schema.getJSONObject("unevaluatedProperties"),null,null),"Property");
		}

		//parse items under "prefixItems" keyword
		if(schema.has("prefixItems")) {
			JSONArray prefixItems=schema.getJSONArray("prefixItems");
			for(int i=0; i<prefixItems.length(); i++) {
				JSONArray newitems=parseSchema(prefixItems.getJSONObject(i),null,null);
				resultArray=merge(resultArray,newitems,"Item");
			}
		}

		//parse item under "items" keyword
		JSONObject items=schema.optJSONObject("items");
		if(items!=null) {
			resultArray=merge(resultArray,parseSchema(items,null,null),"Item");
		}

		//parse item under "contains" keyword
		if(schema.has("contains")) {
			resultArray=merge(resultArray,parseSchema(schema.getJSONObject("contains"),null,null),"Item");
		}

		//parse items under "unevaluatedItems"
		if(schema.has("unevaluatedItems")) {
			resultArray=merge(resultArray,parseSchema(schema.getJSONObject("unevaluatedItems"),null,null),"Item");
		}

		//parse if-then-else
		if(schema.has("if")) {
			JSONArray tmpresult=new JSONArray(resultArray.toString());
			accMerge(resultArray,parseSchema(schema.getJSONObject("if"),null,null));
			if(schema.has("then")) {
				accMerge(resultArray,parseSchema(schema.getJSONObject("then"), null,null));
			}
			if(schema.has("else")) {
				accMerge(tmpresult,parseSchema(schema.getJSONObject("else"), null,null));
			}
			resultArray.putAll(tmpresult);
		}

		//parse "dependentSchemas"
		if(schema.has("dependentSchemas")) {
			JSONObject dep=schema.getJSONObject("dependentSchemas");
			for(String k : dep.keySet()) {
				accMerge(resultArray,parseSchema(dep.getJSONObject(k),null,null));
			}
		}

		//parse "allOf"
		if(schema.has("allOf")) {
			JSONArray allof=schema.getJSONArray("allOf");
			for(int i=0; i<allof.length(); i++) {
				accMerge(resultArray,parseSchema(allof.getJSONObject(i),null,null));
			}
		}

		//parse "anyOf" (identically with "allOf")
		if(schema.has("anyOf")) {
			JSONArray anyof=schema.getJSONArray("anyOf");
			for(int i=0; i<anyof.length(); i++) {
				accMerge(resultArray,parseSchema(anyof.getJSONObject(i),null,null));
			}
		}

		//parse "oneOf"
		if(schema.has("oneOf")) {
			JSONArray itemarr=new JSONArray();
			JSONArray oneof=schema.getJSONArray("oneOf");
			for(int i=0; i<oneof.length(); i++) {
				itemarr.putAll(parseSchema(oneof.getJSONObject(i),null,null));
			}
			accMerge(resultArray,itemarr);
		}

		//parse Encoding object if given
		if(encoding!=null) {
			for(int i=0; i<resultArray.length(); i++) {
				JSONArray properties=resultArray.getJSONObject(i).optJSONArray("Property");
				if(properties==null) {
					continue;
				}
				for(int j=0; j<properties.length(); j++) {
					JSONObject p=properties.getJSONObject(j);
					JSONObject e=encoding.optJSONObject(p.getString("name"));
					if(e!=null) {
						copyField(p,e,"contentType","style","explode");
						if(e.has("headers")) {
							p.put("Header", parseHeaders(e.getJSONObject("headers")));
						}
						p.put("allowReserved", e.optBoolean("allowReserved"));
						parseXProperties(p,e);
					}
				}
			}
		}
		return resultArray;
	}
	
	/*
	* inserts each key-value pair from source to dest, replacing objects with arrays if key exists already
	*/
	private static void accumulate(JSONObject dest, JSONObject source)throws Exception{
		for(String key : source.keySet()) {
			if(dest.has(key)) {
				Object destobj=dest.get(key);
				Object sourceobj=source.get(key);
				if(destobj.getClass()==JSONArray.class) {
					JSONArray destarr=(JSONArray)destobj;
					if(sourceobj.getClass()==JSONArray.class) {
						JSONArray sourcearr=(JSONArray)sourceobj;
						for(int i=0; i<sourcearr.length(); i++) {
							Object s=sourcearr.get(i);
							if(key.equals("Property")) {
								JSONObject sobj=(JSONObject)s;
								for(int j=0; j<destarr.length(); j++) {
									if(destarr.getJSONObject(j).getString("name").equals(sobj.getString("name"))) {
										accumulate(destarr.getJSONObject(j),sobj);
									}
								}
							}else if(!JSONArrayContains(destarr,s)){
								destarr.put(s);
							}
						}
					}else if(!JSONArrayContains(destarr,sourceobj)) {
						destarr.put(sourceobj);
					}
				}else if(sourceobj.getClass()==JSONArray.class) {
					JSONArray sourcearr=(JSONArray)sourceobj;
					if(!JSONArrayContains(sourcearr,destobj)) {
						JSONArray tmp=new JSONArray(sourcearr.toString());
						tmp.put(destobj);
						dest.put(key, tmp);
					}
				}else if(!destobj.equals(sourceobj)){
					dest.put(key, new JSONArray().put(destobj).put(sourceobj));
				}
			}else {
				dest.put(key, source.get(key));
			}
		}
	}
	
	/*
	* Checks if arr contains obj
	*/
	private static boolean JSONArrayContains(JSONArray arr, Object obj) {
		//if obj is JSONObject the function will always return false
		for(int i=0; i<arr.length(); i++) {
			if(arr.get(i).equals(obj)) {
				return true;
			}
		}
		return false;
	}
	
	/*
	* creates a copy of resultArray for each value in newproperty
	* and appends that value to each object in the corresponding copy 
	* of resultArray in an array named key, then merges all arrays and returns the result
	*/
	private static JSONArray merge(JSONArray resultArray,JSONArray newproperty,String key)throws Exception{
		JSONArray[] tmparr=new JSONArray[newproperty.length()];
		tmparr[0]=resultArray;
		for(int i=1; i<tmparr.length; i++) {
			tmparr[i]=new JSONArray(resultArray.toString());
		}
		for(int i=0; i<newproperty.length(); i++) {
			for(int j=0; j<resultArray.length(); j++) {
				tmparr[i].getJSONObject(j).append(key, newproperty.getJSONObject(i));
			}
		}
		for(int i=1; i<tmparr.length; i++) {
			tmparr[0].putAll(tmparr[i]);
		} 
		return tmparr[0];
	}
	
	/*
	* creates a copy of resultArray for each object in newarr
	* and accumulates that object with each object in the corresponding copy 
	*/
	private static void accMerge(JSONArray resultArray,JSONArray newarr) throws Exception{
		String baseStr=resultArray.toString();
		for(int k=0; k<resultArray.length(); k++) {
			accumulate(resultArray.getJSONObject(k), newarr.getJSONObject(0));
		}
		for(int j=1; j<newarr.length(); j++) {
			JSONArray tmp=new JSONArray(baseStr);
			for(int k=0; k<tmp.length(); k++) {
				accumulate(tmp.getJSONObject(k), newarr.getJSONObject(j));
			}
			resultArray.putAll(tmp);
		}
	}
	
	/*
	* Returns object corresponding to an entry in Parameter table
	*/
	private JSONObject parseParam(JSONObject param)throws Exception{
		JSONObject parsedParam=new JSONObject().put("name",param.getString("name"));

		//copy fields
		copyField(parsedParam,param,"in","description");
		parsedParam.put("required",param.optBoolean("required"));
		parsedParam.put("deprecated",param.optBoolean("deprecated"));
		parsedParam.put("allowEmptyValue",param.optBoolean("allowEmptyValue"));
		
		//copy "style" field
		String val;
		if(param.has("style")) {
			val=param.getString("style");
		}else if(param.getString("in").equals("path") || param.getString("in").equals("header")) {
			val="simple";
		}else {
			val="form";
		}
		parsedParam.put("style", val);

		//copy rest fields
		parsedParam.put("explode",param.optBoolean("explode",val.equals("form")));
		parsedParam.put("allowReserved", param.optBoolean("allowReserved"));
		parseXProperties(parsedParam,param);

		//parse Schema or Media Type object
		if(param.has("schema")) {
			parsedParam.put("Schema", parseSchema(param.getJSONObject("schema"),null,null));
		}else if(param.has("content")) {
			JSONObject tmp=parseContent(param.getJSONObject("content")).getJSONObject(0);
			copyField(parsedParam,tmp,"contentType","Schema","Example");
			parseXProperties(parsedParam,tmp);
		}

		//parse examples
		if(param.has("example") || param.has("examples")) {
			if(parsedParam.has("Example")) {
				parsedParam.getJSONArray("Example").putAll(parseExamples(param.opt("example"), param.optJSONObject("examples")));
			}else {
				parsedParam.put("Example", parseExamples(param.opt("example"), param.optJSONObject("examples")));
			}
		}

		return parsedParam;
	}
	
	/*
	* Returns array corresponding to Callback table
	*/
	private JSONArray parseCallbacks(JSONObject callbacks) throws Exception{
		JSONArray result=new JSONArray();
		for(String cname : callbacks.keySet()) {
			//parse each callback
			JSONObject callback=resolveRef(callbacks.getJSONObject(cname));
			for(String path : callback.keySet()) {
				JSONArray tmpresult=parsePathItem(path,callback.getJSONObject(path),null,null);
				for(int i=0; i<tmpresult.length(); i++) {
					tmpresult.getJSONObject(i).put("name", cname);
					parseXProperties(tmpresult.getJSONObject(i),callback);
				}
				result.putAll(tmpresult);
			}
		} 
		return result;
	}
	
	/*
	* Returns array corresponding to Webhook table
	*/
	private JSONArray parseWebhooks(JSONObject webhooks) throws Exception{
		JSONArray result=new JSONArray();
		for(String wname : webhooks.keySet()) {
			//parse each webhook
			JSONArray tmp=parsePathItem(null,webhooks.getJSONObject(wname),null,null);
			for(int i=0; i<tmp.length(); i++) {
				tmp.getJSONObject(i).put("name", wname);
			}
			result.putAll(tmp);
		}
		return result;
	}
	
	/*
	* Parses a Path Item object
	*/
	private JSONArray parsePathItem(String path, JSONObject pathitem, JSONArray baseSecurity, JSONArray baseServers) throws Exception{
		//path,baseSecurity and baseServers might be null
		JSONArray result=new JSONArray();

		//find and add referenced path item
		JSONObject resolvedItem=resolveRef(pathitem);
		if(resolvedItem!=null && pathitem!=resolvedItem) {
			for(String key : resolvedItem.keySet()) {
				pathitem.put(key, resolvedItem.get(key));
			}
		}

		//if servers given for all operations on this path, overwrite global servers
		if(pathitem.has("servers")) {
			baseServers=parseServers(pathitem.getJSONArray("servers"));
		}

		JSONObject baseRequest = new JSONObject();
		copyField(baseRequest,pathitem,"summary","description");

		//parse Parameters
		JSONArray params = pathitem.optJSONArray("parameters");
		if(params!=null) {
			for(int i=0; i<params.length(); i++) {
				baseRequest.append("Parameter",parseParam(resolveRef(params.getJSONObject(i))));
			}
		}

		parseXProperties(baseRequest,pathitem);
		String baseStr = baseRequest.toString();
		
		for(String method : httpMethods) {
			if(pathitem.has(method)) {
				//create separate request for each method
				JSONObject req = pathitem.getJSONObject(method);
				JSONObject newreq = new JSONObject(baseStr);
				if(path!=null) {
					newreq.put("path", path);
				}
				newreq.put("method", method);
				copyField(newreq,req,"summary","description","operationId","tags");

				//if servers defined for this method, overwrite global servers
				if(req.has("servers")) {
					newreq.put("Server",parseServers(req.getJSONArray("servers")));
				}else if(baseServers!=null) {
					newreq.put("Server", baseServers);
				}

				//if security requirements defined for this method, overwrite global security requirements
				if(req.has("security")) {
					newreq.put("Security",parseSecurityRequirements(req.getJSONArray("security")));
				}else if(baseSecurity!=null) {
					newreq.put("Security", baseSecurity);
				}

				//parse callbacks
				if(req.has("callbacks")) {
					newreq.put("Callback",parseCallbacks(req.getJSONObject("callbacks")));
				}

				parseExternalDocs(newreq,req.optJSONObject("externalDocs"));
				newreq.put("deprecated", req.optBoolean("deprecated"));
				parseXProperties(newreq,req);

				//if parameters defined for this operation, overwrite global ones
				if(req.has("parameters")) {
					JSONArray newparams=req.getJSONArray("parameters");
					if(!newreq.has("Parameter")) {
						//no global parameters defined, just append these parameters
						for(int i=0; i<newparams.length(); i++){
							newreq.append("Parameter", parseParam(resolveRef(newparams.getJSONObject(i))));
						}
					}else {
						//need to overwrite only global parameters having the same name as the new parameters defined
						JSONArray baseparams=newreq.getJSONArray("Parameter");
						for(int i=0; i<newparams.length(); i++) {
							JSONObject newparam=newparams.getJSONObject(i);
							for(int j=0; j<baseparams.length(); j++) {
								JSONObject baseparam=baseparams.getJSONObject(j);
								if(newparam.getString("name").equals(baseparam.getString("name")) && newparam.getString("in").equals(baseparam.getString("in"))) {
									baseparams.remove(j);
									break;
								}
							}
							newreq.append("Parameter",parseParam(resolveRef(newparam)));
						}
					}
				}

				//parse responses
				if(req.has("responses")) {
					newreq.put("Response", parseResponses(req.getJSONObject("responses")));
				}

				//parse Request Body object
				if(req.has("requestBody")) {
					JSONObject body = resolveRef(req.getJSONObject("requestBody"));

					copyField(newreq,"bodyDescription",body,"description");
					newreq.put("bodyRequired", body.optBoolean("required"));
					parseXProperties(newreq,body);

					//parse Media Type object
					JSONArray content= parseContent(body.getJSONObject("content"));
					String basereq=newreq.toString();
					for(int i=1; i<content.length(); i++) {
						JSONObject r=new JSONObject(basereq);
						JSONObject c=content.getJSONObject(i);
						copyField(r,c,"contentType","Schema","Example");
						parseXProperties(r,c);
						result.put(r);
					}
					JSONObject c=content.getJSONObject(0);
					copyField(newreq,c,"contentType","Schema","Example");
					parseXProperties(newreq,c);
				}

				result.put(newreq);
			}
		}
		return result;
	}
	
	/*
	* Returns array corresponding to Request table
	*/
	private JSONArray parseRequests(JSONObject paths, JSONArray baseSecurity, JSONArray baseServers)throws Exception{
		//baseSecurity and baseServers might be null
		JSONArray requests=new JSONArray();
		for(String path : paths.keySet()) {
			if(!path.startsWith("/")) { //avoid mistaking extension properties as paths
				continue;
			}
			JSONObject pathitem = paths.getJSONObject(path);
			requests.putAll(parsePathItem(path,pathitem,baseSecurity,baseServers));
		}
		for(int i=0; i<requests.length(); i++) {
			parseXProperties(requests.getJSONObject(i), paths);
		}
		return requests;
	}
	
	/*
	* Parses object under "content"
	*/
	private JSONArray parseContent(JSONObject content)throws Exception{
		JSONArray result=new JSONArray();
		for(String type : content.keySet()) {
			JSONObject typeobj=content.getJSONObject(type);
			JSONObject newtypeobj=new JSONObject();
			newtypeobj.put("contentType", type);
			if(typeobj.has("schema")) {
				newtypeobj.put("Schema",parseSchema(typeobj.getJSONObject("schema"),typeobj.optJSONObject("encoding"),null));
			}
			if(typeobj.has("example") || typeobj.has("examples")) {
				newtypeobj.put("Example", parseExamples(typeobj.opt("example"),typeobj.optJSONObject("examples")));
			}
			parseXProperties(newtypeobj,typeobj);
			result.put(newtypeobj);
		}
		return result;
	}
	
	/*
	* Returns array corresponding to Server table
	*/
	private JSONArray parseServers(JSONArray serv) throws Exception{
		JSONArray result=new JSONArray();
		for(int i=0; i<serv.length(); i++) {			
			result.put(parseServer(serv.getJSONObject(i)));
		}
		return result;
	}
	
	/*
	* Parses a Server object
	*/
	private JSONObject parseServer(JSONObject server) throws Exception{
		JSONObject newserver= new JSONObject();
		copyField(newserver,server,"url","description");
		parseXProperties(newserver,server);

		//create array corresponding to ServerVariable table
		JSONObject vars=server.optJSONObject("variables");
		if(vars!=null) {
			for(String keycounter : vars.keySet()) {
				newserver.append("ServerVariable",vars.getJSONObject(keycounter).put("name", keycounter));
			}
		}
		return newserver;
	}

	/*
	* Copies fields from External Documentation object into newobj
	*/
	private void parseExternalDocs(JSONObject newobj,JSONObject extdocsobj)throws Exception{
		if(extdocsobj==null) {
			return;
		}
		copyField(newobj,"extDocsDescription",extdocsobj,"description");
		copyField(newobj,"extDocsUrl",extdocsobj,"url");
		parseXProperties(newobj,extdocsobj);
	}
}
