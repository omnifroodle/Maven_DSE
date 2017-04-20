# DSE Cassandra Java interaction demo
This demo shows the basics of interacting with data using the DSE Java driver.

Included:
* read data
* insert data with bound statements
* interact with Graph
* interact with Solr

Note: that interacting with Graph and Solr require those features be enabled on your cluster.  

The Solr examples require you to create a solr core with dsetool, and for the phonetic example you would need to create a custome schema.xml.


The schema for the Search keyspace used here is:

```sql
CREATE KEYSPACE search WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'}  AND durable_writes = true;

CREATE TABLE search.people (
    first_name text,
    last_name text,
    solr_query text,
    PRIMARY KEY ((first_name, last_name))
) WITH bloom_filter_fp_chance = 0.01
    AND caching = {'keys': 'ALL', 'rows_per_partition': 'NONE'}
    AND comment = ''
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy', 'max_threshold': '32', 'min_threshold': '4'}
    AND compression = {'chunk_length_in_kb': '64', 'class': 'org.apache.cassandra.io.compress.LZ4Compressor'}
    AND crc_check_chance = 1.0
    AND dclocal_read_repair_chance = 0.1
    AND default_time_to_live = 0
    AND gc_grace_seconds = 864000
    AND max_index_interval = 2048
    AND memtable_flush_period_in_ms = 0
    AND min_index_interval = 128
    AND read_repair_chance = 0.0
    AND speculative_retry = '99PERCENTILE';
```
