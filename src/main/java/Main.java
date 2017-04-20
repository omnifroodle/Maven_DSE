import com.datastax.driver.dse.DseCluster;
import com.datastax.driver.dse.DseSession;
import com.datastax.driver.core.*;
import com.datastax.driver.dse.graph.GraphResultSet;
import com.datastax.driver.dse.graph.GraphStatement;
import com.datastax.driver.dse.graph.SimpleGraphStatement;
// for async access
import com.google.common.base.Function;
import com.google.common.util.concurrent.*;


public class Main {
    public static void main(String[] args) {


        DseCluster cluster = null;
        try {
            cluster = DseCluster.builder()
                    .addContactPoint("127.0.0.1")
                    .build();
            DseSession session = cluster.connect();

            // Get some information about the cluster
            getVersion(session);

            // Get a row from a Column Family in the cluster
            executeSelect(session);

            // Insert some rows into a column family
            String first_name = "Nancey";
            String last_name = "Sixt";
            executeBoundInsert(session, first_name, last_name);

            executeAsyncQuery(cluster);


            // Additional DSE features
            // Graph
            executeGraphQuery(session);

            // Solr Searching
            executeSolrSearch(session);
            // Very fuzzy Solr searching
            executeSolrPhoneticSearch(session);

        } finally {
            if (cluster != null) cluster.close();                                        // (5)
        }
    }

    private static void getVersion(DseSession session) {
        Row row = session.execute("select release_version from system.local").one(); // (3)
        System.out.format("Cassandra Release Version: %s\n", row.getString("release_version")); // (4)
    }

    private static void executeSelect(DseSession session) {
        System.out.println("Finding a row with a simple statement");

        ResultSet rs = session.execute("select * from search.people where last_name = 'Smith' and first_name = 'John';");

        for (Row person : rs) {
            System.out.format("%s, %s\n", person.getString("last_name"), person.getString("first_name"));
        }
    }

    // From http://docs.datastax.com/en/developer/java-driver-dse/1.2/manual/statements/prepared/
    private static void executeBoundInsert(DseSession session, String first_name, String last_name) {
        System.out.println("Insert a row with a prepared statement");

        PreparedStatement prepared = session.prepare("Insert into search.people (last_name, first_name) values (?, ?)");

        BoundStatement bound = prepared.bind(last_name, first_name);
        session.execute(bound);

        // Now reverse the name, to demo prepared statements
        bound = prepared.bind(first_name, last_name);
        session.execute(bound);
    }

    // From http://docs.datastax.com/en/developer/java-driver-dse/1.2/manual/async/
    private static void executeAsyncQuery(DseCluster cluster) {
        // For historical reasons, this returns a Future of Session, not DseSession. But you can safely cast to DseSession in
        // your callbacks if you need to access DSE-specific features. Here it doesn't really matter because we only use CQL
        // features.
        ListenableFuture<Session> session = cluster.connectAsync();

        // Use transform with an AsyncFunction to chain an async operation after another:
        ListenableFuture<ResultSet> resultSet = Futures.transformAsync(session,
                new AsyncFunction<Session, ResultSet>() {
                    public ListenableFuture<ResultSet> apply(Session session) throws Exception {
                        return session.executeAsync("select release_version from system.local");
                    }
                });

        // Use transform with a simple Function to apply a synchronous computation on the result:
        ListenableFuture<String> version = Futures.transform(resultSet,
                new Function<ResultSet, String>() {
                    public String apply(ResultSet rs) {
                        return rs.one().getString("release_version");
                    }
                });

        // Use a callback to perform an action once the future is complete:
        Futures.addCallback(version, new FutureCallback<String>() {
            public void onSuccess(String version) {
                System.out.printf("DSE version: %s%n", version);
            }

            public void onFailure(Throwable t) {
                System.out.printf("Failed to retrieve the version: %s%n",
                        t.getMessage());
            }
        });
    }

    // From http://docs.datastax.com/en/developer/java-driver-dse/1.2/manual/graph/
    private static void executeGraphQuery(DseSession session) {
        session.executeGraph("system.graph('graph_demo').ifNotExists().create()");

        GraphStatement s1 = new SimpleGraphStatement("g.addV(label, 'test_vertex')").setGraphName("graph_demo");
        session.executeGraph(s1);

        GraphStatement s2 = new SimpleGraphStatement("g.V()").setGraphName("graph_demo");
        GraphResultSet rs = session.executeGraph(s2);
        System.out.println(rs.one().asVertex());
    }

    private static void executeSolrSearch(DseSession session) {
        System.out.println("Searching for a value with DSE Solr");
        ResultSet rs = session.execute("SELECT count(*) FROM search.people WHERE solr_query = 'last_name:Jones'");

        System.out.format("%d records for last_name:Jones", rs.one().getLong(0));
    }

    private static void executeSolrPhoneticSearch(DseSession session) {
        //this requires a customer Solr Schema to perform phonetic encoding on the last_name field
        System.out.println("Searching for phonetic match with DSE Solr");
        ResultSet rs = session.execute("SELECT count(*) FROM search.people WHERE solr_query = 'last_sounds:Jones'");

        System.out.format("%d records for last_name that sound like Jones", rs.one().getLong(0));
    }


}
