/** @file Server.java */

package oaql2;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map.Entry;
import org.json.JSONObject;
import org.json.JSONArray;
import org.apache.jena.ext.com.google.common.io.Files;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.concurrent.Executors;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * The server of the OAQL2 service
 */
public class Server implements HttpHandler{

	/** The name of the MongoDB database */
	static String dbName = "openapiDB";

	/** The name of the MongoDB collection holding the metadata objects */
	static String collectionName = "metadataCollection";

	/** The name of the MongoDB collection holding the original OpenAPI descriptions */
	static String originalDescriptionsCollectionName = "originalDescriptions";

	/** The URL of the semantic model used */
	static String semanticModelUrl = "https://schema.org/version/latest/schemaorg-current-https.nt";

	/** The language the semantic model is written in*/
	static String semanticModelLang = "N-TRIPLE";

	/** The endpoint at the server for requests to insert an OpenAPI description */
	static String insertDescriptionPath = "/insertDescription";

	/** The endpoint at the server for requests with OAQL2 queries */
	static String queryPath = "/query";

	/** The endpoint at the server for requests to retrieve the original OpenAPI description */
	static String descriptionPath = "/description/";

	/** The path in the Docker container of the HTML file providing the GUI */
	static File htmlIndex = new File("/usr/src/mymaven/html/index.html");

	/** Holds the semantic model */
	static InfModel semModel;

	/** Holds the URL for the MongoDB service */
	String mongoUrl;
	
	/**
	 * Constructor that loads semantic model, creates indexes in MongoDB if necessary and starts the server
	 * 
	 * @param port the port of the host machine to listen on for requests
	 * @param mongoHostName the hostname of the MongoDB service
	 * @param mongoPort the port of the MongoDB service that the server should send requests to
	 * 
	 */
	public Server(int port, String mongoHostName, int mongoPort) throws IOException {
		mongoUrl = "mongodb://" + mongoHostName + ":" + mongoPort;
		
		//load semantic model and apply reasoner
		semModel = ModelFactory.createInfModel(ReasonerRegistry.getTransitiveReasoner(), ModelFactory.createDefaultModel());
		semModel.read(semanticModelUrl, semanticModelLang);
		
		//create indexes in MongoDB if they do not already exist. If MongoDB is down, keep trying
		while(!createIndexes());
		
		//configure and start server
		HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
		server.createContext("/", this);
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		System.out.println("Server started");
	}
	
	/**
	 * Queries the reasoner and returns subclasses of given value
	 * 
	 * @param uri the URI of a resource in the semantic model
	 * @return a list with all subclasses of the provided resource
	 * 
	 */
	public static ArrayList<String> getSubclassesInModel(String uri){
		String query = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> SELECT ?c WHERE { ?c rdfs:subClassOf|rdfs:subPropertyOf <" + uri + "> }";
	    ResultSet results = QueryExecutionFactory.create(query, semModel).execSelect();
	    ArrayList<String> result = new ArrayList<String>();
		while(results.hasNext()){
	    	result.add(results.nextSolution().getResource("c").toString());
	    }
		if(result.size() == 0) {
			result.add(uri);
		}
		return result;
	}
	
	/**
	 * Handler for HTTP requests
	 * 
	 * @param ex the HttpExchange object of the HTTP request
	 * 
	 */
	public void handle(HttpExchange ex){
		try {
			try{
				if(ex.getRequestURI().getPath().equals(insertDescriptionPath)) {
					new DescriptionParser().handleExchange(mongoUrl, ex);
				}else if(ex.getRequestURI().getPath().equals(queryPath)){
					parseQuery(ex);
				}else if(ex.getRequestURI().getPath().startsWith(descriptionPath)){
					getOriginalDescription(ex);
				}else if(ex.getRequestURI().getPath().equals("/")) {
					ex.getResponseHeaders().put("Content-Type", Arrays.asList("text/html; charset=UTF-8"));
					ex.sendResponseHeaders(200, 0);
					Files.copy(htmlIndex, ex.getResponseBody());
					ex.close();
				}else {
					ex.sendResponseHeaders(404, -1);
					ex.close();
				}
			}catch(Error e){
				throw new Exception("Error: possibly ran out of heap space: " + e.getMessage());
			}
		}catch (Exception e) {
			//some error occured, return status code 400 and error message in response body
			try {
				ex.sendResponseHeaders(400, 0);
			}catch (IOException exc) {
				return;
			}
			PrintWriter p = new PrintWriter(ex.getResponseBody());
			p.write(e.getMessage());
			p.close();
			ex.close();
		}
	}
	
