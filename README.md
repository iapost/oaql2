OpenAPI Query Language 2 Service
================================
OpenAPI Query Language 2 (OAQL2) is a query language for OpenAPI documents. OpenAPI is a standard format for the description of RESTful services, based on JSON. OAQL2 is designed with syntax similar to SQL and supports querying most of the fields in an OpenAPI document, as well as the semantic annotations proposed for OpenAPI. This is an implementation of a web service capable of executing OAQL2 queries. This service stores metadata for each OpenAPI description and executes the queries on them. It builds indexes to speed up queries, can handle composite schema objects and uses reasoning to support searching in a semantic model. The web service consists of a Java server and a MongoDB database, both running inside Docker containers.

## Installation
You need to have Docker and Docker Compose installed.

Execute the following command inside the root directory of the project:

    $ docker-compose up --build
    
Once the server is ready, it will output the message "Server started".

## Usage
The server listens for HTTP requests on port 80 of the host machine. You can connect to it with an internet browser (eg. Firefox) for a graphical user interface. A list of all available endpoints is shown below:

| Path                | Accepts                                                                                               | Returns                                                                                                                          |
|---------------------|-------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| /                   | GET Request                                                                                           | HTML document providing a GUI for inserting or retrieving OpenAPI descriptions and executing OAQL2 queries            |
| /insertDescription  | POST request.<br /> Request body must be a valid OpenAPI description & 204 code with no response body | 204 code with no response body                                                                                                   |
| /query              | POST request.<br /> Request body must be a valid OAQL2 query                                          | 200 code with the results of the query in the response body                                                                      |
| /description/\<id\> | GET request.<br /> \<id\> must be 24 characters long                                                  | 200 code with the requested OpenAPI description in the response body or 404 code if there is no OpenAPI description with that id |

If the server encounters an error, it will respond with a status code of 400 and an error message in the response body. 

## Database of OpenAPI documents
For convenience, the [`database`](database) directory contains 10000 OpenAPI descriptions, taken from Swaggerhub, in compressed form (split in two parts due to Github's file size restrictions). It also contains a bash script to automatically send these documents to the service. To use the script, execute the following command inside [`database`](database):

    $ ./insert.sh <hostname> 

\<hostname\> should be the address of the service. Note that you need to have the *curl* package installed.

## License
Distributed under the GPL-3.0 License. See [`LICENSE`](LICENSE) for more information.

## Notes
- Currently, only OpenAPI documents conforming to OpenAPI Specification v3.1.0 are supported

## References
- I. Apostolakis, N. Mainas and E.G.M. Petrakis, "Simple querying service for OpenAPI descriptions with semantic extensions", *Information Systems* 117 (2023), 102241, https://doi.org/10.1016/j.is.2023.102241
- I. Apostolakis, "Simple querying service for OpenAPI descriptions with Semantic Web extensions", Diploma thesis, School of Electrical and Computer Engineering, Technical University of Crete (TUC), Chania, Crete, Greece (May 2022), https://dias.library.tuc.gr/view/92123
