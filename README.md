OpenAPI Query Language 2 Service
================================
#### A service for querying OpenAPI documents ####
This is an implementation of a web service able to store OpenAPI documents and execute OAQL2 (OpenAPI Query Language 2) queries on them. The web service consists of a Java server and a MongoDB database.

#### Installation ####
You need to have Docker and Docker Compose installed.

Execute the following command inside the root directory of the project:

    $ docker-compose up --build
    
Once the server is ready, it will output a message "Server started".

#### Usage ####
The server listens for HTTP requests on port 80 of the host machine. The available endpoints are shown in the table below:

| Path                | Accepts                                                                                               | Returns                                                                                                                          |
|---------------------|-------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| /                   | GET Request                                                                                           | HTML document providing a user interface for inserting or retrieving OpenAPI descriptions and executing OAQL2 queries            |
| /insertDescription  | POST request.<br /> Request body must be a valid OpenAPI description & 204 code with no response body | 204 code with no response body                                                                                                   |
| /query              | POST request.<br /> Request body must be a valid OAQL2 query                                          | 200 code with the results of the query in the response body                                                                      |
| /description/\<id\> | GET request.<br /> \<id\> must be 24 characters long                                                  | 200 code with the requested OpenAPI description in the response body or 404 code if there is no OpenAPI description with that id |

If the server encounters an error, it will respond with a status code of 400 and an error message in the response body. 

#### Other files ####
For convenience, the *utils* folder contains a zip file with 1000 OpenAPI descriptions taken from Swaggerhub. It also contains a bash script to automatically send these descriptions to the service. To use the script, execute the following command inside *utils* directory:

    $ ./insert.sh <hostname> 

\<hostname\> should be the address of the service. Note that you need to have the *curl* package installed.

#### Notes ####
- Currently, only OpenAPI documents conforming to OpenAPI Specification v3.1.0 are supported
- This web service was implemented as part of a diploma thesis in the School of Electrical & Computer Engineering of the Technical University of Crete