	/**
	 * Translates OAQL2 query to a MongoDB pipeline, executes the search and returns the result. 
	 * Also prints total execution time and number of results
	 * 
	 * @param ex the HttpExchange object of the HTTP request to execute a query
	 * 
	 */
	@SuppressWarnings("deprecation")
	public void parseQuery(HttpExchange ex) throws Exception {
		long startTime = System.currentTimeMillis();
		
		//only accept POST requests
		if(!ex.getRequestMethod().equals("POST")){
			throw new Exception("Only supporting POST requests");
		}
		
		JSONArray responseArr = new JSONArray();
		
		//give OAQL2 query to the parser and receive the MongoDB pipeline
		InputStreamReader input = new InputStreamReader(ex.getRequestBody()); 
		ArrayList<String> exclusionList = new ArrayList<String>();
		ArrayList<Document> pipeline = new Parser(new Lexer(input)).getPipeline(exclusionList);
		input.close();
		
		//execute query
		MongoClient cli = MongoClients.create(mongoUrl);
		MongoDatabase db = cli.getDatabase(Server.dbName);
		MongoCollection<Document> coll = db.getCollection(Server.collectionName);
		for(Document doc : coll.aggregate(pipeline).allowDiskUse(true)) {
			JSONObject newobj = new JSONObject();
			
			//flatten any objects corresponding to <table>.* in SELECT clause
			for(String s : exclusionList) {
				Object obj = doc.remove(s);
				if(obj == null) {
					continue;	
				}
				Document tmp = (Document)obj;
				for(Entry<String, Object> e : tmp.entrySet()) {
					Object tmpval = e.getValue();
					if(tmpval != null) {
						newobj.put(s + "." + e.getKey(), tmpval);
					}
				}
			}
			
			//replace @ with . in keys of fields
			for(Entry<String, Object> e : doc.entrySet()) {
				Object tmp = e.getValue();
				if(tmp != null) {
					newobj.put(e.getKey().replace('@', '.'), tmp);
				}
			}
			
			//add object to result if it is not empty
			if(newobj.length() > 0) {
				responseArr.put(newobj);
			}
			
		}
		cli.close();
		
		//return status code 200 with the resulting array
		ex.getResponseHeaders().put("Content-Type", Arrays.asList("application/json"));
		ex.sendResponseHeaders(200, 0);
		PrintWriter p = new PrintWriter(ex.getResponseBody());
		p.write(responseArr.toString());
		p.close();
		ex.close();
		
		//print execution time and number of results in stdout
		long endTime = System.currentTimeMillis();
		System.out.println("time: " + (endTime - startTime) + "ms, number of results: " + responseArr.length());
	}
	
	/**
	 * Finds and returns the OpenAPI description corresponding to the provided id
	 * 
	 * @param ex the HttpExchange object of the HTTP request to insert a description
	 * 
	 */
	public void getOriginalDescription(HttpExchange ex) throws Exception{
		//only accept GET requests
		if(!ex.getRequestMethod().equals("GET")){
			throw new Exception("Only supporting GET requests");
		}
		
		//get id from request path and check its length
		String id = ex.getRequestURI().getPath().substring(descriptionPath.length());
		if(id.length() != 24) {
			throw new Exception("id needs to be 24 characters long");
		}
		
		//find the OpenAPI description in MongoDB with the specified id
		MongoClient cli = MongoClients.create(mongoUrl);
		MongoDatabase db = cli.getDatabase(dbName);
		MongoCollection<Document> coll = db.getCollection(originalDescriptionsCollectionName);
		Document res = coll.find(new Document("_id", new ObjectId(id))).projection(new Document("_id", 0)).first();
		
		if(res == null) {
			//return status code 404 because no description was found with this id
			ex.sendResponseHeaders(404, -1); 
		}else {
			//return status code 200 and the description that was found in the response body
			ex.getResponseHeaders().put("Content-Type", Arrays.asList("application/json"));
			ex.sendResponseHeaders(200, 0);
			PrintWriter p = new PrintWriter(ex.getResponseBody());
			p.write(res.toJson());
			p.close();
		}
		ex.close();
	}
	
	/**
	 * Creates the specified indexes in MongoDB if they do not already exist
	 * 
	 * @return true if indexes are successfully created or were already existing, false otherwise
	 * 
	 */
	public boolean createIndexes() {
		try {
			//connect to MongoDB
			MongoClient cli = MongoClients.create(mongoUrl);
			MongoDatabase db = cli.getDatabase(Server.dbName);
			MongoCollection<Document> coll = db.getCollection(Server.collectionName);
			
			//get a list of existing indexes
			MongoCursor<Document> curs = coll.listIndexes().iterator();
			curs.tryNext();
			if(curs.tryNext() == null) {
				//if at most 1 index exists, create specified indexes
				for(String p : Model.indexPaths) {
					coll.createIndex(new Document(p, 1));
				}
			}
			cli.close();
		}catch(Exception e) {
			//an error probably means that the MongoDB service has not started running yet
			System.out.println("Attempt to connect to Mongo failed. Retrying...");
			return false;
		}
		return true;
	}
	
	/**
	 * Starts the server
	 */
	public static void main(String[] args) throws Exception {	
		new Server(80, "mongo", 27017);
	}
}