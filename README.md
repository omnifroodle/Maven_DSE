# DSE Cassandra Java interaction demo
This demo shows the basics of interacting with data using the DSE Java driver.

Included:
* Read data
* Insert data with bound statements
* Insert data with the QueryBuilder
* Insert data with the Object Mapper
* Interact with DSE Graph
* Interact with DSE Search

Note: that interacting with Graph and Solr require those features be enabled on your cluster.  The are enabled by default if you download DSE using the following instructions.  

## Try it out

To try this in your local development environment, first download Datastax Enterprise from [https://academy.datastax.com/downloads/welcome](https://academy.datastax.com/downloads/welcome).  You'll need a free account in order to download.

Follow the instructions provided with the download to install and start DSE Cassandra.

Once you are up and running you'll need to setup our test schema. From your command window change into the DSE directory and run: 

```bash
$ bin/cqlsh
```

When the CQL shell is loaded copy and paste the following:
```sql
CREATE KEYSPACE search WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'}  AND durable_writes = true;

CREATE TABLE search.people (
    first_name text,
    last_name text,
    PRIMARY KEY ((first_name, last_name))
);
```
Give Cassandra a minute or so to get going, then open up your Java IDE and run the Main class.

## Adding Solr support

The Solr Examples will require you to enable a Solr core for the search column family.

```sql
$ dsetool create_core search.people generateResources=true
$ dsetool reload_core search.people schema=docs/schema.xml reindex=true
```

NOTE: Make sure to change the "schema=" command to point to the "docs" folder in this project.