# Scala Mongo DAO (SMD)

SMD provides a wrapper around ReactiveMongo to minimize the amount of boiler plate code that you need to utilize 
mongodb from scala. SMD only requires that you define a case class and a JSON formatter to use all the CRUD methods of
reactive mongo.

SMD provides built-in Rest API processing so that a minimal number of files need to be added for new api calls. For each domain object that needs to be served up via the api, a new domain case class and object need to be created, a new controller file is created that contains the collection name,  and new routes added to the play routes file. That's it.

This code has been used in production now since June 2015

This project is based on the work of Pedro De Almeida https://gist.github.com/almeidap/5685801, the work of Trialfire's Max Kremer and Marconi Lanna http://www.slideshare.net/MaxKremer/play-scala-reactive-mongo, StormPath http://docs.stormpath.com/rest/product-guide/#rest-api-general-concepts, and Eugene Burmako's reflection help

Key Goals met by this framework:
1. Immutable
2. Asynchronous
3. Streaming. Supports both the ReactiveMongo Enumerator and BSONDocument Source.
4. Minimal new files added/updated for every new domain object. New Domain file (case class and companion object), new empty controller which sets the type of the Domain object and new routes are the only files that need to get touched for adding basic rest calls of GET, POST, PUSH, DELETE, and GET (multiple).
5. A REST api that allows all basic CRUD commands as well as a builtin filtering capability for bringing down or deleting multiple domain objects
6. A REST api with paging built in

This framework does not use the JSON COAST-TO-COAST capabilities of ReactiveMongo. I found that using BSON and providing specific BSON Readers and Writers gave me the greatest flexibility to use Mongo specific datatypes in the database and only Scala types in the domain objects. It is also very clear from the mappings in the companion object of what is actually transpiring.

An example project for using this library with play can be found here: https://github.com/berryware/reactive-rest-mongo

The unit tests included in smd-core show how to use the library outside of play
 