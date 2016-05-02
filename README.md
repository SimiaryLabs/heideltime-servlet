# heideltime-servlet
A servlet wrapper for the HeidelTime temporal tagger that returns the result in JSON format.  This is a work-in-progress and currently the English parsers are implemented as services.

### Installation

The distribution code is found in the TemporalTagger.war file, which should be added to your webapps directory.

### Usage
Each style of tagger (narrative, news, scientific, and colloquial) is implemented as its own service.  Either GET or POST request can be used.  See the HeidelTime project for more information on these different styles.

Narrative style English:

`/TaggerEnglishNarratives`

News style English:

`/TaggerEnglishNews`

Scientific style English:

`/TaggerEnglishScientific`

Colloquial style English:

`/TaggerEnglishColloquial`

Connecting with these servers will return the HeidelTime XML result refactored into a JSON format.

#### Parameters
There are two parameters to send to the servlet:

**q** : The text to be parsed.
**date** : The base date reference of the document (e.g., publication date).

### Compilation
HeidelTime Servlet requires the following code to compile.

JSON-java:
`https://github.com/stleary/JSON-java`

HeidelTime temporal tagger: 
`https://github.com/HeidelTime/heideltime`

The HeidelTime configuration files and resources must be installed under the WEB-INF directory as in the normal HeidelTime install.

