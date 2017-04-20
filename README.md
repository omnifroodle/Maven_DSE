# DSE Cassandra Java interaction demo
This demo shows the basics of interacting with data using the DSE Java driver.

Included:
* Read data
* Insert data with bound statements
* Insert data with the QueryBuilder
* Insert data with the Object Mapper
* Interact with DSE Graph
* Interact with DSE Search

Note: that interacting with Graph and Solr require those features be enabled on your cluster.  

The Solr examples require you to create a solr core with `dsetool`, and for the phonetic example you would need to create a custom schema.xml.


The schema for the `search` keyspace used here is:

```sql
CREATE KEYSPACE search WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'}  AND durable_writes = true;

CREATE TABLE search.people (
    first_name text,
    last_name text,
    PRIMARY KEY ((first_name, last_name))
);
```
